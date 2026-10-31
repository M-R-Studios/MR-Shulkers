package com.mrstudios.shulkers.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.item.BlockStateShulkerContents;
import com.mrstudios.shulkers.item.ShulkerContents;
import com.mrstudios.shulkers.item.ShulkerItems;
import com.mrstudios.shulkers.rules.OpenPath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The open/save/close flow against MockBukkit's in-memory server. */
class SessionServiceTest {

    private final ShulkerContents contents = new BlockStateShulkerContents();
    private ServerMock server;
    private MRShulkers plugin;
    private ShulkerItems items;
    private SessionService sessions;
    private PlayerMock player;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MRShulkers.class);
        items = new ShulkerItems(plugin);
        sessions = plugin.sessions();
        player = server.addPlayer();
        player.setOp(true); // bypasses the open cooldown between test steps
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private ItemStack shulkerWith(Material... stored) {
        ItemStack shulker = new ItemStack(Material.PURPLE_SHULKER_BOX);
        List<ItemStack> slots = new ArrayList<>(Collections.nCopies(ShulkerItems.SIZE, null));
        for (int i = 0; i < stored.length; i++) {
            slots.set(i, new ItemStack(stored[i]));
        }
        contents.write(shulker, slots);
        return shulker;
    }

    private List<ItemStack> stored(ItemStack shulker) {
        return ShulkerItems.normalise(contents.read(shulker), ItemStack::isEmpty).orElseThrow();
    }

    private ShulkerSession openSlot(int slot) {
        sessions.tryOpen(player, SlotRef.inventory(slot), OpenPath.COMMAND);
        server.getScheduler().performOneTick();
        return sessions.registry().byPlayer(player.getUniqueId()).orElse(null);
    }

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    @Test
    void openShowsContentsAndTagsTheItem() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND, Material.EMERALD));

        ShulkerSession session = openSlot(0);

        assertNotNull(session);
        assertSame(session, ShulkerHolder.sessionOf(top()));
        assertEquals(Material.DIAMOND, top().getItem(0).getType());
        assertEquals(Material.EMERALD, top().getItem(1).getType());
        assertTrue(items.hasTag(player.getInventory().getItem(0), session.id()));
    }

    @Test
    void shulkerCommandOpensTheHeldShulker() {
        player.getInventory().setHeldItemSlot(4);
        player.getInventory().setItem(4, shulkerWith(Material.DIAMOND));

        player.performCommand("shulker");
        server.getScheduler().performOneTick();

        ShulkerSession session = sessions.registry().byPlayer(player.getUniqueId()).orElseThrow();
        assertEquals(SlotRef.inventory(4), session.source());
        assertEquals(Material.DIAMOND, top().getItem(0).getType());
    }

    @Test
    void shulkerCommandWithoutShulkerOpensNothing() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));

        player.performCommand("shulker");
        server.getScheduler().performOneTick();

        assertEquals(0, sessions.registry().size());
    }

    @Test
    void changesAreSavedNextTickAndOnClose() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        ShulkerSession session = openSlot(0);

        top().setItem(0, null);
        top().setItem(5, new ItemStack(Material.GOLD_INGOT, 7));
        sessions.markDirty(session);
        server.getScheduler().performOneTick();

        ItemStack item = player.getInventory().getItem(0);
        assertTrue(items.hasTag(item, session.id()), "still open, still tagged");
        assertNull(stored(item).get(0));
        assertEquals(new ItemStack(Material.GOLD_INGOT, 7), stored(item).get(5));

        top().setItem(6, new ItemStack(Material.APPLE));
        player.closeInventory();

        item = player.getInventory().getItem(0);
        assertFalse(items.hasTag(item));
        assertEquals(new ItemStack(Material.APPLE), stored(item).get(6));
        assertEquals(0, sessions.registry().size());
    }

    @Test
    void openAndCloseWithoutChangesLeavesTheItemIdentical() {
        ItemStack original = shulkerWith(Material.DIAMOND);
        player.getInventory().setItem(0, original.clone());

        openSlot(0);
        player.closeInventory();

        assertEquals(original, player.getInventory().getItem(0));
    }

    @Test
    void identicalShulkersDoNotShareData() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        player.getInventory().setItem(1, shulkerWith(Material.DIAMOND));

        openSlot(0);
        top().setItem(0, new ItemStack(Material.DIRT));
        player.closeInventory();

        assertEquals(Material.DIRT, stored(player.getInventory().getItem(0)).get(0).getType());
        assertEquals(Material.DIAMOND, stored(player.getInventory().getItem(1)).get(0).getType());
    }

    @Test
    void stackOfShulkersIsRefused() {
        ItemStack stack = shulkerWith(Material.DIAMOND);
        stack.editMeta(meta -> meta.setMaxStackSize(64));
        stack.setAmount(2);
        player.getInventory().setItem(0, stack);

        assertNull(openSlot(0));
        assertFalse(items.hasTag(player.getInventory().getItem(0)));
    }

    @Test
    void openingASecondShulkerClosesTheFirst() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        player.getInventory().setItem(1, shulkerWith(Material.EMERALD));
        ShulkerSession first = openSlot(0);
        top().setItem(1, new ItemStack(Material.APPLE));

        ShulkerSession second = openSlot(1);

        assertTrue(first.ended());
        assertFalse(items.hasTag(player.getInventory().getItem(0)));
        assertEquals(Material.APPLE, stored(player.getInventory().getItem(0)).get(1).getType());
        assertSame(second, ShulkerHolder.sessionOf(top()));
        assertEquals(1, sessions.registry().size());
    }

    @Test
    void itemThatLeftIsNotWrittenAndNothingIsGivenBack() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        ShulkerSession session = openSlot(0);
        player.getInventory().setItem(0, null);
        top().setItem(1, new ItemStack(Material.NETHERITE_INGOT));

        assertFalse(sessions.save(session));

        assertTrue(session.ended());
        assertNull(ShulkerHolder.sessionOf(top()));
        assertFalse(player.getInventory().contains(Material.NETHERITE_INGOT));
        assertFalse(player.getInventory().contains(Material.PURPLE_SHULKER_BOX));
        assertEquals(0, sessions.registry().size());
    }

    @Test
    void itemMovedToAnotherSlotIsFoundByTag() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        ShulkerSession session = openSlot(0);
        player.getInventory().setItem(7, player.getInventory().getItem(0));
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));

        top().setItem(2, new ItemStack(Material.APPLE));
        player.closeInventory();

        assertEquals(Material.APPLE, stored(player.getInventory().getItem(7)).get(2).getType());
        assertNull(stored(player.getInventory().getItem(0)).get(2), "the identical shulker in the old slot is untouched");
        assertFalse(items.hasTag(player.getInventory().getItem(7)));
        assertTrue(session.ended());
    }

    @Test
    void quitSavesAndUntags() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        openSlot(0);
        top().setItem(3, new ItemStack(Material.APPLE));

        player.disconnect();

        ItemStack item = player.getInventory().getItem(0);
        assertFalse(items.hasTag(item));
        assertEquals(Material.APPLE, stored(item).get(3).getType());
        assertEquals(0, sessions.registry().size());
    }

    @Test
    void deathWritesTheWindowIntoTheDrops() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        ShulkerSession session = openSlot(0);
        top().setItem(4, new ItemStack(Material.APPLE));
        List<ItemStack> drops = new ArrayList<>(List.of(player.getInventory().getItem(0).clone(), new ItemStack(Material.DIRT)));
        List<ItemStack> kept = new ArrayList<>();

        sessions.onDeath(player, drops, kept);

        ItemStack dropped = drops.get(0);
        assertFalse(items.hasTag(dropped));
        assertEquals(Material.APPLE, stored(dropped).get(4).getType());
        assertEquals(new ItemStack(Material.DIRT), drops.get(1));
        assertTrue(session.ended());
        assertEquals(0, sessions.registry().size());
    }

    @Test
    void disableSavesAndUntags() {
        player.getInventory().setItem(0, shulkerWith(Material.DIAMOND));
        openSlot(0);
        top().setItem(8, new ItemStack(Material.APPLE));

        server.getPluginManager().disablePlugin(plugin);

        ItemStack item = player.getInventory().getItem(0);
        assertFalse(items.hasTag(item));
        assertEquals(Material.APPLE, stored(item).get(8).getType());
    }

    @Test
    void staleTagsAreStrippedOnJoin() {
        PlayerMock other = new PlayerMock(server, "Returning", UUID.randomUUID());
        other.getInventory().setItem(2, items.withTag(shulkerWith(Material.DIAMOND), UUID.randomUUID()));
        other.getEnderChest().setItem(0, items.withTag(new ItemStack(Material.SHULKER_BOX), UUID.randomUUID()));

        server.addPlayer(other);

        assertFalse(items.hasTag(other.getInventory().getItem(2)));
        assertFalse(items.hasTag(other.getEnderChest().getItem(0)));
        assertEquals(Material.DIAMOND, stored(other.getInventory().getItem(2)).get(0).getType());
    }

    @Test
    void staleTagIsStrippedWhenOpening() {
        player.getInventory().setItem(0, items.withTag(shulkerWith(Material.DIAMOND), UUID.randomUUID()));

        ShulkerSession session = openSlot(0);

        assertNotNull(session);
        assertTrue(items.hasTag(player.getInventory().getItem(0), session.id()));
    }
}
