package team.nomi.bridge;

import de.Keyle.MyPet.api.gui.SlotSection;
import de.Keyle.MyPet.gui.MenuInstanceImpl;
import de.Keyle.MyPet.gui.context.PetMenuContext;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** 只桥接四个 MyPet 原生菜单动作，优先使用 MyPet 自己的返回父栈。 */
final class PetNavigation implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, ScheduledTask> pending = new ConcurrentHashMap<>();

    PetNavigation(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private void cancel(UUID playerId) {
        var task = pending.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    void shutdown() {
        pending.values().forEach(ScheduledTask::cancel);
        pending.clear();
    }

    /** 关闭窗口使延迟动作失效，防止关闭后再跳回菜单。 */
    @EventHandler
    public void close(InventoryCloseEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    /** 换菜单取消旧动作；被取消的打开事件不算成功切换。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    /** 玩家退出后不保留路由任务。 */
    @EventHandler
    public void quit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    /** 先匹配允许的原生 section，再取消点击，保留 reset 及未知动作的所有权。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || !(event.getView().getTopInventory().getHolder(false) instanceof MenuInstanceImpl menu)) {
            return;
        }
        String action = actionAt(menu, event.getRawSlot());
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        cancel(player.getUniqueId());
        // 原生菜单关闭/打开不能在点击处理栈内进行，也不能放到全局线程。
        var scheduled = player.getScheduler().runDelayed(plugin, task -> {
            pending.remove(player.getUniqueId(), task);
            if (player.getOpenInventory().getTopInventory().getHolder(false) != menu) {
                return;
            }
            navigate(player, menu, action);
        }, () -> pending.remove(player.getUniqueId()), 1);
        if (scheduled != null) {
            pending.put(player.getUniqueId(), scheduled);
        }
    }

    private String actionAt(MenuInstanceImpl menu, int rawSlot) {
        for (var entry : menu.definition().sections().entrySet()) {
            String action = Navigation.action(entry.getKey());
            if (action != null && entry.getValue() instanceof SlotSection section
                && rawSlot == section.row() * 9 + section.col()) {
                return action;
            }
        }
        return null;
    }

    private void navigate(Player player, MenuInstanceImpl menu, String action) {
        if (!action.equals("return")) {
            player.performCommand(action);
            return;
        }
        if (menu.context() instanceof PetMenuContext) {
            menu.close();
            // 受控返回已在玩家实体线程，关闭与打开在同一回调完成，不留下无主的第二阶段任务。
            new TrMenuGateway(plugin).open(player, "Menu");
        } else if (!menu.popBack()) {
            // 子菜单先走 MyPet 原生父栈；无父项时才退回原生命令。
            menu.close();
            player.performCommand("pet");
        }
    }
}
