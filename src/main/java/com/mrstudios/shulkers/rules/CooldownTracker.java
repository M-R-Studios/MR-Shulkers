package com.mrstudios.shulkers.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Per-player time between opens. The clock is injectable so tests don't wait. */
public final class CooldownTracker {

    private final LongSupplier clock;
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    public CooldownTracker(LongSupplier clock) {
        this.clock = clock;
    }

    public CooldownTracker() {
        this(System::currentTimeMillis);
    }

    /** Milliseconds left before {@code player} may open again; 0 if they may open now. */
    public long remainingMs(UUID player, long cooldownMs) {
        Long last = lastOpen.get(player);
        if (cooldownMs <= 0 || last == null) {
            return 0;
        }
        return Math.max(0, last + cooldownMs - clock.getAsLong());
    }

    public void start(UUID player) {
        lastOpen.put(player, clock.getAsLong());
    }

    public void forget(UUID player) {
        lastOpen.remove(player);
    }

    /** Whole seconds for the cooldown message, rounded up so it never says "0s" while blocked. */
    public static long secondsRoundedUp(long ms) {
        return (ms + 999) / 1000;
    }
}
