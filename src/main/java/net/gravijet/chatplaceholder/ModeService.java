package net.gravijet.chatplaceholder;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Central logic that decides whether a player is in the LOBBY or in BEDWARS and builds the
 * matching placeholder values. Holds no direct MBedwars references; all of that is delegated
 * to the optional {@link MBedwarsHook} so this class stays loadable without MBedwars.
 */
public final class ModeService {

    private final ChatPlaceholderPlugin plugin;
    /** {@code null} when MBedwars is not installed. */
    private final MBedwarsHook bedwars;

    // Cached config values (refreshed on reload).
    private String lobbyMode;
    private String bedwarsMode;
    private String noneTeam;
    private String prefixFormat;
    private String phoenixPlaceholder;
    private String phoenixFallback;

    public ModeService(ChatPlaceholderPlugin plugin, MBedwarsHook bedwars) {
        this.plugin = plugin;
        this.bedwars = bedwars;
        reload();
    }

    /** Re-reads the cached values from config.yml. */
    public void reload() {
        final FileConfiguration c = plugin.getConfig();
        this.lobbyMode = c.getString("mode.lobby", "LOBBY");
        this.bedwarsMode = c.getString("mode.bedwars", "BEDWARS");
        this.noneTeam = c.getString("team.none", "NONE");
        this.prefixFormat = c.getString("bedwars.prefix-format", "{color}[{initials}] ");
        this.phoenixPlaceholder = c.getString("lobby.phoenix-prefix-placeholder", "%phoenix_prefix%");
        this.phoenixFallback = c.getString("lobby.phoenix-prefix-fallback", "");
    }

    /** Snapshot helper: one MBedwars lookup, {@code null} if the player is in the lobby. */
    private MBedwarsHook.TeamSnapshot snapshot(Player player) {
        return bedwars != null ? bedwars.getSnapshot(player) : null;
    }

    public boolean isInBedwars(Player player) {
        return snapshot(player) != null;
    }

    /** %server_mode% -> LOBBY / BEDWARS */
    public String getMode(Player player) {
        return snapshot(player) != null ? bedwarsMode : lobbyMode;
    }

    /** %server_team% -> RED / BLUE / ... or NONE */
    public String getTeamName(Player player) {
        final MBedwarsHook.TeamSnapshot snap = snapshot(player);
        return snap != null ? snap.name : noneTeam;
    }

    /** %server_team_color% -> &c / &9 / ... (empty in the lobby) */
    public String getTeamColor(Player player) {
        final MBedwarsHook.TeamSnapshot snap = snapshot(player);
        return snap != null ? snap.colorCode : "";
    }

    /**
     * %server_chat_prefix% and %server_tab_prefix%.
     * Bedwars: coloured team prefix. Lobby: the Phoenix rank prefix.
     */
    public String getPrefix(Player player) {
        final MBedwarsHook.TeamSnapshot snap = snapshot(player);
        if (snap != null) {
            return prefixFormat
                    .replace("{color}", nz(snap.colorCode))
                    .replace("{initials}", nz(snap.initials))
                    .replace("{team}", nz(snap.name));
        }
        return getLobbyPrefix(player);
    }

    /** Resolves the Phoenix prefix through PlaceholderAPI, falling back when unavailable. */
    private String getLobbyPrefix(Player player) {
        final String resolved = PlaceholderAPI.setPlaceholders(player, phoenixPlaceholder);
        // If Phoenix isn't loaded, PlaceholderAPI returns the input unchanged.
        if (resolved == null || resolved.isEmpty() || resolved.equals(phoenixPlaceholder)) {
            return phoenixFallback;
        }
        return resolved;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
