package ru.cloudwars.cloudrewards.listener;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.gui.GuiHolder;
import ru.cloudwars.cloudrewards.gui.MenuService;
import ru.cloudwars.cloudrewards.reward.ExchangeService;
import ru.cloudwars.cloudrewards.reward.RewardService;

import java.util.List;

public final class GuiListener implements Listener {

    private final ConfigManager config;
    private final MessageService messages;
    private final MenuService menus;
    private final RewardService rewards;
    private final ExchangeService exchange;

    public GuiListener(ConfigManager config, MessageService messages, MenuService menus,
                       RewardService rewards, ExchangeService exchange) {
        this.config = config;
        this.messages = messages;
        this.menus = menus;
        this.rewards = rewards;
        this.exchange = exchange;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        GuiHolder holder = menus.holderOf(event.getInventory());
        if (holder == null) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClick() != ClickType.LEFT) return;

        messages.sound(player, "click", Sound.UI_BUTTON_CLICK);
        int slot = event.getSlot();

        if (holder.kind() == GuiHolder.Kind.MAIN) {
            ConfigurationSection section = config.section("gui.main");
            if (slot == section.getInt("items.missions.slot", -1)) {
                rewards.claimAll(player);
                menus.openMissions(player);
            } else if (slot == section.getInt("items.exchange.slot", -1)) {
                exchange.exchange(player);
            }
            return;
        }

        ConfigurationSection section = config.section("gui.missions");
        ConfigurationSection slots = section.getConfigurationSection("slots");
        for (String key : List.of("normal-1", "hard", "normal-2")) {
            if (slot == slots.getInt(key, -1)) {
                rewards.claimSlot(player, key);
                return;
            }
        }
        if (slot == slots.getInt("back", -1)) menus.openMain(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (menus.holderOf(event.getInventory()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (menus.holderOf(event.getInventory()) != null) {
            menus.close(event.getPlayer().getUniqueId());
        }
    }
}
