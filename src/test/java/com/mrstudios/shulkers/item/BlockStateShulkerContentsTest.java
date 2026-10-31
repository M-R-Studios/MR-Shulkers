package com.mrstudios.shulkers.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Round-trips real item stacks through a shulker item. MockBukkit stores block state in memory, so
 * this checks our read/write code, not the server's NBT serialisation (manual check M1 covers that).
 */
class BlockStateShulkerContentsTest {

    private final ShulkerContents contents = new BlockStateShulkerContents();

    @BeforeAll
    static void start() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    static List<ItemStack> mixedStacks() {
        ItemStack named = new ItemStack(Material.DIAMOND_SWORD);
        named.editMeta(meta -> meta.customName(Component.text("Excalibur")));
        ItemStack enchanted = new ItemStack(Material.DIAMOND_PICKAXE);
        enchanted.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        ItemStack damaged = new ItemStack(Material.IRON_AXE);
        damaged.editMeta(Damageable.class, meta -> meta.setDamage(100));
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        bundle.editMeta(BundleMeta.class, meta -> meta.addItem(new ItemStack(Material.EMERALD, 3)));
        ItemStack potion = new ItemStack(Material.POTION);
        potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.SWIFTNESS));

        List<ItemStack> slots = new ArrayList<>(Collections.nCopies(ShulkerItems.SIZE, null));
        slots.set(0, new ItemStack(Material.DIAMOND, 64));
        slots.set(2, named);
        slots.set(5, enchanted);
        slots.set(9, damaged);
        slots.set(13, bundle);
        slots.set(20, potion);
        slots.set(26, new ItemStack(Material.COBBLESTONE, 17));
        return slots;
    }

    private List<ItemStack> readSlots(ItemStack shulker) {
        return ShulkerItems.normalise(contents.read(shulker), ItemStack::isEmpty).orElseThrow();
    }

    @Test
    void mixedStacksRoundTrip() {
        ItemStack shulker = new ItemStack(Material.PURPLE_SHULKER_BOX);
        List<ItemStack> slots = mixedStacks();

        contents.write(shulker, slots);

        assertEquals(slots, readSlots(shulker));
    }

    @Test
    void roundTripSurvivesClone() {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        contents.write(shulker, mixedStacks());

        assertEquals(mixedStacks(), readSlots(shulker.clone()));
    }

    @Test
    void overwritingWithEmptyClearsEverything() {
        ItemStack shulker = new ItemStack(Material.RED_SHULKER_BOX);
        contents.write(shulker, mixedStacks());

        contents.write(shulker, Collections.nCopies(ShulkerItems.SIZE, null));

        assertEquals(Collections.nCopies(ShulkerItems.SIZE, null), readSlots(shulker));
    }

    @Test
    void newShulkerReadsAsEmpty() {
        assertEquals(Collections.nCopies(ShulkerItems.SIZE, null), readSlots(new ItemStack(Material.LIME_SHULKER_BOX)));
    }

    @Test
    void wrongSizeOrWrongItemIsRefused() {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        assertThrows(IllegalArgumentException.class, () -> contents.write(shulker, Collections.nCopies(26, null)));
        assertThrows(IllegalArgumentException.class, () -> contents.read(new ItemStack(Material.CHEST)));
    }
}
