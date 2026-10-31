package com.mrstudios.shulkers.listener;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.session.SessionService;
import com.mrstudios.shulkers.session.ShulkerHolder;
import com.mrstudios.shulkers.session.ShulkerSession;
import com.mrstudios.shulkers.session.SlotRef;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Saves and closes sessions: window changes, close, open of another window, quit, death, world
 * change, the slot being replaced, and stale tags on join (DESIGN.md 3.5 #29, #31 to #36).
 */
public final class LifecycleListener implements Listener {

    private static final String WORLD_BYPASS = "mrshulkers.bypass.world";

    private final MRShulkers plugin;
    private final SessionService sessions;

    public LifecycleListener(MRShulkers plugin, SessionService sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    /** The click result is applied after the event, so the save runs next tick. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        ShulkerSession session = ShulkerHolder.sessionOf(event.getView().getTopInventory());
        if (session != null) {
            sessions.markDirty(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        ShulkerSession session = ShulkerHolder.sessionOf(event.getView().getTopInventory());
        if (session != null) {
            sessions.markDirty(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        ShulkerSession session = ShulkerHolder.sessionOf(event.getInventory());
        if (session != null && !session.ended()) {
            // Only a close by the player goes back to the ender chest, not a teleport, death or another window.
            sessions.close(session, false, event.getReason() == InventoryCloseEvent.Reason.PLAYER);
        }
    }

    /** Paper normally closes our window with OPEN_NEW first; this is belt and braces. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        sessions.registry().byPlayer(event.getPlayer().getUniqueId())
                .filter(session -> ShulkerHolder.sessionOf(event.getInventory()) != session)
                .ifPresent(session -> sessions.close(session, false));
    }

    /**
     * The open shulker's slot now holds something else: /clear, a kit plugin wiping the inventory,
     * an invsee edit. The item already holds the last save, so end without writing. The session
     * slot is checked directly instead of trusting the event's slot number, which is converted
     * through whatever window is open.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) {
        Player player = event.getPlayer();
        ShulkerSession session = sessions.registry().byPlayer(player.getUniqueId()).orElse(null);
        if (session != null && !session.ended()
                && session.source().container() == SlotRef.Container.PLAYER_INVENTORY
                && !sessions.items().hasTag(player.getInventory().getItem(session.source().index()), session.id())) {
            sessions.endWithoutWrite(session, "slot changed");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.state().settings().isWorldDisabled(player.getWorld().getName()) && !player.hasPermission(WORLD_BYPASS)) {
            sessions.registry().byPlayer(player.getUniqueId()).ifPresent(session -> sessions.close(session, true));
        }
    }

    /** LOWEST: player data is saved after quit handlers run. Also covers kicks. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        sessions.registry().byPlayer(event.getPlayer().getUniqueId())
                .ifPresent(session -> sessions.close(session, true));
        sessions.forget(event.getPlayer());
    }

    /** LOWEST, so grave and death-chest plugins receive the saved, untagged item. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        sessions.onDeath(event.getPlayer(), event.getDrops(), event.getItemsToKeep());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sessions.stripStaleTags(event.getPlayer());
    }
}
