package com.mrstudios.shulkers.item;

import java.util.List;
import org.bukkit.inventory.ItemStack;

/** Reads and writes the items stored inside a shulker box item. */
public interface ShulkerContents {

    /** The stored items as they are in the item; may contain nulls or air for empty slots. */
    List<ItemStack> read(ItemStack shulker);

    /** Replaces the stored items of {@code shulker} in place. {@code contents} has exactly 27 entries. */
    void write(ItemStack shulker, List<ItemStack> contents);
}
