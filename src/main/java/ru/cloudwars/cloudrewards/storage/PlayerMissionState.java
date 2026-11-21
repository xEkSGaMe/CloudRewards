package ru.cloudwars.cloudrewards.storage;

import java.util.UUID;

public record PlayerMissionState(UUID uuid, String rotationDate, String missionId, int progress,
                                 boolean completed, boolean claimed, boolean pending, String meta) {

    public static PlayerMissionState empty(UUID uuid, String date, String missionId) {
        return new PlayerMissionState(uuid, date, missionId, 0, false, false, false, "");
    }
}
