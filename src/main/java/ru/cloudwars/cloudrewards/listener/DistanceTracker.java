package ru.cloudwars.cloudrewards.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.MissionType;
import ru.cloudwars.cloudrewards.mission.MissionTypeHandler;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Дистанционные миссии считаются по факту перемещения игрока, а не по витринной
 * статистике сервера: она приходит с задержкой и «догоняет» уже после остановки.
 * Накопленные сантиметры сбрасываются в прогресс раз в секунду — одновременно
 * с обновлением карточек в меню.
 */
public final class DistanceTracker {

    private static final int FLUSH_EVERY_TICKS = 20;
    /** Большие скачки — телепорт/респавн — в прогресс не идут. */
    private static final double MAX_BLOCKS_PER_TICK = 4.0;

    private final JavaPlugin plugin;
    private final MissionService missions;
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Map<Statistic, Integer>> pending = new HashMap<>();
    private int ticks;
    private BukkitTask task;

    public DistanceTracker(JavaPlugin plugin, MissionService missions) {
        this.plugin = plugin;
        this.missions = missions;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastLocation.clear();
        pending.clear();
    }

    private void tick() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            online.add(uuid);

            Location now = player.getLocation();
            Location previous = lastLocation.put(uuid, now.clone());
            if (previous == null || !previous.getWorld().equals(now.getWorld())) continue;

            double blocks = now.distance(previous);
            if (blocks <= 0 || blocks > MAX_BLOCKS_PER_TICK) continue;

            Statistic statistic = statisticFor(player);
            if (statistic == null) continue;
            int cm = (int) Math.round(blocks * 100);
            if (cm <= 0) continue;

            pending.computeIfAbsent(uuid, key -> new EnumMap<>(Statistic.class))
                    .merge(statistic, cm, Integer::sum);
        }

        lastLocation.keySet().removeIf(uuid -> !online.contains(uuid));
        if (++ticks % FLUSH_EVERY_TICKS == 0) flush();
    }

    private void flush() {
        for (Map.Entry<UUID, Map<Statistic, Integer>> playerEntry : pending.entrySet()) {
            Player player = Bukkit.getPlayer(playerEntry.getKey());
            if (player == null) continue;
            for (Map.Entry<Statistic, Integer> entry : playerEntry.getValue().entrySet()) {
                Statistic statistic = entry.getKey();
                missions.apply(player, MissionType.STAT_DELTA, entry.getValue(),
                        definition -> MissionTypeHandler.statistic(definition, statistic));
            }
        }
        pending.clear();
    }

    /** Какая ванильная статистика соответствует текущему способу передвижения. */
    private static Statistic statisticFor(Player player) {
        if (player.getVehicle() instanceof Boat) return Statistic.BOAT_ONE_CM;
        if (player.getVehicle() instanceof Minecart) return Statistic.MINECART_ONE_CM;
        if (player.isGliding()) return Statistic.AVIATE_ONE_CM;
        if (player.isSwimming()) return Statistic.SWIM_ONE_CM;
        if (player.isOnGround()) return Statistic.WALK_ONE_CM;
        return null;
    }
}
