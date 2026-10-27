package team.nomi.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/** 装配可选导航集成，管理受支持库存的会话与管理员审计入口。 */
public final class MenuReturnBridge extends JavaPlugin implements Listener {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private final Map<UUID, InventorySession> sessions = new ConcurrentHashMap<>();
    private final Map<ClassLoader, String> owners = new HashMap<>();
    private ReturnButton button;
    private PetNavigation petNavigation;

    /** 注册自身命令和已安装的可选集成，保留既有 PDC 命名空间。 */
    @Override
    public void onEnable() {
        button = new ReturnButton(new NamespacedKey(this, "return_to_menu"));
        for (var plugin : getServer().getPluginManager().getPlugins()) {
            if (Set.of("FarmersDelight", "PlayerInv").contains(plugin.getName())) {
                owners.put(plugin.getClass().getClassLoader(), plugin.getName());
            }
        }
        getCommand("nuomimenu").setExecutor(new PublicMenus(this));
        getServer().getPluginManager().registerEvents(this, this);
        var pet = getServer().getPluginManager().getPlugin("MyPet");
        if (pet != null && pet.isEnabled()) {
            try {
                petNavigation = new PetNavigation(this);
                getServer().getPluginManager().registerEvents(petNavigation, this);
            } catch (LinkageError exception) {
                getLogger().log(Level.WARNING, "MyPet navigation unavailable: requires MyPet 4.0.4 GUI API", exception);
            }
        }
    }

    /** 停止所有会话任务，不在插件禁用阶段访问玩家库存。 */
    @Override
    public void onDisable() {
        if (petNavigation != null) {
            petNavigation.shutdown();
        }
        sessions.values().forEach(InventorySession::stop);
        sessions.clear();
    }

    private void stop(UUID playerId) {
        var previous = sessions.remove(playerId);
        if (previous != null) {
            previous.stop();
        }
    }

    private String owner(Inventory inventory) {
        var holder = inventory.getHolder(false);
        String owner = holder == null ? null : owners.get(holder.getClass().getClassLoader());
        if (owner != null) {
            return owner;
        }
        // 原生菜单可能没有 holder；只从已登记插件的调用栈识别，不凭标题认领其他插件。
        return WALKER.walk(frames -> frames
            .map(frame -> owners.get(frame.getDeclaringClass().getClassLoader()))
            .filter(Objects::nonNull)
            .findFirst().orElse(""));
    }

    /** 在成功打开受支持窗口时建立会话；新窗口始终使旧路由失效。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        stop(player.getUniqueId());
        Inventory inventory = event.getInventory();
        if (!Set.of(InventoryType.CHEST, InventoryType.HOPPER, InventoryType.DISPENSER).contains(inventory.getType())) {
            return;
        }
        var holder = inventory.getHolder(false);
        var session = new InventorySession(inventory, owner(inventory),
            PlainTextComponentSerializer.plainText().serialize(event.getView().title()),
            holder == null ? "" : holder.getClass().getName());
        if (!session.isSupported()) {
            return;
        }
        sessions.put(player.getUniqueId(), session);
        // Paper/Folia 库存读写随玩家实体调度；定时刷新兼容原插件异步于打开事件的填充。
        session.refresh = player.getScheduler().runAtFixedRate(this, task -> {
            if (player.getOpenInventory().getTopInventory() != inventory) {
                task.cancel();
                sessions.remove(player.getUniqueId(), session);
                session.stop();
                return;
            }
            session.refreshButtons(button);
        }, () -> sessions.remove(player.getUniqueId(), session), 1, 5);
    }

    /** 只拦截当前会话上层库存内的返回项，下一 tick 再打开公共菜单。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        var session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory
            || !session.allowsSlot(event.getRawSlot()) || !button.isNavigation(event.getCurrentItem())) {
            return;
        }
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
        session.cancelRoute();
        session.route = player.getScheduler().runDelayed(this, task -> {
            if (sessions.get(player.getUniqueId()) != session
                || player.getOpenInventory().getTopInventory() != session.inventory) {
                return;
            }
            stop(player.getUniqueId());
            // 已在玩家实体线程且刚核验源窗口；直接完成返回，避免公共命令再排一 tick 后失去生命周期约束。
            new TrMenuGateway(this).open(player, "Menu");
        }, null, 1);
    }

    /** 仅关闭匹配的旧库存，避免迟到的关闭事件删除新会话。 */
    @EventHandler
    public void close(InventoryCloseEvent event) {
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.inventory == event.getInventory()) {
            stop(event.getPlayer().getUniqueId());
        }
    }

    /** 退出时取消该玩家的刷新和待执行返回。 */
    @EventHandler
    public void quit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    /** 管理员边界断言；audit 在每位玩家实体线程检查当前库存标记。 */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission("menureturn.audit")) {
            return true;
        }
        try {
            Navigation.verify();
            sender.sendMessage("Navigation boundary assertions passed; active sessions=" + sessions.size());
        } catch (AssertionError exception) {
            getLogger().log(Level.SEVERE, "Navigation assertions failed", exception);
            sender.sendMessage("Navigation assertions FAILED");
            return true;
        }
        if (arguments.length == 0 || arguments[0].equalsIgnoreCase("audit")) {
            for (Player player : getServer().getOnlinePlayers()) {
                player.getScheduler().run(this, task -> audit(player), null);
            }
        }
        return true;
    }

    private void audit(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || player.getOpenInventory().getTopInventory() != session.inventory) {
            return;
        }
        int marked = session.audit(button);
        getLogger().info("AUDIT player=" + player.getUniqueId() + " owner=" + session.owner + " marked=" + marked);
    }
}
