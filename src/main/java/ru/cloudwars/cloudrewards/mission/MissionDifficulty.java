package ru.cloudwars.cloudrewards.mission;

public enum MissionDifficulty {

    NORMAL("#93C5FD", "#60A5FA", "<#60A5FA>Обычная"),
    HARD("#D8B4FE", "#A855F7", "<#A855F7>Сложная");

    private final String gradientStart;
    private final String gradientEnd;
    private final String label;

    MissionDifficulty(String gradientStart, String gradientEnd, String label) {
        this.gradientStart = gradientStart;
        this.gradientEnd = gradientEnd;
        this.label = label;
    }

    public String gradientStart() {
        return gradientStart;
    }

    public String gradientEnd() {
        return gradientEnd;
    }

    /** Цвет иконки/статуса сложности. */
    public String color() {
        return gradientEnd;
    }

    public String label() {
        return label;
    }
}
