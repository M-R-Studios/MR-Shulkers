package com.mrstudios.shulkers.item;

import java.util.Arrays;
import java.util.List;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * {@link ShulkerContents} through {@link BlockStateMeta}, the stable API in 1.21.11. The data
 * component API ({@code DataComponentTypes.CONTAINER}) is still experimental.
 */
public final class BlockStateShulkerContents implements ShulkerContents {

    @Override
    public List<ItemStack> read(ItemStack shulker) {
        if (!(shulker.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box)) {
            throw new IllegalArgumentException("Not a shulker box: " + shulker.getType());
        }
        return Arrays.asList(box.getInventory().getContents());
    }

    @Override
    public void write(ItemStack shulker, List<ItemStack> contents) {
        if (contents.size() != ShulkerItems.SIZE) {
            throw new IllegalArgumentException("Expected " + ShulkerItems.SIZE + " slots, got " + contents.size());
        }
        boolean edited = shulker.editMeta(BlockStateMeta.class, meta -> {
            if (!(meta.getBlockState() instanceof ShulkerBox box)) {
                throw new IllegalArgumentException("Not a shulker box: " + shulker.getType());
            }
            box.getInventory().setContents(contents.toArray(new ItemStack[0]));
            meta.setBlockState(box);
        });
        if (!edited) {
            throw new IllegalArgumentException("Not a shulker box: " + shulker.getType());
        }
    }
}
