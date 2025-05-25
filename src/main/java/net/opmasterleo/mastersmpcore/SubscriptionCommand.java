package net.opmasterleo.mastersmpcore;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;

public class SubscriptionCommand implements CommandExecutor, Listener, TabCompleter {

    private final JavaPlugin plugin;
    private final LuckPerms luckPerms;
    private final Map<UUID, String> offlineMessages = new HashMap<>();
    private final FileConfiguration config;

    public SubscriptionCommand(JavaPlugin plugin, LuckPerms luckPerms, FileConfiguration config) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.config = config;
        EventBus bus = luckPerms.getEventBus();
        bus.subscribe(plugin, UserDataRecalculateEvent.class, this::onUserDataRecalculate);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) && !sender.isOp()) {
            sender.sendMessage(main.translateHexColorCodes("&cYou do not have permission."));
            return true;
        }
        if (args.length < 3 || (!args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("remove"))
                || (args[0].equalsIgnoreCase("add") && args.length != 4)
                || (args[0].equalsIgnoreCase("remove") && args.length != 3)) {
            sender.sendMessage(main.translateHexColorCodes("&cUsage: /subscription add <user> <rank> <duration> OR /subscription remove <user> <rank>"));
            return true;
        }
        String action = args[0];
        String username = args[1];
        String rank = args[2];

        if (action.equalsIgnoreCase("remove")) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                UUID uuid = Bukkit.getOfflinePlayer(username).getUniqueId();
                User user = luckPerms.getUserManager().loadUser(uuid).join();
                if (user == null) {
                    sender.sendMessage(main.translateHexColorCodes("&cCould not find user " + username));
                    return;
                }
                List<Node> toRemove = user.getNodes().stream()
                    .filter(n -> n.getType() == NodeType.INHERITANCE && n.getKey().equals("group." + rank))
                    .collect(Collectors.toList());
                toRemove.forEach(n -> user.data().remove(n));
                luckPerms.getUserManager().saveUser(user);

                sender.sendMessage(main.translateHexColorCodes("&aRemoved group " + rank + " from " + username));

                Player target = Bukkit.getPlayerExact(username);
                String msg = main.translateHexColorCodes(config.getString("subscription.revoked_message",
                        "&#a8a8a8Your &#00a4fc%rank% &#a8a8a8subscription has been revoked by an admin!")
                        .replace("%rank%", rank));
                if (target != null) {
                    target.sendMessage(msg);
                } else {
                    offlineMessages.put(uuid, msg);
                }
            });
            return true;
        }

        long secondsTmp = -1;
        boolean permanentTmp = false;
        try {
            if (args[3].equalsIgnoreCase("perma") || args[3].equalsIgnoreCase("perm") || args[3].equalsIgnoreCase("permanent")) {
                permanentTmp = true;
            } else {
                secondsTmp = parseDuration(args[3]);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(main.translateHexColorCodes("&cInvalid duration format."));
            return true;
        }
        final boolean permanent = permanentTmp;
        final long seconds = secondsTmp;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = Bukkit.getOfflinePlayer(username).getUniqueId();
            User user = luckPerms.getUserManager().loadUser(uuid).join();
            if (user == null) {
                sender.sendMessage(main.translateHexColorCodes("&cCould not find user " + username));
                return;
            }
            if (user.getNodes().stream().anyMatch(n -> n.getKey().equals("group." + rank))) {
                sender.sendMessage(main.translateHexColorCodes("&cUser is already in group " + rank));
                return;
            }
            Node node;
            String timeLeft;
            if (permanent) {
                node = Node.builder("group." + rank).build();
                timeLeft = "permanently";
            } else {
                Instant expiry = Instant.now().plusSeconds(seconds);
                node = Node.builder("group." + rank).expiry(expiry).build();
                timeLeft = formatDuration(seconds);
            }
            user.data().add(node);
            luckPerms.getUserManager().saveUser(user);

            sender.sendMessage(main.translateHexColorCodes("&aAdded group " + rank + " to " + username + " for " + timeLeft));

            Player target = Bukkit.getPlayerExact(username);
            String msg = main.translateHexColorCodes(config.getString("subscription.online_message",
                    "&#a8a8a8You received &#00a4fc%rank% &#a8a8a8Rank!\n&#a8a8a8You have &#00a4fc%time% &#a8a8a8left of subscription time.")
                    .replace("%rank%", rank)
                    .replace("%time%", timeLeft));
            if (target != null) {
                target.sendMessage(msg);
            } else {
                offlineMessages.put(uuid, msg);
            }
        });
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (offlineMessages.containsKey(id)) {
            e.getPlayer().sendMessage(offlineMessages.remove(id));
        }
    }

    private void onUserDataRecalculate(UserDataRecalculateEvent e) {
        User user = e.getUser();
        main.runSync(() -> {
            Player player = Bukkit.getPlayer(user.getUniqueId());
            if (player == null) return;

            user.getNodes().stream()
                .filter(node -> node.getKey().startsWith("group.") && node.hasExpiry())
                .forEach(node -> {
                    Instant expiry = node.getExpiry();
                    if (expiry != null && expiry.isBefore(Instant.now())) {
                        String rank = node.getKey().substring("group.".length());
                        String expiredMessage = config.getString("subscription.expired_message",
                                "&#a8a8a8Your &#00a4fc%rank% &#a8a8a8subscription has expired!")
                                .replace("%rank%", rank);
                        String thankYouMessage = config.getString("subscription.thank_you_message",
                                "&#a8a8a8Thank you for being a part of &#00a4fcMasterSMP!");

                        player.sendMessage(main.translateHexColorCodes(expiredMessage));
                        player.sendMessage(main.translateHexColorCodes(thankYouMessage));

                        user.data().remove(node);
                        luckPerms.getUserManager().saveUser(user);
                    }
                });
        });
    }

    private long parseDuration(String in) {
        long total = 0;
        in = in.replaceAll("(?<=\\d)(?=[a-zA-Z])", " ");
        Matcher m = Pattern.compile("(\\d+)\\s*([dhms])", Pattern.CASE_INSENSITIVE).matcher(in);
        boolean found = false;
        while (m.find()) {
            found = true;
            long v = Long.parseLong(m.group(1));
            switch (Character.toLowerCase(m.group(2).charAt(0))) {
                case 'd': total += TimeUnit.DAYS.toSeconds(v); break;
                case 'h': total += TimeUnit.HOURS.toSeconds(v); break;
                case 'm': total += TimeUnit.MINUTES.toSeconds(v); break;
                case 's': total += v; break;
            }
        }
        if (!found) throw new IllegalArgumentException();
        return total;
    }

    private String formatDuration(long seconds) {
        long days = TimeUnit.SECONDS.toDays(seconds);
        seconds -= TimeUnit.DAYS.toSeconds(days);
        long hours = TimeUnit.SECONDS.toHours(seconds);
        seconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds);
        seconds -= TimeUnit.MINUTES.toSeconds(minutes);

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (seconds > 0) result.append(seconds).append("s");

        return result.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("add", "remove").stream()
                    .filter(x -> x.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return luckPerms.getGroupManager().getLoadedGroups().stream()
                    .map(g -> g.getName())
                    .filter(r -> r.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            return Arrays.asList("perma", "1d", "7d", "30d", "12h", "1h", "15m").stream()
                    .filter(d -> d.startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
