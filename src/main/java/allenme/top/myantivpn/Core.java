package allenme.top.myantivpn;

import allenme.top.myantivpn.commands.CommandManager;
import allenme.top.myantivpn.listener.PlayerLoginListener;
import allenme.top.myantivpn.apicheck.APIManager;
import allenme.top.myantivpn.database.DatabaseManager;
import allenme.top.myantivpn.utils.MessageManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.io.File;

public class Core extends JavaPlugin {

    private static Core instance;
    private APIManager apiManager;
    private DatabaseManager databaseManager;
    private FileConfiguration config;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        saveDefaultConfig();
        config = getConfig();

        // Initialize database
        initDatabase();

        // Initialize API Manager
        apiManager = new APIManager(this);
        this.messageManager = new MessageManager(this);

        // Register commands
        getCommand("antivpn").setExecutor(new CommandManager(this));

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerLoginListener(this), this);

        getLogger().info("MyAntiVPN has been enabled!");
    }

    @Override
    public void onDisable() {
        // Close database connections
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }

        getLogger().info("MyAntiVPN has been disabled!");
    }

    private void initDatabase() {
        // Ensure database directory exists
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Initialize database manager
        databaseManager = new DatabaseManager(this);
        databaseManager.initDatabase();
    }

    public static Core getInstance() {
        return instance;
    }

    public APIManager getApiManager() {
        return apiManager;
    }
    public MessageManager getMessageManager() {
        return messageManager;
    }
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public void executeVPNDetectedCommand(String playerName) {
        if (getConfig().getBoolean("Detection.Command.Enabled", true)) {
            String command = getConfig().getString("Detection.Command.Execute", "kick %player% VPN/Proxy usage is not allowed!");
            if (command != null && !command.isEmpty()) {
                command = command.replace("%player%", playerName);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }
    }

    public boolean shouldCheckOnJoin() {
        return getConfig().getBoolean("Detection.CheckOnJoin", true);
    }
}