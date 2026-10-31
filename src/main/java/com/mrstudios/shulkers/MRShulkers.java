package com.mrstudios.shulkers;

import com.mrstudios.shulkers.command.AdminCommand;
import com.mrstudios.shulkers.command.ShulkerCommand;
import com.mrstudios.shulkers.config.LoadResult;
import com.mrstudios.shulkers.config.Messages;
import com.mrstudios.shulkers.config.MessagesLoader;
import com.mrstudios.shulkers.config.Settings;
import com.mrstudios.shulkers.config.SettingsLoader;
import com.mrstudios.shulkers.hook.Hooks;
import com.mrstudios.shulkers.item.BlockStateShulkerContents;
import com.mrstudios.shulkers.item.ShulkerItems;
import com.mrstudios.shulkers.listener.GuardListener;
import com.mrstudios.shulkers.listener.LifecycleListener;
import com.mrstudios.shulkers.listener.OpenListener;
import com.mrstudios.shulkers.rules.CooldownTracker;
import com.mrstudios.shulkers.rules.OpenChecks;
import com.mrstudios.shulkers.session.SessionService;
import com.mrstudios.shulkers.session.Watchdog;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

// Not final: MockBukkit subclasses the plugin class in tests.
public class MRShulkers extends JavaPlugin {

    /** The active settings and messages. Swapped as a whole on reload. */
    public record State(Settings settings, Messages messages) {
    }

    private volatile State state;
    private SettingsLoader settingsLoader;
    private MessagesLoader messagesLoader;
    private SessionService sessions;
    private final Hooks hooks = new Hooks();
    private Watchdog watchdog;

    @Override
    public void onLoad() {
        // WorldGuard only accepts new flags before it enables; softdepend loads it first.
        hooks.load(this);
    }

