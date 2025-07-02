package net.opmasterleo.combat.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class Update {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/OPmasterLEO/MasterCombat/releases/latest";
    private static String latestVersion;
    private static String downloadUrl;
    private static boolean updateCheckInProgress = false;
    private static boolean updateDownloadInProgress = false;

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static void checkForUpdates(Plugin plugin) {
        if (!updateCheckInProgress) {
            updateCheckInProgress = true;
            Bukkit.getConsoleSender().sendMessage("Checking for updates…");
            if (isFolia()) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> performUpdateCheck(plugin));
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performUpdateCheck(plugin));
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void notifyOnServerOnline(Plugin plugin) {
        Runnable notificationTask = () -> {
            String pluginName = plugin.getDescription().getName();
            String currentVersion = plugin.getDescription().getVersion();
            String normalizedCurrent = normalizeVersion(currentVersion);
            String normalizedLatest = latestVersion != null ? normalizeVersion(latestVersion) : null;

            if (latestVersion == null) {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
                return;
            }

            int comparison = compareVersions(normalizedCurrent, normalizedLatest);
            
            if (comparison == 0) {
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " (v" + currentVersion + ").");
            } else if (comparison < 0) {
                Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» An update is required, installed version v" + currentVersion + ", but latest is v" + latestVersion + ".");
                Bukkit.getConsoleSender().sendMessage("§eUse /combat update to update to the latest version.");
            } else {
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» You are running a developer build (v" + currentVersion + "), but the latest public version is v" + latestVersion + ".");
            }
        };

        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> notificationTask.run(), 20L * 3);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, notificationTask, 20L * 3);
        }
    }

    @SuppressWarnings("deprecation")
    public static void notifyOnPlayerJoin(Player player, Plugin plugin) {
        if (!player.isOp()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("update-notify-chat", false)) {
            return;
        }

        String pluginName = plugin.getDescription().getName();
        String currentVersion = plugin.getDescription().getVersion();
        String normalizedCurrent = normalizeVersion(currentVersion);
        String normalizedLatest = latestVersion != null ? normalizeVersion(latestVersion) : null;

        if (latestVersion == null) {
            player.sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
            return;
        }

        int comparison = compareVersions(normalizedCurrent, normalizedLatest);
        
        if (comparison == 0) {
            player.sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " (v" + currentVersion + ").");
        } else if (comparison < 0) {
            player.sendMessage("§e[" + pluginName + "]» This server is running " + pluginName + " version v" + currentVersion +
                    " but the latest is v" + latestVersion + ".");
        } else {
            player.sendMessage("§a[" + pluginName + "]» You are running a developer build (v" + currentVersion + "), but the latest public version is v" + latestVersion + ".");
        }
    }

    public static void downloadAndReplaceJar(Plugin plugin) {
        if (!updateDownloadInProgress) {
            updateDownloadInProgress = true;
            Bukkit.getConsoleSender().sendMessage("Downloading and applying the update...");
            if (isFolia()) {
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> performJarReplacement(plugin));
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performJarReplacement(plugin));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void performUpdateCheck(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(GITHUB_API_URL).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                latestVersion = parseVersion(response.toString());
                downloadUrl = parseDownloadUrl(response.toString());
            } else {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to check for updates. HTTP Response Code: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» An error occurred while checking for updates: " + e.getMessage());
        } finally {
            updateCheckInProgress = false;
        }
    }

    @SuppressWarnings("deprecation")
    private static void performJarReplacement(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        try {
            File updateFolder = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateFolder.exists() && !updateFolder.mkdirs()) {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to create update folder.");
                return;
            }

            File tempFile = new File(updateFolder, pluginName + "-" + latestVersion + ".jar");

            HttpURLConnection connection = (HttpURLConnection) URI.create(downloadUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» Update complete. The new version has been placed in the update folder.");
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» Please restart your server to apply the update.");
            } else {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to download the latest jar. File does not exist or is empty.");
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» An error occurred while downloading the jar: " + e.getMessage());
        } finally {
            updateDownloadInProgress = false;
        }
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, Update.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (IllegalStateException e) {
            Bukkit.getConsoleSender().sendMessage("§c[MasterCombat] Error checking for Folia: " + e.getMessage());
            return false;
        }
    }

    private static String normalizeVersion(String version) {
        return version.replaceAll("[^0-9.]", "");
    }

    private static int compareVersions(String v1, String v2) {
        if (v1 == null || v2 == null) return 0;
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }

    private static String parseVersion(String jsonResponse) {
        String tagPrefix = "\"tag_name\":\"";
        int startIndex = jsonResponse.indexOf(tagPrefix);
        if (startIndex == -1) return null;
        startIndex += tagPrefix.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        return jsonResponse.substring(startIndex, endIndex);
    }

    private static String parseDownloadUrl(String jsonResponse) {
        String urlPrefix = "\"browser_download_url\":\"";
        int startIndex = jsonResponse.indexOf(urlPrefix);
        if (startIndex == -1) return null;
        startIndex += urlPrefix.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        return jsonResponse.substring(startIndex, endIndex);
    }
}