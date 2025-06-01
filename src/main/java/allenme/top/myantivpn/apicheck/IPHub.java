package allenme.top.myantivpn.apicheck;

import allenme.top.myantivpn.Core;
import org.bukkit.Bukkit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class IPHub implements VPNCheckService {

    private final Core plugin;
    private final String apiKey;

    public IPHub(Core plugin, String apiKey) {
        this.plugin = plugin;
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<APIManager.VPNCheckResult> checkIP(String ip, String playerName) {
        CompletableFuture<APIManager.VPNCheckResult> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = "http://v2.api.iphub.info/ip/" + ip;

                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                // Add API key in the header
                if (apiKey != null && !apiKey.isEmpty()) {
                    connection.setRequestProperty("X-Key", apiKey);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    // Parse the JSON response
                    JSONParser parser = new JSONParser();
                    JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());

                    // IPHub uses 'block' parameter to indicate if IP is a proxy/VPN
                    // 0 = not a proxy, 1 = proxy/VPN, 2 = hosting provider/datacenter
                    long blockValue = (long) jsonResponse.get("block");
                    String countryCode = (String) jsonResponse.get("countryCode");
                    String isp = (String) jsonResponse.get("isp");

                    boolean isVPN = blockValue == 1;
                    boolean isHosting = blockValue == 2;

                    String status;
                    if (isVPN) {
                        status = "Proxy/VPN detected";
                    } else if (isHosting) {
                        status = "Hosting/Datacenter detected";
                    } else {
                        status = "Clean IP address";
                    }

                    String reason = status;
                    if (countryCode != null && !countryCode.isEmpty()) {
                        reason += " (Country: " + countryCode;
                        if (isp != null && !isp.isEmpty()) {
                            reason += ", ISP: " + isp;
                        }
                        reason += ")";
                    }

                    // For this API, consider both proxy/VPN and hosting as potentially problematic
                    boolean isSuspicious = isVPN || isHosting;

                    // Log to database
                    plugin.getDatabaseManager().logCheck(playerName, ip, isSuspicious, "IPHub", reason);

                    future.complete(new APIManager.VPNCheckResult(isSuspicious, reason, "IPHub"));
                } else if (responseCode == 429) {
                    // Handle rate limit specifically
                    plugin.getLogger().warning("IPHub rate limit exceeded");
                    future.complete(new APIManager.VPNCheckResult(false, "API rate limit exceeded", "IPHub"));
                } else {
                    plugin.getLogger().warning("IPHub returned error code: " + responseCode);
                    future.complete(new APIManager.VPNCheckResult(false, "API error: " + responseCode, "IPHub"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking IP with IPHub: " + e.getMessage());
                future.complete(new APIManager.VPNCheckResult(false, "API error: " + e.getMessage(), "IPHub"));
            }
        });

        return future;
    }
}