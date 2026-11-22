package ru.cloudwars.cloudrewards.mission;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public record MissionDefinition(String id, String name, String description, String materialName,
                                MissionDifficulty difficulty, MissionType type, Map<String, Object> target,
                                int amount, boolean enabled, String color, String color2) {

    public Material material() {
        Material material = Material.matchMaterial(materialName);
        return material == null ? Material.PAPER : material;
    }

    /** Основной цвет миссии (для иконки/заголовка). */
    public String color() {
        return color == null ? "#B388FF" : color;
    }

    /** Второй, близкий оттенок того же цвета — для короткого плавного градиента. */
    public String color2() {
        return color2 == null ? color() : color2;
    }

    public List<String> strings(String key) {
        Object value = target.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    public String text(String key, String fallback) {
        Object value = target.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public int number(String key, int fallback) {
        Object value = target.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public boolean flag(String key) {
        Object value = target.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
