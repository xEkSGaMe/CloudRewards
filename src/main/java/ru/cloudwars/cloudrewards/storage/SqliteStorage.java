package ru.cloudwars.cloudrewards.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SqliteStorage implements Storage {

    private final Connection connection;

    public SqliteStorage(JavaPlugin plugin) throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        File file = new File(plugin.getDataFolder(), "cloudrewards.db");
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS rotation (
                      id INTEGER PRIMARY KEY CHECK (id = 1),
                      rotation_date TEXT NOT NULL,
                      next_reset_epoch_ms INTEGER NOT NULL,
                      slot12 TEXT NOT NULL,
                      slot13 TEXT NOT NULL,
                      slot14 TEXT NOT NULL,
                      previous_json TEXT NOT NULL DEFAULT '[]'
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_progress (
                      uuid TEXT NOT NULL,
                      rotation_date TEXT NOT NULL,
                      mission_id TEXT NOT NULL,
                      progress INTEGER NOT NULL DEFAULT 0,
                      completed INTEGER NOT NULL DEFAULT 0,
                      claimed INTEGER NOT NULL DEFAULT 0,
                      pending INTEGER NOT NULL DEFAULT 0,
                      progress_meta TEXT NOT NULL DEFAULT '',
                      PRIMARY KEY (uuid, rotation_date, mission_id)
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_bonus (
                      uuid TEXT NOT NULL,
                      rotation_date TEXT NOT NULL,
                      bonus_claimed INTEGER NOT NULL DEFAULT 0,
                      bonus_pending INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY (uuid, rotation_date)
                    )""");
        }
    }

    @Override
    public synchronized RotationState loadRotation() {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM rotation WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;
            return new RotationState(
                    rs.getString("rotation_date"),
                    rs.getLong("next_reset_epoch_ms"),
                    rs.getString("slot12"),
                    rs.getString("slot13"),
                    rs.getString("slot14"),
                    parseIds(rs.getString("previous_json")));
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить ротацию", ex);
        }
    }

    @Override
    public synchronized void saveRotation(RotationState state) {
        String sql = """
                INSERT INTO rotation (id, rotation_date, next_reset_epoch_ms, slot12, slot13, slot14, previous_json)
                VALUES (1, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                  rotation_date = excluded.rotation_date,
                  next_reset_epoch_ms = excluded.next_reset_epoch_ms,
                  slot12 = excluded.slot12,
                  slot13 = excluded.slot13,
                  slot14 = excluded.slot14,
                  previous_json = excluded.previous_json""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, state.rotationDate());
            ps.setLong(2, state.nextResetEpochMs());
            ps.setString(3, state.slot12());
            ps.setString(4, state.slot13());
            ps.setString(5, state.slot14());
            ps.setString(6, toJson(state.previousIds()));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить ротацию", ex);
        }
    }

    @Override
    public synchronized PlayerMissionState loadProgress(UUID uuid, String rotationDate, String missionId) {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT progress, completed, claimed, pending, progress_meta FROM player_progress
                WHERE uuid = ? AND rotation_date = ? AND mission_id = ?""")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rotationDate);
            ps.setString(3, missionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PlayerMissionState(uuid, rotationDate, missionId,
                        rs.getInt("progress"),
                        rs.getBoolean("completed"),
                        rs.getBoolean("claimed"),
                        rs.getBoolean("pending"),
                        rs.getString("progress_meta"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить прогресс", ex);
        }
    }

    @Override
    public synchronized void saveProgress(PlayerMissionState state) {
        String sql = """
                INSERT INTO player_progress
                  (uuid, rotation_date, mission_id, progress, completed, claimed, pending, progress_meta)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, rotation_date, mission_id) DO UPDATE SET
                  progress = excluded.progress,
                  completed = excluded.completed,
                  claimed = excluded.claimed,
                  pending = excluded.pending,
                  progress_meta = excluded.progress_meta""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, state.uuid().toString());
            ps.setString(2, state.rotationDate());
            ps.setString(3, state.missionId());
            ps.setInt(4, state.progress());
            ps.setInt(5, state.completed() ? 1 : 0);
            ps.setInt(6, state.claimed() ? 1 : 0);
            ps.setInt(7, state.pending() ? 1 : 0);
            ps.setString(8, state.meta() == null ? "" : state.meta());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить прогресс", ex);
        }
    }

    @Override
    public synchronized BonusState loadBonus(UUID uuid, String rotationDate) {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT bonus_claimed, bonus_pending FROM player_bonus
                WHERE uuid = ? AND rotation_date = ?""")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rotationDate);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new BonusState(false, false);
                return new BonusState(rs.getBoolean("bonus_claimed"), rs.getBoolean("bonus_pending"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось загрузить бонус", ex);
        }
    }

    @Override
    public synchronized void saveBonus(UUID uuid, String rotationDate, boolean claimed, boolean pending) {
        String sql = """
                INSERT INTO player_bonus (uuid, rotation_date, bonus_claimed, bonus_pending)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid, rotation_date) DO UPDATE SET
                  bonus_claimed = excluded.bonus_claimed,
                  bonus_pending = excluded.bonus_pending""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rotationDate);
            ps.setInt(3, claimed ? 1 : 0);
            ps.setInt(4, pending ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось сохранить бонус", ex);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось закрыть SQLite", ex);
        }
    }

    private static List<String> parseIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        String cleaned = json.replace("[", "").replace("]", "").replace("\"", "").trim();
        if (cleaned.isEmpty()) return List.of();
        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String toJson(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }
}
