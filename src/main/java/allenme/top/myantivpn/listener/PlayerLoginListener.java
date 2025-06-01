package allenme.top.myantivpn.listener;

import allenme.top.myantivpn.Core;
import allenme.top.myantivpn.apicheck.APIManager;
import allenme.top.myantivpn.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.concurrent.TimeUnit;

public class PlayerLoginListener implements Listener {

    private final Core plugin;

    public PlayerLoginListener(Core plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName();
        String ip = event.getAddress().getHostAddress();

        // Check if we have a cached result that's recent enough (within 6 hours)
        DatabaseManager.CachedIPResult cachedResult = plugin.getDatabaseManager().getCachedResult(ip);
        if (cachedResult != null) {
            long cacheTime = cachedResult.getLastChecked().getTime();
            long currentTime = System.currentTimeMillis();
            long diffHours = TimeUnit.MILLISECONDS.toHours(currentTime - cacheTime);

            if (diffHours < 6) {
                // Use cached result
                if (cachedResult.isVPN()) {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "§cVPN/Proxy detected by " + cachedResult.getService() + ": " + cachedResult.getReason());
                    plugin.getLogger().info("Player " + playerName + " was blocked from logging in (cached result): " + cachedResult.getReason());
                }
                return;
            }
        }

        // Perform a new check
        plugin.getApiManager().checkIP(ip, playerName).thenAccept(result -> {
            if (result.isVPN()) {
                // Kick the player if they're using VPN
                if (player.isOnline()) {
                    player.kickPlayer("§cVPN/Proxy detected by " + result.getService() + ": " + result.getReason());
                    plugin.getLogger().info("Player " + playerName + " was kicked for using VPN/Proxy: " + result.getReason());
                }
            }
        });
    }
}