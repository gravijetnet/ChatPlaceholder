package net.gravijet.chatplaceholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * The PlaceholderAPI expansion with identifier {@code server}.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>{@code %server_mode%} - LOBBY / BEDWARS</li>
 *   <li>{@code %server_chat_prefix%} - lobby: Phoenix prefix, bedwars: team prefix</li>
 *   <li>{@code %server_tab_prefix%} - same value as chat_prefix</li>
 *   <li>{@code %server_team%} - RED / BLUE / ... / NONE</li>
 *   <li>{@code %server_team_color%} - &amp;c / &amp;9 / ...</li>
 * </ul>
 */
public final class ServerExpansion extends PlaceholderExpansion {

    private final ChatPlaceholderPlugin plugin;
    private final ModeService modeService;

    public ServerExpansion(ChatPlaceholderPlugin plugin, ModeService modeService) {
        this.plugin = plugin;
        this.modeService = modeService;
    }

    @Override
    public String getIdentifier() {
        return "server";
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
                case "team_color":
                case "chat_prefix":
                case "tab_prefix":
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
            case "tab_prefix":
                return modeService.getPrefix(player);
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
