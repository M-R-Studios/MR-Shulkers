package com.mrstudios.shulkers.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry();

    private static ShulkerSession session(UUID player, int slot) {
        return new ShulkerSession(SessionTag.generate(), player, SlotRef.inventory(slot));
    }

    @Test
    void secondSessionForSamePlayerIsRefused() {
        UUID player = UUID.randomUUID();
        ShulkerSession first = session(player, 0);
        ShulkerSession second = session(player, 1);

        assertTrue(registry.register(first));
        assertFalse(registry.register(second));
        assertSame(first, registry.byPlayer(player).orElseThrow());
        assertFalse(registry.isLive(second.id()));
    }

    @Test
    void sameIdTwiceIsRefused() {
        ShulkerSession first = session(UUID.randomUUID(), 0);
        ShulkerSession copy = new ShulkerSession(first.id(), UUID.randomUUID(), SlotRef.inventory(0));

        assertTrue(registry.register(first));
        assertFalse(registry.register(copy));
        assertTrue(registry.byPlayer(copy.playerId()).isEmpty());
    }

    @Test
    void liveVersusStale() {
        ShulkerSession live = session(UUID.randomUUID(), 0);
        registry.register(live);

        assertTrue(registry.isLive(live.id()));
        assertSame(live, registry.byId(live.id()).orElseThrow());
        assertFalse(registry.isLive(SessionTag.generate()));
        assertTrue(registry.byId(SessionTag.generate()).isEmpty());
    }

    @Test
    void removingFreesBothMaps() {
        UUID player = UUID.randomUUID();
        ShulkerSession session = session(player, 0);
        registry.register(session);

        registry.remove(session);

        assertFalse(registry.isLive(session.id()));
        assertTrue(registry.byPlayer(player).isEmpty());
        assertEquals(0, registry.size());
        assertTrue(registry.register(session(player, 2)));
    }

    @Test
    void removingAnUnregisteredSessionLeavesTheLiveOneAlone() {
        UUID player = UUID.randomUUID();
        ShulkerSession live = session(player, 0);
        registry.register(live);

        registry.remove(session(player, 0));

        assertSame(live, registry.byPlayer(player).orElseThrow());
    }

    /** The AxShulkers "identical shulkers share data" regression: identical items, separate sessions. */
    @Test
    void identicalItemsForDifferentPlayersStaySeparate() {
        ShulkerSession a = session(UUID.randomUUID(), 3);
        ShulkerSession b = session(UUID.randomUUID(), 3);

        assertTrue(registry.register(a));
        assertTrue(registry.register(b));

        assertSame(a, registry.byPlayer(a.playerId()).orElseThrow());
        assertSame(b, registry.byPlayer(b.playerId()).orElseThrow());
        assertSame(a, registry.byId(a.id()).orElseThrow());
        assertSame(b, registry.byId(b.id()).orElseThrow());
        registry.remove(a);
        assertTrue(registry.isLive(b.id()));
        assertEquals(1, registry.size());
    }
}
