package ru.cloudwars.cloudrewards.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.reward.ShardService;
import ru.cloudwars.cloudrewards.util.ItemUtil;
import ru.cloudwars.cloudrewards.util.TextUtil;

import java.util.List;
import java.util.Map;

public final class MainMenu {

    private MainMenu() {
    }

    public static Inventory build(ConfigManager config, ShardService shards, Player player) {
        ConfigurationSection section = config.section("gui.main");
        Component title = TextUtil.mm(section.getString("title", "Миссии"));
        int size = section.getInt("size", 27);
        GuiHolder holder = new GuiHolder(GuiHolder.Kind.MAIN, size, title);
        Inventory inventory = holder.getInventory();

        applyFiller(inventory, size, section.getConfigurationSection("filler"));

        Map<String, Object> vars = TextUtil.vars(
                "reset_time", config.resetTimeText(),
                "shards", shards.countShards(player));
        for (String key : List.of("missions", "help", "exchange")) {
            ConfigurationSection itemSection = section.getConfigurationSection("items." + key);
            if (itemSection == null) continue;
            int slot = itemSection.getInt("slot", -1);
            if (slot < 0 || slot >= size) continue;
            ItemStack item = ItemUtil.stack(itemSection.getString("material"), 1);
            ItemUtil.decorate(item,
                    TextUtil.replace(itemSection.getString("name", ""), vars),
                    itemSection.getStringList("lore").stream()
                            .map(line -> TextUtil.replace(line, vars))
                            .toList());
            inventory.setItem(slot, item);
        }
        return inventory;
    }

    static void applyFiller(Inventory inventory, int size, ConfigurationSection filler) {
        if (filler == null || !filler.getBoolean("enabled", true)) return;
        ItemStack fillerItem = ItemUtil.stack(filler.getString("material"), 1);
        ItemUtil.decorate(fillerItem, filler.getString("name", " "), List.of());
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) inventory.setItem(i, fillerItem.clone());
        }
    }
}
