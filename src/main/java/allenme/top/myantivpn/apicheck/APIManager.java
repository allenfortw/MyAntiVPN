package allenme.top.myantivpn.apicheck;

import allenme.top.myantivpn.Core;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class APIManager {

    private final Core plugin;
    private final Map<String, VPNCheckService> services = new HashMap<>();

    public APIManager(Core plugin) {
        this.plugin = plugin;
        loadServices();
    }

    private void loadServices() {
        // Clear existing services
        services.clear();

        ConfigurationSection servicesSection = plugin.getConfig().getConfigurationSection("Services");
        if (servicesSection == null) {
            plugin.getLogger().warning("No services section found in config!");
            return;
        }

        // Load IP-API service
        if (servicesSection.getBoolean("IP-API.Enabled", false)) {
            services.put("IP-API", new IPAPI(plugin));
            plugin.getLogger().info("Loaded IP-API service");
        }

        // Load ProxyCheck service
        if (servicesSection.getBoolean("ProxyCheck.Enabled", false)) {
            String key = servicesSection.getString("ProxyCheck.key", "");
            services.put("ProxyCheck", new ProxyCheck(plugin, key));
            plugin.getLogger().info("Loaded ProxyCheck service");
        }

        // Load VPNAPI service
        if (servicesSection.getBoolean("VPNAPI.Enabled", false)) {
            String key = servicesSection.getString("VPNAPI.key", "");
            services.put("VPNAPI", new VPNAPI(plugin, key));
            plugin.getLogger().info("Loaded VPNAPI service");
        }

        // Load IPHub service
        if (servicesSection.getBoolean("IPHub.Enabled", false)) {
            String key = servicesSection.getString("IPHub.key", "");
            services.put("IPHub", new IPHub(plugin, key));
            plugin.getLogger().info("Loaded IPHub service");
        }
    }

    public void reloadServices() {
        plugin.getLogger().info("Reloading API services...");
        loadServices();
        plugin.getLogger().info("API services reloaded successfully");
    }

    public CompletableFuture<VPNCheckResult> checkIP(String ip, String playerName) {
        return checkIP(ip, playerName, new ArrayList<>(services.keySet()));
    }

    public CompletableFuture<VPNCheckResult> checkIP(String ip, String playerName, List<String> serviceNames) {
        CompletableFuture<VPNCheckResult> result = new CompletableFuture<>();

        // If no services are enabled or specified, return negative result
        if (services.isEmpty() || serviceNames.isEmpty()) {
            result.complete(new VPNCheckResult(false, "No services available", "None"));
            return result;
        }

        List<VPNCheckService> servicesToCheck = new ArrayList<>();
        for (String serviceName : serviceNames) {
            if (services.containsKey(serviceName)) {
                servicesToCheck.add(services.get(serviceName));
            }
        }

        // If no valid services were found, return negative result
        if (servicesToCheck.isEmpty()) {
            result.complete(new VPNCheckResult(false, "No valid services specified", "None"));
            return result;
        }

        checkWithNextService(ip, playerName, servicesToCheck, 0, result);
        return result;
    }

    private void checkWithNextService(String ip, String playerName, List<VPNCheckService> servicesToCheck, int index,
                                      CompletableFuture<VPNCheckResult> result) {
        if (index >= servicesToCheck.size()) {
            // No more services to check, player is probably not using VPN
            result.complete(new VPNCheckResult(false, "All services cleared", "All"));
            return;
        }

        VPNCheckService service = servicesToCheck.get(index);
        service.checkIP(ip, playerName).thenAccept(checkResult -> {
            if (checkResult.isVPN()) {
                // If VPN detected, complete the future with positive result
                result.complete(checkResult);
            } else {
                // Try next service
                checkWithNextService(ip, playerName, servicesToCheck, index + 1, result);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Error checking with service " + service.getClass().getSimpleName() + ": " + ex.getMessage());
            // Try next service on error
            checkWithNextService(ip, playerName, servicesToCheck, index + 1, result);
            return null;
        });
    }

    public List<String> getAvailableServices() {
        return new ArrayList<>(services.keySet());
    }

    public static class VPNCheckResult {
        private final boolean isVPN;
        private final String reason;
        private final String service;

        public VPNCheckResult(boolean isVPN, String reason, String service) {
            this.isVPN = isVPN;
            this.reason = reason;
            this.service = service;
        }

        public boolean isVPN() {
            return isVPN;
        }

        public String getReason() {
            return reason;
        }

        public String getService() {
            return service;
        }
    }
}