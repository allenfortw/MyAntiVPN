package allenme.top.myantivpn.apicheck;

import allenme.top.myantivpn.Core;
import org.bukkit.Bukkit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VPNAPI implements VPNCheckService {

    private final Core plugin;
    private final String apiKey;

    public VPNAPI(Core plugin, String apiKey) {
        this.plugin = plugin;
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<APIManager.VPNCheckResult> checkIP(String ip, String playerName) {
        CompletableFuture<APIManager.VPNCheckResult> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String apiUrl = "https://vpnapi.io/api/" + ip + "?key=" + apiKey;

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

                    // Extract security information
                    JSONObject security = (JSONObject) jsonResponse.get("security");

                    if (security != null) {
                        boolean isVPN = (boolean) security.get("vpn");
                        boolean isProxy = (boolean) security.get("proxy");
                        boolean isTor = (boolean) security.get("tor");
                        boolean isRelay = (boolean) security.get("relay");

                        // Check if any security issue is detected
                        boolean hasSecurityIssue = isVPN || isProxy || isTor || isRelay;

                        // Get network information for better context
                        JSONObject network = (JSONObject) jsonResponse.get("network");
                        String asnOrg = "";
                        if (network != null) {
                            asnOrg = (String) network.get("autonomous_system_organization");
                        }

                        // Build reason message
                        List<String> securityIssues = new ArrayList<>();
                        if (isVPN) securityIssues.add("VPN");
                        if (isProxy) securityIssues.add("Proxy");
                        if (isTor) securityIssues.add("Tor");
                        if (isRelay) securityIssues.add("Relay");

                        String reason;
                        if (hasSecurityIssue) {
                            reason = String.join(", ", securityIssues) + " detected";
                            if (!asnOrg.isEmpty()) {
                                reason += " (ASN: " + asnOrg + ")";
                            }
                        } else {
                            reason = "No security issues detected";
                        }

                        // Log to database
                        plugin.getDatabaseManager().logCheck(playerName, ip, hasSecurityIssue, "VPNAPI", reason);

                        future.complete(new APIManager.VPNCheckResult(hasSecurityIssue, reason, "VPNAPI"));
                    } else {
                        plugin.getLogger().warning("VPNAPI returned an unexpected response format");
                        future.complete(new APIManager.VPNCheckResult(false, "Invalid response format", "VPNAPI"));
                    }
                } else {
                    plugin.getLogger().warning("VPNAPI returned error code: " + responseCode);
                    future.complete(new APIManager.VPNCheckResult(false, "API error: " + responseCode, "VPNAPI"));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking IP with VPNAPI: " + e.getMessage());
                future.complete(new APIManager.VPNCheckResult(false, "API error: " + e.getMessage(), "VPNAPI"));
            }
        });

        return future;
    }
}