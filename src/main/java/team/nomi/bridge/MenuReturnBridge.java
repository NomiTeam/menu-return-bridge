package team.nomi.bridge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Inventory access and deferred navigation stay on the viewing player's entity scheduler. */
public final class MenuReturnBridge extends JavaPlugin implements Listener {
 private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
 private final Map<ClassLoader,String> owners=new HashMap<>();
 private NamespacedKey key;
 private PetNavigation petNavigation;
 private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
 private static final class Session {
  final Inventory inventory; final String owner,title,holder;
  ScheduledTask refresh,route;
  Session(Inventory i,String o,String t,String h){inventory=i;owner=o;title=t;holder=h;}
  void stop(){if(refresh!=null)refresh.cancel();if(route!=null)route.cancel();}
  boolean slot(int s){return Navigation.menu(owner,title,holder,inventory.getSize(),s);}
 }
 @Override public void onEnable(){
  key=new NamespacedKey(this,"return_to_menu");
  for(var p:getServer().getPluginManager().getPlugins())
   if(Set.of("FarmersDelight","PlayerInv").contains(p.getName()))owners.put(p.getClass().getClassLoader(),p.getName());
  getCommand("nuomimenu").setExecutor(new PublicMenus(this));
  getServer().getPluginManager().registerEvents(this,this);
  var pet=getServer().getPluginManager().getPlugin("MyPet");
  if(pet!=null && pet.isEnabled())try{petNavigation=new PetNavigation(this);getServer().getPluginManager().registerEvents(petNavigation,this);}
  catch(LinkageError ex){getLogger().log(java.util.logging.Level.WARNING,"MyPet navigation unavailable: requires MyPet 4.0.4 GUI API",ex);}
 }
 @Override public void onDisable(){if(petNavigation!=null)petNavigation.shutdown();sessions.values().forEach(Session::stop);sessions.clear();}
 private void stop(UUID id){var old=sessions.remove(id);if(old!=null)old.stop();}
 private String owner(Inventory i){
  var h=i.getHolder(false);String o=h==null?null:owners.get(h.getClass().getClassLoader());
  return o!=null?o:WALKER.walk(s->s.map(f->owners.get(f.getDeclaringClass().getClassLoader())).filter(Objects::nonNull).findFirst().orElse(""));
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent e){
  if(!(e.getPlayer() instanceof Player p))return;
  stop(p.getUniqueId());
  Inventory i=e.getInventory();if(!Set.of(InventoryType.CHEST,InventoryType.HOPPER,InventoryType.DISPENSER).contains(i.getType()))return;
  var h=i.getHolder(false);
  Session s=new Session(i,owner(i),PlainTextComponentSerializer.plainText().serialize(e.getView().title()),h==null?"":h.getClass().getName());
  boolean supported=false;for(int n=0;n<i.getSize();n++)supported|=s.slot(n);if(!supported)return;
  sessions.put(p.getUniqueId(),s);
  s.refresh=p.getScheduler().runAtFixedRate(this,t->{
   if(p.getOpenInventory().getTopInventory()!=i){t.cancel();sessions.remove(p.getUniqueId(),s);s.stop();return;}scan(s);
  },()->sessions.remove(p.getUniqueId(),s),1,5);
 }
 private boolean marked(ItemStack i){return i!=null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(key,PersistentDataType.BYTE);}
 private boolean navigation(ItemStack i){
  if(marked(i))return true;if(i==null || !i.hasItemMeta())return false;
  Component c=i.getItemMeta().displayName();return c!=null && Navigation.name(PlainTextComponentSerializer.plainText().serialize(c));
 }
 private ItemStack button(){
  ItemStack i=new ItemStack(Material.FLOW_BANNER_PATTERN);var m=i.getItemMeta();
  m.displayName(Component.text("↩ 返回上层",TextColor.color(0xFFBBD0)).decoration(TextDecoration.BOLD,true).decoration(TextDecoration.ITALIC,false));
  m.lore(List.of(Component.text("退出当前菜单。",TextColor.color(0xFFF4B4)).decoration(TextDecoration.ITALIC,false)));
  m.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);i.setItemMeta(m);return i;
 }
 private void scan(Session s){for(int n=0;n<s.inventory.getSize();n++)if(s.slot(n)&&navigation(s.inventory.getItem(n))&&!marked(s.inventory.getItem(n)))s.inventory.setItem(n,button());}
 @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e){
  if(!(e.getWhoClicked() instanceof Player p))return;var s=sessions.get(p.getUniqueId());
  if(s==null || e.getView().getTopInventory()!=s.inventory || !s.slot(e.getRawSlot()) || !navigation(e.getCurrentItem()))return;
  e.setCancelled(true);e.setResult(Event.Result.DENY);
  if(s.route!=null)s.route.cancel();
  s.route=p.getScheduler().runDelayed(this,t->{
   if(sessions.get(p.getUniqueId())!=s || p.getOpenInventory().getTopInventory()!=s.inventory)return;
   stop(p.getUniqueId());p.performCommand("nuomimenu");
  },null,1);
 }
 @EventHandler public void close(InventoryCloseEvent e){var s=sessions.get(e.getPlayer().getUniqueId());if(s!=null && s.inventory==e.getInventory())stop(e.getPlayer().getUniqueId());}
 @EventHandler public void quit(PlayerQuitEvent e){stop(e.getPlayer().getUniqueId());}
 @Override public boolean onCommand(CommandSender sender,Command c,String label,String[] a){
  if(!sender.hasPermission("menureturn.audit"))return true;
  try{Navigation.verify();sender.sendMessage("Navigation boundary assertions passed; active sessions="+sessions.size());}
  catch(AssertionError ex){getLogger().log(java.util.logging.Level.SEVERE,"Navigation assertions failed",ex);sender.sendMessage("Navigation assertions FAILED");return true;}
  if(a.length==0 || a[0].equalsIgnoreCase("audit"))for(Player p:getServer().getOnlinePlayers())p.getScheduler().run(this,t->{
   var s=sessions.get(p.getUniqueId());if(s==null || p.getOpenInventory().getTopInventory()!=s.inventory)return;
   int count=0;for(int n=0;n<s.inventory.getSize();n++)if(marked(s.inventory.getItem(n))){if(!s.slot(n))throw new IllegalStateException("Marker outside navigation boundary");count++;}
   getLogger().info("AUDIT player="+p.getUniqueId()+" owner="+s.owner+" marked="+count);
  },null);
  return true;
 }
}
