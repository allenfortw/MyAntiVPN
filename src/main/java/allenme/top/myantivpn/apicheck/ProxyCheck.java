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

public class ProxyCheck implements VPNCheckService {

    private final Core plugin;
    private final String apiKey;

    public ProxyCheck(Core plugin, String apiKey) {
        this.plugin = plugin;
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<APIManager.VPNCheckResult> checkIP(String ip, String playerName) {
        CompletableFuture<APIManager.VPNCheckResult> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = "http://proxycheck.io/v2/" + ip;

                // Add API key if provided
                if (apiKey != null && !apiKey.isEmpty()) {
                    apiUrl += "?key=" + apiKey;
                }

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

                    // Parse the JSON response
                    JSONParser parser = new JSONParser();
                    JSONObject jsonResponse = (JSONObject) parser.parse(response.toString());

                    // Extract result for the IP
                    JSONObject ipResult = (JSONObject) jsonResponse.get(ip);

                    if (ipResult != null) {
                        String proxyStatus = (String) ipResult.get("proxy");
                        String provider = (String) ipResult.get("provider");

                        boolean isProxy = "yes".equalsIgnoreCase(proxyStatus);

                        // Log to database
                        String reason = isProxy ? "Proxy detected with provider: " + provider : "Not a proxy";
                        plugin.getDatabaseManager().logCheck(playerName, ip, isProxy, "ProxyCheck", reason);

                        future.complete(new APIManager.VPNCheckResult(isProxy, reason, "ProxyCheck"));
                    } else {
                        plugin.getLogger().warning("ProxyCheck returned an unexpected response format");
                        future.complete(new APIManager.VPNCheckResult(false, "Invalid response format", "ProxyCheck"));
                    }
                } else {
                    plugin.getLogger().warning("ProxyCheck returned error code: " + responseCode);
                    future.complete(new APIManager.VPNCheckResult(false, "API error: " + responseCode, "ProxyCheck"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking IP with ProxyCheck: " + e.getMessage());
                future.complete(new APIManager.VPNCheckResult(false, "API error: " + e.getMessage(), "ProxyCheck"));
            }
        });

        return future;
    }
}