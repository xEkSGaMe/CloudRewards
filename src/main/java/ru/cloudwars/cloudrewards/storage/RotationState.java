package ru.cloudwars.cloudrewards.storage;

import java.util.List;

public record RotationState(String rotationDate, long nextResetEpochMs,
                            String slot12, String slot13, String slot14, List<String> previousIds) {

    public List<String> slots() {
        return List.of(slot12, slot13, slot14);
    }

    /** Ключ слота из конфигурации -> ID миссии. */
    public String missionId(String slotKey) {
        return switch (slotKey) {
            case "normal-1" -> slot12;
            case "hard" -> slot13;
            case "normal-2" -> slot14;
            default -> null;
        };
    }

    public static RotationState of(String date, long nextReset, List<String> ids, List<String> previous) {
        return new RotationState(date, nextReset, ids.get(0), ids.get(1), ids.get(2), previous);
    }
}
