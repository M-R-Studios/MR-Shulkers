package com.mrstudios.shulkers.config;

import java.util.Locale;
import java.util.Set;
import org.bukkit.NamespacedKey;

/** Everything in {@code config.yml}, validated. Immutable; a reload builds a new one. */
public record Settings(
        boolean inventoryOpenEnabled,
        OpenClick inventoryClick,
        boolean handOpenEnabled,
        boolean handRequireSneak,
        boolean enderChestOpenEnabled,
        boolean enderChestReturnOnClose,
        boolean allowCreative,
        long cooldownMs,
        Set<String> disabledWorlds,
        boolean worldGuard,
        boolean floodgate,
        boolean bedrockSkipSneak,
        boolean windowUseItemName,
        SoundSetting openSound,
        SoundSetting closeSound,
        int watchdogIntervalTicks,
        boolean logBlockedActions) {

    /** Must match the bundled {@code config.yml}; {@code SettingsLoaderTest} checks that. */
    public static final Settings DEFAULTS = new Settings(
            true, OpenClick.RIGHT,
            true, false,
            true, true,
            true,
            500,
            Set.of(),
            true,
            true, true,
            true,
            new SoundSetting(true, NamespacedKey.minecraft("block.shulker_box.open"), 1.0f, 1.0f),
            new SoundSetting(true, NamespacedKey.minecraft("block.shulker_box.close"), 1.0f, 1.0f),
            5,
            false);

    /** @param disabledWorlds world names, lower case */
    public Settings {
        disabledWorlds = Set.copyOf(disabledWorlds);
    }

    public boolean isWorldDisabled(String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    /** The click in your own inventory screen that opens a shulker. */
    public enum OpenClick {
        RIGHT,
        SHIFT_RIGHT
    }

    public record SoundSetting(boolean enabled, NamespacedKey sound, float volume, float pitch) {
    }
}
