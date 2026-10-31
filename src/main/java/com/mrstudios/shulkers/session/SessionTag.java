package com.mrstudios.shulkers.session;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The value of the {@code mrshulkers:open_session} tag: a random UUID per open. Anything that
 * doesn't parse back to exactly the UUID it came from counts as stale.
 */
public final class SessionTag {

    private static final int LENGTH = 36;

    private SessionTag() {
    }

    public static UUID generate() {
        return UUID.randomUUID();
    }

    public static String encode(UUID id) {
        return id.toString();
    }

    /** The UUID in {@code value}, or empty if it is missing or malformed. */
    public static Optional<UUID> parse(String value) {
        if (value == null || value.length() != LENGTH) {
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(value);
            // UUID.fromString also accepts forms like "1-1-1-1-1"; only the canonical form is ours.
            return id.toString().equals(value.toLowerCase(Locale.ROOT)) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
