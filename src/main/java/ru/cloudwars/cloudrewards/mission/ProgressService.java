package ru.cloudwars.cloudrewards.mission;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.config.MissionConfig;
import ru.cloudwars.cloudrewards.reward.RewardService;
import ru.cloudwars.cloudrewards.storage.PlayerMissionState;
import ru.cloudwars.cloudrewards.storage.RotationState;
import ru.cloudwars.cloudrewards.storage.Storage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressService {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MissionConfig missionConfig;
    private final RotationService rotation;
    private final Storage storage;
    private final MessageService messages;
    private final Map<String, Set<String>> uniqueCache = new ConcurrentHashMap<>();
    /** Последние замеченные значения статистики, чтобы не считать дистанцию дважды. */
    private final Map<String, Integer> lastStatistic = new ConcurrentHashMap<>();
    private RewardService rewards;

    public ProgressService(JavaPlugin plugin, ConfigManager config, MissionConfig missionConfig,
                           RotationService rotation, Storage storage, MessageService messages) {
        this.plugin = plugin;
        this.config = config;
        this.missionConfig = missionConfig;
        this.rotation = rotation;
        this.storage = storage;
        this.messages = messages;
    }

    public void setRewardService(RewardService rewards) {
        this.rewards = rewards;
    }

    public PlayerMissionState state(UUID uuid, String missionId) {
        String date = rotation.current().rotationDate();
        PlayerMissionState state = storage.loadProgress(uuid, date, missionId);
        return state == null ? PlayerMissionState.empty(uuid, date, missionId) : state;
    }

    public int progress(UUID uuid, String missionId) {
        return state(uuid, missionId).progress();
    }

    public void increment(UUID uuid, String missionId, int delta) {
        if (delta <= 0) return;
        PlayerMissionState state = state(uuid, missionId);
        if (state.completed() || state.claimed()) return;
        applyProgress(uuid, missionId, state.progress() + delta, null);
    }

    /**
     * Синхронизация накопительной статистики (расстояние лодкой/плаванием/элитрами).
     * И событие, и опрос тикают через этот метод, поэтому дельта применяется один раз.
     */
    public void syncStatistic(Player player, Statistic statistic, String missionId) {
        String key = player.getUniqueId() + "|" + rotation.current().rotationDate()
                + "|" + statistic.name() + "|" + missionId;
        int current = player.getStatistic(statistic);
        Integer previous = lastStatistic.put(key, current);
        if (previous == null || current <= previous) return;
        increment(player.getUniqueId(), missionId, current - previous);
    }

    public void setProgress(UUID uuid, String missionId, int value) {
        applyProgress(uuid, missionId, value, null);
    }

    /** Уникальные элементы коллекций: второй зачёт того же ключа не считается. */
    public void incrementUnique(UUID uuid, String missionId, String key) {
        if (key == null || key.isBlank()) return;
        PlayerMissionState state = state(uuid, missionId);
        if (state.completed() || state.claimed()) return;

        String cacheKey = uuid + "|" + state.rotationDate() + "|" + missionId;
        Set<String> set = uniqueCache.computeIfAbsent(cacheKey, ignored -> parseMeta(state.meta()));
        if (!set.add(key.toLowerCase())) return;
        applyProgress(uuid, missionId, set.size(), String.join(",", set));
    }

    private void applyProgress(UUID uuid, String missionId, int value, String meta) {
        MissionDefinition definition = missionConfig.byId(missionId);
        if (definition == null) return;
        PlayerMissionState state = state(uuid, missionId);
        if (state.claimed()) return;

        int target = Math.max(1, definition.amount());
        int progress = Math.max(0, Math.min(target, value));
        boolean completed = progress >= target;
        boolean justCompleted = completed && !state.completed();
        PlayerMissionState updated = new PlayerMissionState(uuid, state.rotationDate(), missionId,
                progress, completed, false, completed && state.pending(),
                meta == null ? state.meta() : meta);
        storage.saveProgress(updated);

        if (justCompleted) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) messages.send(player, "mission-complete", "mission", definition.name());
            if (rewards != null) rewards.onMissionCompleted(uuid, missionId);
        }
    }

    public void markClaimed(UUID uuid, String missionId) {
        PlayerMissionState state = state(uuid, missionId);
        storage.saveProgress(new PlayerMissionState(uuid, state.rotationDate(), missionId,
                state.progress(), state.completed(), true, false, state.meta()));
    }

    public void markPending(UUID uuid, String missionId, boolean pending) {
        PlayerMissionState state = state(uuid, missionId);
        storage.saveProgress(new PlayerMissionState(uuid, state.rotationDate(), missionId,
                state.progress(), state.completed(), state.claimed(), pending, state.meta()));
    }

    public Storage.BonusState bonus(UUID uuid) {
        return storage.loadBonus(uuid, rotation.current().rotationDate());
    }

    public void markBonus(UUID uuid, boolean claimed, boolean pending) {
        storage.saveBonus(uuid, rotation.current().rotationDate(), claimed, pending);
    }

    /** Сброс прогресса «без сна» при посещении кровати. */
    public void resetType(UUID uuid, MissionType type) {
        RotationState rotationState = rotation.current();
        for (String missionId : rotationState.slots()) {
            MissionDefinition definition = missionConfig.byId(missionId);
            if (definition == null || definition.type() != type) continue;
            PlayerMissionState state = state(uuid, missionId);
            if (state.claimed()) continue;
            applyProgress(uuid, missionId, 0, null);
        }
    }

    private static Set<String> parseMeta(String meta) {
        if (meta == null || meta.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.stream(meta.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .toList());
    }

    public ConfigManager config() {
        return config;
    }

    public JavaPlugin plugin() {
        return plugin;
    }
}
