package com.mrstudios.shulkers.session;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Checks every open session on a timer (DESIGN.md 3.5 #30). A backup for item changes made by
 * other plugins that no event reports, such as a direct {@code setItem} on the ender chest.
 */
public final class Watchdog {

    private final Plugin plugin;
    private final SessionService sessions;
    private BukkitTask task;

    public Watchdog(Plugin plugin, SessionService sessions) {
        this.plugin = plugin;
        this.sessions = sessions;
    }

    /** Starts the timer, or restarts it with a new interval. */
    public void start(int intervalTicks) {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, sessions::verifyAll, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
