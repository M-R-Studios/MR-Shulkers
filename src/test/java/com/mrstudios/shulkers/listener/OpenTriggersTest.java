package com.mrstudios.shulkers.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.rules.OpenPath;
import com.mrstudios.shulkers.session.SessionService;
import com.mrstudios.shulkers.session.ShulkerHolder;
import com.mrstudios.shulkers.session.ShulkerSession;
import com.mrstudios.shulkers.session.SlotRef;
import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;

/** Open triggers (inventory click, hand, /shulker) and OpenChecks, against MockBukkit. */
class OpenTriggersTest {

    private ServerMock server;
    private MRShulkers plugin;
    private SessionService sessions;
    private PlayerMock player;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MRShulkers.class);
        sessions = plugin.sessions();
        player = server.addPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.PURPLE_SHULKER_BOX));
        player.getInventory().setItem(1, new ItemStack(Material.LIME_SHULKER_BOX));
        player.getInventory().setHeldItemSlot(0);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private void config(String path, Object value) throws Exception {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set(path, value);
        yaml.save(file);
        plugin.reload(server.getConsoleSender());
    }

    private ShulkerSession session() {
        return sessions.registry().byPlayer(player.getUniqueId()).orElse(null);
    }

    private String lastMessage() {
        String last = null;
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            last = PlainTextComponentSerializer.plainText().serialize(message);
        }
        return last;
    }

    /**
     * A click on player-inventory slot {@code index}. With no window open, MockBukkit's view has no
     * top inventory, so a CRAFTING one stands in for the player's own inventory screen.
     */
    private InventoryClickEvent clickInventorySlot(int index, ClickType click, InventoryAction action) {
        if (player.getOpenInventory().getTopInventory() == null) {
            player.openInventory(new InventoryMock(player, 5, InventoryType.CRAFTING));
        }
        InventoryView view = player.getOpenInventory();
        int raw = -1;
        for (int r = view.getTopInventory().getSize(); r < view.countSlots(); r++) {
            if (view.convertSlot(r) == index) {
                raw = r;
                break;
            }
        }
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.OUTSIDE, raw, click, action);
        Inventory clicked = view.getInventory(raw);
        event.setCurrentItem(clicked == null ? null : clicked.getItem(index));
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();
        return event;
    }

    private void rightClickAir() {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();
    }

    // --- inventory click ---

    @Test
    void rightClickInOwnInventoryScreenOpens() {
        InventoryClickEvent event = clickInventorySlot(1, ClickType.RIGHT, InventoryAction.PICKUP_HALF);

        assertTrue(event.isCancelled());
        assertNotNull(session());
        assertEquals(SlotRef.inventory(1), session().source());
    }

    @Test
    void leftClickDoesNotOpen() {
        clickInventorySlot(1, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        assertNull(session());
    }

    @Test
    void shiftRightConfiguredPlainRightDoesNotOpen() throws Exception {
        config("open.inventory.click", "SHIFT_RIGHT");

        clickInventorySlot(1, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
        assertNull(session());

        clickInventorySlot(1, ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertNotNull(session());
    }

    @Test
    void inventoryOpenDisabledLeavesTheClickVanilla() throws Exception {
        config("open.inventory.enabled", false);

        InventoryClickEvent event = clickInventorySlot(1, ClickType.RIGHT, InventoryAction.PICKUP_HALF);

        assertFalse(event.isCancelled());
        assertNull(session());
    }

    @Test
    void withoutPermissionTheClickStaysVanilla() {
        player.addAttachment(plugin, OpenPath.INVENTORY.permission(), false);

        InventoryClickEvent event = clickInventorySlot(1, ClickType.RIGHT, InventoryAction.PICKUP_HALF);

        assertFalse(event.isCancelled());
        assertNull(session());
    }

    @Test
    void openClickOnAnotherShulkerSwitchesToIt() {
        player.setOp(true);
        sessions.tryOpen(player, SlotRef.inventory(0), OpenPath.COMMAND);
        server.getScheduler().performOneTick();
        ShulkerSession first = session();

        clickInventorySlot(1, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
        server.getScheduler().performOneTick();

        assertTrue(first.ended());
        assertFalse(sessions.items().hasTag(player.getInventory().getItem(0)));
        assertEquals(SlotRef.inventory(1), session().source());
        assertEquals(session(), ShulkerHolder.sessionOf(player.getOpenInventory().getTopInventory()));
    }

    // --- hand ---

    @Test
    void rightClickAirWithShulkerInHandOpens() {
        rightClickAir();

        assertNotNull(session());
        assertEquals(SlotRef.inventory(0), session().source());
    }

    @Test
    void rightClickBlockStillPlacesAsVanilla() {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), player.getWorld().getBlockAt(0, 64, 0), BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        server.getScheduler().performOneTick();

        assertNull(session());
    }

    @Test
    void requireSneakNeedsSneaking() throws Exception {
        config("open.hand.require-sneak", true);

        rightClickAir();
        assertNull(session());

        player.setSneaking(true);
        rightClickAir();
        assertNotNull(session());
    }

    @Test
    void handOpenDisabledOrNoPermissionDoesNothing() throws Exception {
        player.addAttachment(plugin, OpenPath.HAND.permission(), false);
        rightClickAir();
        assertNull(session());

        player.addAttachment(plugin, OpenPath.HAND.permission(), true);
        config("open.hand.enabled", false);
        rightClickAir();
        assertNull(session());
    }

    // --- checks ---

    @Test
    void cooldownBlocksASecondOpenWithMessage() {
        rightClickAir();
        player.closeInventory();
        lastMessage();

        rightClickAir();

        assertNull(session());
        assertEquals("MR-Shulkers » Wait 1s before opening another shulker.", lastMessage());
    }

    @Test
    void cooldownBypassAndZeroCooldownAllowReopening() throws Exception {
        player.addAttachment(plugin, "mrshulkers.bypass.cooldown", true);
        rightClickAir();
        player.closeInventory();
        rightClickAir();
        assertNotNull(session());
        player.closeInventory();

        player.addAttachment(plugin, "mrshulkers.bypass.cooldown", false);
        config("open.cooldown-ms", 0);
        rightClickAir();
        assertNotNull(session());
    }

    @Test
    void disabledWorldBlocksUnlessBypassed() throws Exception {
        config("open.disabled-worlds", java.util.List.of(player.getWorld().getName().toUpperCase()));

        rightClickAir();
        assertNull(session());
        assertEquals("MR-Shulkers » You can't open shulkers in this world.", lastMessage());

        player.addAttachment(plugin, "mrshulkers.bypass.world", true);
        rightClickAir();
        assertNotNull(session());
    }

    @Test
    void creativeCanBeDisallowed() throws Exception {
        config("open.allow-creative", false);
        player.setGameMode(GameMode.CREATIVE);

        rightClickAir();
        assertNull(session());
        assertEquals("MR-Shulkers » You can't open shulkers this way in creative mode.", lastMessage());

        player.setGameMode(GameMode.SURVIVAL);
        rightClickAir();
        assertNotNull(session());
    }

    @Test
    void shulkerCommandGoesThroughTheChecks() throws Exception {
        config("open.disabled-worlds", java.util.List.of(player.getWorld().getName()));

        player.performCommand("shulker");
        server.getScheduler().performOneTick();

        assertNull(session());
    }

    // --- title ---

    @Test
    void namedShulkerUsesItsNameAsTitle() {
        ItemStack named = new ItemStack(Material.PURPLE_SHULKER_BOX);
        named.editMeta(meta -> meta.customName(Component.text("Ores")));
        player.getInventory().setItem(0, named);

        rightClickAir();

        assertEquals("Ores", PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()));
    }

    @Test
    void useItemNameOffUsesWindowTitleMessage() throws Exception {
        config("window.use-item-name", false);
        ItemStack named = new ItemStack(Material.PURPLE_SHULKER_BOX);
        named.editMeta(meta -> meta.customName(Component.text("Ores")));
        player.getInventory().setItem(0, named);

        rightClickAir();

        // MockBukkit flattens the translatable default name to its key.
        String title = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
        assertTrue(title.contains("purple_shulker_box"), title);
    }
}
