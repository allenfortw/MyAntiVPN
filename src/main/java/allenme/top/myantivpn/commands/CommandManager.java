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

public class CommandManager implements CommandExecutor, TabCompleter {

    private final Core plugin;
    private final ServicesCommand servicesCommand;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final BanlistCommand banlistCommand;

    public CommandManager(Core plugin) {
        this.plugin = plugin;
        this.servicesCommand = new ServicesCommand(plugin);
        this.banlistCommand = new BanlistCommand(plugin.getBanManager()); // 在构造函数中初始化
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
            case "ban":
            case "unban":
                return banlistCommand.onCommand(sender, command, label, args);
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

            case "services":
                servicesCommand.execute(sender);
                return true;

            case "reload":
                reloadPlugin(sender);
                return true;

            default:
                showHelp(sender);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("myantivpn.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> commands = Arrays.asList("check", "logs", "services", "reload", "ban", "unban");
            return filterStartsWith(commands, args[0]);
        }

        if (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban")) {
            return banlistCommand.onTabComplete(sender, command, alias, args);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("logs"))) {
            List<String> players = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return filterStartsWith(players, args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        String lowercasePrefix = prefix.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String item : list) {
            if (item.toLowerCase().startsWith(lowercasePrefix)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.header"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.help-command"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.check-command"));
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.help.logs-command"));
        sender.sendMessage(ChatColor.GOLD + "/antivpn ban <type> <value> " + ChatColor.WHITE + "- Ban an IP, ISP, or ASN");
        sender.sendMessage(ChatColor.GOLD + "/antivpn unban [type] [value] " + ChatColor.WHITE + "- List bans or unban an entry");
        sender.sendMessage(ChatColor.GOLD + "/antivpn services " + ChatColor.WHITE + "- Show all services status");
        sender.sendMessage(ChatColor.GOLD + "/antivpn reload " + ChatColor.WHITE + "- Reload the plugin");
    }

    private void checkPlayer(CommandSender sender, String playerName, String servicesArg) {
        Player target = Bukkit.getPlayerExact(playerName);

        if (target == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.player-not-found"));
            return;
        }

        String ip = target.getAddress().getAddress().getHostAddress();
        List<String> services = new ArrayList<>();

        if (servicesArg != null && !servicesArg.isEmpty()) {
            services = Arrays.asList(servicesArg.split(","));
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", playerName);
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.checking-player", placeholders));

        // Fix the checkIP call to match the API
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

            // Remove isError check since it doesn't exist
            if (result.isVPN()) {
                placeholders.put("reason", result.getReason());
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.vpn-detected", placeholders));
            } else {
                sender.sendMessage(plugin.getMessageManager().getMessage("messages.check.clean", placeholders));
            }
        });
    }

    private void showPlayerLogs(CommandSender sender, String playerName) {
        // 修改此處，移除多餘的參數
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

    private void reloadPlugin(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Plugin configuration reloaded successfully!");
    }
}