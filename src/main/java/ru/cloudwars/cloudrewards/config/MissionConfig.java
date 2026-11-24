package ru.cloudwars.cloudrewards.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.mission.MissionDefinition;
import ru.cloudwars.cloudrewards.mission.MissionDifficulty;
import ru.cloudwars.cloudrewards.mission.MissionType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MissionConfig {

    private final ConfigManager config;
    private final JavaPlugin plugin;
    private final Map<String, MissionDefinition> byId = new LinkedHashMap<>();
    private final Map<MissionDifficulty, List<MissionDefinition>> pools = new EnumMap<>(MissionDifficulty.class);

    public MissionConfig(ConfigManager config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    public void load() {
        byId.clear();
        pools.clear();
        int colorIndex = 0;
        for (MissionDifficulty difficulty : MissionDifficulty.values()) {
            List<MissionDefinition> list = new ArrayList<>();
            // missions.yml хранит пулы списками: getConfigurationSection() для них возвращает null.
            for (Object value : entries(config.missions().get("pools." + difficulty.name()))) {
                ConfigurationSection section = asSection(value);
                if (section == null) continue;
                MissionDefinition definition = parse(section, difficulty, colorIndex++);
                if (definition == null) continue;
                list.add(definition);
                byId.put(definition.id(), definition);
            }
            pools.put(difficulty, list);
            plugin.getLogger().info("Загружено " + list.size() + " миссий (" + difficulty + ")");
        }
    }

    private static List<?> entries(Object raw) {
        if (raw instanceof List<?> list) return list;
        if (raw instanceof ConfigurationSection section) return new ArrayList<>(section.getValues(false).values());
        if (raw instanceof Map<?, ?> map) return new ArrayList<>(map.values());
        return List.of();
    }

    private static ConfigurationSection asSection(Object value) {
        if (value instanceof ConfigurationSection section) return section;
        if (value instanceof Map<?, ?> map) {
            YamlConfiguration wrapper = new YamlConfiguration();
            map.forEach((key, entry) -> wrapper.set(String.valueOf(key), entry));
            return wrapper;
        }
        return null;
    }

    private MissionDefinition parse(ConfigurationSection section, MissionDifficulty difficulty, int colorIndex) {
        String fallbackId = section.getName();
        String id = section.getString("id", fallbackId == null ? "" : fallbackId);
        if (id == null || id.isBlank()) return null;

        String typeName = section.getString("type", "");
        MissionType type;
        try {
            type = MissionType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Неизвестный тип миссии '" + typeName + "' у " + id + " — миссия отключена");
            type = null;
        }

        String base = color(section.getString("color"), colorIndex);

        return new MissionDefinition(
                id,
                section.getString("name", id),
                section.getString("description", ""),
                section.getString("material", "PAPER"),
                difficulty,
                type,
                targetMap(section.get("target")),
                Math.max(1, section.getInt("amount", 1)),
                section.getBoolean("enabled", true) && type != null,
                base);
    }

    /** Явно заданный hex или детерминированный пастельный оттенок по индексу миссии. */
    private static String color(String raw, int index) {
        String explicit = normalizeHex(raw);
        if (explicit != null) return explicit;
        float hue = (index * 0.61803398875f) % 1.0f; // золотое сечение = равномерные оттенки
        int rgb = Color.HSBtoRGB(hue, 0.45f, 1.00f);
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static String normalizeHex(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (!value.startsWith("#")) value = "#" + value;
        return value.matches("#[0-9a-fA-F]{6}") ? value : null;
    }

    private static Map<String, Object> targetMap(Object raw) {
        if (raw instanceof ConfigurationSection section) return section.getValues(true);
        if (raw instanceof Map<?, ?> map) {
            YamlConfiguration wrapper = new YamlConfiguration();
            map.forEach((key, value) -> wrapper.set(String.valueOf(key), value));
            return wrapper.getValues(true);
        }
        return Map.of();
    }

    public List<MissionDefinition> pool(MissionDifficulty difficulty) {
        return pools.getOrDefault(difficulty, List.of());
    }

    public MissionDefinition byId(String id) {
        return byId.get(id);
    }

    public Map<String, MissionDefinition> all() {
        return byId;
    }
}
