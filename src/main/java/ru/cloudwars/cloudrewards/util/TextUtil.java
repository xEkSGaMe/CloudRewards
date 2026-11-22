package ru.cloudwars.cloudrewards.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern GRADIENT_TAG = Pattern.compile("<gradient:([^<>]+)>");
    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6})");
    /** Собственный формат окраски: &#RRGGBB окрашивает все символы до следующей такой метки. */
    private static final Pattern CUSTOM_COLOR = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final int GRADIENT_STEPS = 12;

    private TextUtil() {
    }

    public static Component mm(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        String smooth = smoothGradients(decodeColors(raw));
        try {
            return MINI.deserialize(smooth);
        } catch (RuntimeException ex) {
            return Component.text(smooth.replaceAll("<[^>]*>", ""));
        }
    }

    public static List<Component> mmList(List<String> lines) {
        if (lines == null) return List.of();
        return lines.stream().map(TextUtil::mm).toList();
    }

    /** &#RRGGBB → обычный MiniMessage-тег <#RRGGBB>, чтобы конфиги могли использовать наш формат. */
    private static String decodeColors(String raw) {
        return CUSTOM_COLOR.matcher(raw).replaceAll(match -> "<#" + match.group(1) + ">");
    }

    /**
     * MiniMessage интерполирует градиент только по символам: на коротких названиях
     * двух стопов дают резкий переход, а RGB-интерполяция между светлыми оттенками
     * проходит через выцветший серый. Заменяем такие теги на набор стопов,
     * посчитанных в HSV — цвет остаётся насыщенным на всём протяжении строки.
     */
    private static String smoothGradients(String raw) {
        Matcher matcher = GRADIENT_TAG.matcher(raw);
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            result.append(raw, last, matcher.start());
            String expanded = expandGradient(matcher.group(1));
            result.append(expanded == null ? matcher.group() : expanded);
            last = matcher.end();
        }
        result.append(raw, last, raw.length());
        return result.toString();
    }

    private static String expandGradient(String inner) {
        String[] tokens = inner.split(":", -1);
        List<float[]> stops = new ArrayList<>();
        for (String token : tokens) {
            Matcher hex = HEX.matcher(token.trim());
            if (!hex.matches()) return null; // именованные цвета/опции оставляем как есть
            int rgb = Integer.parseInt(hex.group(1), 16);
            stops.add(Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null));
        }
        if (stops.size() < 2) return null;

        StringBuilder tag = new StringBuilder("<gradient:");
        for (int i = 0; i <= GRADIENT_STEPS; i++) {
            if (i > 0) tag.append(':');
            tag.append(colorAt(stops, (double) i / GRADIENT_STEPS));
        }
        return tag.append('>').toString();
    }

    private static String colorAt(List<float[]> stops, double position) {
        double scaled = position * (stops.size() - 1);
        int index = Math.min((int) scaled, stops.size() - 2);
        float t = (float) (scaled - index);
        float[] from = stops.get(index);
        float[] to = stops.get(index + 1);

        float hueDelta = to[0] - from[0];
        if (hueDelta > 0.5f) hueDelta -= 1f;
        if (hueDelta < -0.5f) hueDelta += 1f;
        float hue = (from[0] + hueDelta * t + 1f) % 1f;
        float saturation = lerp(from[1], to[1], t);
        float brightness = lerp(from[2], to[2], t);
        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
        return String.format("#%06X", rgb);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static String replace(String raw, Map<String, Object> values) {
        if (raw == null) return "";
        String out = raw;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return out;
    }

    public static String replace(String raw, Object... kv) {
        return replace(raw, vars(kv));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static Map<String, Object> vars(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
