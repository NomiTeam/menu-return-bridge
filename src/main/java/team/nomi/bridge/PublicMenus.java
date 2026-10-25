package team.nomi.bridge;

import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 仅允许玩家为自己打开白名单菜单，不执行目标菜单的业务。 */
final class PublicMenus implements CommandExecutor {
    private final JavaPlugin plugin;
    private final TrMenuGateway menus;

    PublicMenus(JavaPlugin plugin) {
        this.plugin = plugin;
        this.menus = new TrMenuGateway(plugin);
    }

    /** 校验参数数量并返回原生菜单 ID；null 表示拒绝路由。 */
    static String menuId(String[] arguments) {
        if (arguments.length == 0) {
            return "Menu";
        }
        if (arguments.length != 1) {
            return null;
        }
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> "Menu";
            case "activity" -> "Activity";
            case "vipcontrol" -> "VipControl";
            case "sponsor" -> "Sponsor";
            case "dailyquestreset" -> "DailyQuestReset";
            default -> null;
        };
    }

    /** 将已校验的自身菜单请求交给玩家实体线程执行。 */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open their own menu.");
            return true;
        }
        String menuId = menuId(arguments);
        if (menuId == null) {
            sender.sendMessage("/nuomimenu [Menu|Activity|VipControl|Sponsor|DailyQuestReset]");
            return true;
        }
        // Paper/Folia 上的库存打开必须归属该玩家，不能切到全局调度器。
        player.getScheduler().run(plugin, task -> menus.open(player, menuId), null);
        return true;
    }
}
