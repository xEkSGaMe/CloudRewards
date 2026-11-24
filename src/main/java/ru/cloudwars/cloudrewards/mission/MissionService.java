package ru.cloudwars.cloudrewards.mission;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import ru.cloudwars.cloudrewards.config.MissionConfig;
import ru.cloudwars.cloudrewards.storage.PlayerMissionState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class MissionService {

    private static final List<String> SLOT_KEYS = List.of("normal-1", "hard", "normal-2");

    private final MissionConfig missionConfig;
    private final RotationService rotation;
    private final ProgressService progress;

    public MissionService(MissionConfig missionConfig, RotationService rotation, ProgressService progress) {
        this.missionConfig = missionConfig;
        this.rotation = rotation;
        this.progress = progress;
    }

    public List<ActiveMission> active(UUID uuid) {
        List<ActiveMission> result = new ArrayList<>();
        for (String slotKey : SLOT_KEYS) {
            ActiveMission mission = bySlot(uuid, slotKey);
            if (mission != null) result.add(mission);
        }
        return result;
    }

    public ActiveMission bySlot(UUID uuid, String slotKey) {
        String missionId = rotation.current().missionId(slotKey);
        return byId(uuid, missionId);
    }

    public ActiveMission byId(UUID uuid, String missionId) {
        if (missionId == null) return null;
        MissionDefinition definition = missionConfig.byId(missionId);
        if (definition == null) return null;
        PlayerMissionState state = progress.state(uuid, missionId);
        return new ActiveMission(definition, slotKeyOf(missionId), state.progress(),
                state.completed(), state.claimed(), state.pending());
    }

    private String slotKeyOf(String missionId) {
        for (String key : SLOT_KEYS) {
            if (missionId.equals(rotation.current().missionId(key))) return key;
        }
        return "";
    }

    /** Порядок: обычная -> сложная -> обычная. Следующая открывается только после выполнения предыдущей. */
    public boolean isUnlocked(UUID uuid, String slotKey) {
        if (slotKey == null || slotKey.isBlank()) return true;
        return switch (slotKey) {
            case "hard" -> completed(uuid, "normal-1");
            case "normal-2" -> completed(uuid, "hard");
            default -> true;
        };
    }

    private boolean completed(UUID uuid, String slotKey) {
        ActiveMission mission = bySlot(uuid, slotKey);
        return mission != null && mission.completed();
    }

    /** Слот предыдущей миссии в цепочке (для подсказки в закрытой карточке). */
    public String previousSlotKey(String slotKey) {
        return switch (slotKey) {
            case "hard" -> "normal-1";
            case "normal-2" -> "hard";
            default -> null;
        };
    }

    /** Имя предыдущей миссии для сообщения «выполните сначала её». */
    public String previousMissionName(UUID uuid, String slotKey) {
        String previous = previousSlotKey(slotKey);
        if (previous == null) return "предыдущую миссию";
        ActiveMission mission = bySlot(uuid, previous);
        return mission == null ? "предыдущую миссию" : mission.definition().name();
    }

    public void apply(Player player, MissionType type, int amount, Predicate<MissionDefinition> filter) {
        if (amount <= 0) return;
        for (ActiveMission mission : active(player.getUniqueId())) {
            if (!isUnlocked(player.getUniqueId(), mission.slotKey())) continue;
            MissionDefinition definition = mission.definition();
            if (!definition.enabled() || definition.type() != type) continue;
            if (filter != null && !filter.test(definition)) continue;
            progress.increment(player.getUniqueId(), definition.id(), amount);
        }
    }

    /** Статистика для дистанций обрабатывается DistanceTracker — здесь только остальные типы. */
    public void syncStatistic(Player player, Statistic statistic) {
        if (MissionTypeHandler.trackedDistance(statistic)) return;
        for (ActiveMission mission : active(player.getUniqueId())) {
            if (!isUnlocked(player.getUniqueId(), mission.slotKey())) continue;
            MissionDefinition definition = mission.definition();
            if (!definition.enabled() || definition.type() != MissionType.STAT_DELTA) continue;
            if (!MissionTypeHandler.statistic(definition, statistic)) continue;
            progress.syncStatistic(player, statistic, definition.id());
        }
    }

    public void applyUnique(Player player, MissionType type, String key, Predicate<MissionDefinition> filter) {
        for (ActiveMission mission : active(player.getUniqueId())) {
            if (!isUnlocked(player.getUniqueId(), mission.slotKey())) continue;
            MissionDefinition definition = mission.definition();
            if (!definition.enabled() || definition.type() != type) continue;
            if (filter != null && !filter.test(definition)) continue;
            progress.incrementUnique(player.getUniqueId(), definition.id(), key);
        }
    }

    public int completedCount(UUID uuid) {
        return (int) active(uuid).stream()
                .filter(mission -> isUnlocked(uuid, mission.slotKey()))
                .filter(ActiveMission::completed)
                .count();
    }

    public boolean allClaimed(UUID uuid) {
        List<ActiveMission> missions = active(uuid);
        return missions.size() == 3 && missions.stream().allMatch(ActiveMission::claimed);
    }

    public void resetType(UUID uuid, MissionType type) {
        progress.resetType(uuid, type);
    }
}
