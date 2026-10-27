package team.nomi.bridge;

import java.util.Locale;

/** 无服务器状态的导航策略：名称、上层库存边界及 MyPet 动作白名单。 */
final class Navigation {
    static boolean name(String displayName) {
        String normalized = displayName.replaceAll("(?i)[§&][0-9a-fk-orx]", "")
            .strip().toLowerCase(Locale.ROOT)
            .replaceAll("^[\\[\\]<>«»←↩\\s]+|[\\[\\]<>«»\\s]+$", "").strip();
        return (normalized.contains("返回") && !normalized.contains("返回数量"))
            || normalized.matches(".*关闭(菜单|界面)?[。！!]?")
            || normalized.matches("(back|return)( to .*)?[.!]?")
            || normalized.matches("close( (this |the )?(menu|gui|inventory))?[.!]?")
            || normalized.matches(".*[.:](back|close)");
    }

    static boolean menu(String owner, String title, String holder, int size, int slot) {
        // 先检查 rawSlot，避免下层玩家库存、窗口外点击及尺寸不足的容器被接管。
        if (slot < 0 || slot >= size) {
            return false;
        }
        return (owner.equals("FarmersDelight") && title.equals("农夫乐事 · 配方"))
            || (owner.equals("PlayerInv")
                && holder.equals("com.playerinv.Util.InvHolder.VaultMenuHolder") && slot == 53);
    }

    static String action(String sectionId) {
        return switch (sectionId) {
            case "nuomi-return" -> "return";
            case "nuomi-backpack" -> "petinventory";
            case "nuomi-skilltree" -> "petchooseskilltree";
            case "nuomi-shop" -> "petshop";
            default -> null;
        };
    }

    /** 运行时审计也执行真实断言，不依赖 JVM 是否启用 -ea。 */
    static void verify() {
        for (String name : new String[] {"返回服务器市场", "[关闭]", "Back to main menu.", "gui.recipe.back", "§c↩ 返回上层"}) {
            check(name(name), name);
        }
        for (String name : new String[] {"上一页", "下一页", "退出公会", "返回数量", "番茄种子"}) {
            check(!name(name), name);
        }
        check(menu("FarmersDelight", "农夫乐事 · 配方", "", 54, 9), "recipe root");
        check(!menu("FarmersDelight", "农夫乐事 · 锅", "", 54, 9), "pot");
        check(!menu("FarmersDelight", "农夫乐事 · 配方 · 第二页", "", 54, 9), "exact title");
        check(menu("PlayerInv", "", "com.playerinv.Util.InvHolder.VaultMenuHolder", 54, 53), "vault");
        check(!menu("PlayerInv", "", "com.playerinv.Util.InvHolder.VaultMenuHolder", 54, 52), "vault slot");
        check(!menu("PlayerInv", "", "OtherHolder", 54, 53), "holder");
        check(action("nuomi-reset") == null && action("nuomi-unknown") == null, "ownership");
        check("petinventory".equals(action("nuomi-backpack")), "backpack route");
        check("petchooseskilltree".equals(action("nuomi-skilltree")), "tree route");
        check("petshop".equals(action("nuomi-shop")), "shop route");
        check("return".equals(action("nuomi-return")), "parent route");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
