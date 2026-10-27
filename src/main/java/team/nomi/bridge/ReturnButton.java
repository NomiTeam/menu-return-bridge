package team.nomi.bridge;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** 统一已有返回项识别和兼容按钮样式；不决定哪个库存槽可被替换。 */
final class ReturnButton {
    private final NamespacedKey key;

    ReturnButton(NamespacedKey key) {
        this.key = key;
    }

    boolean isMarked(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    boolean isNavigation(ItemStack item) {
        if (isMarked(item)) {
            return true;
        }
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Component name = item.getItemMeta().displayName();
        return name != null && Navigation.name(PlainTextComponentSerializer.plainText().serialize(name));
    }

    ItemStack create() {
        ItemStack item = new ItemStack(Material.FLOW_BANNER_PATTERN);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("↩ 返回上层", TextColor.color(0xFFBBD0))
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("退出当前菜单。", TextColor.color(0xFFF4B4))
            .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
