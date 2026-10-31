package com.mrstudios.shulkers.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final CooldownTracker tracker = new CooldownTracker(now::get);
    private final UUID player = UUID.randomUUID();

    @Test
    void nobodyIsBlockedBeforeTheirFirstOpen() {
        assertEquals(0, tracker.remainingMs(player, 500));
    }

    @Test
    void blockedBeforeExpiry() {
        tracker.start(player);
        now.addAndGet(200);

        assertEquals(300, tracker.remainingMs(player, 500));
    }

    @Test
    void allowedAtAndAfterExpiry() {
        tracker.start(player);
        now.addAndGet(500);
        assertEquals(0, tracker.remainingMs(player, 500));

        now.addAndGet(1);
        assertEquals(0, tracker.remainingMs(player, 500));
    }

    @Test
    void zeroDisablesTheCooldown() {
        tracker.start(player);

        assertEquals(0, tracker.remainingMs(player, 0));
    }

    @Test
    void cooldownsArePerPlayer() {
        tracker.start(player);

        assertEquals(0, tracker.remainingMs(UUID.randomUUID(), 500));
    }

    @Test
    void forgetClearsTheCooldown() {
        tracker.start(player);
        tracker.forget(player);

        assertEquals(0, tracker.remainingMs(player, 500));
    }

    @Test
    void remainingSecondsAreRoundedUp() {
        assertEquals(0, CooldownTracker.secondsRoundedUp(0));
        assertEquals(1, CooldownTracker.secondsRoundedUp(1));
        assertEquals(1, CooldownTracker.secondsRoundedUp(1000));
        assertEquals(2, CooldownTracker.secondsRoundedUp(1001));
        assertEquals(3, CooldownTracker.secondsRoundedUp(2500));
    }
}
