package allenme.top.myantivpn.banservice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.*;
import java.util.*;

public class BanServiceManager {
    private final File banlistFile;
    private BanList banList;
    private final Gson gson;

    public static class BanList {
        private Set<String> ipBans;
        private Set<String> ispBans;
        private Set<String> asnBans;

        public BanList() {
            this.ipBans = new HashSet<>();
            this.ispBans = new HashSet<>();
            this.asnBans = new HashSet<>();
        }

        // Getters and setters
        public Set<String> getIpBans() { return ipBans; }
        public Set<String> getIspBans() { return ispBans; }
        public Set<String> getAsnBans() { return asnBans; }
    }

    public BanServiceManager(File dataFolder) {
        this.banlistFile = new File(dataFolder, "banlist.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadBanList();
    }

    private void loadBanList() {
        if (!banlistFile.exists()) {
            banList = new BanList();
            saveBanList();
            return;
        }

        try (Reader reader = new FileReader(banlistFile)) {
            banList = gson.fromJson(reader, BanList.class);
            if (banList == null) banList = new BanList();
        } catch (IOException e) {
            e.printStackTrace();
            banList = new BanList();
        }
    }

    private void saveBanList() {
        try (Writer writer = new FileWriter(banlistFile)) {
            gson.toJson(banList, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean addBan(String type, String value) {
        Set<String> targetList = null;
        switch (type.toLowerCase()) {
            case "ip":
                targetList = banList.ipBans;
                break;
            case "isp":
                targetList = banList.ispBans;
                break;
            case "asn":
                targetList = banList.asnBans;
                break;
        }

        if (targetList == null) return false;

        boolean result = targetList.add(value.toLowerCase());
        if (result) saveBanList();
        return result;
    }

    public boolean removeBan(String type, String value) {
        Set<String> targetList = null;
        switch (type.toLowerCase()) {
            case "ip":
                targetList = banList.ipBans;
                break;
            case "isp":
                targetList = banList.ispBans;
                break;
            case "asn":
                targetList = banList.asnBans;
                break;
        }

        if (targetList == null) return false;

        boolean result = targetList.remove(value.toLowerCase());
        if (result) saveBanList();
        return result;
    }

    public Set<String> getBanList(String type) {
        switch (type.toLowerCase()) {
            case "ip":
                return Collections.unmodifiableSet(banList.ipBans);
            case "isp":
                return Collections.unmodifiableSet(banList.ispBans);
            case "asn":
                return Collections.unmodifiableSet(banList.asnBans);
            default:
                return Collections.emptySet();
        }
    }

    public boolean isIpBanned(String ip) {
        return banList.ipBans.contains(ip.toLowerCase());
    }

    public boolean isIspBanned(String isp) {
        return banList.ispBans.contains(isp.toLowerCase());
    }

    public boolean isAsnBanned(String asn) {
        return banList.asnBans.contains(asn.toLowerCase());
    }
}