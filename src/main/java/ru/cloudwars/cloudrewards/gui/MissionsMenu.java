package ru.cloudwars.cloudrewards.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.mission.ActiveMission;
import ru.cloudwars.cloudrewards.mission.MissionDefinition;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.MissionType;
import ru.cloudwars.cloudrewards.mission.RotationService;
import ru.cloudwars.cloudrewards.util.ItemUtil;
import ru.cloudwars.cloudrewards.util.TextUtil;
import ru.cloudwars.cloudrewards.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MissionsMenu {

    private static final String NAME_TEMPLATE =
            "<!italic><gray>[</gray><{color}>⟡<gray>]</gray> <{color}><bold>{mission_name}</bold>";

    private static final List<String> LORE_TEMPLATE = List.of(
            "<!italic><{color}>⟡ <white>{mission_description}</white>",
            "",
            "<!italic><gray>• <white>Сложность: {difficulty_label}</white></gray>",
            "<!italic><gray>• <white>Прогресс: <{color}>{current}<gray>/</gray><white>{target}{unit}</white></gray>",
            "<!italic><gray>• <white>Награда: <{color}>{reward} шард</white></gray>",
            "",
            "<!italic><gray>Обновление: <white>ежедневно в {reset_time} МСК</white></gray>",
            "");

    private MissionsMenu() {
    }

    public static Inventory build(ConfigManager config, MissionService missions,
                                  RotationService rotation, Player player) {
        ConfigurationSection section = config.section("gui.missions");
        Component title = TextUtil.mm(section.getString("title", "Ежедневные миссии"));
        int size = section.getInt("size", 27);
        GuiHolder holder = new GuiHolder(GuiHolder.Kind.MISSIONS, size, title);
        Inventory inventory = holder.getInventory();

        MainMenu.applyFiller(inventory, size, section.getConfigurationSection("filler"));

        ConfigurationSection slots = section.getConfigurationSection("slots");
        int infoSlot = slots.getInt("info", 4);
        int backSlot = slots.getInt("back", 22);

        ItemStack info = infoItem(config, missions, rotation, player);
        if (info != null && infoSlot >= 0 && infoSlot < size) {
            inventory.setItem(infoSlot, info);
        }

        ConfigurationSection backSection = section.getConfigurationSection("back-item");
        if (backSection != null && backSlot >= 0 && backSlot < size) {
            ItemStack back = ItemUtil.stack(backSection.getString("material"), 1);
            ItemUtil.decorate(back, backSection.getString("name", "Назад"), backSection.getStringList("lore"));
            inventory.setItem(backSlot, back);
        }

        // Порядок слотов: обычная -> сложная -> обычная.
        for (String slotKey : List.of("normal-1", "hard", "normal-2")) {
            int slot = slots.getInt(slotKey, -1);
            if (slot < 0 || slot >= size) continue;

            ItemStack item = missionSlotItem(config, missions, player, slotKey);
            if (item != null) inventory.setItem(slot, item);
        }
        return inventory;
    }

    /** Обновить часы и карточки в уже открытом меню — без пересоздания инвентаря. */
    public static void refreshLive(ConfigManager config, MissionService missions,
                                   RotationService rotation, Player player, Inventory inventory) {
        ConfigurationSection section = config.section("gui.missions");
        if (section == null) return;
        ConfigurationSection slots = section.getConfigurationSection("slots");
        if (slots == null) return;

        int infoSlot = slots.getInt("info", 4);
        if (infoSlot >= 0 && infoSlot < inventory.getSize()) {
            ItemStack info = infoItem(config, missions, rotation, player);
            if (info != null) inventory.setItem(infoSlot, info);
        }
        for (String slotKey : List.of("normal-1", "hard", "normal-2")) {
            int slot = slots.getInt(slotKey, -1);
            if (slot < 0 || slot >= inventory.getSize()) continue;
            ItemStack item = missionSlotItem(config, missions, player, slotKey);
            if (item != null) inventory.setItem(slot, item);
        }
    }

    private static ItemStack missionSlotItem(ConfigManager config, MissionService missions,
                                             Player player, String slotKey) {
        if (!missions.isUnlocked(player.getUniqueId(), slotKey)) return locked(config);
        ActiveMission mission = missions.bySlot(player.getUniqueId(), slotKey);
        return mission == null ? null : card(config, mission);
    }

    private static ItemStack infoItem(ConfigManager config, MissionService missions,
                                      RotationService rotation, Player player) {
        ConfigurationSection infoSection = config.section("gui.missions.info-item");
        if (infoSection == null) return null;

        var rotationState = rotation.current();
        Map<String, Object> common = TextUtil.vars(
                "reset_time", config.resetTimeText(),
                "time_left", rotationState == null ? "—"
                        : TimeUtil.timeLeft(rotationState.nextResetEpochMs()),
                "completed", missions.completedCount(player.getUniqueId()));

        ItemStack info = ItemUtil.stack(infoSection.getString("material"), 1);
        ItemUtil.decorate(info,
                TextUtil.replace(infoSection.getString("name", ""), common),
                infoSection.getStringList("lore").stream()
                        .map(line -> TextUtil.replace(line, common))
                        .toList());
        return info;
    }

    private static ItemStack card(ConfigManager config, ActiveMission mission) {
        var definition = mission.definition();
        Map<String, Object> vars = TextUtil.vars(
                "color", definition.color(),
                "mission_name", definition.name(),
                "mission_description", definition.description(),
                "difficulty_label", definition.difficulty().label(),
                "reward", Math.max(1, config.shardsPerMission()),
                "reset_time", config.resetTimeText());
        vars.putAll(progress(mission));

        List<String> lore = new ArrayList<>();
        for (String line : LORE_TEMPLATE) lore.add(TextUtil.replace(line, vars));
        lore.addAll(status(mission));

        ItemStack item = icon(definition);
        ItemUtil.decorate(item, TextUtil.replace(NAME_TEMPLATE, vars), lore);
        return item;
    }

    /** Иконка зелья должна выглядеть как то зелье, которое нужно выпить. */
    private static ItemStack icon(MissionDefinition definition) {
        ItemStack item = ItemUtil.stack(definition.materialName(), 1);
        if (item.getItemMeta() instanceof PotionMeta potion) {
            String type = definition.strings("potion-types").stream().findFirst().orElse("");
            if (!type.isBlank()) {
                try {
                    potion.setBasePotionType(PotionType.valueOf(type.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // неизвестный тип — остаётся обычный пузырёк
                }
            }
            item.setItemMeta(potion);
        }
        return item;
    }

    private static ItemStack locked(ConfigManager config) {
        ConfigurationSection section = config.section("gui.missions.locked-item");
        ItemStack item = ItemUtil.stack(
                section == null ? "BARRIER" : section.getString("material", "BARRIER"), 1);
        String name = section == null ? "<gray>Неизвестная миссия</gray>"
                : section.getString("name", "Неизвестная миссия");
        List<String> lore = section == null
                ? List.of("<!italic><gray>Эта миссия пока скрыта.</gray>")
                : section.getStringList("lore");
        ItemUtil.decorate(item, name, lore);
        return item;
    }

    /**
     * Статистика расстояния в Minecraft считается в сантиметрах, время — в секундах.
     * В меню показываем человекочитаемые метры/минуты, не меняя внутренний прогресс.
     */
    private static Map<String, Object> progress(ActiveMission mission) {
        int current = mission.progress();
        int target = mission.target();
        String unit = "";
        var definition = mission.definition();
        if (definition.type() == MissionType.STAT_DELTA
                && definition.text("statistic", "").toUpperCase().endsWith("_ONE_CM")) {
            current /= 100;
            target /= 100;
            unit = " м";
        } else if (definition.type() == MissionType.PLAY_TIME) {
            current /= 60;
            target /= 60;
            unit = " мин";
        }
        return TextUtil.vars("current", current, "target", target, "unit", unit);
    }

    private static List<String> status(ActiveMission mission) {
        if (mission.completed() && mission.claimed()) {
            return List.of("<!italic><green>✓ Выполнено</green>");
        }
        if (mission.completed() && mission.pending()) {
            return List.of("<!italic><red>✖ Освободите место в инвентаре</red>",
                    "<!italic><yellow>▸ ЛКМ — забрать награду</yellow>");
        }
        if (mission.completed()) {
            return List.of("<!italic><yellow>▸ ЛКМ — забрать награду</yellow>");
        }
        return List.of("<!italic><yellow>▸ В процессе</yellow>");
    }
}
