package allenme.top.myantivpn.commands;

import allenme.top.myantivpn.Core;
import allenme.top.myantivpn.apicheck.APIManager;
import allenme.top.myantivpn.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final Core plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public CommandManager(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("myantivpn.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /antivpn check <player_name> [service1,service2,...]");
                    return true;
                }
                checkPlayer(sender, args[1], args.length > 2 ? args[2] : null);
                return true;

            case "logs":
                if (args.length > 1) {
                    showPlayerLogs(sender, args[1]);
                } else {
                    showRecentLogs(sender);
                }
                return true;

            default:
                showHelp(sender);
                return true;
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== MyAntiVPN Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/antivpn" + ChatColor.WHITE + " - Show this help message");
        sender.sendMessage(ChatColor.YELLOW + "/antivpn check <player_name> [services]" + ChatColor.WHITE + " - Force check a player for VPN/Proxy");
        sender.sendMessage(ChatColor.YELLOW + "/antivpn logs [player_name]" + ChatColor.WHITE + " - View check logs");
    }

    private void checkPlayer(CommandSender sender, String playerName, String servicesArg) {
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online.");
            return;
        }

        String ip = target.getAddress().getAddress().getHostAddress();
        List<String> services = new ArrayList<>();

        if (servicesArg != null && !servicesArg.isEmpty()) {
            services = Arrays.asList(servicesArg.split(","));
        }

        sender.sendMessage(ChatColor.YELLOW + "Checking player " + playerName + " for VPN/Proxy...");

        if (services.isEmpty()) {
            plugin.getApiManager().checkIP(ip, playerName).thenAccept(result -> {
                sendCheckResult(sender, playerName, result);
            });
        } else {
            plugin.getApiManager().checkIP(ip, playerName, services).thenAccept(result -> {
                sendCheckResult(sender, playerName, result);
            });
        }
    }

    private void sendCheckResult(CommandSender sender, String playerName, APIManager.VPNCheckResult result) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result.isVPN()) {
                sender.sendMessage(ChatColor.RED + "Player " + playerName + " is using a VPN/Proxy!");
                sender.sendMessage(ChatColor.RED + "Detected by: " + result.getService());
                sender.sendMessage(ChatColor.RED + "Reason: " + result.getReason());
            } else {
                sender.sendMessage(ChatColor.GREEN + "Player " + playerName + " is not using a VPN/Proxy.");
                sender.sendMessage(ChatColor.GREEN + "Service(s) used: " + result.getService());
            }
        });
    }

    private void showPlayerLogs(CommandSender sender, String playerName) {
        List<DatabaseManager.LogEntry> logs = plugin.getDatabaseManager().getPlayerLogs(playerName);

        if (logs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No logs found for player " + playerName);
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "=== Logs for " + playerName + " ===");
        for (DatabaseManager.LogEntry log : logs) {
            String status = log.isVPN() ? ChatColor.RED + "VPN/Proxy" : ChatColor.GREEN + "Clean";
            sender.sendMessage(
                    ChatColor.GRAY + dateFormat.format(log.getCheckTime()) + " " +
                            ChatColor.WHITE + log.getIp() + " " +
                            status + ChatColor.GRAY + " [" + log.getService() + "] " +
                            ChatColor.WHITE + log.getReason()
            );
        }
    }

    private void showRecentLogs(CommandSender sender) {
        List<DatabaseManager.LogEntry> logs = plugin.getDatabaseManager().getRecentLogs(20);

        if (logs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No logs found");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "=== Recent Logs ===");
        for (DatabaseManager.LogEntry log : logs) {
            String status = log.isVPN() ? ChatColor.RED + "VPN/Proxy" : ChatColor.GREEN + "Clean";
            sender.sendMessage(
                    ChatColor.GRAY + dateFormat.format(log.getCheckTime()) + " " +
                            ChatColor.YELLOW + log.getPlayerName() + " " +
                            ChatColor.WHITE + log.getIp() + " " +
                            status + ChatColor.GRAY + " [" + log.getService() + "] " +
                            ChatColor.WHITE + log.getReason()
            );
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("myantivpn.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("check");
            completions.add("logs");
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "check":
                case "logs":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            return plugin.getApiManager().getAvailableServices().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}