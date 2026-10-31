package com.mrstudios.shulkers.session;

import java.util.UUID;
import org.bukkit.inventory.Inventory;

/** One open shulker window. Only touched on the main thread. */
public final class ShulkerSession {

    private final UUID id;
    private final UUID playerId;
    private SlotRef source;
    private Inventory view;
    private boolean dirty;
    private boolean saveScheduled;
    private boolean ended;

    public ShulkerSession(UUID id, UUID playerId, SlotRef source) {
        this.id = id;
        this.playerId = playerId;
        this.source = source;
    }

    /** The value of the open tag on the item. */
    public UUID id() {
        return id;
    }

    public UUID playerId() {
        return playerId;
    }

    public SlotRef source() {
        return source;
    }

    void source(SlotRef source) {
        this.source = source;
    }

    public Inventory view() {
        return view;
    }

    void view(Inventory view) {
        this.view = view;
    }

    public boolean dirty() {
        return dirty;
    }

    void dirty(boolean dirty) {
        this.dirty = dirty;
    }

    boolean saveScheduled() {
        return saveScheduled;
    }

    void saveScheduled(boolean saveScheduled) {
        this.saveScheduled = saveScheduled;
    }

    public boolean ended() {
        return ended;
    }

    void end() {
        this.ended = true;
    }
}
