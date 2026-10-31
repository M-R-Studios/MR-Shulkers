package com.mrstudios.shulkers.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Builds {@link Messages} from {@code messages.yml}. A missing key, or a message with a tag
 * MiniMessage can't use, falls back to the text bundled in the jar with a warning naming the key.
 */
public final class MessagesLoader {

    private static final String VERSION_KEY = "messages-version";
    private static final String HELP_KEY = "help";

    /** Placeholders each message may use besides {@code <prefix>} and the standard MiniMessage tags. */
    private static final Map<String, Set<String>> PLACEHOLDERS = Map.of(
            "window-title", Set.of("name"),
            "cooldown", Set.of("remaining"),
            "version", Set.of("version", "server", "hooks"),
            "reload-warnings", Set.of("count"),
            "reload-failed", Set.of("file"),
            "reload-detail", Set.of("detail"));

    /** MiniMessage leaves tags it can't resolve in the output as plain text; this finds them. */
    private static final Pattern LEFTOVER_TAG = Pattern.compile("</?!?[#a-zA-Z_][^<>\\n]*>");

    private final YamlConfiguration defaults;
    private final ConfigMigrator migrator = ConfigMigrator.forMessages();

    /** @param defaults the {@code messages.yml} bundled in the jar */
    public MessagesLoader(YamlConfiguration defaults) {
        this.defaults = defaults;
    }

    public static YamlConfiguration bundledDefaults() {
        try (InputStream in = MessagesLoader.class.getResourceAsStream("/messages.yml")) {
            if (in == null) {
                throw new IllegalStateException("messages.yml is missing from the jar");
            }
            return YamlFiles.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "bundled messages.yml");
        } catch (IOException | ConfigException e) {
            throw new IllegalStateException("Could not read the bundled messages.yml", e);
        }
    }

    /** The bundled messages, used when {@code messages.yml} can't be loaded at startup. */
    public Messages defaults() {
        return read(defaults).value();
    }

    /** Reads {@code file}, migrating it on disk first if it is from an older version. */
    public LoadResult<Messages> load(File file, Consumer<String> log) {
        try {
            return read(YamlFiles.load(file, migrator, log));
        } catch (ConfigException e) {
            return LoadResult.failed(e.getMessage());
        }
    }

    /** Parses and validates YAML text. Migrations are applied in memory only. */
    public LoadResult<Messages> load(String text) {
        try {
            YamlConfiguration yaml = YamlFiles.parse(text, migrator.fileName());
            migrator.migrate(yaml);
            return read(yaml);
        } catch (ConfigException e) {
            return LoadResult.failed(e.getMessage());
        }
    }

    LoadResult<Messages> read(YamlConfiguration yaml) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> texts = new HashMap<>();
        List<String> help = readHelp(yaml, warnings);
        for (String key : defaults.getKeys(false)) {
            if (key.equals(VERSION_KEY) || key.equals(HELP_KEY)) {
                continue;
            }
            String def = defaults.getString(key);
            Object value = yaml.get(key);
            if (value == null) {
                warnings.add(key + " is missing, using the default");
                texts.put(key, def);
            } else if (!(value instanceof String text)) {
                warnings.add(key + ": '" + value + "' is not text, using the default");
                texts.put(key, def);
            } else {
                String problem = problem(key, text);
                if (problem != null) {
                    warnings.add(key + ": " + problem + ", using the default");
                    texts.put(key, def);
                } else {
                    texts.put(key, text);
                }
            }
        }
        for (String key : yaml.getKeys(false)) {
            if (!defaults.contains(key)) {
                warnings.add(key + " is not a known message (typo?), ignored");
            }
        }
        return LoadResult.loaded(new Messages(texts, help), warnings);
    }

    private List<String> readHelp(YamlConfiguration yaml, List<String> warnings) {
        List<String> def = defaults.getStringList(HELP_KEY);
        Object value = yaml.get(HELP_KEY);
        if (value == null) {
            warnings.add(HELP_KEY + " is missing, using the default");
            return def;
        }
        if (!(value instanceof List<?> list)) {
            warnings.add(HELP_KEY + " is not a list of lines, using the default");
            return def;
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String problem = list.get(i) instanceof String line ? problem(HELP_KEY, line) : "is not text";
            if (problem != null) {
                warnings.add(HELP_KEY + " line " + (i + 1) + ": " + problem + ", using the default help");
                return def;
            }
            lines.add((String) list.get(i));
        }
        return lines;
    }

    /** Why {@code text} can't be used for {@code key}, or null if it's fine. */
    private static String problem(String key, String text) {
        List<TagResolver> resolvers = new ArrayList<>();
        if (!key.equals("prefix")) {
            resolvers.add(Placeholder.unparsed("prefix", ""));
        }
        for (String name : PLACEHOLDERS.getOrDefault(key, Set.of())) {
            resolvers.add(Placeholder.unparsed(name, ""));
        }
        String plain;
        try {
            plain = PlainTextComponentSerializer.plainText()
                    .serialize(Messages.MINI_MESSAGE.deserialize(text, TagResolver.resolver(resolvers)));
        } catch (RuntimeException e) {
            return "is not valid MiniMessage (" + e.getMessage() + ")";
        }
        Matcher leftover = LEFTOVER_TAG.matcher(plain);
        if (leftover.find()) {
            return leftover.group() + " is not a valid tag in this message";
        }
        return null;
    }
}
