package net.gravijet.chatplaceholder;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Best-effort integration with the TAB plugin.
 *
 * <p>TAB re-resolves placeholders on its own refresh interval, so this hook only exists to
 * make lobby &lt;-&gt; bedwars transitions feel instant. Everything is done reflectively and
 * fully guarded: if TAB is absent, or its API differs from what we expect, this silently does
 * nothing and TAB's normal refresh still picks the change up. That keeps the plugin free of a
 * hard TAB dependency (important for the CI build).</p>
 */
public final class TabHook {

    private final ChatPlaceholderPlugin plugin;

    private boolean available;
    private Object tabApi;               // me.neznamy.tab.api.TabAPI instance
    private Method getPlayerMethod;      // TabAPI#getPlayer(UUID) -> TabPlayer
    private Method forceRefreshMethod;   // TabPlayer#forceRefresh()

    public TabHook(ChatPlaceholderPlugin plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
            return;
        }
        try {
            final Class<?> tabApiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            this.tabApi = tabApiClass.getMethod("getInstance").invoke(null);
            this.getPlayerMethod = tabApiClass.getMethod("getPlayer", UUID.class);

            final Class<?> tabPlayerClass = Class.forName("me.neznamy.tab.api.TabPlayer");
            this.forceRefreshMethod = tabPlayerClass.getMethod("forceRefresh");

            this.available = this.tabApi != null;
            if (this.available) {
                plugin.getLogger().info("TAB integration enabled (instant prefix refresh on arena changes).");
            }
        } catch (Throwable t) {
            this.available = false;
            plugin.getLogger().info("TAB found but its API could not be hooked reflectively; "
                    + "relying on TAB's own refresh interval instead (" + t.getClass().getSimpleName() + ").");
        }
    }

    /** Schedules a TAB refresh on the next tick, when the arena/team state has settled. */
    public void refreshLater(Player player) {
        if (!available || player == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> refreshNow(uuid));
    }

    private void refreshNow(UUID uuid) {
        try {
            final Object tabPlayer = getPlayerMethod.invoke(tabApi, uuid);
            if (tabPlayer != null) {
                forceRefreshMethod.invoke(tabPlayer);
            }
        } catch (Throwable ignored) {
            // Best effort only - never let a TAB API mismatch break arena events.
        }
    }
}
