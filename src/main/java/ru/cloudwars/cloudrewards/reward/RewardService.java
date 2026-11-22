package ru.cloudwars.cloudrewards.reward;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.gui.MenuService;
import ru.cloudwars.cloudrewards.mission.ActiveMission;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.ProgressService;
import ru.cloudwars.cloudrewards.storage.Storage;

import java.util.UUID;

public final class RewardService {

    private final ConfigManager config;
    private final MessageService messages;
    private final MissionService missions;
    private final ProgressService progress;
    private final ShardService shards;
    private final MenuService menus;

    public RewardService(ConfigManager config, MessageService messages, MissionService missions,
                         ProgressService progress, ShardService shards, MenuService menus) {
        this.config = config;
        this.messages = messages;
        this.missions = missions;
        this.progress = progress;
        this.shards = shards;
        this.menus = menus;
    }

    /** Повторная попытка выдачи всех pending-наград (join / открытие меню). */
    public void claimAll(Player player) {
        for (ActiveMission mission : missions.active(player.getUniqueId())) {
            if (!missions.isUnlocked(player.getUniqueId(), mission.slotKey())) continue;
            if (mission.canClaim()) claim(player, mission);
        }
        tryBonus(player);
    }

    /** Клик по карточке миссии. */
    public void claimSlot(Player player, String slotKey) {
        if (!missions.isUnlocked(player.getUniqueId(), slotKey)) return;
        ActiveMission mission = missions.bySlot(player.getUniqueId(), slotKey);
        if (mission == null) return;
        // Pending-награда повторно выдаётся по клику, если место освободилось.
        if (claim(player, mission)) menus.refreshIfOpen(player);
    }

    /** Вызывается ProgressService в момент завершения миссии. */
    public void onMissionCompleted(UUID uuid, String missionId) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        ActiveMission mission = missions.byId(uuid, missionId);
        if (mission != null && missions.isUnlocked(uuid, mission.slotKey())) claim(player, mission);
    }

    private boolean claim(Player player, ActiveMission mission) {
        if (!mission.canClaim()) return false;
        UUID uuid = player.getUniqueId();
        String missionId = mission.definition().id();
        int amount = Math.max(1, config.shardsPerMission());

        if (!config.requireInventorySpace() || shards.hasSpace(player, amount)) {
            shards.giveShards(player, amount);
            progress.markClaimed(uuid, missionId);
            messages.send(player, "reward-given", "amount", amount);
            messages.sound(player, "success", Sound.ENTITY_PLAYER_LEVELUP);
            tryBonus(player);
            menus.refreshIfOpen(player);
            return true;
        }

        progress.markPending(uuid, missionId, true);
        int slots = Math.max(1, shards.slotsNeeded(player, amount));
        messages.send(player, "reward-no-space", "slots", slots);
        messages.sound(player, "fail", Sound.BLOCK_NOTE_BLOCK_BASS);
        menus.refreshIfOpen(player);
        return false;
    }

    private void tryBonus(Player player) {
        UUID uuid = player.getUniqueId();
        if (!missions.allClaimed(uuid)) return;
        Storage.BonusState bonus = progress.bonus(uuid);
        if (bonus.claimed()) return;

        int amount = Math.max(1, config.allCompleteBonus());
        if (!config.requireInventorySpace() || shards.hasSpace(player, amount)) {
            shards.giveShards(player, amount);
            progress.markBonus(uuid, true, false);
            messages.send(player, "all-complete");
            messages.send(player, "bonus-given", "amount", amount);
            messages.sound(player, "success", Sound.ENTITY_PLAYER_LEVELUP);
            menus.refreshIfOpen(player);
            return;
        }

        progress.markBonus(uuid, false, true);
        if (!bonus.pending()) {
            messages.send(player, "reward-no-space", "slots", Math.max(1, shards.slotsNeeded(player, amount)));
        }
        messages.sound(player, "fail", Sound.BLOCK_NOTE_BLOCK_BASS);
    }
}
