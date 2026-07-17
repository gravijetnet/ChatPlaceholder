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

    /** Sort block for players inside a running round, so teams stay above lobby players. */
    private static final int SORT_BLOCK_BEDWARS = 0;
    private static final int SORT_BLOCK_LOBBY = 1;

    /** Widths of the three sort segments; see {@link #buildSort(int, int, int)}. */
    private static final int MAX_TEAM_ORDER = 99;
    private static final int MAX_RANK_WEIGHT = 9999;

    private final ChatPlaceholderPlugin plugin;
    /** {@code null} when MBedwars is not installed. */
    private final MBedwarsHook bedwars;

    // Cached config values (refreshed on reload).
    private String lobbyMode;
    private String bedwarsMode;
    private String noneTeam;

    private String bedwarsChatPrefixFormat;
    private String bedwarsTabPrefixFormat;

    private String lobbyChatPrefixPlaceholder;
    private String lobbyTabPrefixPlaceholder;
    private String lobbyPrefixFallback;
    private String lobbyNameColorPlaceholder;
    private String lobbyNameColorFallback;
    private String lobbyPriorityPlaceholder;
    private int lobbyPriorityFallback;

    private boolean higherPriorityIsHigherRank;
    private int maxRankPriority;

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

        this.bedwarsChatPrefixFormat = c.getString("bedwars.chat-prefix-format", "{color}[{initials}] ");
        this.bedwarsTabPrefixFormat = c.getString("bedwars.tab-prefix-format", "{color}[{initials}] ");

        this.lobbyChatPrefixPlaceholder = c.getString("lobby.chat-prefix-placeholder", "%phoenix_player_rank_prefix%");
        this.lobbyTabPrefixPlaceholder = c.getString("lobby.tab-prefix-placeholder", "%phoenix_player_rank_prefix%");
        this.lobbyPrefixFallback = c.getString("lobby.prefix-fallback", "");
        this.lobbyNameColorPlaceholder = c.getString("lobby.name-color-placeholder", "%phoenix_player_rank_color%");
        this.lobbyNameColorFallback = c.getString("lobby.name-color-fallback", "&7");
        this.lobbyPriorityPlaceholder = c.getString("lobby.priority-placeholder", "%phoenix_player_rank_priority%");
        this.lobbyPriorityFallback = c.getInt("lobby.priority-fallback", 0);

        this.higherPriorityIsHigherRank = c.getBoolean("tab-sort.higher-priority-is-higher-rank", true);
        this.maxRankPriority = c.getInt("tab-sort.max-rank-priority", 9999);
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
     * %server_chat_prefix% -> bedwars: coloured team prefix, lobby: the Phoenix rank prefix.
     */
    public String getChatPrefix(Player player) {
        final MBedwarsHook.TeamSnapshot snap = snapshot(player);
        if (snap != null) {
            return applyTeamTokens(bedwarsChatPrefixFormat, snap);
        }
        return resolveOr(player, lobbyChatPrefixPlaceholder, lobbyPrefixFallback);
    }

    /**
     * %server_tab_prefix% -> bedwars: coloured team prefix, lobby: the Phoenix rank prefix.
     * Kept separate from {@link #getChatPrefix(Player)} so TAB can show a plain rank prefix
     * while chat additionally carries suffix/tag.
     */
    public String getTabPrefix(Player player) {
        final MBedwarsHook.TeamSnapshot snap = snapshot(player);
        if (snap != null) {
            return applyTeamTokens(bedwarsTabPrefixFormat, snap);
        }
        return resolveOr(player, lobbyTabPrefixPlaceholder, lobbyPrefixFallback);
    }

    /**
     * %server_tab_name_color% -> the colour the player's name should have in TAB.
     * Bedwars: the team colour, so a name never stays white. Lobby: the Phoenix rank colour.
     */
    public String getTabNameColor(Player player) {
        final MBedwarsHook.TeamSnapshot snap = snapshot(player);
        if (snap != null) {
            return snap.colorCode;
        }
        return resolveOr(player, lobbyNameColorPlaceholder, lobbyNameColorFallback);
    }

    /**
     * %server_tab_sort% -> a zero padded number, lowest value first.
     *
     * <p>Bedwars players are grouped by their team, lobby players by their Phoenix rank
     * priority. Within a Bedwars team the rank still decides the order, so a team's staff
     * stay on top of their own block.</p>
     */
    public String getTabSort(Player player) {
        final MBedwarsHook.TeamSnapshot snap = snapshot(player);
        final int rankWeight = rankWeight(player);
        if (snap != null) {
            return buildSort(SORT_BLOCK_BEDWARS, snap.order, rankWeight);
        }
        return buildSort(SORT_BLOCK_LOBBY, 0, rankWeight);
    }

    /**
     * Builds the sort key as {@code <block><team><rank>} with fixed width segments. The fixed
     * width means TAB's numeric ({@code PLACEHOLDER_LOW_TO_HIGH}) and alphabetic
     * ({@code PLACEHOLDER_A_TO_Z}) sorting produce the exact same order, so the placeholder
     * still behaves if the sorting type is ever changed.
     */
    private static String buildSort(int block, int teamOrder, int rankWeight) {
        return String.format("%d%02d%04d",
                block,
                clamp(teamOrder, 0, MAX_TEAM_ORDER),
                clamp(rankWeight, 0, MAX_RANK_WEIGHT));
    }

    /**
     * Turns the Phoenix rank priority into a "lower is better" weight, because the sort key is
     * ascending but a higher priority usually means a higher rank.
     */
    private int rankWeight(Player player) {
        final int priority = rankPriority(player);
        final int weight = higherPriorityIsHigherRank ? maxRankPriority - priority : priority;
        return clamp(weight, 0, MAX_RANK_WEIGHT);
    }

    /** Reads %phoenix_player_rank_priority% as a number, falling back when unavailable. */
    private int rankPriority(Player player) {
        final String raw = resolve(player, lobbyPriorityPlaceholder);
        if (raw == null) {
            return lobbyPriorityFallback;
        }
        try {
            // Parsed as a double first so a "90.0" style value does not fall back to 0.
            return (int) Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return lobbyPriorityFallback;
        }
    }

    private String applyTeamTokens(String format, MBedwarsHook.TeamSnapshot snap) {
        return format
                .replace("{color}", nz(snap.colorCode))
                .replace("{initials}", nz(snap.initials))
                .replace("{team}", nz(snap.displayName))
                .replace("{team_id}", nz(snap.name));
    }

    private String resolveOr(Player player, String placeholder, String fallback) {
        final String resolved = resolve(player, placeholder);
        return resolved != null ? resolved : fallback;
    }

    /**
     * Resolves a placeholder through PlaceholderAPI, returning {@code null} when it could not
     * be resolved. PlaceholderAPI hands the input back unchanged when the expansion behind it
     * is missing (e.g. Phoenix not installed), which would otherwise leak a raw
     * {@code %phoenix_...%} into chat or TAB.
     */
    private String resolve(Player player, String placeholder) {
        if (placeholder == null || placeholder.isEmpty()) {
            return null;
        }
        final String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
        if (resolved == null || resolved.isEmpty() || resolved.equals(placeholder)) {
            return null;
        }
        return resolved;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }
}
