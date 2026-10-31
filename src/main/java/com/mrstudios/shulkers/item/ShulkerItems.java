package com.mrstudios.shulkers.item;

import com.mrstudios.shulkers.session.SessionTag;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Shulker item checks and the {@code mrshulkers:open_session} tag. Tag changes always return a
 * changed copy; callers put it back with {@code setItem}.
 */
public final class ShulkerItems {

    /** Slots in a shulker box. */
    public static final int SIZE = 27;

    private final NamespacedKey openKey;

    public ShulkerItems(Plugin plugin) {
        this.openKey = new NamespacedKey(plugin, "open_session");
    }

    public static boolean isShulker(ItemStack item) {
        return item != null && Tag.ITEMS_SHULKER_BOXES.isTagged(item.getType());
    }

    public boolean hasTag(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(openKey);
    }

    /**
     * The session id in the item's open tag. Empty if there is no tag or the tag is malformed; use
     * {@link #hasTag} to tell those apart.
     */
    public Optional<UUID> tag(ItemStack item) {
        if (!hasTag(item)) {
            return Optional.empty();
        }
        return SessionTag.parse(item.getPersistentDataContainer().get(openKey, PersistentDataType.STRING));
    }

    /** Whether {@code item} carries exactly the tag of session {@code id}. */
    public boolean hasTag(ItemStack item, UUID id) {
        return tag(item).filter(id::equals).isPresent();
    }

    public ItemStack withTag(ItemStack item, UUID id) {
        ItemStack copy = item.clone();
        copy.editPersistentDataContainer(pdc -> pdc.set(openKey, PersistentDataType.STRING, SessionTag.encode(id)));
        return copy;
    }

    public ItemStack withoutTag(ItemStack item) {
        ItemStack copy = item.clone();
        copy.editPersistentDataContainer(pdc -> pdc.remove(openKey));
        return copy;
    }

    /**
     * Pads {@code stored} to exactly {@link #SIZE} entries, with null for every empty slot. Empty if
     * there are more than {@link #SIZE} entries: opening and saving those would delete items.
     */
    public static <T> Optional<List<T>> normalise(List<T> stored, Predicate<T> isEmpty) {
        if (stored.size() > SIZE) {
            return Optional.empty();
        }
        List<T> slots = new ArrayList<>(SIZE);
        for (T item : stored) {
            slots.add(item == null || isEmpty.test(item) ? null : item);
        }
        while (slots.size() < SIZE) {
            slots.add(null);
        }
        return Optional.of(Collections.unmodifiableList(slots));
    }
}