    @Override
    public void onEnable() {
        settingsLoader = new SettingsLoader(key -> Registry.SOUNDS.get(key) != null);
        messagesLoader = new MessagesLoader(MessagesLoader.bundledDefaults());

        Files files = loadFiles();
        Settings settings = files.settings.ok() ? files.settings.value() : Settings.DEFAULTS;
        Messages messages = files.messages.ok() ? files.messages.value() : messagesLoader.defaults();
        state = new State(settings, messages);
        for (String line : files.warnings()) {
            getLogger().warning(line);
        }
        if (!files.settings.ok()) {
            getLogger().severe("config.yml could not be loaded, using the built-in defaults: " + files.settings.error());
        }
        if (!files.messages.ok()) {
            getLogger().severe("messages.yml could not be loaded, using the built-in defaults: " + files.messages.error());
        }

        hooks.enable(this);
        sessions = new SessionService(this, new ShulkerItems(this), new BlockStateShulkerContents(),
                new OpenChecks(new CooldownTracker(), hooks));
        getServer().getPluginManager().registerEvents(new GuardListener(this, sessions), this);
        getServer().getPluginManager().registerEvents(new LifecycleListener(this, sessions), this);
        getServer().getPluginManager().registerEvents(new OpenListener(this, sessions, hooks), this);
        watchdog = new Watchdog(this, sessions);
        watchdog.start(state.settings().watchdogIntervalTicks());
        getServer().getOnlinePlayers().forEach(sessions::stripStaleTags);

        PluginCommand adminCommand = Objects.requireNonNull(getCommand("mrshulkers"));
        AdminCommand admin = new AdminCommand(this);
        adminCommand.setExecutor(admin);
        adminCommand.setTabCompleter(admin);
        PluginCommand shulkerCommand = Objects.requireNonNull(getCommand("shulker"));
        ShulkerCommand shulker = new ShulkerCommand(this, sessions);
        shulkerCommand.setExecutor(shulker);
        shulkerCommand.setTabCompleter(shulker);
        updatePermissionMessage();

        getLogger().info("MR-Shulkers " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        // Save every open shulker and remove its tag before player data is written.
        if (watchdog != null) {
            watchdog.stop();
        }
        if (sessions != null) {
            sessions.closeAll();
        }
    }

    public State state() {
        return state;
    }

    public SessionService sessions() {
        return sessions;
    }

    public Hooks hooks() {
        return hooks;
    }

    /**
     * Reloads {@code config.yml} and {@code messages.yml}. A file that fails keeps its previous
     * settings; the sender and the console are told what failed and why.
     */
    public void reload(CommandSender sender) {
        Files files = loadFiles();
        State old = state;
        State updated = new State(
                files.settings.ok() ? files.settings.value() : old.settings(),
                files.messages.ok() ? files.messages.value() : old.messages());
        state = updated;
        updatePermissionMessage();
        watchdog.start(updated.settings().watchdogIntervalTicks());

        Messages messages = updated.messages();
        List<String> warnings = files.warnings();
        if (warnings.isEmpty() && files.settings.ok() && files.messages.ok()) {
            messages.send(sender, "reload-success");
        }
        if (!warnings.isEmpty()) {
            messages.send(sender, "reload-warnings", Placeholder.unparsed("count", String.valueOf(warnings.size())));
            for (String warning : warnings) {
                messages.send(sender, "reload-detail", Placeholder.unparsed("detail", warning));
            }
        }
        for (LoadResult<?> failed : List.of(files.settings, files.messages)) {
            if (!failed.ok()) {
                String file = failed == files.settings ? "config.yml" : "messages.yml";
                messages.send(sender, "reload-failed", Placeholder.unparsed("file", file));
                messages.send(sender, "reload-detail", Placeholder.unparsed("detail", failed.error()));
            }
        }
        // Hooks are detected at startup only (DESIGN.md 5.2 step 7).
        for (String hook : hooks.changedSinceStartup(getServer().getPluginManager())) {
            String detail = hook + " was added or removed since startup; restart the server to change the hook.";
            messages.send(sender, "reload-detail", Placeholder.unparsed("detail", detail));
            getLogger().warning(detail);
        }

        if (!(sender instanceof ConsoleCommandSender)) {
            getLogger().info(sender.getName() + " reloaded MR-Shulkers.");
            for (String line : files.report()) {
                getLogger().warning(line);
            }
        }
    }

    private Files loadFiles() {
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
        }
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        LoadResult<Settings> settings = settingsLoader.load(new File(getDataFolder(), "config.yml"), getLogger()::info);
        LoadResult<Messages> messages = messagesLoader.load(new File(getDataFolder(), "messages.yml"), getLogger()::info);
        if (settings.ok()) {
            logUnknownWorlds(settings.value());
        }
        return new Files(settings, messages);
    }

    /** Unknown world names are allowed (the world may load later), but worth a note. */
    private void logUnknownWorlds(Settings settings) {
        Set<String> loaded = Bukkit.getWorlds().stream()
                .map(World::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (String world : settings.disabledWorlds()) {
            if (!loaded.contains(world)) {
                getLogger().info("config.yml: open.disabled-worlds lists '" + world + "', which isn't loaded right now.");
            }
        }
    }

    private void updatePermissionMessage() {
        for (String name : List.of("mrshulkers", "shulker")) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.permissionMessage(state.messages().component("no-permission"));
            }
        }
    }

    private record Files(LoadResult<Settings> settings, LoadResult<Messages> messages) {

        List<String> warnings() {
            List<String> warnings = new ArrayList<>();
            settings.warnings().forEach(w -> warnings.add("config.yml: " + w));
            messages.warnings().forEach(w -> warnings.add("messages.yml: " + w));
            return warnings;
        }

        /** Console lines: every warning, then every failure. */
        List<String> report() {
            List<String> lines = new ArrayList<>(warnings());
            if (!settings.ok()) {
                lines.add("Kept the previous config.yml settings: " + settings.error());
            }
            if (!messages.ok()) {
                lines.add("Kept the previous messages.yml: " + messages.error());
            }
            return lines;
        }
    }
}
