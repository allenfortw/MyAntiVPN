package allenme.top.myantivpn.banservice;

import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

public class PlayerChecker {
    private final BanServiceManager banManager;
    private final FileConfiguration config;

    public PlayerChecker(BanServiceManager banManager, FileConfiguration config) {
        this.banManager = banManager;
        this.config = config;
    }

    public boolean checkPlayer(Player player, String ip, String isp, String asn) {
        // Check if any of the player's details are in the ban lists
        if (banManager.isIpBanned(ip) ||
                banManager.isIspBanned(isp) ||
                banManager.isAsnBanned(asn)) {

            // If command execution is enabled in config
            if (config.getBoolean("Command.Enabled", true)) {
                String command = config.getString("Command.Execute", "kick %player% VPN/Proxy usage is not allowed!")
                        .replace("%player%", player.getName());

                // Execute the command as console
                player.getServer().dispatchCommand(
                        player.getServer().getConsoleSender(),
                        command
                );
            }
            return true;
        }
        return false;
    }
}