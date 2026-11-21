package ru.cloudwars.cloudrewards.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.mission.ActiveMission;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.RotationService;
import ru.cloudwars.cloudrewards.util.ItemUtil;
import ru.cloudwars.cloudrewards.util.TextUtil;
import ru.cloudwars.cloudrewards.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MissionsMenu {

    private static final String NAME_TEMPLATE =
            "<!italic><gray>[</gray>{diff_color}⟡<gray>]</gray> <gradient:{g1}:{g2}><bold>{mission_name}</bold></gradient>";

    private static final List<String> LORE_TEMPLATE = List.of(
            "<!italic>{diff_color}⟡ <white>{mission_description}</white>",
            "",
            "<!italic><gray>• <white>Сложность: {difficulty_label}</white></gray>",
            "<!italic><gray>• <white>Прогресс: <gradient:{g1}:{g2}>{current}</gradient><gray>/</gray><white>{target}</white></gray>",
            "<!italic><gray>• <white>Награда: <#C084FC>{reward} шард ⟡</#C084FC></white></gray>",
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

        Map<String, Object> common = TextUtil.vars(
                "reset_time", config.resetTimeText(),
                "time_left", TimeUtil.timeLeft(rotation.current().nextResetEpochMs()),
                "completed", missions.completedCount(player.getUniqueId()));

        ConfigurationSection infoSection = section.getConfigurationSection("info-item");
        if (infoSection != null && infoSlot >= 0 && infoSlot < size) {
            ItemStack info = ItemUtil.stack(infoSection.getString("material"), 1);
            ItemUtil.decorate(info,
                    TextUtil.replace(infoSection.getString("name", ""), common),
                    infoSection.getStringList("lore").stream()
                            .map(line -> TextUtil.replace(line, common))
                            .toList());
            inventory.setItem(infoSlot, info);
        }

        ConfigurationSection backSection = section.getConfigurationSection("back-item");
        if (backSection != null && backSlot >= 0 && backSlot < size) {
            ItemStack back = ItemUtil.stack(backSection.getString("material"), 1);
            ItemUtil.decorate(back, backSection.getString("name", "Назад"), backSection.getStringList("lore"));
            inventory.setItem(backSlot, back);
        }

        for (String slotKey : List.of("normal-1", "hard", "normal-2")) {
            int slot = slots.getInt(slotKey, -1);
            if (slot < 0 || slot >= size) continue;
            ActiveMission mission = missions.bySlot(player.getUniqueId(), slotKey);
            if (mission == null) continue;
            inventory.setItem(slot, card(config, mission));
        }
        return inventory;
    }

    private static ItemStack card(ConfigManager config, ActiveMission mission) {
        var definition = mission.definition();
        Map<String, Object> vars = TextUtil.vars(
                "diff_color", definition.difficulty().color(),
                "g1", definition.difficulty().gradientStart(),
                "g2", definition.difficulty().gradientEnd(),
                "mission_name", definition.name(),
                "mission_description", definition.description(),
                "difficulty_label", definition.difficulty().label(),
                "current", mission.progress(),
                "target", mission.target(),
                "reward", Math.max(1, config.shardsPerMission()),
                "reset_time", config.resetTimeText());

        List<String> lore = new ArrayList<>();
        for (String line : LORE_TEMPLATE) lore.add(TextUtil.replace(line, vars));
        lore.addAll(status(mission));

        ItemStack item = new ItemStack(definition.material(), 1);
        ItemUtil.decorate(item, TextUtil.replace(NAME_TEMPLATE, vars), lore);
        return item;
    }

    private static List<String> status(ActiveMission mission) {
        if (mission.completed() && mission.claimed()) {
            return List.of("<green>✓ Выполнено</green>");
        }
        if (mission.completed() && mission.pending()) {
            return List.of("<red>✖ Освободите место в инвентаре</red>",
                    "<yellow>▸ ЛКМ — забрать награду</yellow>");
        }
        if (mission.completed()) {
            return List.of("<yellow>▸ ЛКМ — забрать награду</yellow>");
        }
        return List.of("<yellow>▸ В процессе</yellow>");
    }
}
