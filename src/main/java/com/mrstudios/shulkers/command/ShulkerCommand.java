package com.mrstudios.shulkers.command;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.config.Messages;
import com.mrstudios.shulkers.item.ShulkerItems;
import com.mrstudios.shulkers.rules.OpenPath;
import com.mrstudios.shulkers.session.SessionService;
import com.mrstudios.shulkers.session.SlotRef;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** {@code /shulker}: opens the shulker box in your main hand. Works on every client, Bedrock included. */
public final class ShulkerCommand implements TabExecutor {

    private final MRShulkers plugin;
    private final SessionService sessions;

    public ShulkerCommand(MRShulkers plugin, SessionService sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.state().messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission(OpenPath.COMMAND.permission())) {
            messages.send(player, "no-permission");
            return true;
        }
        if (!ShulkerItems.isShulker(player.getInventory().getItemInMainHand())) {
            messages.send(player, "not-holding-shulker");
            return true;
        }
        sessions.tryOpen(player, SlotRef.inventory(player.getInventory().getHeldItemSlot()), OpenPath.COMMAND);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
