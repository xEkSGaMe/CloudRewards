package ru.cloudwars.cloudrewards.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.gui.MenuService;
import ru.cloudwars.cloudrewards.reward.RewardService;

public final class JoinListener implements Listener {

    private final JavaPlugin plugin;
    private final RewardService rewards;
    private final MenuService menus;

    public JoinListener(JavaPlugin plugin, RewardService rewards, MenuService menus) {
        this.plugin = plugin;
        this.rewards = rewards;
        this.menus = menus;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rewards.claimAll(event.getPlayer());
            menus.refreshIfOpen(event.getPlayer());
        }, 20L);
    }
}
