package com.mrstudios.shulkers.session;

/** Where an opened shulker lives: a slot of the player's inventory or ender chest. */
public record SlotRef(Container container, int index) {

    public enum Container {
        PLAYER_INVENTORY,
        ENDER_CHEST
    }

    public static SlotRef inventory(int index) {
        return new SlotRef(Container.PLAYER_INVENTORY, index);
    }

    public static SlotRef enderChest(int index) {
        return new SlotRef(Container.ENDER_CHEST, index);
    }
}
