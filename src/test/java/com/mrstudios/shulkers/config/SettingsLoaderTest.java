package com.mrstudios.shulkers.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrstudios.shulkers.config.Settings.OpenClick;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SettingsLoaderTest {

    private static final Set<NamespacedKey> SOUNDS = Set.of(
            NamespacedKey.minecraft("block.shulker_box.open"),
            NamespacedKey.minecraft("block.shulker_box.close"),
            NamespacedKey.minecraft("entity.player.levelup"));

    private final SettingsLoader loader = new SettingsLoader(SOUNDS::contains);

    static String bundledConfig() throws IOException {
        try (InputStream in = SettingsLoaderTest.class.getResourceAsStream("/config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String bundledWith(String path, Object value) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(bundledConfig());
        yaml.set(path, value);
        return yaml.saveToString();
    }

    @Test
    void bundledConfigLoadsWithoutWarningsAndMatchesDefaults() throws IOException {
        LoadResult<Settings> result = loader.load(bundledConfig());

        assertTrue(result.ok(), result.error());
        assertEquals(List.of(), result.warnings());
        assertEquals(Settings.DEFAULTS, result.value());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "open.inventory.click           | MIDDLE           | RIGHT or SHIFT_RIGHT",
        "open.inventory.enabled         | maybe            | true or false",
        "open.hand.require-sneak        | 3                | true or false",
        "open.cooldown-ms               | -1               | at least 0",
        "open.cooldown-ms               | 1.5              | at least 0",
        "open.cooldown-ms               | soon             | at least 0",
        "open.disabled-worlds           | world            | a list of world names",
        "sounds.open.sound              | not a key!       | a sound key",
        "sounds.close.sound             | minecraft:nope   | a known sound",
        "sounds.open.volume             | 11               | 0.0 to 10.0",
        "sounds.close.pitch             | 0.1              | 0.5 to 2.0",
        "sounds.close.pitch             | high             | 0.5 to 2.0",
        "safety.watchdog-interval-ticks | 0                | 1 to 20",
        "safety.watchdog-interval-ticks | 21               | 1 to 20",
    })
    void invalidValueFallsBackToDefaultWithWarning(String path, String bad, String accepted) throws Exception {
        Object value = parseScalar(bad);
        LoadResult<Settings> result = loader.load(bundledWith(path, value));

        assertTrue(result.ok(), result.error());
        assertEquals(Settings.DEFAULTS, result.value());
        assertEquals(1, result.warnings().size(), result.warnings().toString());
        String warning = result.warnings().get(0);
        assertTrue(warning.startsWith(path + ": '" + value + "'"), warning);
        assertTrue(warning.contains(accepted), warning);
        assertTrue(warning.contains("using "), warning);
    }

    /** Lets the CSV rows above produce YAML numbers as well as strings. */
    private static Object parseScalar(String s) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("v: " + (s.contains("!") ? "'" + s + "'" : s));
        return yaml.get("v");
    }

    @Test
    void validNonDefaultValuesAreRead() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(bundledConfig());
        yaml.set("open.inventory.click", "shift_right");
        yaml.set("open.cooldown-ms", 0);
        yaml.set("open.disabled-worlds", List.of("World_Nether", "Spawn"));
        yaml.set("sounds.open.sound", "entity.player.levelup");
        yaml.set("sounds.open.volume", 2);
        yaml.set("safety.watchdog-interval-ticks", 20);

        LoadResult<Settings> result = loader.load(yaml.saveToString());

        assertEquals(List.of(), result.warnings());
        Settings settings = result.value();
        assertEquals(OpenClick.SHIFT_RIGHT, settings.inventoryClick());
        assertEquals(0, settings.cooldownMs());
        assertEquals(Set.of("world_nether", "spawn"), settings.disabledWorlds());
        assertTrue(settings.isWorldDisabled("WORLD_NETHER"));
        assertFalse(settings.isWorldDisabled("world"));
        assertEquals(NamespacedKey.minecraft("entity.player.levelup"), settings.openSound().sound());
        assertEquals(2.0f, settings.openSound().volume());
        assertEquals(20, settings.watchdogIntervalTicks());
    }

    @Test
    void unknownKeysWarnOncePerUnknownSection() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(bundledConfig());
        yaml.set("open.cooldwn-ms", 100);
        yaml.set("extra.a", 1);
        yaml.set("extra.b", 2);

        LoadResult<Settings> result = loader.load(yaml.saveToString());

        assertTrue(result.ok());
        assertEquals(Settings.DEFAULTS, result.value());
        assertEquals(List.of(
                "open.cooldwn-ms is not a known setting (typo?), ignored",
                "extra is not a known setting (typo?), ignored"), result.warnings());
    }

    @Test
    void missingKeyFallsBackWithWarning() throws Exception {
        LoadResult<Settings> result = loader.load(bundledWith("open.cooldown-ms", null));

        assertEquals(Settings.DEFAULTS, result.value());
        assertEquals(List.of("open.cooldown-ms is missing, using 500"), result.warnings());
    }

    @Test
    void yamlSyntaxErrorFailsWithLineNumber() {
        LoadResult<Settings> result = loader.load("config-version: 1\nopen:\n  cooldown-ms: 5\n bad: x: y\n");

        assertFalse(result.ok());
        assertNull(result.value());
        assertTrue(result.error().startsWith("config.yml line 4, column 2: "), result.error());
    }

    @Test
    void newerConfigVersionFails() throws Exception {
        LoadResult<Settings> result = loader.load(bundledWith("config-version", 99));

        assertFalse(result.ok());
        assertTrue(result.error().contains("newer MR-Shulkers"), result.error());
    }
}
