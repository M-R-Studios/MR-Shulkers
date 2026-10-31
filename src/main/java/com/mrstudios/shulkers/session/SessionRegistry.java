package com.mrstudios.shulkers.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every live session, by session id and by player. At most one session per player, and one per
 * item because the id is unique per opened item.
 */
public final class SessionRegistry {

    private final Map<UUID, ShulkerSession> byId = new HashMap<>();
    private final Map<UUID, ShulkerSession> byPlayer = new HashMap<>();

    /** Adds {@code session}. Refused if its player already has one or its id is already live. */
    public boolean register(ShulkerSession session) {
        if (byPlayer.containsKey(session.playerId()) || byId.containsKey(session.id())) {
            return false;
        }
        byId.put(session.id(), session);
        byPlayer.put(session.playerId(), session);
        return true;
    }

    public Optional<ShulkerSession> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<ShulkerSession> byPlayer(UUID playerId) {
        return Optional.ofNullable(byPlayer.get(playerId));
    }

    /** A tag is live if its session is registered; anything else is stale. */
    public boolean isLive(UUID id) {
        return byId.containsKey(id);
    }

    /** Removes {@code session} from both maps. Does nothing if it isn't the registered one. */
    public void remove(ShulkerSession session) {
        if (byId.get(session.id()) == session) {
            byId.remove(session.id());
            byPlayer.remove(session.playerId());
        }
    }

    public List<ShulkerSession> all() {
        return new ArrayList<>(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
