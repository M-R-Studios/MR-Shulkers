package com.mrstudios.shulkers.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import java.util.logging.Logger;
import org.bukkit.entity.Player;

/**
 * The {@code mrshulkers-open} region flag (default allow). This is the only class that touches
 * WorldGuard types, and it is only loaded when WorldGuard is installed.
 */
public final class WorldGuardHook implements RegionCheck {

    public static final String FLAG_NAME = "mrshulkers-open";

    private final StateFlag flag;

    private WorldGuardHook(StateFlag flag) {
        this.flag = flag;
    }

    /**
     * Registers the flag, or reuses a {@code StateFlag} another plugin already registered under the
     * same name. Must run in {@code onLoad}: WorldGuard locks its flag registry when it enables.
     *
     * @return the hook, or null if the name is taken by a different kind of flag or registration is closed
     */
    static WorldGuardHook register(Logger log) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(FLAG_NAME, true);
            registry.register(flag);
            return new WorldGuardHook(flag);
        } catch (FlagConflictException | IllegalStateException e) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag state) {
                return new WorldGuardHook(state);
            }
            log.warning("WorldGuard hook disabled: could not register the " + FLAG_NAME + " flag (" + e.getMessage() + ").");
            return null;
        }
    }

    @Override
    public boolean allows(Player player) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .testState(BukkitAdapter.adapt(player.getLocation()), WorldGuardPlugin.inst().wrapPlayer(player), flag);
    }
}
