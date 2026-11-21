package ru.cloudwars.cloudrewards.util;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemUtil {

    private ItemUtil() {
    }

    public static Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material material = Material.matchMaterial(name);
        return material == null ? fallback : material;
    }

    public static ItemStack stack(String materialName, int amount) {
        return new ItemStack(material(materialName, Material.PAPER), Math.max(1, amount));
    }

    public static void decorate(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(TextUtil.mm(name));
        if (lore != null) meta.lore(TextUtil.mmList(lore));
        item.setItemMeta(meta);
    }

    public static Sound sound(String name, Sound fallback) {
        if (name == null || name.isBlank()) return fallback;
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
