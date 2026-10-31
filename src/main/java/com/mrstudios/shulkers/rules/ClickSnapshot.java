package com.mrstudios.shulkers.rules;

import com.mrstudios.shulkers.config.Settings.OpenClick;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

/**
 * What {@link ClickRules} needs to know about one inventory click. "Open shulker" always means an
 * item carrying a <em>live</em> open tag; stale tags are stripped before the snapshot is taken.
 */
public record ClickSnapshot(
        ClickType click,
        InventoryAction action,
        ViewKind view,
        SlotKind clicked,
        boolean currentIsOpenShulker,
        boolean currentIsShulker,
        boolean cursorEmpty,
        boolean cursorIsOpenShulker,
        boolean cursorIsShulker,
        boolean hotbarTargetIsOpenShulker,
        boolean hotbarTargetIsShulker,
        boolean offhandIsOpenShulker,
        boolean offhandIsShulker,
        OpenClick openClick,
        boolean inventoryOpenEnabled,
        boolean enderChestOpenAllowed) {

    /** Which screen the player has open, by its top inventory. */
    public enum ViewKind {
        /** One of our shulker windows. */
        OUR_SHULKER,
        /** The player's own inventory screen (top is CRAFTING or CREATIVE). */
        PLAYER_SCREEN,
        /** The player's own ender chest. */
        OWN_ENDER_CHEST,
        /** Anything else: chests, other plugins' GUIs, someone else's ender chest. */
        OTHER
    }

    /** Where the click landed. */
    public enum SlotKind {
        TOP,
        BOTTOM,
        OUTSIDE
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ClickType click = ClickType.LEFT;
        private InventoryAction action = InventoryAction.PICKUP_ALL;
        private ViewKind view = ViewKind.PLAYER_SCREEN;
        private SlotKind clicked = SlotKind.BOTTOM;
        private boolean currentIsOpenShulker;
        private boolean currentIsShulker;
        private boolean cursorEmpty = true;
        private boolean cursorIsOpenShulker;
        private boolean cursorIsShulker;
        private boolean hotbarTargetIsOpenShulker;
        private boolean hotbarTargetIsShulker;
        private boolean offhandIsOpenShulker;
        private boolean offhandIsShulker;
        private OpenClick openClick = OpenClick.RIGHT;
        private boolean inventoryOpenEnabled = true;
        private boolean enderChestOpenAllowed;

        public Builder click(ClickType click, InventoryAction action) {
            this.click = click;
            this.action = action;
            return this;
        }

        public Builder view(ViewKind view, SlotKind clicked) {
            this.view = view;
            this.clicked = clicked;
            return this;
        }

        /** The clicked slot holds a shulker; {@code open} if it is the open one. */
        public Builder currentShulker(boolean open) {
            this.currentIsShulker = true;
            this.currentIsOpenShulker = open;
            return this;
        }

        /** The cursor holds a non-shulker item. */
        public Builder cursorItem() {
            this.cursorEmpty = false;
            return this;
        }

        public Builder cursorShulker(boolean open) {
            this.cursorEmpty = false;
            this.cursorIsShulker = true;
            this.cursorIsOpenShulker = open;
            return this;
        }

        public Builder hotbarTargetShulker(boolean open) {
            this.hotbarTargetIsShulker = true;
            this.hotbarTargetIsOpenShulker = open;
            return this;
        }

        public Builder offhandShulker(boolean open) {
            this.offhandIsShulker = true;
            this.offhandIsOpenShulker = open;
            return this;
        }

        public Builder openClick(OpenClick openClick) {
            this.openClick = openClick;
            return this;
        }

        public Builder inventoryOpenEnabled(boolean enabled) {
            this.inventoryOpenEnabled = enabled;
            return this;
        }

        public Builder enderChestOpenAllowed(boolean allowed) {
            this.enderChestOpenAllowed = allowed;
            return this;
        }

        public ClickSnapshot build() {
            return new ClickSnapshot(click, action, view, clicked, currentIsOpenShulker, currentIsShulker,
                    cursorEmpty, cursorIsOpenShulker, cursorIsShulker, hotbarTargetIsOpenShulker, hotbarTargetIsShulker,
                    offhandIsOpenShulker, offhandIsShulker, openClick, inventoryOpenEnabled, enderChestOpenAllowed);
        }
    }
}
