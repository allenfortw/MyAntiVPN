package allenme.top.myantivpn.apicheck;

import allenme.top.myantivpn.Core;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class IPAPI implements VPNCheckService {

    private final Core plugin;

    public IPAPI(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<APIManager.VPNCheckResult> checkIP(String ip, String playerName) {
        CompletableFuture<APIManager.VPNCheckResult> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = "http://ip-api.com/json/" + ip + "?fields=status,message,proxy,hosting";
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Parse the JSON response (simplified for example)
                    String jsonResponse = response.toString();

                    // Check if proxy or hosting is true (indicating VPN/Proxy)
                    boolean isProxy = jsonResponse.contains("\"proxy\":true") || jsonResponse.contains("\"hosting\":true");

                    if (isProxy) {
                        // Log to database
                        plugin.getDatabaseManager().logCheck(playerName, ip, true, "IP-API", "Proxy or hosting detected");

                        future.complete(new APIManager.VPNCheckResult(true, "Proxy or hosting detected", "IP-API"));
                    } else {
                        // Log to database
                        plugin.getDatabaseManager().logCheck(playerName, ip, false, "IP-API", "No proxy detected");

                        future.complete(new APIManager.VPNCheckResult(false, "No proxy detected", "IP-API"));
                    }
                } else {
                    plugin.getLogger().warning("IP-API returned error code: " + responseCode);
                    future.complete(new APIManager.VPNCheckResult(false, "API error: " + responseCode, "IP-API"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking IP with IP-API: " + e.getMessage());
                future.complete(new APIManager.VPNCheckResult(false, "API error: " + e.getMessage(), "IP-API"));
            }
        });

        return future;
    }
}