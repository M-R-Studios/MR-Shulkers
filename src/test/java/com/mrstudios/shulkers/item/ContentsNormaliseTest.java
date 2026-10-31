package com.mrstudios.shulkers.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContentsNormaliseTest {

    private static final String AIR = "air";

    private static List<String> normalise(List<String> stored) {
        return ShulkerItems.normalise(stored, AIR::equals).orElseThrow();
    }

    @Test
    void shorterListIsPaddedTo27() {
        List<String> slots = normalise(List.of("diamond", "sword"));

        assertEquals(27, slots.size());
        assertEquals("diamond", slots.get(0));
        assertEquals("sword", slots.get(1));
        for (int i = 2; i < 27; i++) {
            assertNull(slots.get(i));
        }
    }

    @Test
    void emptyListGives27EmptySlots() {
        assertEquals(Collections.nCopies(27, null), normalise(List.of()));
    }

    @Test
    void nullsAndEmptyItemsStayEmptyInPlace() {
        List<String> stored = Arrays.asList(null, "a", AIR, "b");

        List<String> slots = normalise(stored);

        assertNull(slots.get(0));
        assertEquals("a", slots.get(1));
        assertNull(slots.get(2));
        assertEquals("b", slots.get(3));
    }

    @Test
    void exactly27IsKept() {
        List<String> stored = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            stored.add("item" + i);
        }

        assertEquals(stored, normalise(stored));
    }

    @Test
    void moreThan27IsRejected() {
        List<String> stored = Collections.nCopies(28, "item");

        assertTrue(ShulkerItems.normalise(stored, AIR::equals).isEmpty());
    }
}
