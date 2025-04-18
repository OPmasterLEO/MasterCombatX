package net.opmasterleo.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class Update {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Kaleshnikk/Combat/releases/latest";
    private static String latestVersion;
    private static String downloadUrl;
    private static boolean updateConfirmationRequired = true;
    private static long confirmationTimeout = 0;

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static void checkForUpdates(Plugin plugin) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> performUpdateCheck(plugin));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performUpdateCheck(plugin));
        }
    }

    public static void notifyOnServerOnline(Plugin plugin) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                String pluginName = plugin.getDescription().getName();
                String currentVersion = plugin.getDescription().getVersion();

                if (latestVersion == null) {
                    Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
                    return;
                }

                if (isNewerVersion(currentVersion, latestVersion)) {
                    Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» Unable to check your version! Self-built? Build information: Version " + currentVersion + ", latest: " + latestVersion);
                } else if (currentVersion.equalsIgnoreCase(latestVersion)) {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " version.");
                } else {
                    Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» This server is running " + pluginName + " version " + currentVersion +
                            " but the latest is " + latestVersion + ".");
                    Bukkit.getConsoleSender().sendMessage("§eUse /combat update to update to the latest version.");
                }
            }, 20L * 3); // 3 seconds delay
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                String pluginName = plugin.getDescription().getName();
                String currentVersion = plugin.getDescription().getVersion();

                if (latestVersion == null) {
                    Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
                    return;
                }

                if (isNewerVersion(currentVersion, latestVersion)) {
                    Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» Unable to check your version! Self-built? Build information: Version " + currentVersion + ", latest: " + latestVersion);
                } else if (currentVersion.equalsIgnoreCase(latestVersion)) {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " version.");
                } else {
                    Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» This server is running " + pluginName + " version " + currentVersion +
                            " but the latest is " + latestVersion + ".");
                    Bukkit.getConsoleSender().sendMessage("§eUse /combat update to update to the latest version.");
                }
            }, 20L * 3); // 3 seconds delay
        }
    }

    public static void notifyOnPlayerJoin(Player player, Plugin plugin) {
        if (!player.isOp()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("update-notify-chat", false)) {
            return;
        }

        String pluginName = plugin.getDescription().getName();
        String currentVersion = plugin.getDescription().getVersion();

        if (latestVersion == null) {
            player.sendMessage("§c[" + pluginName + "]» Unable to fetch update information.");
            return;
        }

        if (currentVersion.equalsIgnoreCase(latestVersion)) {
            player.sendMessage("§a[" + pluginName + "]» This server is running the latest " + pluginName + " version.");
        } else {
            player.sendMessage("§e[" + pluginName + "]» This server is running " + pluginName + " version " + currentVersion +
                    " but the latest is " + latestVersion + ".");
            player.sendMessage("§e[" + pluginName + "]» Run /combat update again if you confirm to update the plugin version.");
        }
    }

    public static void downloadAndReplaceJar(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        if (latestVersion == null || downloadUrl == null) {
            Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» No update information available. Please check for updates first.");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (updateConfirmationRequired || currentTime > confirmationTimeout) {
            Bukkit.getConsoleSender().sendMessage("§e[" + pluginName + "]» Run /combat update again if you confirm to update the plugin version.");
            updateConfirmationRequired = false;
            confirmationTimeout = currentTime + 10000; // Set timeout to 10 seconds
            return;
        }

        String currentVersion = plugin.getDescription().getVersion();
        if (isNewerVersion(currentVersion, latestVersion)) {
            Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» This server is running a newer version (" + currentVersion + ") than the latest (" + latestVersion + "), skipping update.");
            return;
        }

        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> performJarReplacement(plugin));
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> performJarReplacement(plugin));
        }
    }

    private static void performUpdateCheck(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(GITHUB_API_URL).openConnection();
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
        }
    }

    private static void performJarReplacement(Plugin plugin) {
        String pluginName = plugin.getDescription().getName();
        try {
            File pluginFile = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            File tempFile = new File(pluginFile.getParent(), pluginName + "-" + latestVersion + ".jar");

            HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
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

            if (tempFile.exists()) {
                Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» Download complete. Replacing the old jar...");
                if (pluginFile.delete() && tempFile.renameTo(pluginFile)) {
                    Bukkit.getConsoleSender().sendMessage("§a[" + pluginName + "]» Update successful! Please restart your server to apply the changes.");
                } else {
                    Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to replace the old jar. Please replace it manually.");
                }
            } else {
                Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» Failed to download the latest jar.");
            }
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("§c[" + pluginName + "]» An error occurred while downloading the update: " + e.getMessage());
        }
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String parseVersion(String jsonResponse) {
        String tagPrefix = "\"tag_name\":\"";
        int startIndex = jsonResponse.indexOf(tagPrefix) + tagPrefix.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        return jsonResponse.substring(startIndex, endIndex);
    }

    private static String parseDownloadUrl(String jsonResponse) {
        String urlPrefix = "\"browser_download_url\":\"";
        int startIndex = jsonResponse.indexOf(urlPrefix) + urlPrefix.length();
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        return startIndex > urlPrefix.length() - 1 ? jsonResponse.substring(startIndex, endIndex) : null;
    }

    private static boolean isNewerVersion(String currentVersion, String latestVersion) {
        currentVersion = currentVersion.replaceAll("[^0-9.]", "");
        latestVersion = latestVersion.replaceAll("[^0-9.]", "");

        String[] currentParts = currentVersion.split("\\.");
        String[] latestParts = latestVersion.split("\\.");
        for (int i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            if (currentPart > latestPart) {
                return true;
            } else if (currentPart < latestPart) {
                return false;
            }
        }
        return false;
    }
}
