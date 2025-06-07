package allenme.top.myantivpn.commands;

import allenme.top.myantivpn.Core;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ServicesCommand {
    private final Core plugin;

    public ServicesCommand(Core plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender) {
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.header"));

        ConfigurationSection servicesSection = plugin.getConfig().getConfigurationSection("Services");
        if (servicesSection == null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.no-services"));
            return;
        }

        checkIPAPIStatus(sender, servicesSection)
                .thenCompose(v -> checkProxyCheckStatus(sender, servicesSection))
                .thenCompose(v -> checkVPNAPIStatus(sender, servicesSection))
                .thenCompose(v -> checkIPHubStatus(sender, servicesSection));
    }
    private CompletableFuture<Void> checkIPAPIStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("IP-API.Enabled", false);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("service", "IP-API");
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.service-name", placeholders));

        if (!enabled) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.disabled"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.not-required"));
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
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.available"));
                    } else {
                        Map<String, String> errorPlaceholders = new HashMap<>();
                        errorPlaceholders.put("error", "HTTP " + responseCode);
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    }
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.not-required"));
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> errorPlaceholders = new HashMap<>();
                    errorPlaceholders.put("error", e.getMessage());
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.not-required"));
                    future.complete(null);
                });
            }
        });
        return future;
    }

    private CompletableFuture<Void> checkProxyCheckStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("ProxyCheck.Enabled", false);
        String apiKey = servicesSection.getString("ProxyCheck.Key", "");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("service", "ProxyCheck");
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.service-name", placeholders));

        if (!enabled) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.disabled"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.required"));
            future.complete(null);
            return future;
        }

        if (apiKey.isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.not-set"));
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("http://proxycheck.io/v2/8.8.8.8?key=" + apiKey + "&vpn=1");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.available"));
                    } else {
                        Map<String, String> errorPlaceholders = new HashMap<>();
                        errorPlaceholders.put("error", "HTTP " + responseCode);
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    }
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.configured"));
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> errorPlaceholders = new HashMap<>();
                    errorPlaceholders.put("error", e.getMessage());
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.configured"));
                    future.complete(null);
                });
            }
        });
        return future;
    }

    private CompletableFuture<Void> checkVPNAPIStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("VPN-API.Enabled", false);
        String apiKey = servicesSection.getString("VPN-API.Key", "");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("service", "VPN-API");
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.service-name", placeholders));

        if (!enabled) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.disabled"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.required"));
            future.complete(null);
            return future;
        }

        if (apiKey.isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.not-set"));
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://vpnapi.io/api/8.8.8.8?key=" + apiKey);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.available"));
                    } else {
                        Map<String, String> errorPlaceholders = new HashMap<>();
                        errorPlaceholders.put("error", "HTTP " + responseCode);
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    }
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.configured"));
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> errorPlaceholders = new HashMap<>();
                    errorPlaceholders.put("error", e.getMessage());
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.configured"));
                    future.complete(null);
                });
            }
        });
        return future;
    }

    private CompletableFuture<Void> checkIPHubStatus(CommandSender sender, ConfigurationSection servicesSection) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        boolean enabled = servicesSection.getBoolean("IPHub.Enabled", false);
        String apiKey = servicesSection.getString("IPHub.Key", "");

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("service", "IPHub");
        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.service-name", placeholders));

        if (!enabled) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.disabled"));
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.required"));
            future.complete(null);
            return future;
        }

        if (apiKey.isEmpty()) {
            sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.not-set"));
            future.complete(null);
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("http://v2.api.iphub.info/ip/8.8.8.8");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("X-Key", apiKey);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (responseCode == 200) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.available"));
                    } else {
                        Map<String, String> errorPlaceholders = new HashMap<>();
                        errorPlaceholders.put("error", "HTTP " + responseCode);
                        sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    }
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.configured"));
                    future.complete(null);
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<String, String> errorPlaceholders = new HashMap<>();
                    errorPlaceholders.put("error", e.getMessage());
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.status.error", errorPlaceholders));
                    sender.sendMessage(plugin.getMessageManager().getMessage("messages.services.key.configured"));
                    future.complete(null);
                });
            }
        });
        return future;
    }
}