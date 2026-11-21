package ru.cloudwars.cloudrewards.reward;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.gui.MenuService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class ExchangeService {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MessageService messages;
    private final ShardService shards;
    private final MenuService menus;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private Supplier<KeyGrantGateway> gateway = () -> null;

    public ExchangeService(JavaPlugin plugin, ConfigManager config, MessageService messages,
                           ShardService shards, MenuService menus) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.shards = shards;
        this.menus = menus;
    }

    public void setGateway(Supplier<KeyGrantGateway> gateway) {
        this.gateway = gateway;
    }

    public void exchange(Player player) {
        UUID uuid = player.getUniqueId();
        ConfigManager.ExchangeSettings settings = config.exchange();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(uuid);
        if (last != null && now - last < Math.max(0, settings.cooldownSeconds()) * 1000L) {
            messages.send(player, "exchange-cooldown");
            return;
        }

        int have = shards.countShards(player);
        int cost = Math.max(1, settings.cost());
        if (have < cost) {
            messages.send(player, "exchange-not-enough", "have", have, "cost", cost);
            messages.sound(player, "fail", Sound.BLOCK_NOTE_BLOCK_BASS);
            return;
        }
        cooldowns.put(uuid, now);

        RolledCase rolled = roll(settings);
        KeyGrantGateway gateway = this.gateway.get();
        boolean available = gateway != null && gateway.available();

        if (settings.mode() == ConfigManager.Mode.STUB || !available) {
            if (!settings.stubRefundShards()) shards.removeShards(player, cost);
            messages.send(player, "exchange-stub");
            messages.sound(player, "exchange", Sound.BLOCK_AMETHYST_BLOCK_CHIME);
            menus.refreshIfOpen(player);
            return;
        }

        if (!shards.removeShards(player, cost)) {
            messages.send(player, "exchange-not-enough", "have", shards.countShards(player), "cost", cost);
            return;
        }

        UUID sourcePlayer = settings.sourcePlayerInitiator() ? uuid : null;
        gateway.giveKey(uuid, rolled.caseId(), plugin.getName(), sourcePlayer)
                .whenComplete((result, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (result == null || !result.success()) {
                        shards.refund(uuid, cost);
                        if (online != null) messages.send(online, "exchange-failed");
                        plugin.getLogger().warning("Exchange refund for " + uuid + ": "
                                + (result == null ? String.valueOf(ex) : result.failure()));
                        return;
                    }
                    if (online != null) {
                        messages.send(online, "exchange-success", "cost", cost, "reward", rolled.displayName());
                        messages.sound(online, "exchange", Sound.BLOCK_AMETHYST_BLOCK_CHIME);
                        menus.refreshIfOpen(online);
                    }
                }));
    }

    private RolledCase roll(ConfigManager.ExchangeSettings settings) {
        double small = Math.max(0, settings.smallChance());
        double medium = Math.max(0, settings.mediumChance());
        double large = Math.max(0, settings.largeChance());
        double total = small + medium + large;
        if (total <= 0) {
            small = 50;
            medium = 25;
            large = 25;
            total = 100;
        }
        double value = ThreadLocalRandom.current().nextDouble(total);
        if (value < small) return new RolledCase(settings.smallCase(), "Малый ключ");
        if (value < small + medium) return new RolledCase(settings.mediumCase(), "Средний ключ");
        return new RolledCase(settings.largeCase(), "Большой ключ");
    }

    private record RolledCase(String caseId, String displayName) {
    }
}
