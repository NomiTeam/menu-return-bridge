package team.nomi.bridge;
final class Navigation {
 static boolean name(String s){
  String n=s.replaceAll("(?i)[§&][0-9a-fk-orx]", "").strip().toLowerCase(java.util.Locale.ROOT)
   .replaceAll("^[\\[\\]<>«»←↩\\s]+|[\\[\\]<>«»\\s]+$", "").strip();
  return (n.contains("返回") && !n.contains("返回数量")) || n.matches(".*关闭(菜单|界面)?[。！!]?")
   || n.matches("(back|return)( to .*)?[.!]?") || n.matches("close( (this |the )?(menu|gui|inventory))?[.!]?") || n.matches(".*[.:](back|close)");
 }
 static boolean menu(String owner,String title,String holder,int size,int slot){
  if(slot<0 || slot>=size)return false;
  return owner.equals("FarmersDelight") && title.equals("农夫乐事 · 配方")
   || owner.equals("PlayerInv") && holder.equals("com.playerinv.Util.InvHolder.VaultMenuHolder") && slot==53;
 }
 static String action(String s){return switch(s){
 case "nuomi-return" -> "return"; case "nuomi-backpack" -> "petinventory";
 case "nuomi-skilltree" -> "petchooseskilltree"; case "nuomi-shop" -> "petshop"; default -> null;};}
 static void verify(){
  for(String n:new String[]{"返回服务器市场","[关闭]","Back to main menu.","gui.recipe.back","§c↩ 返回上层"}) check(name(n),n);
  for(String n:new String[]{"上一页","下一页","退出公会","返回数量","番茄种子"}) check(!name(n),n);
  check(menu("FarmersDelight","农夫乐事 · 配方","",54,9),"recipe root");
  check(!menu("FarmersDelight","农夫乐事 · 锅","",54,9),"pot");
  check(!menu("FarmersDelight","农夫乐事 · 配方 · 第二页","",54,9),"exact title");
  check(menu("PlayerInv","","com.playerinv.Util.InvHolder.VaultMenuHolder",54,53),"vault");
  check(!menu("PlayerInv","","com.playerinv.Util.InvHolder.VaultMenuHolder",54,52),"vault slot");
  check(!menu("PlayerInv","","OtherHolder",54,53),"holder");
  check(action("nuomi-reset")==null && action("nuomi-unknown")==null,"ownership");
  check("petinventory".equals(action("nuomi-backpack")),"backpack route");
  check("petchooseskilltree".equals(action("nuomi-skilltree")),"tree route");
  check("petshop".equals(action("nuomi-shop")),"shop route");
  check("return".equals(action("nuomi-return")),"parent route");
 }
 private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
}
