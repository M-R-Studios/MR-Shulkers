package com.mrstudios.shulkers.config;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Every player-facing string from {@code messages.yml}, already validated. Placeholder values are
 * passed as {@link Placeholder#unparsed} so names typed by players are never parsed as tags.
 */
public final class Messages {

    static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Map<String, String> texts;
    private final List<String> help;
    private final TagResolver prefix;

    Messages(Map<String, String> texts, List<String> help) {
        this.texts = Map.copyOf(texts);
        this.help = List.copyOf(help);
        this.prefix = Placeholder.component("prefix", MINI_MESSAGE.deserialize(this.texts.getOrDefault("prefix", "")));
    }

    /** The MiniMessage text of {@code key}, before placeholders are filled in. */
    public String raw(String key) {
        String text = texts.get(key);
        if (text == null) {
            throw new IllegalArgumentException("No message '" + key + "'");
        }
        return text;
    }

    public List<String> help() {
        return help;
    }

    public Component component(String key, TagResolver... placeholders) {
        return render(raw(key), placeholders);
    }

    /** Sends {@code key} to {@code to}. A message set to "" sends nothing. */
    public void send(Audience to, String key, TagResolver... placeholders) {
        String text = raw(key);
        if (!text.isEmpty()) {
            to.sendMessage(render(text, placeholders));
        }
    }

    public void sendHelp(Audience to) {
        for (String line : help) {
            if (!line.isEmpty()) {
                to.sendMessage(render(line));
            }
        }
    }

    private Component render(String text, TagResolver... placeholders) {
        return MINI_MESSAGE.deserialize(text, TagResolver.resolver(prefix, TagResolver.resolver(placeholders)));
    }
}
