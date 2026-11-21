package ru.cloudwars.cloudrewards.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.cloudwars.cloudrewards.config.MessageService;
import ru.cloudwars.cloudrewards.gui.MenuService;
import ru.cloudwars.cloudrewards.reward.RewardService;

public final class MissionsCommand implements CommandExecutor {

    private final MessageService messages;
    private final MenuService menus;
    private final RewardService rewards;

    public MissionsCommand(MessageService messages, MenuService menus, RewardService rewards) {
        this.messages = messages;
        this.menus = menus;
        this.rewards = rewards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("cloudrewards.command.missions")) {
            messages.send(player, "no-permission");
            return true;
        }
        rewards.claimAll(player);
        menus.openMain(player);
        return true;
    }
}
