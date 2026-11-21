package ru.cloudwars.cloudrewards.mission;

public record ActiveMission(MissionDefinition definition, String slotKey, int progress,
                            boolean completed, boolean claimed, boolean pending) {

    public int target() {
        return Math.max(1, definition.amount());
    }

    public boolean canClaim() {
        return completed && !claimed;
    }
}
