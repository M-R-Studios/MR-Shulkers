package com.mrstudios.shulkers.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrstudios.shulkers.MRShulkers;
import com.mrstudios.shulkers.listener.OpenListener;
import com.mrstudios.shulkers.session.SessionService;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Bedrock detection: the sneak skip for hand open, hook status, and running without Floodgate. */
class FloodgateHookTest {

    private ServerMock server;
    private MRShulkers plugin;
    private SessionService sessions;
    private PlayerMock bedrock;
    private PlayerMock java;
    private OpenListener listener;

    @BeforeEach
    void start() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MRShulkers.class);
        sessions = plugin.sessions();
        bedrock = server.addPlayer("BedrockGuy");
        java = server.addPlayer("JavaGuy");
        for (PlayerMock p : new PlayerMock[] {bedrock, java}) {
            p.getInventory().setItem(0, new ItemStack(Material.PURPLE_SHULKER_BOX));
            p.getInventory().setHeldItemSlot(0);
        }
        // The plugin's own listener sees no Floodgate; this one sees BedrockGuy as a Bedrock player.
        listener = new OpenListener(plugin, sessions, new Hooks(null, p -> p.equals(bedrock)));
        config("open.hand.require-sneak", true);
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

    private boolean rightClickAir(Player player) {
        listener.onRightClickAir(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                player.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND));
        server.getScheduler().performOneTick();
        return sessions.registry().byPlayer(player.getUniqueId()).isPresent();
    }

    @Test
    void bedrockPlayerSkipsTheSneakRequirement() {
        assertTrue(rightClickAir(bedrock));
    }

    @Test
    void javaPlayerStillHasToSneak() {
        assertFalse(rightClickAir(java));

        java.setSneaking(true);
        assertTrue(rightClickAir(java));
    }

    @Test
    void skipSneakOffMakesBedrockSneakToo() throws Exception {
        config("bedrock.skip-sneak-requirement", false);

        assertFalse(rightClickAir(bedrock));
    }

    @Test
    void floodgateSettingOffTreatsBedrockAsJava() throws Exception {
        config("bedrock.floodgate", false);

        assertFalse(rightClickAir(bedrock));
    }

    @Test
    void withoutRequireSneakEveryoneOpens() throws Exception {
        config("open.hand.require-sneak", false);

        assertTrue(rightClickAir(java));
        assertTrue(rightClickAir(bedrock));
    }

    @Test
    void describeListsActiveHooks() {
        assertEquals("Floodgate", new Hooks(null, p -> true).describe());
        assertEquals("WorldGuard, Floodgate", new Hooks(p -> true, p -> true).describe());
        assertEquals("none", new Hooks().describe());
    }

    @Test
    void withoutFloodgateNobodyIsBedrock() {
        assertFalse(new Hooks().isBedrock(bedrock));
        assertFalse(plugin.hooks().isBedrock(bedrock));
        assertEquals("none", plugin.hooks().describe());
    }

    @Test
    void floodgateHookIsNullWhenTheApiIsMissing() {
        Logger log = Logger.getLogger("test");

        FloodgateHook hook = FloodgateHook.createFor("org.geysermc.floodgate.api.FloodgateApi",
                getClass().getClassLoader(), log);

        assertNull(hook);
    }

    @Test
    void floodgateHookCallsTheApiByReflection() {
        FakeFloodgate.bedrock = bedrock.getUniqueId();
        FloodgateHook hook = FloodgateHook.createFor(FakeFloodgate.class.getName(), getClass().getClassLoader(),
                Logger.getLogger("test"));

        assertNotNull(hook);
        assertTrue(hook.isBedrock(bedrock));
        assertFalse(hook.isBedrock(java));
    }

    /** Stands in for org.geysermc.floodgate.api.FloodgateApi: same two methods. */
    public static final class FakeFloodgate {
        static java.util.UUID bedrock;
        private static final FakeFloodgate INSTANCE = new FakeFloodgate();

        public static FakeFloodgate getInstance() {
            return INSTANCE;
        }

        public boolean isFloodgatePlayer(java.util.UUID id) {
            return id.equals(bedrock);
        }
    }
}
