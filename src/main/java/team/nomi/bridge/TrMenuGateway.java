package team.nomi.bridge;

import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** 隔离可选 TrMenu 的固定反射 API，避免未安装时产生硬链接。 */
final class TrMenuGateway {
    private final JavaPlugin plugin;

    TrMenuGateway(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 在玩家实体线程调用原生菜单 API；缺失或签名不兼容时明确失败。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void open(Player player, String menuId) {
        var trMenu = plugin.getServer().getPluginManager().getPlugin("TrMenu");
        if (trMenu == null || !trMenu.isEnabled()) {
            player.sendMessage("菜单暂不可用。");
            return;
        }
        try {
            ClassLoader loader = trMenu.getClass().getClassLoader();
            String namespace = "me.arasple.mc.trmenu";
            Class<?> api;
            try {
                api = Class.forName(namespace + ".api.TrMenuAPI", true, loader);
            } catch (ClassNotFoundException exception) {
                // 只在旧 namespace 不存在时回退；签名错误不能靠猜测其他方法掩盖。
                namespace = "trplugins.menu";
                api = Class.forName(namespace + ".api.TrMenuAPI", true, loader);
            }
            Object menu = api.getMethod("getMenuById", String.class)
                .invoke(api.getField("INSTANCE").get(null), menuId);
            if (menu == null) {
                player.sendMessage("菜单未配置：" + menuId);
                return;
            }
            Class<? extends Enum> reason = (Class<? extends Enum>) Class.forName(
                namespace + ".api.event.MenuOpenEvent$Reason", true, loader);
            // 固定四参数 API 保留 TrMenu 自己的打开流程和 BINDING_COMMANDS 原因。
            menu.getClass().getMethod("open", Player.class, Integer.class, reason, Consumer.class)
                .invoke(menu, player, null, Enum.valueOf(reason, "BINDING_COMMANDS"),
                    (Consumer<Object>) ignored -> {});
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Menu route failed: " + menuId, exception);
            player.sendMessage("菜单打开失败，请联系管理员。");
        }
    }
}
