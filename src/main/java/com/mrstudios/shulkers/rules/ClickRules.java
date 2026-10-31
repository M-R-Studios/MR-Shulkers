package com.mrstudios.shulkers.rules;

import com.mrstudios.shulkers.config.Settings.OpenClick;
import com.mrstudios.shulkers.rules.ClickSnapshot.SlotKind;
import com.mrstudios.shulkers.rules.ClickSnapshot.ViewKind;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;

/**
 * The inventory click rules of DESIGN.md section 3.5, rows 1 to 10. Pure: no server needed. Rows
 * that block an open shulker from moving come first, so no open trigger can override them.
 */
public final class ClickRules {

    private ClickRules() {
    }

    public static ClickDecision decide(ClickSnapshot s) {
        // 6: creative clone / middle click on the open shulker would make a tagged copy.
        if (s.currentIsOpenShulker() && (s.action() == InventoryAction.CLONE_STACK
                || s.click() == ClickType.MIDDLE || s.click() == ClickType.CREATIVE)) {
            return ClickDecision.cancel(6);
        }
        // 1: anything done to the open shulker itself.
        if (s.currentIsOpenShulker()) {
            return ClickDecision.cancel(1);
        }
        // 2: the open shulker on the cursor. Shouldn't be possible.
        if (s.cursorIsOpenShulker()) {
            return ClickDecision.cancel(2);
        }
        // 3: number key that would swap the open shulker out of its hotbar slot.
        if (isHotbarSwap(s) && s.hotbarTargetIsOpenShulker()) {
            return ClickDecision.cancel(3);
        }
        // 4: offhand swap with the open shulker in the offhand.
        if (s.click() == ClickType.SWAP_OFFHAND && s.offhandIsOpenShulker()) {
            return ClickDecision.cancel(4);
        }

        boolean openClick = isOpenClick(s.click(), s.openClick()) && s.cursorEmpty() && s.currentIsShulker();
        if (s.view() == ViewKind.OUR_SHULKER) {
            // 10: open click on another shulker in your inventory switches to it.
            if (openClick && s.clicked() == SlotKind.BOTTOM && s.inventoryOpenEnabled()) {
                return ClickDecision.open(10);
            }
            if (putsShulkerIntoTop(s)) {
                return ClickDecision.cancel(5);
            }
            // 9: an ordinary click in our window; the listener saves it next tick.
            return ClickDecision.allow(9);
        }
        // 7: open click in your own inventory screen.
        if (s.view() == ViewKind.PLAYER_SCREEN && s.clicked() == SlotKind.BOTTOM && openClick && s.inventoryOpenEnabled()) {
            return ClickDecision.open(7);
        }
        // 8: open click on a shulker inside your own ender chest.
        if (s.view() == ViewKind.OWN_ENDER_CHEST && s.clicked() == SlotKind.TOP && openClick && s.enderChestOpenAllowed()) {
            return ClickDecision.open(8);
        }
        return ClickDecision.ALLOW;
    }

    /**
     * 11: creative clients send whole stacks. Refuse a tagged copy on the cursor, and any write over
     * the open shulker's slot, so an old copy can't overwrite it.
     */
    public static ClickDecision decideCreative(boolean cursorIsOpenShulker, boolean targetIsSessionSlot) {
        return cursorIsOpenShulker || targetIsSessionSlot ? ClickDecision.cancel(11) : ClickDecision.ALLOW;
    }

    /** 12: dragging the open shulker, or dragging any shulker across our window's slots. */
    public static ClickDecision decideDrag(boolean oldCursorIsOpenShulker, boolean topIsOurs,
            boolean oldCursorIsShulker, boolean touchesTop) {
        if (oldCursorIsOpenShulker || (topIsOurs && oldCursorIsShulker && touchesTop)) {
            return ClickDecision.cancel(12);
        }
        return topIsOurs ? ClickDecision.allow(12) : ClickDecision.ALLOW;
    }

    /** 5: no shulker of any kind may go into our window. */
    private static boolean putsShulkerIntoTop(ClickSnapshot s) {
        if (s.clicked() == SlotKind.TOP) {
            if (s.cursorIsShulker() && s.action() != InventoryAction.NOTHING) {
                return true;
            }
            if (isHotbarSwap(s) && s.hotbarTargetIsShulker()) {
                return true;
            }
            if (s.click() == ClickType.SWAP_OFFHAND && s.offhandIsShulker()) {
                return true;
            }
        }
        return s.clicked() == SlotKind.BOTTOM && s.currentIsShulker()
                && (s.action() == InventoryAction.MOVE_TO_OTHER_INVENTORY || s.click().isShiftClick());
    }

    private static boolean isHotbarSwap(ClickSnapshot s) {
        return s.click() == ClickType.NUMBER_KEY
                || s.action() == InventoryAction.HOTBAR_SWAP
                || s.action() == InventoryAction.HOTBAR_MOVE_AND_READD;
    }

    public static boolean isOpenClick(ClickType click, OpenClick configured) {
        return switch (configured) {
            case RIGHT -> click == ClickType.RIGHT;
            case SHIFT_RIGHT -> click == ClickType.SHIFT_RIGHT;
        };
    }
}
