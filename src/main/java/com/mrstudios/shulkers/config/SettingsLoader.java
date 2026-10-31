package com.mrstudios.shulkers.config;

import com.mrstudios.shulkers.config.Settings.OpenClick;
import com.mrstudios.shulkers.config.Settings.SoundSetting;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Builds {@link Settings} from {@code config.yml}. Every invalid or missing value falls back to its
 * default with a warning that names the key, the bad value and what is accepted.
 */
public final class SettingsLoader {

    private static final List<String> KNOWN_KEYS = List.of(
            "config-version",
            "open.inventory.enabled",
            "open.inventory.click",
            "open.hand.enabled",
            "open.hand.require-sneak",
            "open.ender-chest.enabled",
            "open.ender-chest.return-on-close",
            "open.allow-creative",
            "open.cooldown-ms",
            "open.disabled-worlds",
            "open.worldguard",
            "bedrock.floodgate",
            "bedrock.skip-sneak-requirement",
            "window.use-item-name",
            "sounds.open.enabled",
            "sounds.open.sound",
            "sounds.open.volume",
            "sounds.open.pitch",
            "sounds.close.enabled",
            "sounds.close.sound",
            "sounds.close.volume",
            "sounds.close.pitch",
            "safety.watchdog-interval-ticks",
            "safety.log-blocked-actions");

    private final Predicate<NamespacedKey> soundExists;
    private final ConfigMigrator migrator;

    /** @param soundExists whether a key names a sound in the server's sound registry */
    public SettingsLoader(Predicate<NamespacedKey> soundExists) {
        this(soundExists, ConfigMigrator.forConfig());
    }

    SettingsLoader(Predicate<NamespacedKey> soundExists, ConfigMigrator migrator) {
        this.soundExists = soundExists;
        this.migrator = migrator;
    }

    /** Reads {@code file}, migrating it on disk first if it is from an older version. */
    public LoadResult<Settings> load(File file, Consumer<String> log) {
        try {
            return read(YamlFiles.load(file, migrator, log));
        } catch (ConfigException e) {
            return LoadResult.failed(e.getMessage());
        }
    }

    /** Parses and validates YAML text. Migrations are applied in memory only. */
    public LoadResult<Settings> load(String text) {
        try {
            YamlConfiguration yaml = YamlFiles.parse(text, migrator.fileName());
            migrator.migrate(yaml);
            return read(yaml);
        } catch (ConfigException e) {
            return LoadResult.failed(e.getMessage());
        }
    }

    LoadResult<Settings> read(YamlConfiguration yaml) {
        Reader r = new Reader(yaml);
        Settings d = Settings.DEFAULTS;
        Settings settings = new Settings(
                r.bool("open.inventory.enabled", d.inventoryOpenEnabled()),
                r.openClick("open.inventory.click", d.inventoryClick()),
                r.bool("open.hand.enabled", d.handOpenEnabled()),
                r.bool("open.hand.require-sneak", d.handRequireSneak()),
                r.bool("open.ender-chest.enabled", d.enderChestOpenEnabled()),
                r.bool("open.ender-chest.return-on-close", d.enderChestReturnOnClose()),
                r.bool("open.allow-creative", d.allowCreative()),
                r.wholeNumber("open.cooldown-ms", 0, Long.MAX_VALUE, d.cooldownMs()),
                r.worlds("open.disabled-worlds", d.disabledWorlds()),
                r.bool("open.worldguard", d.worldGuard()),
                r.bool("bedrock.floodgate", d.floodgate()),
                r.bool("bedrock.skip-sneak-requirement", d.bedrockSkipSneak()),
                r.bool("window.use-item-name", d.windowUseItemName()),
                r.sound("sounds.open", d.openSound()),
                r.sound("sounds.close", d.closeSound()),
                (int) r.wholeNumber("safety.watchdog-interval-ticks", 1, 20, d.watchdogIntervalTicks()),
                r.bool("safety.log-blocked-actions", d.logBlockedActions()));
        r.warnUnknownKeys();
        return LoadResult.loaded(settings, r.warnings);
    }

    private final class Reader {

