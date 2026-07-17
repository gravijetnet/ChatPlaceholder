package net.gravijet.chatplaceholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * The PlaceholderAPI expansion, by default registered under the identifier {@code server}.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>{@code %server_mode%} - LOBBY / BEDWARS</li>
 *   <li>{@code %server_chat_prefix%} - lobby: Phoenix rank prefix, bedwars: team prefix</li>
 *   <li>{@code %server_tab_prefix%} - lobby: Phoenix rank prefix, bedwars: team prefix</li>
 *   <li>{@code %server_tab_sort%} - lobby: rank priority, bedwars: team priority</li>
 *   <li>{@code %server_tab_name_color%} - lobby: rank colour, bedwars: team colour</li>
 *   <li>{@code %server_team%} - RED / BLUE / ... / NONE</li>
 *   <li>{@code %server_team_color%} - &amp;c / &amp;9 / ...</li>
 * </ul>
 */
public final class ServerExpansion extends PlaceholderExpansion {

    /**
     * Sort key handed out for offline players: the lobby block with the lowest possible rank.
     * TAB parses %server_tab_sort% as a number, so this must never be empty or non-numeric.
     */
    private static final String OFFLINE_SORT = "1009999";

    private final ChatPlaceholderPlugin plugin;
    private final ModeService modeService;
    /** Read once: PlaceholderAPI caches the identifier, it must not change at runtime. */
    private final String identifier;

    public ServerExpansion(ChatPlaceholderPlugin plugin, ModeService modeService) {
        this.plugin = plugin;
        this.modeService = modeService;
        this.identifier = readIdentifier(plugin);
    }

    /** An empty or missing identifier would make the expansion unusable, so fall back to "server". */
    private static String readIdentifier(ChatPlaceholderPlugin plugin) {
        final String configured = plugin.getConfig().getString("expansion.identifier", "server");
        if (configured == null || configured.trim().isEmpty()) {
            plugin.getLogger().warning("'expansion.identifier' is empty - falling back to 'server'.");
            return "server";
        }
        return configured.trim().toLowerCase();
    }

    @Override
    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public String getAuthor() {
        return "gravijet";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /** Keep the expansion registered across {@code /papi reload}. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        // These placeholders describe live, in-game context, so an online player is required.
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            switch (params.toLowerCase()) {
                case "mode":
                    return plugin.getConfig().getString("mode.lobby", "LOBBY");
                case "team":
                    return plugin.getConfig().getString("team.none", "NONE");
                case "tab_sort":
                    return OFFLINE_SORT;
                case "team_color":
                case "chat_prefix":
                case "tab_prefix":
                case "tab_name_color":
                    return "";
                default:
                    return null;
            }
        }

        final Player player = offlinePlayer.getPlayer();

        switch (params.toLowerCase()) {
            case "mode":
                return modeService.getMode(player);
            case "chat_prefix":
                return modeService.getChatPrefix(player);
            case "tab_prefix":
                return modeService.getTabPrefix(player);
            case "tab_sort":
                return modeService.getTabSort(player);
            case "tab_name_color":
                return modeService.getTabNameColor(player);
            case "team":
                return modeService.getTeamName(player);
            case "team_color":
                return modeService.getTeamColor(player);
            default:
                // Unknown placeholder -> let PlaceholderAPI leave it untouched.
                return null;
        }
    }
}
