package ru.cloudwars.cloudrewards.config;

import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.cloudwars.cloudrewards.util.TextUtil;

public final class MessageService {

    private final ConfigManager config;

    public MessageService(ConfigManager config) {
        this.config = config;
    }

    public String raw(String key, Object... placeholders) {
        String prefix = config.messages().getString("prefix", "");
        String value = config.messages().getString(key, key);
        value = value.replace("{prefix}", prefix);
        return TextUtil.replace(value, placeholders);
    }

    public Component component(String key, Object... placeholders) {
        return TextUtil.mm(raw(key, placeholders));
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void sound(Player player, String key, Sound fallback) {
        player.playSound(player.getLocation(), config.sound(key, fallback), 1.0f, 1.0f);
    }
}
