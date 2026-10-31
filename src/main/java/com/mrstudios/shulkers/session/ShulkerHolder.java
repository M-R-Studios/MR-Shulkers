package com.mrstudios.shulkers.session;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as one of our shulker windows. */
public final class ShulkerHolder implements InventoryHolder {

    private final ShulkerSession session;

    ShulkerHolder(ShulkerSession session) {
        this.session = session;
    }

    public ShulkerSession session() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return session.view();
    }

    /**
     * The session behind {@code inventory}, or null if it isn't one of our windows. The type check
     * comes first so chests and other block inventories never build a block-state snapshot in
     * {@code getHolder()}; only placed shulker boxes can, and those are rarely open.
     */
    public static ShulkerSession sessionOf(Inventory inventory) {
        if (inventory == null || inventory.getType() != InventoryType.SHULKER_BOX) {
            return null;
        }
        return inventory.getHolder() instanceof ShulkerHolder holder ? holder.session : null;
    }
}
