package ru.cloudwars.cloudrewards.gui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.RotationService;
import ru.cloudwars.cloudrewards.reward.ShardService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuService {

    private final ConfigManager config;
    private final MessageService messages;
    private final MissionService missions;
    private final RotationService rotation;
    private final ShardService shards;
    private final Map<UUID, GuiHolder> open = new HashMap<>();

    public MenuService(JavaPlugin plugin, ConfigManager config, MessageService messages,
                       MissionService missions, RotationService rotation, ShardService shards) {
        this.config = config;
        this.messages = messages;
        this.missions = missions;
        this.rotation = rotation;
        this.shards = shards;
    }

    public void openMain(Player player) {
        Inventory inventory = MainMenu.build(config, shards, player);
        player.openInventory(inventory);
        open.put(player.getUniqueId(), (GuiHolder) inventory.getHolder());
        messages.sound(player, "open", Sound.BLOCK_CHEST_OPEN);
    }

    public void openMissions(Player player) {
        Inventory inventory = MissionsMenu.build(config, missions, rotation, player);
        player.openInventory(inventory);
        open.put(player.getUniqueId(), (GuiHolder) inventory.getHolder());
        messages.sound(player, "open", Sound.BLOCK_CHEST_OPEN);
    }

    public void close(UUID uuid) {
        open.remove(uuid);
    }

    public GuiHolder holderOf(Inventory inventory) {
        return inventory.getHolder() instanceof GuiHolder holder ? holder : null;
    }

    /** Обновить содержимое уже открытого меню, не закрывая его. */
    public void refreshIfOpen(Player player) {
        GuiHolder holder = open.get(player.getUniqueId());
        if (holder == null) return;
        Inventory fresh = holder.kind() == GuiHolder.Kind.MAIN
                ? MainMenu.build(config, shards, player)
                : MissionsMenu.build(config, missions, rotation, player);
        holder.getInventory().setContents(fresh.getContents());
    }
}
