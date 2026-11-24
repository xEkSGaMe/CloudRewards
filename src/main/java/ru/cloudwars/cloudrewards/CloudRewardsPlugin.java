package ru.cloudwars.cloudrewards;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cloudwars.cloudrewards.command.CloudRewardsCommand;
import ru.cloudwars.cloudrewards.command.MissionsCommand;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.config.MissionConfig;
import ru.cloudwars.cloudrewards.gui.MenuService;
import ru.cloudwars.cloudrewards.listener.DistanceTracker;
import ru.cloudwars.cloudrewards.listener.GuiListener;
import ru.cloudwars.cloudrewards.listener.JoinListener;
import ru.cloudwars.cloudrewards.listener.ProgressListener;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.ProgressService;
import ru.cloudwars.cloudrewards.mission.RotationService;
import ru.cloudwars.cloudrewards.reward.CoolContainersHook;
import ru.cloudwars.cloudrewards.reward.ExchangeService;
import ru.cloudwars.cloudrewards.reward.RewardService;
import ru.cloudwars.cloudrewards.reward.ShardService;
import ru.cloudwars.cloudrewards.storage.SqliteStorage;
import ru.cloudwars.cloudrewards.storage.Storage;

public final class CloudRewardsPlugin extends JavaPlugin {

    private ConfigManager config;
    private Storage storage;
    private RotationService rotation;
    private MenuService menus;
    private DistanceTracker distanceTracker;

    @Override
    public void onEnable() {
        config = new ConfigManager(this);
        config.load();

        MessageService messages = new MessageService(config);
        MissionConfig missionConfig = new MissionConfig(config, this);
        missionConfig.load();

        try {
            storage = new SqliteStorage(this);
        } catch (Exception ex) {
            getLogger().severe("Не удалось инициализировать SQLite — плагин отключается: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        rotation = new RotationService(this, config, missionConfig, storage);
        ProgressService progress = new ProgressService(this, config, missionConfig, rotation, storage, messages);
        ShardService shards = new ShardService(this, config);
        shards.load();
        MissionService missions = new MissionService(missionConfig, rotation, progress);
        menus = new MenuService(this, config, messages, missions, rotation, shards);
        RewardService rewards = new RewardService(config, messages, missions, progress, shards, menus);
        progress.setRewardService(rewards);

        ExchangeService exchange = new ExchangeService(this, config, messages, shards, menus);
        // Хук создаётся только если CoolContainers реально установлен.
        exchange.setGateway(() -> Bukkit.getPluginManager().getPlugin("CoolContainers") != null
                ? new CoolContainersHook(this)
                : null);

        MissionsCommand missionsCommand = new MissionsCommand(messages, menus, rewards);
        CloudRewardsCommand adminCommand = new CloudRewardsCommand(
                config, missionConfig, messages, missions, rotation, progress, shards);
        if (getCommand("missions") != null) getCommand("missions").setExecutor(missionsCommand);
        if (getCommand("cloudrewards") != null) {
            getCommand("cloudrewards").setExecutor(adminCommand);
            getCommand("cloudrewards").setTabCompleter(adminCommand);
        }

        register(new GuiListener(config, messages, menus, missions, rewards, exchange));
        register(new JoinListener(this, rewards, menus));
        register(new ProgressListener(this, missions));
        distanceTracker = new DistanceTracker(this, missions);
        distanceTracker.start();

        rotation.initialize();
        getLogger().info("CloudRewards включён. Ресет ежедневно в "
                + config.resetTimeText() + " (" + config.zone().getId() + ")");
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        if (distanceTracker != null) distanceTracker.stop();
        if (menus != null) menus.shutdown();
        if (rotation != null) rotation.shutdown();
        if (storage != null) storage.close();
    }
}
