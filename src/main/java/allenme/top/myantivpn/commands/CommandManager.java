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
import java.util.*;
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
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.help-command"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.check-command"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.logs-command"));
    }


    private void checkPlayer(CommandSender sender, String playerName, String servicesArg) {
        Player target = Bukkit.getPlayerExact(playerName);

        // 檢查玩家是否存在
        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.player-not-found"));
            return;
        }

        String ip = target.getAddress().getAddress().getHostAddress();
        List<String> services = new ArrayList<>();

        // 處理指定的服務
        if (servicesArg != null && !servicesArg.isEmpty()) {
            services = Arrays.asList(servicesArg.split(","));
        }

        // 創建佔位符
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.checking-player", placeholders));

        // 根據是否指定服務來進行檢查
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
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", playerName);
            placeholders.put("service", result.getService());
            placeholders.put("reason", result.getReason());

            if (result.isVPN()) {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.result.vpn-detected", placeholders));
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.result.detected-by", placeholders));
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.result.reason", placeholders));
            } else {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.result.clean", placeholders));
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.result.service-used", placeholders));
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