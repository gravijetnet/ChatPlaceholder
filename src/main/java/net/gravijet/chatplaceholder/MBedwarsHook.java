package net.gravijet.chatplaceholder;

import de.marcely.bedwars.api.GameAPI;
import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.arena.ArenaStatus;
import de.marcely.bedwars.api.arena.Team;
import org.bukkit.entity.Player;

/**
 * Isolates every reference to the MBedwars API in a single class.
 *
 * <p>It is only ever instantiated when the "MBedwars" plugin is present, which means the
 * MBedwars classes are only linked at that point. Consequently the rest of the plugin
 * ({@link ModeService}, the expansion, ...) has no compile- or runtime dependency on
 * MBedwars and works fine on a server without it.</p>
 */
public final class MBedwarsHook {

    /** Immutable snapshot of a player's current Bedwars team, taken in a single API lookup. */
    public static final class TeamSnapshot {
        /** Enum name of the team, e.g. {@code RED}, {@code BLUE}, {@code LIGHT_BLUE}. */
        public final String name;
        /** Legacy colour code of the team, e.g. {@code &c}. */
        public final String colorCode;
        /** Team initials as configured in MBedwars, e.g. {@code R}. */
        public final String initials;
        /** Display name as configured in MBedwars, e.g. {@code Red}. */
        public final String displayName;
        /**
         * Position of the team in the MBedwars {@code Team} enum. Stable across restarts and
         * identical on every server, which makes it a natural sort key for TAB.
         */
        public final int order;

        TeamSnapshot(String name, String colorCode, String initials, String displayName, int order) {
            this.name = name;
            this.colorCode = colorCode;
            this.initials = initials;
            this.displayName = displayName;
            this.order = order;
        }
    }

    /**
     * Returns a snapshot of the player's team, or {@code null} if the player is effectively
     * in the lobby. The player counts as "in Bedwars" only when <b>all</b> of the following
     * hold (as required):
     * <ul>
     *     <li>an MBedwars arena player object exists ({@code getArenaByPlayer != null});</li>
     *     <li>the arena is running ({@code status == RUNNING});</li>
     *     <li>the player has a team ({@code getPlayerTeam != null}).</li>
     * </ul>
     * Spectators are reported as lobby because MBedwars returns {@code null} for them here.
     */
    public TeamSnapshot getSnapshot(Player player) {
        if (player == null || !player.isOnline()) {
            return null;
        }

        final GameAPI api = GameAPI.get();
        if (api == null) {
            return null;
        }

        final Arena arena = api.getArenaByPlayer(player);
        if (arena == null) {
            return null; // not inside a match (also covers spectators)
        }
        if (arena.getStatus() != ArenaStatus.RUNNING) {
            return null; // arena exists but the round is not actually running
        }

        final Team team = arena.getPlayerTeam(player);
        if (team == null) {
            return null; // in the arena but without a team
        }

        return new TeamSnapshot(team.name(), colorCode(team), team.getInitials(),
                team.getDisplayName(), team.ordinal());
    }

    /** {@code true} when the player is in a running Bedwars round with a team. */
    public boolean isPlayingBedwars(Player player) {
        return getSnapshot(player) != null;
    }

    /**
     * Converts the team's MBedwars colour into a legacy {@code &}-code (e.g. {@code &c}).
     * The colour is taken straight from MBedwars so custom team colours are respected.
     */
    private String colorCode(Team team) {
        final net.md_5.bungee.api.ChatColor color = team.getBungeeChatColor();
        if (color == null) {
            return "";
        }
        // toString() yields the section-sign form (e.g. "§c"); expose it as "&c".
        return color.toString().replace('§', '&');
    }
}
