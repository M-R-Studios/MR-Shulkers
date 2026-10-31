package com.mrstudios.shulkers.rules;

import com.mrstudios.shulkers.config.Settings;
import com.mrstudios.shulkers.hook.Hooks;
import com.mrstudios.shulkers.hook.RegionCheck;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * The checks every open path goes through, in the order of DESIGN.md 3.4: permission, cooldown,
 * world, WorldGuard region, creative. The stack-size check happens when the window is built.
 */
public final class OpenChecks {

    public static final String BYPASS_COOLDOWN = "mrshulkers.bypass.cooldown";
    public static final String BYPASS_WORLD = "mrshulkers.bypass.world";
    public static final String BYPASS_REGION = "mrshulkers.bypass.region";

    private final CooldownTracker cooldowns;
    private final Hooks hooks;

    public OpenChecks(CooldownTracker cooldowns, Hooks hooks) {
        this.cooldowns = cooldowns;
        this.hooks = hooks;
    }

    /** Why {@code player} may not open a shulker this way: a message key and its placeholders. */
    public record Failure(String message, TagResolver... placeholders) {
    }

    /** The first check that fails, or null if the player may open. */
    public Failure check(Player player, OpenPath path, Settings settings) {
        if (!player.hasPermission(path.permission())) {
            return new Failure("no-permission");
        }
        if (!player.hasPermission(BYPASS_COOLDOWN)) {
            long remaining = cooldowns.remainingMs(player.getUniqueId(), settings.cooldownMs());
            if (remaining > 0) {
                return new Failure("cooldown",
                        Placeholder.unparsed("remaining", String.valueOf(CooldownTracker.secondsRoundedUp(remaining))));
            }
        }
        if (settings.isWorldDisabled(player.getWorld().getName()) && !player.hasPermission(BYPASS_WORLD)) {
            return new Failure("disabled-world");
        }
        RegionCheck region = hooks.worldGuard();
        if (region != null && settings.worldGuard() && !player.hasPermission(BYPASS_REGION) && !region.allows(player)) {
            return new Failure("disabled-region");
        }
        if (player.getGameMode() == GameMode.CREATIVE && !settings.allowCreative()) {
            return new Failure("creative-disabled");
        }
        return null;
    }

    /** Called when a window actually opened. */
    public void opened(Player player) {
        cooldowns.start(player.getUniqueId());
    }

    public void forget(Player player) {
        cooldowns.forget(player.getUniqueId());
    }
}
