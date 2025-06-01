package allenme.top.myantivpn.apicheck;

import java.util.concurrent.CompletableFuture;

public interface VPNCheckService {
    CompletableFuture<APIManager.VPNCheckResult> checkIP(String ip, String playerName);
}