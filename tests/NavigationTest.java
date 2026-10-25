package team.nomi.bridge;

/** 在真实依赖上验证领域边界；不替代服务器内的点击验收。 */
public final class NavigationTest {
    public static void main(String[] args) {
        Navigation.verify();
        assert "Menu".equals(PublicMenus.menuId(new String[0]));
        String[][] menus = {
            {"MENU", "Menu"}, {"activity", "Activity"},
            {"VipControl", "VipControl"}, {"sponsor", "Sponsor"},
            {"DailyQuestReset", "DailyQuestReset"}
        };
        for (String[] menu : menus) {
            assert menu[1].equals(PublicMenus.menuId(new String[] {menu[0]}));
        }
        // PersonalInfo 交给 TrMenu 原生命令注册，不扩大桥接的公开目标。
        assert PublicMenus.menuId(new String[] {"PersonalInfo"}) == null;
        assert PublicMenus.menuId(new String[] {"personalinfo"}) == null;
        assert PublicMenus.menuId(new String[] {"PERSONALINFO"}) == null;
        assert PublicMenus.menuId(new String[] {"PersonalInfo", "other-player"}) == null;
        assert PublicMenus.menuId(new String[] {"PersonalInfo:1"}) == null;
        assert PublicMenus.menuId(new String[] {"PersonalInfo other-player"}) == null;
        assert PublicMenus.menuId(new String[] {"../PersonalInfo"}) == null;
        assert PublicMenus.menuId(new String[] {"PersonalInfo", ""}) == null;
        assert PublicMenus.menuId(new String[] {"pet"}) == null;
        assert PublicMenus.menuId(new String[] {"Menu", "other-player"}) == null;
        assert !Navigation.menu("PlayerInv", "", "com.playerinv.Util.InvHolder.VaultMenuHolder", 53, 53);
        assert !Navigation.menu("FarmersDelight", "农夫乐事 · 配方", "", 54, -1);
        assert !Navigation.menu("OtherPlugin", "农夫乐事 · 配方", "", 54, 1);
        System.out.println("Navigation and public menu boundary assertions passed");
    }
}
