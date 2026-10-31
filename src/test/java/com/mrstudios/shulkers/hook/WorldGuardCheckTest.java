package com.mrstudios.shulkers.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.config.Settings;
import com.mrstudios.shulkers.rules.CooldownTracker;
import com.mrstudios.shulkers.rules.OpenChecks;
import com.mrstudios.shulkers.rules.OpenPath;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The region check in OpenChecks, and the plugin without WorldGuard installed. */
class WorldGuardCheckTest {

    private ServerMock server;
    private MRShulkers plugin;
    private PlayerMock player;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MRShulkers.class);
        player = server.addPlayer();
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private static Settings withWorldGuard(boolean on) {
        Settings d = Settings.DEFAULTS;
        return new Settings(d.inventoryOpenEnabled(), d.inventoryClick(), d.handOpenEnabled(), d.handRequireSneak(),
                d.enderChestOpenEnabled(), d.enderChestReturnOnClose(), d.allowCreative(), d.cooldownMs(),
                d.disabledWorlds(), on, d.floodgate(), d.bedrockSkipSneak(), d.windowUseItemName(),
                d.openSound(), d.closeSound(), d.watchdogIntervalTicks(), d.logBlockedActions());
    }

    private static OpenChecks checks(RegionCheck region) {
        return new OpenChecks(new CooldownTracker(), new Hooks(region));
    }

    @Test
    void regionDenyRefusesWithTheRegionMessage() {
        OpenChecks.Failure failure = checks(p -> false).check(player, OpenPath.HAND, Settings.DEFAULTS);

        assertEquals("disabled-region", failure.message());
    }

    @Test
    void regionAllowOpens() {
        assertNull(checks(p -> true).check(player, OpenPath.HAND, Settings.DEFAULTS));
    }

    @Test
    void bypassPermissionSkipsTheRegionCheck() {
        player.addAttachment(plugin, OpenChecks.BYPASS_REGION, true);
        AtomicInteger asked = new AtomicInteger();

        assertNull(checks(p -> {
            asked.incrementAndGet();
            return false;
        }).check(player, OpenPath.HAND, Settings.DEFAULTS));
        assertEquals(0, asked.get());
    }

    @Test
    void worldguardSettingOffSkipsTheRegionCheck() {
        assertNull(checks(p -> false).check(player, OpenPath.HAND, withWorldGuard(false)));
    }

    @Test
    void withoutWorldGuardThereIsNoRegionCheck() {
        assertNull(new OpenChecks(new CooldownTracker(), new Hooks()).check(player, OpenPath.HAND, Settings.DEFAULTS));
    }

    @Test
    void disabledWorldIsCheckedBeforeTheRegion() throws Exception {
        Settings d = Settings.DEFAULTS;
        Settings disabled = new Settings(d.inventoryOpenEnabled(), d.inventoryClick(), d.handOpenEnabled(),
                d.handRequireSneak(), d.enderChestOpenEnabled(), d.enderChestReturnOnClose(), d.allowCreative(),
                d.cooldownMs(), java.util.Set.of(player.getWorld().getName().toLowerCase()), true, d.floodgate(),
                d.bedrockSkipSneak(), d.windowUseItemName(), d.openSound(), d.closeSound(), d.watchdogIntervalTicks(),
                d.logBlockedActions());

        assertEquals("disabled-world", checks(p -> false).check(player, OpenPath.HAND, disabled).message());
    }

    @Test
    void pluginLoadsWithoutWorldGuardAndReportsNoHooks() {
        assertEquals("none", plugin.hooks().describe());
        assertNull(plugin.hooks().worldGuard());
        assertTrue(plugin.hooks().changedSinceStartup(server.getPluginManager()).isEmpty());

        player.setOp(true);
        player.performCommand("mrshulkers version");
        Component message = player.nextComponentMessage();
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        assertTrue(text.endsWith("Hooks: none"), text);
    }

    @Test
    void describeListsWorldGuardWhenHooked() {
        assertEquals("WorldGuard", new Hooks(p -> true).describe());
    }
}
