package com.mrstudios.shulkers.listener;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.config.Settings;
import com.mrstudios.shulkers.rules.ClickDecision;
import com.mrstudios.shulkers.rules.ClickRules;
import com.mrstudios.shulkers.rules.ClickSnapshot;
import com.mrstudios.shulkers.rules.ClickSnapshot.SlotKind;
import com.mrstudios.shulkers.rules.ClickSnapshot.ViewKind;
import com.mrstudios.shulkers.rules.OpenPath;
import com.mrstudios.shulkers.session.SessionService;
import com.mrstudios.shulkers.session.ShulkerHolder;
import com.mrstudios.shulkers.session.ShulkerSession;
import com.mrstudios.shulkers.session.SlotRef;
import com.mrstudios.shulkers.item.ShulkerItems;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.Container;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Stops an open shulker from moving, being copied, dropped, placed, crafted with or handed over
 * (DESIGN.md 3.5 #1 to #28). Guards run at LOWEST so we cancel before other plugins act, and don't
 * skip cancelled events. Click and drag are checked again at HIGHEST in case another plugin
 * un-cancelled them in between.
 */
public final class GuardListener implements Listener {

    private final MRShulkers plugin;
    private final SessionService sessions;
    private final ShulkerItems items;
    private final Set<UUID> resyncPending = new HashSet<>();

    public GuardListener(MRShulkers plugin, SessionService sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.items = sessions.items();
    }

    // --- #1 to #11: clicks ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (sessions.isStale(current)) {
            event.setCurrentItem(items.withoutTag(current));
        }
        ClickDecision decision = decide(event, player);
        if (decision.kind() == ClickDecision.Kind.CANCEL) {
            event.setCancelled(true);
            blocked(player, "inventory click (rule " + decision.rule() + ", " + event.getClick() + "/" + event.getAction() + ")");
            if (event instanceof InventoryCreativeEvent) {
                player.updateInventory();
            }
        } else if (decision.kind() == ClickDecision.Kind.OPEN) {
            // 7: open from your own inventory screen; 8: from your own ender chest; 10: switch to
            // another shulker from our window. The open (and closing the current window) happens
            // next tick, outside the click handler.
            event.setCancelled(true);
            if (decision.rule() == 8) {
                sessions.tryOpen(player, SlotRef.enderChest(event.getSlot()), OpenPath.ENDER_CHEST);
            } else {
                sessions.tryOpen(player, SlotRef.inventory(event.getSlot()), OpenPath.INVENTORY);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClickRecheck(InventoryClickEvent event) {
        if (!event.isCancelled() && event.getWhoClicked() instanceof Player player
                && decide(event, player).cancels()) {
            event.setCancelled(true);
            resync(player);
            plugin.getLogger().warning("Another plugin un-cancelled a blocked click on an open shulker by "
                    + player.getName() + "; cancelled it again.");
        }
    }

    private ClickDecision decide(InventoryClickEvent event, Player player) {
        if (event instanceof InventoryCreativeEvent creative) {
            ClickDecision decision = ClickRules.decideCreative(sessions.isOpen(creative.getCursor()), isSessionSlot(event, player));
            if (decision.cancels()) {
                return decision;
            }
        }
        return ClickRules.decide(snapshot(event, player));
    }

    /** Whether the click targets the slot of the clicker's own open shulker. */
    private boolean isSessionSlot(InventoryClickEvent event, Player player) {
        ShulkerSession session = sessions.registry().byPlayer(player.getUniqueId()).orElse(null);
        return session != null
                && session.source().container() == SlotRef.Container.PLAYER_INVENTORY
                && event.getClickedInventory() instanceof PlayerInventory inventory
                && player.equals(inventory.getHolder())
                && event.getSlot() == session.source().index();
    }

    private ClickSnapshot snapshot(InventoryClickEvent event, Player player) {
        Inventory top = event.getView().getTopInventory();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getHotbarButton() >= 0 ? player.getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack offhand = player.getInventory().getItemInOffHand();
        Settings settings = plugin.state().settings();
        return new ClickSnapshot(
                event.getClick(),
                event.getAction(),
                viewKind(top, player),
                event.getClickedInventory() == null ? SlotKind.OUTSIDE
                        : event.getRawSlot() < top.getSize() ? SlotKind.TOP : SlotKind.BOTTOM,
                sessions.isOpen(current),
                ShulkerItems.isShulker(current),
                cursor == null || cursor.isEmpty(),
                sessions.isOpen(cursor),
                ShulkerItems.isShulker(cursor),
                sessions.isOpen(hotbar),
                ShulkerItems.isShulker(hotbar),
                sessions.isOpen(offhand),
                ShulkerItems.isShulker(offhand),
                settings.inventoryClick(),
                // Without the permission the click stays vanilla; OpenChecks repeats the check.
                settings.inventoryOpenEnabled() && player.hasPermission(OpenPath.INVENTORY.permission()),
                settings.enderChestOpenEnabled() && player.hasPermission(OpenPath.ENDER_CHEST.permission()));
    }

    private static ViewKind viewKind(Inventory top, Player player) {
        if (ShulkerHolder.sessionOf(top) != null) {
            return ViewKind.OUR_SHULKER;
        }
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) {
            return ViewKind.PLAYER_SCREEN;
        }
        if (top.getType() == InventoryType.ENDER_CHEST && top.equals(player.getEnderChest())) {
            return ViewKind.OWN_ENDER_CHEST;
        }
        return ViewKind.OTHER;
    }

    // --- #12: drag ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (decideDrag(event).kind() == ClickDecision.Kind.CANCEL) {
            event.setCancelled(true);
            blocked(event.getWhoClicked(), "drag");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragRecheck(InventoryDragEvent event) {
        if (!event.isCancelled() && decideDrag(event).kind() == ClickDecision.Kind.CANCEL) {
            event.setCancelled(true);
            resync(event.getWhoClicked());
            plugin.getLogger().warning("Another plugin un-cancelled a blocked drag of a shulker; cancelled it again.");
        }
    }

    private ClickDecision decideDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        ItemStack oldCursor = event.getOldCursor();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        return ClickRules.decideDrag(sessions.isOpen(oldCursor), ShulkerHolder.sessionOf(top) != null,
                ShulkerItems.isShulker(oldCursor), touchesTop);
    }

    // --- #13 to #20: player actions with the item in hand ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (sessions.isOpen(event.getMainHandItem()) || sessions.isOpen(event.getOffHandItem())) {
            cancel(event, event.getPlayer(), "hand swap");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        Item drop = event.getItemDrop();
        if (sessions.isOpen(drop.getItemStack())) {
            cancel(event, event.getPlayer(), "drop");
        } else if (sessions.isStale(drop.getItemStack())) {
            drop.setItemStack(items.withoutTag(drop.getItemStack()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickItem(PlayerPickItemEvent event) {
        PlayerInventory inventory = event.getPlayer().getInventory();
        if (isOpenAt(inventory, event.getSourceSlot()) || isOpenAt(inventory, event.getTargetSlot())) {
            cancel(event, event.getPlayer(), "pick block");
        }
    }

    private boolean isOpenAt(PlayerInventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getSize() && sessions.isOpen(inventory.getItem(slot));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (sessions.isOpen(event.getItemInHand())) {
            cancel(event, event.getPlayer(), "block place");
        }
    }

    /** The window is open, so a world interaction with the item means a desynced client. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL && sessions.isOpen(event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            blocked(event.getPlayer(), "interact");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (sessions.isOpen(event.getPlayer().getInventory().getItem(event.getHand()))) {
            cancel(event, event.getPlayer(), "entity interact");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        onInteractEntity(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (sessions.isOpen(event.getPlayerItem())) {
            cancel(event, event.getPlayer(), "armor stand");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemFrame(PlayerItemFrameChangeEvent event) {
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE && sessions.isOpen(event.getItemStack())) {
            cancel(event, event.getPlayer(), "item frame");
        }
    }

    // --- #21, #22: hoppers, droppers, crafters ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMoveItem(InventoryMoveItemEvent event) {
        if (sessions.isOpen(event.getItem())) {
            event.setCancelled(true);
            plugin.getLogger().warning("Blocked a hopper/dropper moving an open shulker.");
        } else if (sessions.isStale(event.getItem())) {
            event.setItem(items.withoutTag(event.getItem()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        ShulkerSession session = sessions.liveSession(event.getItem().getItemStack());
        if (session != null) {
            event.setCancelled(true);
        }
        stripEscaped(event.getItem(), "picked up by a hopper");
    }

    // --- #23 to #26: crafting and workstations ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (anyOpen(event.getInventory().getMatrix()) || sessions.isOpen(result)) {
            event.getInventory().setResult(null);
        } else if (sessions.isStale(result)) {
            event.getInventory().setResult(items.withoutTag(result));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraft(CraftItemEvent event) {
        if (anyOpen(event.getInventory().getMatrix()) || sessions.isOpen(event.getInventory().getResult())) {
            cancel(event, event.getWhoClicked(), "craft");
        }
    }

    /** The dye recipe copies components, PDC included, onto the result. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCrafter(CrafterCraftEvent event) {
        boolean slotOpen = event.getBlock().getState(false) instanceof Container crafter
                && anyOpen(crafter.getInventory().getContents());
        if (slotOpen || sessions.isOpen(event.getResult())) {
            event.setCancelled(true);
            plugin.getLogger().warning("Blocked a crafter crafting with an open shulker.");
        } else if (sessions.isStale(event.getResult())) {
            event.setResult(items.withoutTag(event.getResult()));
        }
    }

    /** Anvil, grindstone, smithing table, cartography table, loom, stonecutter. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrepareResult(PrepareInventoryResultEvent event) {
        ItemStack result = event.getResult();
        if (anyOpen(event.getInventory().getContents()) || sessions.isOpen(result)) {
            event.setResult(null);
        } else if (sessions.isStale(result)) {
            event.setResult(items.withoutTag(result));
        }
    }

    private boolean anyOpen(ItemStack[] stacks) {
        return Arrays.stream(stacks).anyMatch(sessions::isOpen);
    }

    // --- #27, #28: the item as an entity ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        stripEscaped(event.getEntity(), "dropped into the world");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPickup(PlayerAttemptPickupItemEvent event) {
        stripEscaped(event.getItem(), "picked up from the ground");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickup(EntityPickupItemEvent event) {
        stripEscaped(event.getItem(), "picked up from the ground");
    }

    /**
     * An item entity carries an open tag. Its contents are already saved, so it is the one real
     * copy: strip the tag, and if its session is live, end that session without writing.
     */
    private void stripEscaped(Item entity, String how) {
        ItemStack stack = entity.getItemStack();
        if (!items.hasTag(stack)) {
            return;
        }
        ShulkerSession session = sessions.liveSession(stack);
        entity.setItemStack(items.withoutTag(stack));
        if (session != null) {
            plugin.getLogger().warning("An open shulker was " + how + "; its window was closed. Please report how this happened.");
            sessions.endWithoutWrite(session, "item " + how);
        }
    }

    // --- helpers ---

    private void cancel(org.bukkit.event.Cancellable event, HumanEntity who, String what) {
        event.setCancelled(true);
        blocked(who, what);
    }

    private void blocked(HumanEntity who, String what) {
        resync(who);
        if (plugin.state().settings().logBlockedActions()) {
            plugin.getLogger().info("Blocked " + what + " on an open shulker by " + who.getName() + ".");
        }
    }

    /**
     * The client already showed the refused action; send it the real inventory next tick so no ghost
     * item is left on screen. Bedrock clients (through Geyser) are the most prone to keep one.
     * At most one resync per player per tick, however many actions were refused.
     */
    private void resync(HumanEntity who) {
        if (who instanceof Player player && resyncPending.add(player.getUniqueId())) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                resyncPending.remove(player.getUniqueId());
                if (player.isOnline()) {
                    player.updateInventory();
                }
            });
        }
    }
}
