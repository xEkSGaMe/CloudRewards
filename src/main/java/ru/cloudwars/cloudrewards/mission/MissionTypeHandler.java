package ru.cloudwars.cloudrewards.mission;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Set;

/** Общие проверки target-параметров для обработчиков типов миссий. */
public final class MissionTypeHandler {

    /** Дистанции, которые считает DistanceTracker по факту перемещения. */
    private static final Set<Statistic> TRACKED_DISTANCE = Set.of(
            Statistic.BOAT_ONE_CM, Statistic.MINECART_ONE_CM, Statistic.AVIATE_ONE_CM,
            Statistic.SWIM_ONE_CM, Statistic.WALK_ONE_CM);

    private MissionTypeHandler() {
    }

    public static boolean trackedDistance(Statistic statistic) {
        return TRACKED_DISTANCE.contains(statistic);
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
        PotionType type = meta.getBasePotionType();
        return type == null ? "" : type.name();
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
