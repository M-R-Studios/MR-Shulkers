package com.mrstudios.shulkers.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesLoaderTest {

    private final YamlConfiguration bundled = MessagesLoader.bundledDefaults();
    private final MessagesLoader loader = new MessagesLoader(bundled);

    private String bundledWith(String key, Object value) {
        YamlConfiguration yaml = new YamlConfiguration();
        bundled.getValues(false).forEach(yaml::set);
        yaml.set(key, value);
        return yaml.saveToString();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void bundledMessagesLoadWithoutWarnings() {
        LoadResult<Messages> result = loader.load(bundled.saveToString());

        assertTrue(result.ok(), result.error());
        assertEquals(List.of(), result.warnings());
        assertEquals(2, result.value().help().size());
    }

    @Test
    void missingKeyFallsBackWithWarning() {
        LoadResult<Messages> result = loader.load(bundledWith("cooldown", null));

        assertEquals(List.of("cooldown is missing, using the default"), result.warnings());
        assertEquals(bundled.getString("cooldown"), result.value().raw("cooldown"));
    }

    @Test
    void customMessageIsUsedAndPlaceholdersAreNotParsed() {
        LoadResult<Messages> result = loader.load(bundledWith("cooldown", "<prefix>Wait <remaining> seconds"));

        assertEquals(List.of(), result.warnings());
        Component message = result.value().component("cooldown", Placeholder.unparsed("remaining", "<red>2"));
        assertEquals("MR-Shulkers » Wait <red>2 seconds", plain(message));
    }

    @Test
    void unknownTagFallsBackWithWarningNamingTheKey() {
        LoadResult<Messages> result = loader.load(bundledWith("no-permission", "<prefix><bogus>Nope"));

        assertEquals(1, result.warnings().size());
        String warning = result.warnings().get(0);
        assertTrue(warning.startsWith("no-permission: <bogus> is not a valid tag"), warning);
        assertEquals(bundled.getString("no-permission"), result.value().raw("no-permission"));
    }

    @Test
    void malformedTagFallsBackWithWarning() {
        LoadResult<Messages> result = loader.load(bundledWith("disabled-world", "<gradient:nope:nope>No"));

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("disabled-world: <gradient:nope:nope>"), result.warnings().get(0));
        assertEquals(bundled.getString("disabled-world"), result.value().raw("disabled-world"));
    }

    @Test
    void placeholderFromAnotherMessageIsRejected() {
        LoadResult<Messages> result = loader.load(bundledWith("no-permission", "<red>Wait <remaining>s"));

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("no-permission: <remaining>"), result.warnings().get(0));
    }

    @Test
    void emptyMessageIsAllowed() {
        LoadResult<Messages> result = loader.load(bundledWith("reload-success", ""));

        assertEquals(List.of(), result.warnings());
        assertEquals("", result.value().raw("reload-success"));
    }

    @Test
    void nonTextAndUnknownKeysWarn() {
        YamlConfiguration yaml = new YamlConfiguration();
        bundled.getValues(false).forEach(yaml::set);
        yaml.set("player-only", 5);
        yaml.set("no-permision", "typo");

        LoadResult<Messages> result = loader.load(yaml.saveToString());

        assertEquals(List.of(
                "player-only: '5' is not text, using the default",
                "no-permision is not a known message (typo?), ignored"), result.warnings());
    }

    @Test
    void badHelpLineFallsBackToDefaultHelp() {
        LoadResult<Messages> result = loader.load(bundledWith("help", List.of("<prefix>ok", "<nope>")));

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("help line 2: "), result.warnings().get(0));
        assertEquals(bundled.getStringList("help"), result.value().help());
    }

    @Test
    void yamlSyntaxErrorFails() {
        LoadResult<Messages> result = loader.load("prefix: \"unclosed\n");

        assertFalse(result.ok());
        assertTrue(result.error().startsWith("messages.yml line "), result.error());
    }

    @Test
    void prefixCannotReferToItself() {
        LoadResult<Messages> result = loader.load(bundledWith("prefix", "<prefix> "));

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).startsWith("prefix: <prefix>"), result.warnings().get(0));
    }
}
