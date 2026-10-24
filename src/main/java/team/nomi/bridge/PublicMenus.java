package team.nomi.bridge;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class PublicMenus implements CommandExecutor {
 private final JavaPlugin plugin;
 PublicMenus(JavaPlugin p){plugin=p;}
 @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
  if(!(s instanceof Player p)){s.sendMessage("Only players can open their own menu.");return true;}
  String id=a.length==0?"Menu":a.length==1?switch(a[0].toLowerCase(Locale.ROOT)){
   case "menu"->"Menu";case "activity"->"Activity";case "vipcontrol"->"VipControl";case "sponsor"->"Sponsor";case "dailyquestreset"->"DailyQuestReset";default->null;}:null;
  if(id==null){s.sendMessage("/nuomimenu [Menu|Activity|VipControl|Sponsor|DailyQuestReset]");return true;}
  p.getScheduler().run(plugin,t->open(p,id),null);return true;
 }
 @SuppressWarnings({"unchecked","rawtypes"}) private void open(Player p,String id){
  var tr=plugin.getServer().getPluginManager().getPlugin("TrMenu");
  if(tr==null || !tr.isEnabled()){p.sendMessage("菜单暂不可用。");return;}
  try{
   String prefix="me.arasple.mc.trmenu";Class<?> api;
   try{api=Class.forName(prefix+".api.TrMenuAPI",true,tr.getClass().getClassLoader());}
   catch(ClassNotFoundException ex){prefix="trplugins.menu";api=Class.forName(prefix+".api.TrMenuAPI",true,tr.getClass().getClassLoader());}
   Object menu=api.getMethod("getMenuById",String.class).invoke(api.getField("INSTANCE").get(null),id);
   if(menu==null){p.sendMessage("菜单未配置："+id);return;}
   Class<? extends Enum> reason=(Class<? extends Enum>)Class.forName(prefix+".api.event.MenuOpenEvent$Reason",true,tr.getClass().getClassLoader());
   menu.getClass().getMethod("open",Player.class,Integer.class,reason,Consumer.class).invoke(menu,p,null,Enum.valueOf(reason,"BINDING_COMMANDS"),(Consumer<Object>)ignored->{});
  }catch(ReflectiveOperationException|LinkageError ex){plugin.getLogger().log(java.util.logging.Level.WARNING,"Menu route failed: "+id,ex);p.sendMessage("菜单打开失败，请联系管理员。");}
 }
}
