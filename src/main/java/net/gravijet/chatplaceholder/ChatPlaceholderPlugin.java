package net.gravijet.chatplaceholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class of ChatPlaceholder.
 *
 * <p>The plugin is a thin "core" that other plugins (Phoenix chat, TAB, scoreboards, ...)
 * can pull placeholders from. All Bedwars detection is done through the official MBedwars
 * API; the lobby values are resolved from Phoenix through PlaceholderAPI. This way every
 * consumer stays in sync and only this plugin has to know about MBedwars.</p>
 */
public final class ChatPlaceholderPlugin extends JavaPlugin {

    /** Delay for the post-start self check; PlaceholderAPI loads /expansions after server load. */
    private static final long VERIFY_DELAY_TICKS = 20L;

    private ModeService modeService;
    private TabHook tabHook;
    private ServerExpansion expansion;

    @Override
    public void onEnable() {
        // Write config.yml on first start.
        saveDefaultConfig();

        final PluginManager pm = Bukkit.getPluginManager();

        // PlaceholderAPI is mandatory: without it there is nothing to register into.
        if (pm.getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI is required but was not found. Disabling ChatPlaceholder.");
            pm.disablePlugin(this);
            return;
        }

        // MBedwars is optional. It is isolated in MBedwarsHook so that this plugin also
        // loads cleanly on a server that has no MBedwars installed (everyone -> LOBBY).
        MBedwarsHook bedwarsHook = null;
        if (pm.getPlugin("MBedwars") != null) {
            bedwarsHook = new MBedwarsHook();
            getLogger().info("MBedwars detected - Bedwars context is active.");
        } else {
            getLogger().warning("MBedwars not found - every player will resolve as LOBBY.");
        }

        warnAboutLegacyConfigKeys();

        this.tabHook = new TabHook(this);
        this.modeService = new ModeService(this, bedwarsHook);

        this.expansion = new ServerExpansion(this, modeService);
        registerExpansion();

        // Hook MBedwars events (only if MBedwars is present) to nudge TAB on transitions.
        if (bedwarsHook != null) {
            pm.registerEvents(new ArenaListener(this, tabHook), this);
        }

        Bukkit.getScheduler().runTaskLater(this, this::verifyRegistration, VERIFY_DELAY_TICKS);

        getLogger().info("ChatPlaceholder v" + getDescription().getVersion() + " enabled.");
    }

    /**
     * saveDefaultConfig() never touches an existing config.yml, so a server upgrading from an
     * older build keeps its renamed keys. They are inert now and the (correct) defaults apply
     * instead, which is silent and confusing - so point at them explicitly.
     */
    private void warnAboutLegacyConfigKeys() {
        final String[][] renamed = {
                {"lobby.phoenix-prefix-placeholder", "lobby.chat-prefix-placeholder / lobby.tab-prefix-placeholder"},
                {"lobby.phoenix-prefix-fallback", "lobby.prefix-fallback"},
                {"bedwars.prefix-format", "bedwars.chat-prefix-format / bedwars.tab-prefix-format"},
        };
        for (String[] entry : renamed) {
            if (getConfig().isSet(entry[0])) {
                getLogger().warning("config.yml still contains the old key '" + entry[0] + "', which is "
                        + "no longer read. It was replaced by: " + entry[1] + ". Delete config.yml and "
                        + "restart to regenerate it.");
            }
        }
    }

    private void registerExpansion() {
        final String id = expansion.getIdentifier();

        // PlaceholderAPI keeps exactly one expansion per identifier. "server" is also the
        // identifier of the popular eCloud Server expansion (%server_online%, %server_tps%),
        // so warn instead of silently breaking whichever of the two loses.
        if (PlaceholderAPI.isRegistered(id)) {
            getLogger().warning("The placeholder identifier '" + id + "' is already in use by another "
                    + "expansion. ChatPlaceholder is taking it over, so that expansion's placeholders "
                    + "(e.g. %" + id + "_online%) will stop working. Set 'expansion.identifier' in "
                    + "ChatPlaceholder's config.yml to a free name if you still need them.");
        }

        if (expansion.register()) {
            getLogger().info("Registered PlaceholderAPI expansion '" + id + "'.");
        } else {
            getLogger().severe("Failed to register the PlaceholderAPI expansion '" + id + "'. "
                    + "Placeholders will show up literally (e.g. %" + id + "_tab_prefix%).");
        }
    }

    /**
     * Runs once shortly after startup, when PlaceholderAPI has loaded the /expansions folder
     * too. If something else claimed our identifier in the meantime, the placeholders would
     * render literally in TAB and chat - which is confusing to debug, so say it out loud.
     */
    private void verifyRegistration() {
        final String id = expansion.getIdentifier();
        if (expansion.isRegistered()) {
            getLogger().info("Placeholders are live: %" + id + "_mode%, %" + id + "_chat_prefix%, %"
                    + id + "_tab_prefix%, %" + id + "_tab_sort%, %" + id + "_tab_name_color%, %"
                    + id + "_team%, %" + id + "_team_color%.");
            return;
        }
        getLogger().severe("The expansion '" + id + "' is no longer registered - another plugin or "
                + "expansion claimed that identifier. Placeholders such as %" + id + "_tab_prefix% "
                + "will render literally. Set 'expansion.identifier' in config.yml to a free name "
                + "and restart the server.");
    }

    @Override
    public void onDisable() {
        if (this.expansion != null) {
            this.expansion.unregister();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chatplaceholder.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            this.modeService.reload();
            sender.sendMessage("§aChatPlaceholder configuration reloaded.");
            // The identifier is fixed at registration time, so it deliberately stays untouched.
            sender.sendMessage("§7Note: changing §fexpansion.identifier §7needs a server restart.");
            return true;
        }
        sender.sendMessage("§6ChatPlaceholder §7v" + getDescription().getVersion());
        sender.sendMessage("§7/" + label + " reload §8- §7reload the configuration");
        return true;
    }

    public ModeService getModeService() {
        return this.modeService;
    }

    public TabHook getTabHook() {
        return this.tabHook;
    }
}
