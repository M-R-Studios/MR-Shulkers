package com.mrstudios.shulkers.listener;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.config.Settings;
import com.mrstudios.shulkers.hook.Hooks;
import com.mrstudios.shulkers.item.ShulkerItems;
import com.mrstudios.shulkers.rules.OpenPath;
import com.mrstudios.shulkers.session.SessionService;
import com.mrstudios.shulkers.session.SlotRef;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Open from hand: right-click the air with a shulker box in your main hand. Right-clicking a block
 * still places the shulker as in vanilla. Inventory-click opens are decided by {@code ClickRules} in
 * {@link GuardListener}, next to the rules that block moving an open shulker.
 */
public final class OpenListener implements Listener {

    private final MRShulkers plugin;
    private final SessionService sessions;
    private final Hooks hooks;

    public OpenListener(MRShulkers plugin, SessionService sessions, Hooks hooks) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.hooks = hooks;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClickAir(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND
                || !ShulkerItems.isShulker(event.getItem()) || sessions.isOpen(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        Settings settings = plugin.state().settings();
        if (!settings.handOpenEnabled() || !player.hasPermission(OpenPath.HAND.permission())) {
            return;
        }
        if (settings.handRequireSneak() && !player.isSneaking() && !skipsSneak(player, settings)) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        sessions.tryOpen(player, SlotRef.inventory(player.getInventory().getHeldItemSlot()), OpenPath.HAND);
    }

    /** Sneak + tap is awkward on touch screens, so Bedrock players (seen through Floodgate) may skip it. */
    private boolean skipsSneak(Player player, Settings settings) {
        return settings.floodgate() && settings.bedrockSkipSneak() && hooks.isBedrock(player);
    }
}
