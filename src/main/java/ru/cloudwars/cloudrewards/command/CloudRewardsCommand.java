package ru.cloudwars.cloudrewards.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.cloudwars.cloudrewards.config.ConfigManager;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.config.MissionConfig;
import ru.cloudwars.cloudrewards.mission.MissionDefinition;
import ru.cloudwars.cloudrewards.mission.MissionService;
import ru.cloudwars.cloudrewards.mission.ProgressService;
import ru.cloudwars.cloudrewards.mission.RotationService;
import ru.cloudwars.cloudrewards.reward.ShardService;

import java.util.ArrayList;
import java.util.List;

public final class CloudRewardsCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager config;
    private final MissionConfig missionConfig;
    private final MessageService messages;
    private final MissionService missions;
    private final RotationService rotation;
    private final ProgressService progress;
    private final ShardService shards;

    public CloudRewardsCommand(ConfigManager config, MissionConfig missionConfig, MessageService messages,
                               MissionService missions, RotationService rotation,
                               ProgressService progress, ShardService shards) {
        this.config = config;
        this.missionConfig = missionConfig;
        this.messages = messages;
        this.missions = missions;
        this.rotation = rotation;
        this.progress = progress;
        this.shards = shards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7/cloudrewards <reload|reset|giveshard|setprogress>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!check(sender, "cloudrewards.admin.reload")) return true;
                config.reload();
                missionConfig.load();
                shards.load();
                rotation.recalculateNextReset();
                messages.send(sender, "reload");
            }
            case "reset" -> {
                if (!check(sender, "cloudrewards.admin.reset")) return true;
                rotation.forceRotate();
                messages.send(sender, "reset");
            }
            case "giveshard" -> {
                if (!check(sender, "cloudrewards.admin.giveshard")) return true;
                if (args.length < 2) {
                    sender.sendMessage("§7/cloudrewards giveshard <player> [amount]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "admin-player-not-found");
                    return true;
                }
                int amount = parseInt(args.length > 2 ? args[2] : "1", 1);
                if (!shards.giveShards(target, amount)) {
                    messages.send(sender, "reward-no-space", "slots", shards.slotsNeeded(target, amount));
                    return true;
                }
                messages.send(sender, "admin-giveshard", "amount", amount, "player", target.getName());
            }
            case "setprogress" -> {
                if (!check(sender, "cloudrewards.admin.setprogress")) return true;
                if (args.length < 4) {
                    sender.sendMessage("§7/cloudrewards setprogress <player> <missionId|slot> <value>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    messages.send(sender, "admin-player-not-found");
                    return true;
                }
                String missionId = rotation.current().missionId(args[2]);
                if (missionId == null) missionId = args[2];
                MissionDefinition definition = missionConfig.byId(missionId);
                if (definition == null || !rotation.current().slots().contains(missionId)) {
                    messages.send(sender, "admin-mission-not-found");
                    return true;
                }
                int value = parseInt(args[3], 0);
                progress.setProgress(target.getUniqueId(), missionId, value);
                messages.send(sender, "admin-setprogress",
                        "player", target.getName(), "mission", definition.name(), "value", value);
            }
            default -> sender.sendMessage("§7/cloudrewards <reload|reset|giveshard|setprogress>");
        }
        return true;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        messages.send(sender, "no-permission");
        return false;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(List.of("reload", "reset", "giveshard", "setprogress"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("giveshard")
                || args.length == 2 && args[0].equalsIgnoreCase("setprogress")) {
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setprogress")) {
            rotation.current().slots().forEach(suggestions::add);
            missionConfig.all().keySet().forEach(suggestions::add);
        }
        String prefix = args[args.length - 1].toLowerCase();
        return suggestions.stream().filter(s -> s.toLowerCase().startsWith(prefix)).toList();
    }
}
