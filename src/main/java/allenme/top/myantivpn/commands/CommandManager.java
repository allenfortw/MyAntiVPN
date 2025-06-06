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
import org.bukkit.configuration.ConfigurationSection;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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

            case "services":
                showServicesStatus(sender);
                return true;

            case "reload":
                reloadPlugin(sender);
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
        sender.sendMessage(ChatColor.GOLD + "/antivpn services " + ChatColor.WHITE + "- Show all services status");
        sender.sendMessage(ChatColor.GOLD + "/antivpn reload " + ChatColor.WHITE + "- Reload the plugin");
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

    private void showServicesStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== 服務狀態 ===");

        ConfigurationSection servicesSection = plugin.getConfig().getConfigurationSection("Services");
        if (servicesSection == null) {
            sender.sendMessage(ChatColor.RED + "No services section found in config!");
            return;
        }

        // 使用 CompletableFuture 按順序執行檢查
        checkIPAPIStatus(sender, servicesSection)
                .thenCompose(v -> checkProxyCheckStatus(sender, servicesSection))
                .thenCompose(v -> checkVPNAPIStatus(sender, servicesSection))
                .thenCompose(v -> checkIPHubStatus(sender, servicesSection));
    }

    private CompletableFuture<Void> checkIPAPIStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("IP-API.Enabled", false);
        sender.sendMessage(ChatColor.AQUA + "IP-API:");

        if (!enabled) {
            sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "未啟用");
            sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "不須使用");
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("http://ip-api.com/json/8.8.8.8?fields=status");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.GREEN + "可用");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (HTTP " + responseCode + ")");
                    }
                    sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "不須使用");
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (" + e.getMessage() + ")");
                    sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "不須使用");
                    future.complete(null);
                });
            }
        });

        return future;
    }

    private CompletableFuture<Void> checkProxyCheckStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("ProxyCheck.Enabled", false);
        String key = servicesSection.getString("ProxyCheck.key", "");

        sender.sendMessage(ChatColor.AQUA + "ProxyCheck:");

        if (!enabled) {
            sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "未啟用");
            sender.sendMessage(ChatColor.GRAY + " 密鑰: " + (key.isEmpty() ? ChatColor.RED + "未設定" : ChatColor.YELLOW + "已設定"));
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = "http://proxycheck.io/v2/8.8.8.8";
                if (!key.isEmpty()) {
                    apiUrl += "?key=" + key;
                }

                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.GREEN + "可用");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (HTTP " + responseCode + ")");
                    }

                    if (key.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "不須使用 (免費版)");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.GREEN + "有效");
                    }
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (" + e.getMessage() + ")");
                    sender.sendMessage(ChatColor.GRAY + " 密鑰: " + (key.isEmpty() ? ChatColor.RED + "未設定" : ChatColor.YELLOW + "已設定"));
                    future.complete(null);
                });
            }
        });

        return future;
    }

    private CompletableFuture<Void> checkVPNAPIStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("VPNAPI.Enabled", false);
        String key = servicesSection.getString("VPNAPI.key", "");

        sender.sendMessage(ChatColor.AQUA + "VPNAPI:");

        if (!enabled) {
            sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "未啟用");
            sender.sendMessage(ChatColor.GRAY + " 密鑰: " + (key.isEmpty() ? ChatColor.RED + "未設定" : ChatColor.YELLOW + "已設定"));
            future.complete(null);
            return future;
        }

        if (key.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用");
            sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.RED + "未設定");
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://vpnapi.io/api/8.8.8.8?key=" + key);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.GREEN + "可用");
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.GREEN + "有效");
                    } else if (responseCode == 401) {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用");
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.RED + "無效");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (HTTP " + responseCode + ")");
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "已設定");
                    }
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (" + e.getMessage() + ")");
                    sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "已設定");
                    future.complete(null);
                });
            }
        });

        return future;
    }

    private CompletableFuture<Void> checkIPHubStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("IPHub.Enabled", false);
        String key = servicesSection.getString("IPHub.key", "");

        sender.sendMessage(ChatColor.AQUA + "IPHub:");

        if (!enabled) {
            sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "未啟用");
            sender.sendMessage(ChatColor.GRAY + " 密鑰: " + (key.isEmpty() ? ChatColor.RED + "未設定" : ChatColor.YELLOW + "已設定"));
            future.complete(null);
            return future;
        }

        if (key.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用");
            sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.RED + "未設定");
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("http://v2.api.iphub.info/ip/8.8.8.8");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("X-Key", key);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.GREEN + "可用");
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.GREEN + "有效");
                    } else if (responseCode == 401) {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用");
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.RED + "無效");
                    } else {
                        sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (HTTP " + responseCode + ")");
                        sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "已設定");
                    }
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GRAY + " 狀態: " + ChatColor.RED + "不可用 (" + e.getMessage() + ")");
                    sender.sendMessage(ChatColor.GRAY + " 密鑰: " + ChatColor.YELLOW + "已設定");
                    future.complete(null);
                });
            }
        });

        return future;
    }

    private void reloadPlugin(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "正在重新載入 MyAntiVPN...");

        try {
            // 1. 關閉資料庫連接
            sender.sendMessage(ChatColor.GRAY + "關閉資料庫連接...");
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().closeConnection();
            }

            // 2. 取消所有未完成的任務
            sender.sendMessage(ChatColor.GRAY + "取消未完成的任務...");
            Bukkit.getScheduler().cancelTasks(plugin);

            // 3. 重新載入配置檔案
            sender.sendMessage(ChatColor.GRAY + "重新載入配置檔案...");
            plugin.reloadConfig();

            // 4. 重新載入訊息檔案
            sender.sendMessage(ChatColor.GRAY + "重新載入訊息檔案...");
            plugin.getMessageManager().reload();

            // 5. 重新初始化資料庫
            sender.sendMessage(ChatColor.GRAY + "重新初始化資料庫...");
            plugin.getDatabaseManager().initDatabase();

            // 6. 重新載入 API 服務 (需要修改 APIManager 來支援重新載入)
            sender.sendMessage(ChatColor.GRAY + "重新載入 API 服務...");
            // 這裡需要新增重新載入服務的邏輯

            sender.sendMessage(ChatColor.GREEN + "MyAntiVPN 重新載入完成！");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "重新載入過程中發生錯誤: " + e.getMessage());
            plugin.getLogger().severe("Error during plugin reload: " + e.getMessage());
            e.printStackTrace();

            // 強制關閉插件
            sender.sendMessage(ChatColor.RED + "正在強制關閉插件...");
            Bukkit.getPluginManager().disablePlugin(plugin);
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
            completions.add("services");
            completions.add("reload");
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