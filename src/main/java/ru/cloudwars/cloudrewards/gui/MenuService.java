package ru.cloudwars.cloudrewards.gui;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.RotationService;
import ru.cloudwars.cloudrewards.reward.ShardService;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MenuService {

    private final ConfigManager config;
    private final MessageService messages;
    private final MissionService missions;
    private final RotationService rotation;
    private final ShardService shards;
    private final Map<UUID, GuiHolder> open = new HashMap<>();
    private final BukkitTask tickTask;
    private Object lastRotation;

    public MenuService(JavaPlugin plugin, ConfigManager config, MessageService messages,
                       MissionService missions, RotationService rotation, ShardService shards) {
        this.config = config;
        this.messages = messages;
        this.missions = missions;
        this.rotation = rotation;
        this.shards = shards;
        // Раз в секунду обновляем обратный отсчёт в открытом меню миссий.
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
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

    /** Учитываем закрытие именно того инвентаря, который ещё числится открытым. */
    public void close(UUID uuid, Inventory inventory) {
        GuiHolder holder = open.get(uuid);
        if (holder != null && holder.getInventory().equals(inventory)) {
            open.remove(uuid);
        }
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

    private void tick() {
        Object state = rotation.current();
        boolean rotationChanged = state != lastRotation;
        if (rotationChanged) lastRotation = state;

        Iterator<Map.Entry<UUID, GuiHolder>> entries = open.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, GuiHolder> entry = entries.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                entries.remove();
                continue;
            }
            Inventory top = player.getOpenInventory().getTopInventory();
            GuiHolder holder = entry.getValue();
            if (!holder.getInventory().equals(top)) {
                entries.remove();
                continue;
            }
            if (holder.kind() != GuiHolder.Kind.MISSIONS) continue;
            if (rotationChanged) {
                refreshIfOpen(player); // новая ротация: карточки и флаги тоже могли измениться
            } else {
                MissionsMenu.refreshLive(config, missions, rotation, player, top);
            }
        }
    }

    public void shutdown() {
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        open.clear();
    }
}
