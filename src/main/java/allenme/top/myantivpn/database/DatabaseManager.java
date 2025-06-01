package allenme.top.myantivpn.database;

import allenme.top.myantivpn.Core;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private final Core plugin;
    private Connection cacheConnection;
    private Connection playerConnection;

    public DatabaseManager(Core plugin) {
        this.plugin = plugin;
    }

    public void initDatabase() {
        try {
            // Load JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Initialize cache database
            File cacheFile = new File(plugin.getDataFolder(), "cache.db");
            cacheConnection = DriverManager.getConnection("jdbc:sqlite:" + cacheFile.getAbsolutePath());

            Statement cacheStatement = cacheConnection.createStatement();
            cacheStatement.execute(
                    "CREATE TABLE IF NOT EXISTS ip_cache (" +
                            "ip TEXT PRIMARY KEY, " +
                            "is_vpn BOOLEAN, " +
                            "reason TEXT, " +
                            "service TEXT, " +
                            "last_checked TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );
            cacheStatement.close();

            // Initialize player database
            File playerFile = new File(plugin.getDataFolder(), "player.db");
            playerConnection = DriverManager.getConnection("jdbc:sqlite:" + playerFile.getAbsolutePath());

            Statement playerStatement = playerConnection.createStatement();
            playerStatement.execute(
                    "CREATE TABLE IF NOT EXISTS player_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "player_name TEXT, " +
                            "ip TEXT, " +
                            "is_vpn BOOLEAN, " +
                            "service TEXT, " +
                            "reason TEXT, " +
                            "check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );
            playerStatement.close();

            plugin.getLogger().info("Database initialized successfully");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            if (cacheConnection != null) {
                cacheConnection.close();
            }
            if (playerConnection != null) {
                playerConnection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database connections: " + e.getMessage());
        }
    }

    public void cacheIPResult(String ip, boolean isVPN, String reason, String service) {
        try {
            PreparedStatement statement = cacheConnection.prepareStatement(
                    "INSERT OR REPLACE INTO ip_cache (ip, is_vpn, reason, service, last_checked) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)"
            );

            statement.setString(1, ip);
            statement.setBoolean(2, isVPN);
            statement.setString(3, reason);
            statement.setString(4, service);

            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error caching IP result: " + e.getMessage());
        }
    }

    public CachedIPResult getCachedResult(String ip) {
        try {
            PreparedStatement statement = cacheConnection.prepareStatement(
                    "SELECT is_vpn, reason, service, last_checked FROM ip_cache WHERE ip = ?"
            );

            statement.setString(1, ip);
            ResultSet result = statement.executeQuery();

            if (result.next()) {
                boolean isVPN = result.getBoolean("is_vpn");
                String reason = result.getString("reason");
                String service = result.getString("service");
                Timestamp lastChecked = result.getTimestamp("last_checked");

                result.close();
                statement.close();

                return new CachedIPResult(ip, isVPN, reason, service, lastChecked);
            }

            result.close();
            statement.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error retrieving cached IP result: " + e.getMessage());
        }

        return null; // No cached result found
    }

    public void logCheck(String playerName, String ip, boolean isVPN, String service, String reason) {
        try {
            PreparedStatement statement = playerConnection.prepareStatement(
                    "INSERT INTO player_logs (player_name, ip, is_vpn, service, reason) VALUES (?, ?, ?, ?, ?)"
            );

            statement.setString(1, playerName);
            statement.setString(2, ip);
            statement.setBoolean(3, isVPN);
            statement.setString(4, service);
            statement.setString(5, reason);

            statement.executeUpdate();
            statement.close();

            // Also update the cache
            cacheIPResult(ip, isVPN, reason, service);
        } catch (SQLException e) {
            plugin.getLogger().warning("Error logging check: " + e.getMessage());
        }
    }

    public List<LogEntry> getPlayerLogs(String playerName) {
        List<LogEntry> logs = new ArrayList<>();

        try {
            PreparedStatement statement = playerConnection.prepareStatement(
                    "SELECT * FROM player_logs WHERE player_name = ? ORDER BY check_time DESC"
            );

            statement.setString(1, playerName);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                logs.add(new LogEntry(
                        result.getInt("id"),
                        result.getString("player_name"),
                        result.getString("ip"),
                        result.getBoolean("is_vpn"),
                        result.getString("service"),
                        result.getString("reason"),
                        result.getTimestamp("check_time")
                ));
            }

            result.close();
            statement.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error retrieving player logs: " + e.getMessage());
        }

        return logs;
    }

    public List<LogEntry> getRecentLogs(int limit) {
        List<LogEntry> logs = new ArrayList<>();

        try {
            PreparedStatement statement = playerConnection.prepareStatement(
                    "SELECT * FROM player_logs ORDER BY check_time DESC LIMIT ?"
            );

            statement.setInt(1, limit);
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                logs.add(new LogEntry(
                        result.getInt("id"),
                        result.getString("player_name"),
                        result.getString("ip"),
                        result.getBoolean("is_vpn"),
                        result.getString("service"),
                        result.getString("reason"),
                        result.getTimestamp("check_time")
                ));
            }

            result.close();
            statement.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error retrieving recent logs: " + e.getMessage());
        }

        return logs;
    }

    public static class CachedIPResult {
        private final String ip;
        private final boolean isVPN;
        private final String reason;
        private final String service;
        private final Timestamp lastChecked;

        public CachedIPResult(String ip, boolean isVPN, String reason, String service, Timestamp lastChecked) {
            this.ip = ip;
            this.isVPN = isVPN;
            this.reason = reason;
            this.service = service;
            this.lastChecked = lastChecked;
        }

        public String getIp() {
            return ip;
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

        public Timestamp getLastChecked() {
            return lastChecked;
        }
    }

    public static class LogEntry {
        private final int id;
        private final String playerName;
        private final String ip;
        private final boolean isVPN;
        private final String service;
        private final String reason;
        private final Timestamp checkTime;

        public LogEntry(int id, String playerName, String ip, boolean isVPN, String service, String reason, Timestamp checkTime) {
            this.id = id;
            this.playerName = playerName;
            this.ip = ip;
            this.isVPN = isVPN;
            this.service = service;
            this.reason = reason;
            this.checkTime = checkTime;
        }

        public int getId() {
            return id;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getIp() {
            return ip;
        }

        public boolean isVPN() {
            return isVPN;
        }

        public String getService() {
            return service;
        }

        public String getReason() {
            return reason;
        }

        public Timestamp getCheckTime() {
            return checkTime;
        }
    }
}