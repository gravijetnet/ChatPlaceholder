package net.gravijet.chatplaceholder;

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
 * API; the lobby prefix is resolved from Phoenix through PlaceholderAPI. This way every
 * consumer stays in sync and only this plugin has to know about MBedwars.</p>
 */
public final class ChatPlaceholderPlugin extends JavaPlugin {

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

        this.tabHook = new TabHook(this);
        this.modeService = new ModeService(this, bedwarsHook);

        // Register the "server" PlaceholderAPI expansion.
        this.expansion = new ServerExpansion(this, modeService);
        if (this.expansion.register()) {
            getLogger().info("Registered PlaceholderAPI expansion '%server_...%'.");
        } else {
            getLogger().severe("Failed to register the PlaceholderAPI expansion 'server'.");
        }

        // Hook MBedwars events (only if MBedwars is present) to nudge TAB on transitions.
        if (bedwarsHook != null) {
            pm.registerEvents(new ArenaListener(this, tabHook), this);
        }

        getLogger().info("ChatPlaceholder v" + getDescription().getVersion() + " enabled.");
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
