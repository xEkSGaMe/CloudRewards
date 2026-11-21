package ru.cloudwars.cloudrewards.util;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private TimeUtil() {
    }

    public static ZoneId zone(String name) {
        try {
            return ZoneId.of(name);
        } catch (RuntimeException ex) {
            return ZoneId.of("Europe/Moscow");
        }
    }

    public static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME);
        } catch (RuntimeException ex) {
            return LocalTime.of(13, 0);
        }
    }

    public static String formatTime(LocalTime time) {
        return time.format(TIME);
    }

    public static String date(ZonedDateTime now) {
        return now.format(DATE);
    }

    /** Дата активной ротации: после reset сегодня — сегодня, до reset — вчера. */
    public static String rotationDate(ZonedDateTime now, LocalTime reset) {
        ZonedDateTime day = passed(now, reset) ? now : now.minusDays(1);
        return day.format(DATE);
    }

    public static boolean passed(ZonedDateTime now, LocalTime reset) {
        return !now.toLocalTime().isBefore(reset);
    }

    /** Следующий момент ресета в миллисекундах epoch. */
    public static long nextResetEpochMs(ZonedDateTime now, LocalTime reset) {
        ZonedDateTime target = passed(now, reset)
                ? now.plusDays(1).with(reset)
                : now.with(reset);
        return target.toInstant().toEpochMilli();
    }

    public static String timeLeft(long epochMs) {
        long seconds = Math.max(0L, (epochMs - System.currentTimeMillis()) / 1000L);
        long h = seconds / 3600L;
        long m = (seconds % 3600L) / 60L;
        long s = seconds % 60L;
        return h + "ч " + m + "м " + s + "с";
    }

    public static long ticksUntil(long epochMs) {
        return Math.max(1L, (epochMs - System.currentTimeMillis()) / 50L);
    }
}
