package com.mrstudios.shulkers.hook;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Optional integrations, detected once at startup. Each is null when its plugin is missing, so the
 * plugin runs the same without them. Reload doesn't re-detect; changing them needs a restart.
 */
public final class Hooks {

    private static final String WORLDGUARD = "WorldGuard";
    private static final String FLOODGATE = "floodgate";

    private RegionCheck worldGuard;
    private BedrockCheck floodgate;
    private boolean worldGuardAtStartup;
    private boolean floodgateAtStartup;

    public Hooks() {
    }

    /** For tests: hooks with this region check in place of WorldGuard. */
    public Hooks(RegionCheck worldGuard) {
        this(worldGuard, null);
    }

    /** For tests: hooks with these checks in place of WorldGuard and Floodgate (either may be null). */
    public Hooks(RegionCheck worldGuard, BedrockCheck floodgate) {
        this.worldGuard = worldGuard;
        this.floodgate = floodgate;
    }

    /** {@code onLoad}: registers the WorldGuard flag if WorldGuard is installed. */
    public void load(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin(WORLDGUARD) == null) {
            return;
        }
        try {
            worldGuard = WorldGuardHook.register(plugin.getLogger());
        } catch (LinkageError | RuntimeException e) {
            plugin.getLogger().warning("WorldGuard hook disabled: " + e);
            worldGuard = null;
        }
    }

    /**
     * {@code onEnable}: drops the WorldGuard hook if WorldGuard itself failed to enable, and hooks
     * Floodgate if it is enabled (softdepend enables it first).
     */
    public void enable(Plugin plugin) {
        PluginManager plugins = plugin.getServer().getPluginManager();
        worldGuardAtStartup = plugins.isPluginEnabled(WORLDGUARD);
        floodgateAtStartup = plugins.isPluginEnabled(FLOODGATE);
        if (worldGuard != null && !worldGuardAtStartup) {
            plugin.getLogger().warning("WorldGuard is installed but not enabled; region checks are off.");
            worldGuard = null;
        }
        if (worldGuard != null) {
            plugin.getLogger().info("Hooked into WorldGuard (flag " + WorldGuardHook.FLAG_NAME + ").");
        }
        if (floodgateAtStartup) {
            floodgate = FloodgateHook.create(plugins.getPlugin(FLOODGATE), plugin.getLogger());
            if (floodgate != null) {
                plugin.getLogger().info("Hooked into Floodgate (Bedrock player detection).");
            }
        }
    }

    /** The WorldGuard region check, or null when WorldGuard isn't hooked. */
    public RegionCheck worldGuard() {
        return worldGuard;
    }

    /** Whether Floodgate is hooked and says {@code player} is on Bedrock. False without Floodgate. */
    public boolean isBedrock(Player player) {
        return floodgate != null && floodgate.isBedrock(player);
    }

    /** Active hooks for {@code /mrshulkers version}, e.g. "WorldGuard, Floodgate", or "none". */
    public String describe() {
        List<String> active = new ArrayList<>();
        if (worldGuard != null) {
            active.add(WORLDGUARD);
        }
        if (floodgate != null) {
            active.add("Floodgate");
        }
        return active.isEmpty() ? "none" : String.join(", ", active);
    }

    /** Hook plugins that were added or removed since startup, which only a restart picks up. */
    public List<String> changedSinceStartup(PluginManager plugins) {
        List<String> changed = new ArrayList<>();
        if (plugins.isPluginEnabled(WORLDGUARD) != worldGuardAtStartup) {
            changed.add(WORLDGUARD);
        }
        if (plugins.isPluginEnabled(FLOODGATE) != floodgateAtStartup) {
            changed.add("Floodgate");
        }
        return changed;
    }
}
