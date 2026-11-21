package ru.cloudwars.cloudrewards.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TextUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private TextUtil() {
    }

    public static Component mm(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        try {
            return MINI.deserialize(raw);
        } catch (RuntimeException ex) {
            return Component.text(raw.replaceAll("<[^>]*>", ""));
        }
    }

    public static List<Component> mmList(List<String> lines) {
        if (lines == null) return List.of();
        return lines.stream().map(TextUtil::mm).toList();
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
