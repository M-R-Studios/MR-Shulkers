package com.mrstudios.shulkers.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ShulkerItemsTest {

    private static Plugin plugin;
    private static ShulkerItems items;

    @BeforeAll
    static void start() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("MRShulkers");
        items = new ShulkerItems(plugin);
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    @Test
    void allShulkerColoursCount() {
        assertTrue(ShulkerItems.isShulker(new ItemStack(Material.SHULKER_BOX)));
        assertTrue(ShulkerItems.isShulker(new ItemStack(Material.BLACK_SHULKER_BOX)));
        assertFalse(ShulkerItems.isShulker(new ItemStack(Material.CHEST)));
        assertFalse(ShulkerItems.isShulker(null));
    }

    @Test
    void tagRoundTripsOnACopy() {
        ItemStack original = new ItemStack(Material.SHULKER_BOX);
        UUID id = UUID.randomUUID();

        ItemStack tagged = items.withTag(original, id);

        assertFalse(items.hasTag(original), "the original is not changed");
        assertTrue(items.hasTag(tagged));
        assertTrue(items.hasTag(tagged, id));
        assertFalse(items.hasTag(tagged, UUID.randomUUID()));
        assertEquals(Optional.of(id), items.tag(tagged));
    }

    @Test
    void identicalShulkersStopBeingSimilarOnceOneIsTagged() {
        ItemStack a = new ItemStack(Material.SHULKER_BOX);
        ItemStack b = new ItemStack(Material.SHULKER_BOX);

        assertFalse(items.withTag(a, UUID.randomUUID()).isSimilar(b));
    }

    @Test
    void strippedItemIsVanillaAgain() {
        ItemStack original = new ItemStack(Material.SHULKER_BOX);

        ItemStack stripped = items.withoutTag(items.withTag(original, UUID.randomUUID()));

        assertFalse(items.hasTag(stripped));
        assertEquals(original, stripped);
        assertTrue(stripped.isSimilar(original));
    }

    @Test
    void malformedTagIsPresentButHasNoId() {
        ItemStack item = new ItemStack(Material.SHULKER_BOX);
        item.editPersistentDataContainer(pdc ->
                pdc.set(new NamespacedKey(plugin, "open_session"), PersistentDataType.STRING, "not-a-uuid"));

        assertTrue(items.hasTag(item));
        assertTrue(items.tag(item).isEmpty());
        assertFalse(items.hasTag(items.withoutTag(item)));
    }
}
