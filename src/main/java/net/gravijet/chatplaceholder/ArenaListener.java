package net.gravijet.chatplaceholder;

import de.marcely.bedwars.api.arena.Arena;
import de.marcely.bedwars.api.event.arena.ArenaStatusChangeEvent;
import de.marcely.bedwars.api.event.player.PlayerJoinArenaEvent;
import de.marcely.bedwars.api.event.player.PlayerQuitArenaEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens to MBedwars events and asks {@link TabHook} to refresh the affected players.
 *
 * <p>The placeholders themselves are computed live on every request, so this listener is not
 * required for correctness - it just makes the lobby &lt;-&gt; bedwars transition appear
 * instantly in TAB instead of waiting for TAB's own refresh interval. It is only registered
 * when MBedwars is installed.</p>
 */
public final class ArenaListener implements Listener {

    private final ChatPlaceholderPlugin plugin;
    private final TabHook tabHook;

    public ArenaListener(ChatPlaceholderPlugin plugin, TabHook tabHook) {
        this.plugin = plugin;
        this.tabHook = tabHook;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinArena(PlayerJoinArenaEvent event) {
        tabHook.refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuitArena(PlayerQuitArenaEvent event) {
        tabHook.refreshLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaStatusChange(ArenaStatusChangeEvent event) {
        // Covers the lobby -> running transition (teams get assigned) and the end of a round.
        refreshArena(event.getArena());
    }

    private void refreshArena(Arena arena) {
        if (arena == null) {
            return;
        }
        for (Player player : arena.getPlayers()) {
            tabHook.refreshLater(player);
        }
    }
}
