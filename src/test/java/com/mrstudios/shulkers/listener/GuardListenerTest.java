package com.mrstudios.shulkers.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.item.ShulkerItems;
import com.mrstudios.shulkers.rules.OpenPath;
import com.mrstudios.shulkers.session.SessionService;
import com.mrstudios.shulkers.session.ShulkerHolder;
import com.mrstudios.shulkers.session.ShulkerSession;
import com.mrstudios.shulkers.session.SlotRef;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ItemEntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Guards and lifecycle handlers, fired as real events against MockBukkit. */
class GuardListenerTest {

    private ServerMock server;
    private MRShulkers plugin;
    private SessionService sessions;
    private ShulkerItems items;
    private PlayerMock player;
    private ShulkerSession session;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MRShulkers.class);
        sessions = plugin.sessions();
        items = sessions.items();
        player = server.addPlayer();
        player.setOp(true);
        player.getInventory().setItem(0, new ItemStack(Material.PURPLE_SHULKER_BOX));
        player.getInventory().setItem(1, new ItemStack(Material.LIME_SHULKER_BOX));
        sessions.tryOpen(player, SlotRef.inventory(0), OpenPath.COMMAND);
        server.getScheduler().performOneTick();
        session = sessions.registry().byPlayer(player.getUniqueId()).orElseThrow();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private ItemStack openItem() {
        return player.getInventory().getItem(0);
    }

    private <T extends org.bukkit.event.Event> T call(T event) {
        server.getPluginManager().callEvent(event);
        return event;
    }

    /**
     * A click on {@code rawSlot}. MockBukkit's view returns null for items in the bottom inventory,
     * so the clicked item is handed to the event directly (the OUTSIDE slot type keeps it in a field).
     */
    private InventoryClickEvent click(int rawSlot, ClickType click, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.OUTSIDE, rawSlot, click, action);
        Inventory clicked = view.getInventory(rawSlot);
        event.setCurrentItem(clicked == null ? null : clicked.getItem(view.convertSlot(rawSlot)));
        return call(event);
    }

    /** An item entity that isn't spawned, like the one a player drop creates before ItemSpawnEvent. */
    private Item unspawned(ItemStack stack) {
        return new ItemEntityMock(server, UUID.randomUUID(), stack);
    }

    /** Raw slot of player inventory slot {@code index} below our window, as the view maps it. */
    private int bottomRaw(int index) {
        InventoryView view = player.getOpenInventory();
        int top = view.getTopInventory().getSize();
        for (int raw = top; raw < view.countSlots(); raw++) {
            if (view.convertSlot(raw) == index) {
                return raw;
            }
        }
        throw new IllegalStateException("No raw slot for " + index);
    }

    @Test
    void clickOnTheOpenShulkerIsCancelled() {
        assertTrue(click(bottomRaw(0), ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertTrue(click(bottomRaw(0), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertTrue(click(bottomRaw(0), ClickType.DROP, InventoryAction.DROP_ONE_SLOT).isCancelled());
    }

    @Test
    void shiftClickOfAnotherShulkerIntoTheWindowIsCancelled() {
        assertTrue(click(bottomRaw(1), ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
    }

    @Test
    void ordinaryClickInTheWindowIsAllowed() {
        assertFalse(click(3, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
    }

    @Test
    void dropOfTheOpenShulkerIsCancelled() {
        assertTrue(call(new PlayerDropItemEvent(player, unspawned(openItem().clone()))).isCancelled());
    }

    @Test
    void dropOfAnotherShulkerIsAllowed() {
        assertFalse(call(new PlayerDropItemEvent(player, unspawned(new ItemStack(Material.LIME_SHULKER_BOX)))).isCancelled());
    }

    @Test
    void handSwapWithTheOpenShulkerIsCancelled() {
        PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(player, new ItemStack(Material.DIRT), openItem());

        assertTrue(call(event).isCancelled());
    }

    @Test
    void placingTheOpenShulkerIsCancelled() {
        var block = player.getWorld().getBlockAt(0, 64, 0);
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0),
                openItem(), player, true, EquipmentSlot.HAND);

        assertTrue(call(event).isCancelled());
    }

    @Test
    void hopperMovingTheOpenShulkerIsCancelledAndStaleTagsAreStripped() {
        Inventory source = Bukkit.createInventory(null, InventoryType.HOPPER);
        Inventory destination = Bukkit.createInventory(null, InventoryType.HOPPER);

        assertTrue(call(new InventoryMoveItemEvent(source, openItem(), destination, true)).isCancelled());

        ItemStack stale = items.withTag(new ItemStack(Material.SHULKER_BOX), UUID.randomUUID());
        InventoryMoveItemEvent staleMove = call(new InventoryMoveItemEvent(source, stale, destination, true));
        assertFalse(staleMove.isCancelled());
        assertFalse(items.hasTag(staleMove.getItem()));
    }

    @Test
    void openShulkerThatEscapedAsAnEntityIsStrippedAndTheSessionEndsWithoutWriting() {
        Item escaped = unspawned(openItem().clone());
        player.getInventory().setItem(0, null);

        call(new ItemSpawnEvent(escaped));

        assertFalse(items.hasTag(escaped.getItemStack()));
        assertTrue(session.ended());
        assertEquals(0, sessions.registry().size());
    }

    @Test
    void slotReplacedEndsTheSessionWithoutWriting() {
        ItemStack before = openItem();
        player.getInventory().setItem(0, null);
        call(new PlayerInventorySlotChangeEvent(player, 36, before, new ItemStack(Material.AIR)));

        assertTrue(session.ended());
        assertNull(ShulkerHolder.sessionOf(player.getOpenInventory().getTopInventory()));
        assertFalse(player.getInventory().contains(Material.PURPLE_SHULKER_BOX));
    }

    @Test
    void watchdogEndsTheSessionWhenTheItemIsGone() {
        player.getInventory().setItem(0, null);

        server.getScheduler().performTicks(plugin.state().settings().watchdogIntervalTicks());

        assertTrue(session.ended());
        assertEquals(0, sessions.registry().size());
    }

    @Test
    void watchdogLeavesAHealthySessionOpen() {
        server.getScheduler().performTicks(40);

        assertFalse(session.ended());
        assertTrue(items.hasTag(openItem(), session.id()));
    }

    @Test
    void openingAnotherInventoryClosesTheSessionNormally() {
        player.openInventory(Bukkit.createInventory(null, 9));

        assertTrue(session.ended());
        assertFalse(items.hasTag(openItem()));
        assertEquals(Material.PURPLE_SHULKER_BOX, openItem().getType());
    }
}
