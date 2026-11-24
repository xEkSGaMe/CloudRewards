package ru.cloudwars.cloudrewards.config;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.util.ItemUtil;
import ru.cloudwars.cloudrewards.util.TimeUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final List<String> CONFIG_MIGRATIONS = List.of(
            "gui.main.title",
            "gui.main.items.missions.name",
            "gui.main.items.help.name",
            "gui.main.items.exchange.name",
            "gui.main.items.help.lore",
            "gui.missions.title",
            "gui.missions.info-item.name",
            "gui.missions.locked-item.lore");
    private static final List<String> MESSAGE_MIGRATIONS = List.of(
            "prefix", "mission-complete", "mission-locked", "all-complete");
    private static final List<String> NEW_COLOR_MARKERS = List.of(
            "&#7422EE", "&#22BCEE", "&#F2C6DE");
    /** Опечатка вида <#A855F7⏰ — MiniMessage показывает такой тег как обычный текст. */
    private static final Pattern BROKEN_HEX_TAG = Pattern.compile("<#[0-9a-fA-F]{6}[^>]");

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration missions;
    private ZoneId zone;
    private LocalTime resetTime;
    private ShardSettings shard;
    private ExchangeSettings exchange;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        for (String name : List.of("config.yml", "messages.yml", "missions.yml")) {
            File file = new File(plugin.getDataFolder(), name);
            if (!file.exists()) plugin.saveResource(name, false);
        }
        config = loadFile("config.yml");
        messages = loadFile("messages.yml");
        missions = loadFile("missions.yml");

        // Файлы могут быть созданы предыдущей версией плагина — подтягиваем
        // только те ключи, которые всё ещё содержат старые градиенты/подсказки.
        migrate(config, "config.yml", CONFIG_MIGRATIONS);
        migrate(messages, "messages.yml", MESSAGE_MIGRATIONS);

        zone = TimeUtil.zone(config.getString("timezone", "Europe/Moscow"));
        resetTime = TimeUtil.parseTime(config.getString("reset-time", "13:00"));
        shard = parseShard();
        exchange = parseExchange();
    }

    public void reload() {
        load();
    }

    private FileConfiguration loadFile(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        return YamlConfiguration.loadConfiguration(file);
    }

    /** Обновить только устаревшие значения из свежей копии ресурса, не трогая остальные настройки. */
    private void migrate(FileConfiguration target, String fileName, List<String> paths) {
        InputStream resource = plugin.getResource(fileName);
        if (resource == null) return;
        FileConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            return;
        }

        boolean changed = false;
        for (String path : paths) {
            Object current = target.get(path);
            Object fresh = defaults.get(path);
            if (fresh == null || fresh.equals(current) || !legacy(current)) continue;
            target.set(path, fresh);
            changed = true;
        }
        if (changed) {
            try {
                target.save(new File(plugin.getDataFolder(), fileName));
            } catch (IOException ex) {
                plugin.getLogger().warning("Не удалось сохранить обновлённый " + fileName + ": " + ex.getMessage());
            }
        }
    }

    private static boolean legacy(Object value) {
        if (value instanceof String text) {
            return (text.contains("<gradient:")
                    && NEW_COLOR_MARKERS.stream().noneMatch(text::contains))
                    || BROKEN_HEX_TAG.matcher(text).find()
                    || text.contains("{previous}");
        }
        if (value instanceof List<?> lines) {
            return lines.stream().anyMatch(line -> line instanceof String text
                    && (text.contains("{previous}") || text.contains("с подсказкой")
                    || BROKEN_HEX_TAG.matcher(text).find()));
        }
        return false;
    }

    private ShardSettings parseShard() {
        ConfigurationSection section = config.getConfigurationSection("shard");
        if (section == null) section = config.createSection("shard");
        return new ShardSettings(
                ItemUtil.material(section.getString("material"), org.bukkit.Material.PRISMARINE_CRYSTALS),
                section.getString("name", "Шард"),
                section.getStringList("lore"),
                section.getInt("custom-model-data", 0),
                section.getBoolean("glint", true),
                section.getBoolean("hide-flags", true),
                section.getString("pdc-key", "cloudrewards_shard"));
    }

    private ExchangeSettings parseExchange() {
        ConfigurationSection section = config.getConfigurationSection("exchange");
        if (section == null) section = config.createSection("exchange");
        ConfigurationSection chances = section.getConfigurationSection("chances");
        if (chances == null) chances = section.createSection("chances");
        ConfigurationSection cases = section.getConfigurationSection("cases");
        if (cases == null) cases = section.createSection("cases");

        Mode mode;
        try {
            mode = Mode.valueOf(section.getString("mode", "STUB").toUpperCase());
        } catch (IllegalArgumentException ex) {
            mode = Mode.STUB;
        }

        return new ExchangeSettings(
                mode,
                section.getInt("cost", 20),
                section.getInt("cooldown-seconds", 1),
                section.getBoolean("stub-refund-shards", true),
                chances.getDouble("small", 50),
                chances.getDouble("medium", 25),
                chances.getDouble("large", 25),
                cases.getString("small", "TREASURE_SMALL"),
                cases.getString("medium", "TREASURE_MEDIUM"),
                cases.getString("large", "TREASURE_LARGE"),
                section.getString("source-player", "INITIATOR").equalsIgnoreCase("INITIATOR"));
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public FileConfiguration missions() {
        return missions;
    }

    public ConfigurationSection section(String path) {
        return config.getConfigurationSection(path);
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalTime resetTime() {
        return resetTime;
    }

    public String resetTimeText() {
        return TimeUtil.formatTime(resetTime);
    }

    public int shardsPerMission() {
        return config.getInt("rewards.shards-per-mission", 1);
    }

    public int allCompleteBonus() {
        return config.getInt("rewards.all-complete-bonus", 2);
    }

    public boolean requireInventorySpace() {
        return config.getBoolean("rewards.require-inventory-space", true);
    }

    public ShardSettings shard() {
        return shard;
    }

    public ExchangeSettings exchange() {
        return exchange;
    }

    public Sound sound(String key, Sound fallback) {
        return ItemUtil.sound(config.getString("sounds." + key), fallback);
    }

    public enum Mode {
        STUB,
        COOLDOWNTAINERS
    }

    public record ShardSettings(org.bukkit.Material material, String name, List<String> lore,
                                int customModelData, boolean glint, boolean hideFlags, String pdcKey) {
    }

    public record ExchangeSettings(Mode mode, int cost, int cooldownSeconds, boolean stubRefundShards,
                                   double smallChance, double mediumChance, double largeChance,
                                   String smallCase, String mediumCase, String largeCase,
                                   boolean sourcePlayerInitiator) {
    }
}
