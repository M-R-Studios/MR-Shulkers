package com.mrstudios.shulkers.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrstudios.shulkers.config.Settings.OpenClick;
import com.mrstudios.shulkers.rules.ClickDecision.Kind;
import com.mrstudios.shulkers.rules.ClickSnapshot.SlotKind;
import com.mrstudios.shulkers.rules.ClickSnapshot.ViewKind;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Every click row of DESIGN.md section 3.5 (#1 to #12). */
class ClickRulesTest {

    private static ClickSnapshot.Builder snap() {
        return ClickSnapshot.builder();
    }

    private static void assertDecision(Kind kind, int rule, ClickSnapshot snapshot) {
        assertEquals(new ClickDecision(kind, rule), ClickRules.decide(snapshot), snapshot.toString());
    }

    static Stream<Arguments> everyClickOnEveryView() {
        List<Arguments> cases = new ArrayList<>();
        InventoryAction[] actions = InventoryAction.values();
        for (ClickType click : ClickType.values()) {
            for (InventoryAction action : actions) {
                for (ViewKind view : ViewKind.values()) {
                    for (SlotKind slot : List.of(SlotKind.TOP, SlotKind.BOTTOM)) {
                        cases.add(Arguments.of(click, action, view, slot));
                    }
                }
            }
        }
        return cases.stream();
    }

    // --- 1, 6: the open shulker itself never moves ---

    @ParameterizedTest
    @MethodSource("everyClickOnEveryView")
    void anyClickOnTheOpenShulkerIsCancelled(ClickType click, InventoryAction action, ViewKind view, SlotKind slot) {
        ClickDecision decision = ClickRules.decide(snap().click(click, action).view(view, slot).currentShulker(true).build());

        assertEquals(Kind.CANCEL, decision.kind());
        assertTrue(decision.rule() == 1 || decision.rule() == 6);
    }

    @Test
    void cloneOfTheOpenShulkerIsRule6() {
        assertDecision(Kind.CANCEL, 6, snap().click(ClickType.MIDDLE, InventoryAction.CLONE_STACK).currentShulker(true).build());
        assertDecision(Kind.CANCEL, 6, snap().click(ClickType.CREATIVE, InventoryAction.PLACE_ALL).currentShulker(true).build());
    }

    @Test
    void cloneOfAnotherShulkerIsAllowed() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.MIDDLE, InventoryAction.CLONE_STACK).currentShulker(false).build());
    }

    // --- 2: open shulker on the cursor ---

    @ParameterizedTest
    @MethodSource("everyClickOnEveryView")
    void anyClickWithTheOpenShulkerOnTheCursorIsCancelled(ClickType click, InventoryAction action, ViewKind view, SlotKind slot) {
        ClickDecision decision = ClickRules.decide(snap().click(click, action).view(view, slot).cursorShulker(true).build());

        assertEquals(new ClickDecision(Kind.CANCEL, 2), decision);
    }

    @Test
    void clickOutsideWithTheOpenShulkerOnTheCursorIsCancelled() {
        assertDecision(Kind.CANCEL, 2, snap().click(ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR)
                .view(ViewKind.OUR_SHULKER, SlotKind.OUTSIDE).cursorShulker(true).build());
    }

    // --- 3: number keys ---

    @ParameterizedTest
    @EnumSource(value = ViewKind.class)
    void numberKeyOntoTheOpenShulkersHotbarSlotIsCancelled(ViewKind view) {
        assertDecision(Kind.CANCEL, 3, snap().click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP)
                .view(view, SlotKind.TOP).hotbarTargetShulker(true).build());
        assertDecision(Kind.CANCEL, 3, snap().click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP)
                .view(view, SlotKind.BOTTOM).hotbarTargetShulker(true).build());
    }

    @Test
    void numberKeyOntoAnotherHotbarSlotIsAllowed() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP)
                .view(ViewKind.PLAYER_SCREEN, SlotKind.BOTTOM).build());
        assertDecision(Kind.ALLOW, 9, snap().click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP)
                .view(ViewKind.OUR_SHULKER, SlotKind.TOP).build());
    }

    // --- 4: offhand swap ---

    @ParameterizedTest
    @EnumSource(value = ViewKind.class)
    void offhandSwapWithTheOpenShulkerInTheOffhandIsCancelled(ViewKind view) {
        assertDecision(Kind.CANCEL, 4, snap().click(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP)
                .view(view, SlotKind.TOP).offhandShulker(true).build());
    }

    @Test
    void offhandSwapOfOrdinaryItemsIsAllowed() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP).build());
    }

    // --- 5: no shulker goes into our window ---

    @Test
    void cursorShulkerPlacedIntoTheWindowIsCancelled() {
        for (InventoryAction action : List.of(InventoryAction.PLACE_ALL, InventoryAction.PLACE_ONE,
                InventoryAction.PLACE_SOME, InventoryAction.SWAP_WITH_CURSOR, InventoryAction.UNKNOWN)) {
            assertDecision(Kind.CANCEL, 5, snap().click(ClickType.LEFT, action)
                    .view(ViewKind.OUR_SHULKER, SlotKind.TOP).cursorShulker(false).build());
        }
    }

    @Test
    void shiftClickOfAnyShulkerFromTheBottomIsCancelled() {
        assertDecision(Kind.CANCEL, 5, snap().click(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                .view(ViewKind.OUR_SHULKER, SlotKind.BOTTOM).currentShulker(false).build());
        assertDecision(Kind.CANCEL, 5, snap().click(ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                .view(ViewKind.OUR_SHULKER, SlotKind.BOTTOM).currentShulker(false).build());
    }

    @Test
    void numberKeyOrOffhandSwapOfAShulkerIntoTheWindowIsCancelled() {
        assertDecision(Kind.CANCEL, 5, snap().click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP)
                .view(ViewKind.OUR_SHULKER, SlotKind.TOP).hotbarTargetShulker(false).build());
        assertDecision(Kind.CANCEL, 5, snap().click(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP)
                .view(ViewKind.OUR_SHULKER, SlotKind.TOP).offhandShulker(false).build());
    }

    @Test
    void shulkersStillMoveFreelyInsideYourOwnInventory() {
        assertDecision(Kind.ALLOW, 9, snap().click(ClickType.LEFT, InventoryAction.PLACE_ALL)
                .view(ViewKind.OUR_SHULKER, SlotKind.BOTTOM).cursorShulker(false).build());
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                .view(ViewKind.PLAYER_SCREEN, SlotKind.BOTTOM).currentShulker(false).build());
    }

    // --- 7, 8, 10: open triggers ---

    @Test
    void rightClickWithEmptyCursorOnAShulkerInYourInventoryScreenOpens() {
        assertDecision(Kind.OPEN, 7, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF).currentShulker(false).build());
    }

    @Test
    void sameClickWithAnItemOnTheCursorIsVanilla() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.SWAP_WITH_CURSOR)
                .currentShulker(false).cursorItem().build());
    }

    /** Kit-plugin regression: never open from another plugin's GUI. */
    @Test
    void sameClickInAnotherPluginsGuiDoesNotOpen() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .view(ViewKind.OTHER, SlotKind.TOP).currentShulker(false).build());
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .view(ViewKind.OTHER, SlotKind.BOTTOM).currentShulker(false).build());
    }

    @Test
    void shiftRightConfiguredPlainRightDoesNotOpen() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .currentShulker(false).openClick(OpenClick.SHIFT_RIGHT).build());
        assertDecision(Kind.OPEN, 7, snap().click(ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY)
                .currentShulker(false).openClick(OpenClick.SHIFT_RIGHT).build());
    }

    @Test
    void inventoryOpenDisabledOrNotAShulkerDoesNotOpen() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .currentShulker(false).inventoryOpenEnabled(false).build());
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF).build());
    }

    @Test
    void clickInTheCraftingGridDoesNotOpen() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .view(ViewKind.PLAYER_SCREEN, SlotKind.TOP).currentShulker(false).build());
    }

    @Test
    void enderChestOpensOnlyWithPermission() {
        ClickSnapshot.Builder base = snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .view(ViewKind.OWN_ENDER_CHEST, SlotKind.TOP).currentShulker(false);

        assertDecision(Kind.OPEN, 8, base.enderChestOpenAllowed(true).build());
        assertDecision(Kind.ALLOW, 0, base.enderChestOpenAllowed(false).build());
    }

    @Test
    void enderChestScreenBottomDoesNotOpen() {
        assertDecision(Kind.ALLOW, 0, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .view(ViewKind.OWN_ENDER_CHEST, SlotKind.BOTTOM).currentShulker(false).enderChestOpenAllowed(true).build());
    }

    @Test
    void openClickOnAnotherShulkerWhileOneIsOpenSwitches() {
        assertDecision(Kind.OPEN, 10, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .view(ViewKind.OUR_SHULKER, SlotKind.BOTTOM).currentShulker(false).build());
    }

    @Test
    void openClickOnAnotherShulkerWithOpenDisabledIsJustAClick() {
        assertDecision(Kind.ALLOW, 9, snap().click(ClickType.RIGHT, InventoryAction.PICKUP_HALF)
                .view(ViewKind.OUR_SHULKER, SlotKind.BOTTOM).currentShulker(false).inventoryOpenEnabled(false).build());
    }

    // --- 9: ordinary clicks in our window ---

    @Test
    void ordinaryClicksInOurWindowAreAllowedAndSaved() {
        for (InventoryAction action : List.of(InventoryAction.PICKUP_ALL, InventoryAction.PLACE_ALL,
                InventoryAction.MOVE_TO_OTHER_INVENTORY, InventoryAction.COLLECT_TO_CURSOR, InventoryAction.DROP_ALL_SLOT)) {
            assertDecision(Kind.ALLOW, 9, snap().click(ClickType.LEFT, action).view(ViewKind.OUR_SHULKER, SlotKind.TOP).cursorItem().build());
        }
    }

    // --- 11: creative ---

    @Test
    void creativeRules() {
        assertEquals(new ClickDecision(Kind.CANCEL, 11), ClickRules.decideCreative(true, false));
        assertEquals(new ClickDecision(Kind.CANCEL, 11), ClickRules.decideCreative(false, true));
        assertEquals(ClickDecision.ALLOW, ClickRules.decideCreative(false, false));
    }

    // --- 12: drag ---

    @Test
    void dragRules() {
        assertEquals(new ClickDecision(Kind.CANCEL, 12), ClickRules.decideDrag(true, false, true, false));
        assertEquals(new ClickDecision(Kind.CANCEL, 12), ClickRules.decideDrag(false, true, true, true));
        assertEquals(new ClickDecision(Kind.ALLOW, 12), ClickRules.decideDrag(false, true, true, false));
        assertEquals(new ClickDecision(Kind.ALLOW, 12), ClickRules.decideDrag(false, true, false, true));
        assertEquals(ClickDecision.ALLOW, ClickRules.decideDrag(false, false, true, true));
    }

    @Test
    void openClickMapping() {
        assertTrue(ClickRules.isOpenClick(ClickType.RIGHT, OpenClick.RIGHT));
        assertTrue(ClickRules.isOpenClick(ClickType.SHIFT_RIGHT, OpenClick.SHIFT_RIGHT));
        assertEquals(false, ClickRules.isOpenClick(ClickType.SHIFT_RIGHT, OpenClick.RIGHT));
    }
}
