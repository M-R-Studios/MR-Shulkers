package com.mrstudios.shulkers.session;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.config.Settings.SoundSetting;
import com.mrstudios.shulkers.item.ShulkerContents;
import com.mrstudios.shulkers.item.ShulkerItems;
import com.mrstudios.shulkers.rules.OpenChecks;
import com.mrstudios.shulkers.rules.OpenPath;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Opens, saves and closes shulker windows. The item is the only storage: every change in the window
 * is written back to the item, and the item is only ever found by its session tag (DESIGN.md 3.4).
 */
public final class SessionService {

    private final MRShulkers plugin;
    private final ShulkerItems items;
    private final ShulkerContents contents;
    private final OpenChecks checks;
    private final SessionRegistry registry = new SessionRegistry();
    private final Set<UUID> pendingOpens = new HashSet<>();

    public SessionService(MRShulkers plugin, ShulkerItems items, ShulkerContents contents, OpenChecks checks) {
        this.plugin = plugin;
        this.items = items;
        this.contents = contents;
        this.checks = checks;
    }

    public SessionRegistry registry() {
        return registry;
    }

    /**
     * Opens the shulker at {@code source} on the next tick: the trigger is usually a click, and
     * opening or closing an inventory inside a click handler is unsafe. Every open path goes through
     * here. The checks (permission, cooldown, world, creative) run now; an open shulker is closed
     * and the item checks (shulker, stack size, live tag) run on the next tick.
     */
    public void tryOpen(Player player, SlotRef source, OpenPath path) {
        OpenChecks.Failure failure = checks.check(player, path, plugin.state().settings());
        if (failure != null) {
            plugin.state().messages().send(player, failure.message(), failure.placeholders());
            return;
        }
        UUID playerId = player.getUniqueId();
        if (!pendingOpens.add(playerId)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingOpens.remove(playerId);
            openNow(player, source);
        });
    }

    private void openNow(Player player, SlotRef source) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        registry.byPlayer(player.getUniqueId()).ifPresent(session -> close(session, true));

        ItemStack item = get(player, source);
        if (!ShulkerItems.isShulker(item)) {
            fail(player, "open-failed");
            return;
        }
        if (item.getAmount() != 1) {
            // Writing one set of contents onto a stack would dupe them.
            fail(player, "cannot-open-stack");
            return;
        }
        if (items.hasTag(item)) {
            Optional<UUID> tag = items.tag(item);
            if (tag.isPresent() && registry.isLive(tag.get())) {
                fail(player, "open-failed");
                return;
            }
            item = items.withoutTag(item);
            set(player, source, item);
        }

        List<ItemStack> slots = readSlots(item);
        if (slots == null) {
            plugin.getLogger().warning("Refused to open " + player.getName() + "'s shulker at " + source
                    + ": it holds more than " + ShulkerItems.SIZE + " stacks or can't be read.");
            fail(player, "open-failed");
            return;
        }

        // Build the window before tagging, so a failure here can't leave a tag behind.
        UUID id = SessionTag.generate();
        ShulkerSession session = new ShulkerSession(id, player.getUniqueId(), source);
        Inventory view = Bukkit.createInventory(new ShulkerHolder(session), InventoryType.SHULKER_BOX, title(item));
        view.setContents(slots.toArray(new ItemStack[0]));
        session.view(view);
        set(player, source, items.withTag(item, id));

        if (!registry.register(session) || player.openInventory(view) == null) {
            // Another plugin cancelled the open: undo the tag, write nothing.
            registry.remove(session);
            session.end();
            if (items.hasTag(get(player, source), id)) {
                set(player, source, items.withoutTag(get(player, source)));
            }
            fail(player, "open-failed");
            return;
        }
        checks.opened(player);
        playSound(player, plugin.state().settings().openSound());
    }

    /**
     * The shulker's anvil name when it has one and {@code window.use-item-name} is on; otherwise
     * {@code window-title} from messages.yml with {@code <name>} as the item's default name.
     */
    private Component title(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (plugin.state().settings().windowUseItemName() && meta != null && meta.hasCustomName()) {
            return meta.customName();
        }
        return plugin.state().messages().component("window-title",
                Placeholder.component("name", Component.translatable(item.getType())));
    }

    private static void playSound(Player player, SoundSetting sound) {
        if (sound.enabled()) {
            player.playSound(Sound.sound(sound.sound(), Sound.Source.BLOCK, sound.volume(), sound.pitch()));
        }
    }

    /** Forgets per-player state (the cooldown) when they leave. */
    public void forget(Player player) {
        checks.forget(player);
    }

    private void fail(Player player, String message) {
        plugin.state().messages().send(player, message);
    }

    /** Marks the window changed and saves it on the next tick (once, however many clicks come in). */
    public void markDirty(ShulkerSession session) {
        session.dirty(true);
        if (session.saveScheduled()) {
            return;
        }
        session.saveScheduled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.saveScheduled(false);
            if (session.dirty()) {
                save(session);
            }
        });
    }

    /**
     * Writes the window's contents into the item. If the item can't be found it has left the
     * inventory, already holding the last saved contents, and the window is discarded.
     *
     * @return whether the session is still open
     */
    public boolean save(ShulkerSession session) {
        if (session.ended()) {
            return false;
        }
        Player player = Bukkit.getPlayer(session.playerId());
        Found found = player == null ? null : locate(player, session);
        if (found == null) {
            endWithoutWrite(session, "item left inventory");
            return false;
        }
        ItemStack saved = savedCopy(found.item, windowSlots(session), false);
        if (saved != found.item) {
            put(player, found, saved);
        }
        session.dirty(false);
        return true;
    }

    /** Normal close without returning to the ender chest. */
    public void close(ShulkerSession session, boolean closeView) {
        close(session, closeView, false);
    }

    /**
     * Normal close: save, remove the tag, unregister.
     *
     * @param closeView whether to close the window too; false when called from its close event
     * @param returnToEnderChest reopen the ender chest next tick if the shulker came from it and
     *     {@code open.ender-chest.return-on-close} is on
     */
    public void close(ShulkerSession session, boolean closeView, boolean returnToEnderChest) {
        if (!save(session)) {
            return;
        }
        Player player = Bukkit.getPlayer(session.playerId());
        Found found = locate(player, session);
        if (found == null) {
            endWithoutWrite(session, "item left inventory");
            return;
        }
        // End first, so slot-change and close handlers see an ended session while we untag.
        finish(session, closeView ? player : null);
        put(player, found, items.withoutTag(found.item));
        playSound(player, plugin.state().settings().closeSound());
        if (returnToEnderChest && session.source().container() == SlotRef.Container.ENDER_CHEST
                && plugin.state().settings().enderChestReturnOnClose()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Only if nothing else was opened in the meantime.
                Inventory top = player.getOpenInventory().getTopInventory();
                if (player.isOnline() && !player.isDead() && (top == null || top.getType() == InventoryType.CRAFTING)) {
                    player.openInventory(player.getEnderChest());
                }
            });
        }
    }

    /**
     * Ends the session without writing anything. Only used when the item can't be trusted; its
     * contents are already in the item, so the window's contents are never dropped or given back.
     */
    public void endWithoutWrite(ShulkerSession session, String why) {
        if (session.ended()) {
            return;
        }
        Player player = Bukkit.getPlayer(session.playerId());
        finish(session, player);
        String name = player != null ? player.getName() : session.playerId().toString();
        plugin.getLogger().info("Closed " + name + "'s shulker without saving: " + why + ".");
        if (player != null) {
            plugin.state().messages().send(player, "closed-item-moved");
        }
    }

    private void finish(ShulkerSession session, Player closeFor) {
        registry.remove(session);
        session.end();
        if (closeFor != null && ShulkerHolder.sessionOf(closeFor.getOpenInventory().getTopInventory()) == session) {
            closeFor.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    /**
     * Death, when it comes before the window's close event (DESIGN.md 3.6). Writes the window into
     * every copy of the tagged item we can find, strips the tag on each, and ends the session.
     */
    public void onDeath(Player player, List<ItemStack> drops, List<ItemStack> itemsToKeep) {
        ShulkerSession session = registry.byPlayer(player.getUniqueId()).orElse(null);
        if (session == null || session.ended()) {
            return;
        }
        List<ItemStack> window = windowSlots(session);
        Found found = locate(player, session);
        finish(session, player);
        if (found != null) {
            put(player, found, savedCopy(found.item, window, true));
        }
        int written = (found != null ? 1 : 0) + saveInto(drops, session, window) + saveInto(itemsToKeep, session, window);
        if (written == 0) {
            plugin.getLogger().warning(player.getName() + " died with a shulker open but it wasn't in their inventory or drops."
                    + " It keeps the contents from the last save.");
        }
    }

    private int saveInto(List<ItemStack> list, ShulkerSession session, List<ItemStack> window) {
        int written = 0;
        for (ListIterator<ItemStack> it = list.listIterator(); it.hasNext(); ) {
            ItemStack item = it.next();
            if (isSessionItem(item, session)) {
                it.set(savedCopy(item, window, true));
                written++;
            }
        }
        return written;
    }

    /**
     * Watchdog check (DESIGN.md 3.5 #30): flush if dirty, make sure the item is still there, and close
     * the session if another plugin replaced the window without a close event.
     */
    public void verify(ShulkerSession session) {
        if (session.ended()) {
            return;
        }
        Player player = Bukkit.getPlayer(session.playerId());
        if (player == null) {
            endWithoutWrite(session, "player went offline");
            return;
        }
        if (ShulkerHolder.sessionOf(player.getOpenInventory().getTopInventory()) != session) {
            close(session, false);
            return;
        }
        if (session.dirty()) {
            save(session);
        } else if (locate(player, session) == null) {
            endWithoutWrite(session, "item left inventory");
        }
    }

    public void verifyAll() {
        registry.all().forEach(this::verify);
    }

    /** The live session whose tag {@code item} carries, or null. */
    public ShulkerSession liveSession(ItemStack item) {
        return items.tag(item).flatMap(registry::byId).orElse(null);
    }

    /** Whether {@code item} carries a live open tag: it is a shulker that is open right now. */
    public boolean isOpen(ItemStack item) {
        return liveSession(item) != null;
    }

    /** Whether {@code item} carries an open tag whose session isn't live. */
    public boolean isStale(ItemStack item) {
        return items.hasTag(item) && liveSession(item) == null;
    }

    public ShulkerItems items() {
        return items;
    }

    /** Closes every session, saving each one. For plugin disable. */
    public void closeAll() {
        for (ShulkerSession session : registry.all()) {
            close(session, true);
        }
        pendingOpens.clear();
    }

    /** Removes open tags whose session isn't live, e.g. left over from a crash. */
    public void stripStaleTags(Player player) {
        int stripped = stripStale(player.getInventory()) + stripStale(player.getEnderChest());
        if (stripped > 0) {
            plugin.getLogger().info("Removed " + stripped + " leftover open tag(s) from " + player.getName() + "'s shulkers.");
        }
    }

    private int stripStale(Inventory inventory) {
        int stripped = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (items.hasTag(item) && items.tag(item).filter(registry::isLive).isEmpty()) {
                inventory.setItem(i, items.withoutTag(item));
                stripped++;
            }
        }
        return stripped;
    }

    // --- Finding and writing the item ---

    /** Where a session's item was found. {@code slot} is null for the cursor. */
    private record Found(SlotRef slot, ItemStack item) {
    }

    private Found locate(Player player, ShulkerSession session) {
        ItemStack atSource = get(player, session.source());
        if (isSessionItem(atSource, session)) {
            return new Found(session.source(), atSource);
        }
        Found found = scan(player.getInventory(), SlotRef.Container.PLAYER_INVENTORY, session);
        if (found == null && isSessionItem(player.getItemOnCursor(), session)) {
            found = new Found(null, player.getItemOnCursor());
        }
        if (found == null) {
            found = scan(player.getEnderChest(), SlotRef.Container.ENDER_CHEST, session);
        }
        if (found != null) {
            plugin.getLogger().warning(player.getName() + "'s open shulker moved from " + session.source() + " to "
                    + (found.slot == null ? "the cursor" : found.slot) + ". This should not happen; please report it.");
            if (found.slot != null) {
                session.source(found.slot);
            }
        }
        return found;
    }

    private Found scan(Inventory inventory, SlotRef.Container container, ShulkerSession session) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isSessionItem(inventory.getItem(i), session)) {
                return new Found(new SlotRef(container, i), inventory.getItem(i));
            }
        }
        return null;
    }

    /** Fail closed: only a single shulker carrying exactly this session's tag counts. */
    private boolean isSessionItem(ItemStack item, ShulkerSession session) {
        return ShulkerItems.isShulker(item) && item.getAmount() == 1 && items.hasTag(item, session.id());
    }

    private void put(Player player, Found found, ItemStack item) {
        if (found.slot == null) {
            player.setItemOnCursor(item);
        } else {
            set(player, found.slot, item);
        }
    }

    private static Inventory inventoryOf(Player player, SlotRef slot) {
        return slot.container() == SlotRef.Container.ENDER_CHEST ? player.getEnderChest() : player.getInventory();
    }

    private static ItemStack get(Player player, SlotRef slot) {
        return inventoryOf(player, slot).getItem(slot.index());
    }

    private static void set(Player player, SlotRef slot, ItemStack item) {
        inventoryOf(player, slot).setItem(slot.index(), item);
    }

    /** The window's 27 slots, null for empty. */
    private static List<ItemStack> windowSlots(ShulkerSession session) {
        return ShulkerItems.normalise(Arrays.asList(session.view().getContents()), ItemStack::isEmpty).orElseThrow();
    }

    private List<ItemStack> readSlots(ItemStack item) {
        try {
            return ShulkerItems.normalise(contents.read(item), ItemStack::isEmpty).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * {@code item} with {@code window} as its contents, and without the tag if {@code strip}. Returns
     * {@code item} itself when nothing would change, so an unchanged shulker stays byte-identical.
     */
    private ItemStack savedCopy(ItemStack item, List<ItemStack> window, boolean strip) {
        boolean changed = !window.equals(readSlots(item));
        if (!changed && !strip) {
            return item;
        }
        ItemStack copy = strip ? items.withoutTag(item) : item.clone();
        if (changed) {
            contents.write(copy, window);
        }
        return copy;
    }
}
