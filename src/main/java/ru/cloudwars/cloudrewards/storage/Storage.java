package ru.cloudwars.cloudrewards.storage;

import java.util.UUID;

public interface Storage extends AutoCloseable {

    RotationState loadRotation();

    void saveRotation(RotationState state);

    PlayerMissionState loadProgress(UUID uuid, String rotationDate, String missionId);

    void saveProgress(PlayerMissionState state);

    BonusState loadBonus(UUID uuid, String rotationDate);

    void saveBonus(UUID uuid, String rotationDate, boolean claimed, boolean pending);

    @Override
    void close();

    record BonusState(boolean claimed, boolean pending) {
    }
}
