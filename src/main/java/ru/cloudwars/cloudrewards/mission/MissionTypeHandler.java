package ru.cloudwars.cloudrewards.mission;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

/** Общие проверки target-параметров для обработчиков типов миссий. */
public final class MissionTypeHandler {

    private MissionTypeHandler() {
    }

    public static boolean material(MissionDefinition definition, Material material) {
        if (material == null) return false;
        String name = material.name();
        return definition.strings("materials").stream().anyMatch(name::equalsIgnoreCase);
    }

    public static boolean statistic(MissionDefinition definition, Statistic statistic) {
        return definition.text("statistic", "").equalsIgnoreCase(statistic.name());
    }

    public static boolean entity(MissionDefinition definition, EntityType type) {
        if (type == null) return false;
        return definition.text("entity", "").equalsIgnoreCase(type.name());
    }

    public static boolean projectile(MissionDefinition definition, EntityType type) {
        if (type == null) return false;
        return definition.text("projectile", "").equalsIgnoreCase(type.name());
    }

    public static String potionKey(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta)) return "";
        PotionData data = meta.getBasePotionData();
        if (data == null) return "";
        String base = data.getType().name();
        if (data.isUpgraded()) return "STRONG_" + base;
        if (data.isExtended()) return "LONG_" + base;
        return base;
    }

    public static boolean potion(MissionDefinition definition, ItemStack item) {
        String key = potionKey(item);
        if (key.isEmpty()) return false;
        return definition.strings("potion-types").stream().anyMatch(key::equalsIgnoreCase);
    }

    public static boolean command(MissionDefinition definition, String input) {
        if (input == null) return false;
        String used = firstToken(input);
        return definition.strings("commands").stream()
                .map(MissionTypeHandler::firstToken)
                .anyMatch(used::equals);
    }

    private static String firstToken(String raw) {
        String value = raw.trim();
        while (value.startsWith("/")) value = value.substring(1);
        int space = value.indexOf(' ');
        if (space >= 0) value = value.substring(0, space);
        return value.toLowerCase();
    }
}
