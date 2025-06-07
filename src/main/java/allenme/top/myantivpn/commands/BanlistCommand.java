package allenme.top.myantivpn.commands;

import allenme.top.myantivpn.banservice.BanServiceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BanlistCommand implements CommandExecutor, TabCompleter {
    private final BanServiceManager banManager;

    public BanlistCommand(BanServiceManager banManager) {
        this.banManager = banManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myantivpn.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "ban":
                handleBan(sender, args);
                break;
            case "unban":
                handleUnban(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleBan(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /antivpn ban <type> <value>");
            return;
        }

        String type = args[1].toLowerCase();
        String value = args[2];

        if (!isValidType(type)) {
            sender.sendMessage(ChatColor.RED + "Invalid type! Use: ip, isp, or asn");
            return;
        }

        if (banManager.addBan(type, value)) {
            sender.sendMessage(ChatColor.GREEN + "Successfully banned " + type + ": " + value);
        } else {
            sender.sendMessage(ChatColor.RED + "This entry is already banned!");
        }
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Show all banned entries
            sender.sendMessage(ChatColor.YELLOW + "=== Banned IPs ===");
            banManager.getBanList("ip").forEach(ip -> sender.sendMessage("- " + ip));

            sender.sendMessage(ChatColor.YELLOW + "=== Banned ISPs ===");
            banManager.getBanList("isp").forEach(isp -> sender.sendMessage("- " + isp));

            sender.sendMessage(ChatColor.YELLOW + "=== Banned ASNs ===");
            banManager.getBanList("asn").forEach(asn -> sender.sendMessage("- " + asn));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /antivpn unban <type> <value>");
            return;
        }

        String type = args[1].toLowerCase();
        String value = args[2];

        if (!isValidType(type)) {
            sender.sendMessage(ChatColor.RED + "Invalid type! Use: ip, isp, or asn");
            return;
        }

        if (banManager.removeBan(type, value)) {
            sender.sendMessage(ChatColor.GREEN + "Successfully unbanned " + type + ": " + value);
        } else {
            sender.sendMessage(ChatColor.RED + "This entry is not banned!");
        }
    }

    private boolean isValidType(String type) {
        return type.equals("ip") || type.equals("isp") || type.equals("asn");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== MyAntiVPN Ban Commands ===");
        sender.sendMessage(ChatColor.GOLD + "/antivpn ban <type> <value> " + ChatColor.WHITE + "- Ban an IP, ISP, or ASN");
        sender.sendMessage(ChatColor.GOLD + "/antivpn unban " + ChatColor.WHITE + "- List all banned entries");
        sender.sendMessage(ChatColor.GOLD + "/antivpn unban <type> <value> " + ChatColor.WHITE + "- Unban an entry");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("ban");
            completions.add("unban");
        } else if (args.length == 2) {
            completions.add("ip");
            completions.add("isp");
            completions.add("asn");
        }

        return completions;
    }
}