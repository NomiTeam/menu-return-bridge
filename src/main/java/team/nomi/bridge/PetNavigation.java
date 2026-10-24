package team.nomi.bridge;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;
import de.Keyle.MyPet.api.gui.SlotSection;
import de.Keyle.MyPet.gui.MenuInstanceImpl;
import de.Keyle.MyPet.gui.context.PetMenuContext;

final class PetNavigation implements Listener {
 private final JavaPlugin plugin;
 private final Map<UUID,ScheduledTask> pending=new ConcurrentHashMap<>();
 private void cancel(UUID id){var t=pending.remove(id);if(t!=null)t.cancel();}
 void shutdown(){pending.values().forEach(ScheduledTask::cancel);pending.clear();}
 @EventHandler public void close(InventoryCloseEvent e){cancel(e.getPlayer().getUniqueId());}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent e){cancel(e.getPlayer().getUniqueId());}
 @EventHandler public void quit(PlayerQuitEvent e){cancel(e.getPlayer().getUniqueId());}
 PetNavigation(JavaPlugin p){plugin=p;}
 @EventHandler(priority=EventPriority.LOWEST) public void click(InventoryClickEvent e){
  if(!(e.getWhoClicked() instanceof Player p) || !(e.getView().getTopInventory().getHolder(false) instanceof MenuInstanceImpl m))return;
  String action=null;
  for(var entry:m.definition().sections().entrySet()){
   String route=Navigation.action(entry.getKey()); // Reset and every unknown action are ignored before cancellation.
   if(route!=null && entry.getValue() instanceof SlotSection s && e.getRawSlot()==s.row()*9+s.col()){action=route;break;}
  }
  if(action==null)return;e.setCancelled(true);e.setResult(Event.Result.DENY);String selected=action;
  cancel(p.getUniqueId());
  var scheduled=p.getScheduler().runDelayed(plugin,t->{
   pending.remove(p.getUniqueId(),t);
   if(p.getOpenInventory().getTopInventory().getHolder(false)!=m)return;
   if(!selected.equals("return")){p.performCommand(selected);return;}
   if(m.context() instanceof PetMenuContext){m.close();p.performCommand("nuomimenu");}
   else if(!m.popBack()){m.close();p.performCommand("pet");}
  },()->pending.remove(p.getUniqueId()),1);
  if(scheduled!=null)pending.put(p.getUniqueId(),scheduled);
 }
}
