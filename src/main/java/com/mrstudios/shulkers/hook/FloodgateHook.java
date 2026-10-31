package com.mrstudios.shulkers.hook;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Detects Bedrock players through Floodgate's {@code FloodgateApi.getInstance().isFloodgatePlayer(UUID)}.
 * Called by reflection: Floodgate only publishes {@code -SNAPSHOT} API builds, and this one stable
 * method is all we need, so the build doesn't depend on a moving snapshot.
 */
final class FloodgateHook implements BedrockCheck {

    private static final String API_CLASS = "org.geysermc.floodgate.api.FloodgateApi";

    private final Object api;
    private final Method isFloodgatePlayer;
    private final Logger log;
    private boolean warned;

    private FloodgateHook(Object api, Method isFloodgatePlayer, Logger log) {
        this.api = api;
        this.isFloodgatePlayer = isFloodgatePlayer;
        this.log = log;
    }

    /** @return the hook, or null if Floodgate's API isn't there or doesn't look as expected */
    static FloodgateHook create(Plugin floodgate, Logger log) {
        return createFor(API_CLASS, floodgate.getClass().getClassLoader(), log);
    }

    /** {@link #create} with the API class named explicitly, so tests can use a stand-in. */
    static FloodgateHook createFor(String apiClass, ClassLoader loader, Logger log) {
        try {
            Class<?> type = Class.forName(apiClass, true, loader);
            Object api = type.getMethod("getInstance").invoke(null);
            Method method = type.getMethod("isFloodgatePlayer", UUID.class);
            if (api == null || method.getReturnType() != boolean.class) {
                log.warning("Floodgate hook disabled: unexpected FloodgateApi.");
                return null;
            }
            return new FloodgateHook(api, method, log);
        } catch (ReflectiveOperationException | LinkageError e) {
            log.warning("Floodgate hook disabled: " + e);
            return null;
        }
    }

    @Override
    public boolean isBedrock(Player player) {
        try {
            return (boolean) isFloodgatePlayer.invoke(api, player.getUniqueId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                log.log(Level.WARNING, "Floodgate check failed; treating players as Java players", e);
            }
            return false;
        }
    }
}
