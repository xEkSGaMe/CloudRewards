package ru.cloudwars.cloudrewards.mission;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MissionConfig;
import ru.cloudwars.cloudrewards.storage.RotationState;
import ru.cloudwars.cloudrewards.storage.Storage;
import ru.cloudwars.cloudrewards.util.TimeUtil;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class RotationService {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MissionConfig missionConfig;
    private final Storage storage;
    private RotationState current;
    private int taskId = -1;

    public RotationService(JavaPlugin plugin, ConfigManager config, MissionConfig missionConfig, Storage storage) {
        this.plugin = plugin;
        this.config = config;
        this.missionConfig = missionConfig;
        this.storage = storage;
    }

    public void initialize() {
        current = storage.loadRotation();
        String today = TimeUtil.rotationDate(ZonedDateTime.now(config.zone()), config.resetTime());
        if (current == null || !current.rotationDate().equals(today)) {
            rotate();
        } else {
            // Ротации старой версии могли положить HARD в третий слот — чиним на месте.
            ensureDifficultyLayout();
        }
        if (current == null) {
            plugin.getLogger().severe("Не удалось создать ротацию — проверьте missions.yml. Плагин отключается.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }
        schedule();
    }

    public RotationState current() {
        return current;
    }

    /** Гарантия: 1-я и 3-я миссии обычные, 2-я всегда сложная. */
    public void ensureDifficultyLayout() {
        if (current == null || validLayout(current)) return;

        List<String> normals = new ArrayList<>();
        List<String> hards = new ArrayList<>();
        for (String id : current.slots()) {
            MissionDefinition definition = missionConfig.byId(id);
            if (definition == null || !definition.enabled()) continue;
            if (definition.difficulty() == MissionDifficulty.NORMAL && normals.size() < 2) normals.add(id);
            if (definition.difficulty() == MissionDifficulty.HARD && hards.isEmpty()) hards.add(id);
        }

        if (normals.size() < 2 || hards.isEmpty()) {
            rotate(); // в старой ротации нет подходящих миссий — выбираем заново
            return;
        }

        current = new RotationState(current.rotationDate(), current.nextResetEpochMs(),
                normals.get(0), hards.get(0), normals.get(1), current.previousIds());
        storage.saveRotation(current);
        plugin.getLogger().warning("Ротация исправлена: обычная → сложная → обычная.");
    }

    private boolean validLayout(RotationState state) {
        MissionDefinition first = missionConfig.byId(state.missionId("normal-1"));
        MissionDefinition second = missionConfig.byId(state.missionId("hard"));
        MissionDefinition third = missionConfig.byId(state.missionId("normal-2"));
        return first != null && first.enabled() && first.difficulty() == MissionDifficulty.NORMAL
                && second != null && second.enabled() && second.difficulty() == MissionDifficulty.HARD
                && third != null && third.enabled() && third.difficulty() == MissionDifficulty.NORMAL;
    }

    public void forceRotate() {
        rotate();
        schedule();
    }

    /** После изменения reset-time в конфиге: обновить таймер, но не сбрасывать ротацию. */
    public void recalculateNextReset() {
        if (current == null) return;
        refreshResetTime();
        schedule();
    }

    private void refreshResetTime() {
        long next = TimeUtil.nextResetEpochMs(ZonedDateTime.now(config.zone()), config.resetTime());
        if (next == current.nextResetEpochMs()) return;
        current = new RotationState(current.rotationDate(), next,
                current.slot12(), current.slot13(), current.slot14(), current.previousIds());
        storage.saveRotation(current);
    }

    private void schedule() {
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        if (current == null) return;
        if (current.nextResetEpochMs() <= System.currentTimeMillis()) refreshResetTime();
        long ticks = TimeUtil.ticksUntil(current.nextResetEpochMs());
        taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rotate();
            schedule();
        }, ticks).getTaskId();
    }

    private void rotate() {
        ZonedDateTime now = ZonedDateTime.now(config.zone());
        String date = TimeUtil.rotationDate(now, config.resetTime());
        List<String> previous = current == null ? List.of() : current.slots();

        List<String> normal = pick(missionConfig.pool(MissionDifficulty.NORMAL), new HashSet<>(previous), 2);
        List<String> hard = pick(missionConfig.pool(MissionDifficulty.HARD), new HashSet<>(previous), 1);

        if (normal.size() < 2 || hard.size() < 1) {
            plugin.getLogger().severe("В missions.yml слишком мало активных миссий: нужно минимум 2 NORMAL и 1 HARD.");
            return;
        }
        // Слоты: normal-1 (12), hard (13), normal-2 (14) — сложная ВСЕГДА вторая.
        List<String> ids = List.of(normal.get(0), hard.get(0), normal.get(1));
        current = RotationState.of(date,
                TimeUtil.nextResetEpochMs(now, config.resetTime()), ids, previous);
        storage.saveRotation(current);
        plugin.getLogger().info("Ротация " + date + ": " + String.join(", ", ids));
    }

    private List<String> pick(List<MissionDefinition> source, Set<String> exclude, int count) {
        List<MissionDefinition> candidates = source.stream()
                .filter(MissionDefinition::enabled)
                .filter(definition -> !exclude.contains(definition.id()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates);

        if (candidates.size() < count) {
            Set<String> chosen = candidates.stream()
                    .limit(count)
                    .map(MissionDefinition::id)
                    .collect(Collectors.toCollection(HashSet::new));
            List<MissionDefinition> fallback = source.stream()
                    .filter(MissionDefinition::enabled)
                    .filter(definition -> !chosen.contains(definition.id()))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(fallback);
            for (MissionDefinition definition : fallback) {
                if (candidates.size() >= count) break;
                candidates.add(definition);
            }
        }
        return candidates.stream().limit(count).map(MissionDefinition::id).toList();
    }

    public void shutdown() {
        if (taskId >= 0) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