        private final YamlConfiguration yaml;
        private final List<String> warnings = new ArrayList<>();

        Reader(YamlConfiguration yaml) {
            this.yaml = yaml;
        }

        private Object value(String path, Object def) {
            Object value = yaml.get(path);
            if (value == null) {
                warnings.add(path + " is missing, using " + def);
            }
            return value;
        }

        private void invalid(String path, Object value, String accepted, Object def) {
            warnings.add(path + ": '" + value + "' is not " + accepted + ", using " + def);
        }

        boolean bool(String path, boolean def) {
            Object value = value(path, def);
            if (value == null) {
                return def;
            }
            if (value instanceof Boolean b) {
                return b;
            }
            invalid(path, value, "true or false", def);
            return def;
        }

        OpenClick openClick(String path, OpenClick def) {
            Object value = value(path, def);
            if (value == null) {
                return def;
            }
            if (value instanceof String s) {
                for (OpenClick click : OpenClick.values()) {
                    if (click.name().equalsIgnoreCase(s.strip())) {
                        return click;
                    }
                }
            }
            invalid(path, value, "RIGHT or SHIFT_RIGHT", def);
            return def;
        }

        long wholeNumber(String path, long min, long max, long def) {
            Object value = value(path, def);
            if (value == null) {
                return def;
            }
            if (value instanceof Integer || value instanceof Long) {
                long n = ((Number) value).longValue();
                if (n >= min && n <= max) {
                    return n;
                }
            }
            String accepted = max == Long.MAX_VALUE
                    ? "a whole number of at least " + min
                    : "a whole number from " + min + " to " + max;
            invalid(path, value, accepted, def);
            return def;
        }

        float decimal(String path, double min, double max, float def) {
            Object value = value(path, def);
            if (value == null) {
                return def;
            }
            if (value instanceof Number n) {
                double d = n.doubleValue();
                if (d >= min && d <= max) {
                    return (float) d;
                }
            }
            invalid(path, value, "a number from " + min + " to " + max, def);
            return def;
        }

        Set<String> worlds(String path, Set<String> def) {
            Object value = value(path, "[]");
            if (value == null) {
                return def;
            }
            if (!(value instanceof List<?> list)) {
                invalid(path, value, "a list of world names", "[]");
                return def;
            }
            Set<String> worlds = new HashSet<>();
            for (Object entry : list) {
                if (entry == null || entry instanceof Map<?, ?> || entry instanceof List<?>) {
                    warnings.add(path + ": '" + entry + "' is not a world name, skipped");
                    continue;
                }
                worlds.add(entry.toString().strip().toLowerCase(Locale.ROOT));
            }
            return worlds;
        }

        SoundSetting sound(String section, SoundSetting def) {
            boolean enabled = bool(section + ".enabled", def.enabled());
            NamespacedKey key = soundKey(section + ".sound", def.sound());
            float volume = decimal(section + ".volume", 0.0, 10.0, def.volume());
            float pitch = decimal(section + ".pitch", 0.5, 2.0, def.pitch());
            return new SoundSetting(enabled, key, volume, pitch);
        }

        private NamespacedKey soundKey(String path, NamespacedKey def) {
            Object value = value(path, def);
            if (value == null) {
                return def;
            }
            NamespacedKey key = value instanceof String s ? NamespacedKey.fromString(s.strip()) : null;
            if (key == null) {
                invalid(path, value, "a sound key like minecraft:block.shulker_box.open", def);
                return def;
            }
            if (!soundExists.test(key)) {
                invalid(path, value, "a known sound", def);
                return def;
            }
            return key;
        }

        void warnUnknownKeys() {
            List<String> unknown = new ArrayList<>();
            for (String path : yaml.getKeys(true)) {
                boolean known = KNOWN_KEYS.stream().anyMatch(k -> k.equals(path) || k.startsWith(path + "."));
                boolean underUnknown = unknown.stream().anyMatch(u -> path.startsWith(u + "."));
                if (!known && !underUnknown) {
                    unknown.add(path);
                    warnings.add(path + " is not a known setting (typo?), ignored");
                }
            }
        }
    }
}
