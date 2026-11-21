package ru.cloudwars.cloudrewards.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.mission.MissionDefinition;
import ru.cloudwars.cloudrewards.mission.MissionDifficulty;
import ru.cloudwars.cloudrewards.mission.MissionType;

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
        for (MissionDifficulty difficulty : MissionDifficulty.values()) {
            List<MissionDefinition> list = new ArrayList<>();
            ConfigurationSection pool = config.missions().getConfigurationSection("pools." + difficulty.name());
            if (pool == null) {
                pools.put(difficulty, list);
                continue;
            }
            for (Object value : pool.getValues(false).values()) {
                if (!(value instanceof ConfigurationSection section)) continue;
                MissionDefinition definition = parse(section, difficulty);
                if (definition == null) continue;
                list.add(definition);
                byId.put(definition.id(), definition);
            }
            pools.put(difficulty, list);
            plugin.getLogger().info("Загружено " + list.size() + " миссий (" + difficulty + ")");
        }
    }

    private MissionDefinition parse(ConfigurationSection section, MissionDifficulty difficulty) {
        String key = section.getName();
        String id = section.getString("id", key);
        String typeName = section.getString("type", "");
        MissionType type;
        try {
            type = MissionType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Неизвестный тип миссии '" + typeName + "' у " + id + " — миссия отключена");
            type = null;
        }
        ConfigurationSection targetSection = section.getConfigurationSection("target");
        Map<String, Object> target = targetSection == null ? Map.of() : targetSection.getValues(true);
        return new MissionDefinition(
                id,
                section.getString("name", id),
                section.getString("description", ""),
                section.getString("material", "PAPER"),
                difficulty,
                type,
                target,
                Math.max(1, section.getInt("amount", 1)),
                section.getBoolean("enabled", true) && type != null);
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
