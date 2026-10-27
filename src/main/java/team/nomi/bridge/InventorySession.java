package team.nomi.bridge;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.inventory.Inventory;

/** 一次受控库存展示及其刷新、返回任务；生命周期由查看玩家的实体线程管理。 */
final class InventorySession {
    final Inventory inventory;
    final String owner;
    private final String title;
    private final String holder;
    ScheduledTask refresh;
    ScheduledTask route;

    InventorySession(Inventory inventory, String owner, String title, String holder) {
        this.inventory = inventory;
        this.owner = owner;
        this.title = title;
        this.holder = holder;
    }

    boolean allowsSlot(int slot) {
        // rawSlot 必须在上层库存内，绝不能触碰玩家背包或窗口外点击。
        return Navigation.menu(owner, title, holder, inventory.getSize(), slot);
    }

    boolean isSupported() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (allowsSlot(slot)) {
                return true;
            }
        }
        return false;
    }

    void refreshButtons(ReturnButton button) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!allowsSlot(slot)) {
                continue;
            }
            var item = inventory.getItem(slot);
            if (button.isNavigation(item) && !button.isMarked(item)) {
                inventory.setItem(slot, button.create());
            }
        }
    }

    int audit(ReturnButton button) {
        int marked = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (button.isMarked(inventory.getItem(slot))) {
                if (!allowsSlot(slot)) {
                    throw new IllegalStateException("Marker outside navigation boundary");
                }
                marked++;
            }
        }
        return marked;
    }

    void cancelRoute() {
        if (route != null) {
            route.cancel();
        }
    }

    void stop() {
        // 关闭、换菜单、退出和禁用都要同时取消刷新与延迟路由，避免旧窗口回跳。
        if (refresh != null) {
            refresh.cancel();
        }
        cancelRoute();
    }
}
