package com.gearlimitations;

import com.gearlimitations.TierManager.GearTier;
import com.gearlimitations.TierManager.UnlockResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class GearCommand implements CommandExecutor, TabCompleter {

    private final GearLimitations plugin;
    private final TierManager tierManager;
    private final Map<UUID, PendingUnlock> pendingUnlocks = new HashMap<>();

    public GearCommand(GearLimitations plugin) {
        this.plugin = plugin;
        this.tierManager = plugin.getTierManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gearlimitations.admin")) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-permission", 
                    "&cYou don't have permission to do this!"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "unlock" -> handleUnlock(sender, args);
            case "testsound" -> handleTestSound(sender);
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "&cThis command can only be used by players!");
            return;
        }

        if (args.length < 2) {
            sendMessage(sender, "&cUsage: /gl unlock <TIER>");
            sendMessage(sender, "&7Valid tiers: GOLDEN, COPPER, DIAMOND, NETHERITE");
            return;
        }

        String tierName = args[1].toUpperCase();
        GearTier tier;
        
        try {
            tier = GearTier.valueOf(tierName);
        } catch (IllegalArgumentException e) {
            sendMessage(sender, plugin.getConfig().getString("messages.invalid-tier",
                    "&cInvalid tier! Valid tiers: GOLDEN, COPPER, DIAMOND, NETHERITE"));
            return;
        }

        if (!tier.isLockableByDefault()) {
            sendMessage(sender, "&cThis tier cannot be unlocked (it's never locked).");
            return;
        }

        if (!tierManager.isTierLocked(tier)) {
            sendMessage(sender, plugin.getConfig().getString("messages.already-unlocked",
                    "&eTier %tier% is already unlocked.").replace("%tier%", tier.name()));
            return;
        }

        if (tier == GearTier.DIAMOND || tier == GearTier.NETHERITE) {
            boolean goldenUnlocked = tierManager.getUnlockDates().containsKey(GearTier.GOLDEN);
            boolean copperUnlocked = tierManager.getUnlockDates().containsKey(GearTier.COPPER);
            
            if (!goldenUnlocked && !copperUnlocked) {
                sendMessage(sender, plugin.getConfig().getString("messages.prerequisite-error",
                        "&cYou must unlock GOLDEN or COPPER before unlocking %tier%!")
                        .replace("%tier%", tier.name()));
                return;
            }
        }

        sendMessage(sender, plugin.getConfig().getString("messages.enter-date",
                "&eEnter the unlock date in chat (format: YYYY-MM-DD):"));
        
        pendingUnlocks.put(player.getUniqueId(), new PendingUnlock(tier, System.currentTimeMillis()));
        
        registerDateListener(player);
    }

    private void registerDateListener(Player player) {
        Listener chatListener = new Listener() {
            @EventHandler
            public void onChat(AsyncPlayerChatEvent event) {
                if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                    return;
                }

                PendingUnlock pending = pendingUnlocks.get(player.getUniqueId());
                if (pending == null) {
                    HandlerList.unregisterAll(this);
                    return;
                }

                if (System.currentTimeMillis() - pending.timestamp() > 60000) {
                    pendingUnlocks.remove(player.getUniqueId());
                    sendMessage(player, "&cUnlock request timed out.");
                    HandlerList.unregisterAll(this);
                    return;
                }

                event.setCancelled(true);
                String dateInput = event.getMessage().trim();

                if (!tierManager.isValidDateFormat(dateInput)) {
                    sendMessage(player, plugin.getConfig().getString("messages.invalid-date",
                            "&cInvalid date format! Please use YYYY-MM-DD"));
                    return;
                }

                LocalDate date = tierManager.parseDate(dateInput);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    UnlockResult result = tierManager.unlockTier(pending.tier(), date);
                    
                    switch (result) {
                        case SUCCESS -> {
                            String msg = plugin.getConfig().getString("messages.tier-unlocked",
                                    "&aTier %tier% has been unlocked on %date%!");
                            msg = msg.replace("%tier%", pending.tier().name())
                                    .replace("%date%", dateInput);
                            sendMessage(player, msg);
                            
                            Bukkit.broadcast(Component.text(colorize(
                                    "&6[GearLimitations] &a" + pending.tier().name() + " tier has been unlocked!")));
                            
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        }
                        case SAME_DATE_EXISTS -> sendMessage(player, plugin.getConfig().getString(
                                "messages.same-date-error", "&cYou cannot unlock multiple tiers on the same date!"));
                        case PREREQUISITE_NOT_MET -> sendMessage(player, plugin.getConfig().getString(
                                "messages.prerequisite-error", "&cYou must unlock GOLDEN or COPPER before unlocking %tier%!")
                                .replace("%tier%", pending.tier().name()));
                        case ALREADY_UNLOCKED -> sendMessage(player, plugin.getConfig().getString(
                                "messages.already-unlocked", "&eTier %tier% is already unlocked.")
                                .replace("%tier%", pending.tier().name()));
                        default -> sendMessage(player, "&cUnlock failed.");
                    }
                    
                    pendingUnlocks.remove(player.getUniqueId());
                });
                
                HandlerList.unregisterAll(this);
            }
        };

        Bukkit.getPluginManager().registerEvents(chatListener, plugin);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingUnlocks.containsKey(player.getUniqueId())) {
                pendingUnlocks.remove(player.getUniqueId());
                HandlerList.unregisterAll(chatListener);
            }
        }, 1200L);
    }

    private void handleTestSound(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "&cThis command can only be used by players!");
            return;
        }

        Location location = player.getLocation().add(0, 1, 0);
        float volume = (float) plugin.getConfig().getDouble("xp-effect.sound-volume", 0.7);
        float pitch = (float) plugin.getConfig().getDouble("xp-effect.sound-pitch", 1.0);
        int particleCount = plugin.getConfig().getInt("xp-effect.particle-count", 8);

        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume, pitch);
        player.getWorld().spawnParticle(Particle.INSTANT_EFFECT, location, particleCount, 0.3, 0.3, 0.3, 0.1);
        
        sendMessage(sender, "&aXP effect played!");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPluginConfig();
        sendMessage(sender, plugin.getConfig().getString("messages.config-reloaded", "&aConfiguration reloaded!"));
    }

    private void handleStatus(CommandSender sender) {
        sendMessage(sender, "&6=== GearLimitations Status ===");
        sendMessage(sender, "&7Locked Tiers: &c" + tierManager.getLockedTiers().stream()
                .map(GearTier::name)
                .collect(Collectors.joining(", ")));
        
        sendMessage(sender, "&7Unlock History:");
        Map<GearTier, LocalDate> dates = tierManager.getUnlockDates();
        if (dates.isEmpty()) {
            sendMessage(sender, "  &8No tiers unlocked yet.");
        } else {
            for (Map.Entry<GearTier, LocalDate> entry : dates.entrySet()) {
                sendMessage(sender, "  &a" + entry.getKey().name() + " &7- " + entry.getValue().toString());
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, "&6=== GearLimitations Commands ===");
        sendMessage(sender, "&e/gl unlock <TIER> &7- Unlock a gear tier");
        sendMessage(sender, "&e/gl status &7- View locked tiers and unlock history");
        sendMessage(sender, "&e/gl testsound &7- Test XP effect");
        sendMessage(sender, "&e/gl reload &7- Reload configuration");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("gearlimitations.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("unlock", "status", "testsound", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("unlock")) {
            List<String> lockedTierNames = tierManager.getLockedTiers().stream()
                    .map(GearTier::name)
                    .collect(Collectors.toList());
            return filterStartsWith(lockedTierNames, args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(Component.text(colorize(message)));
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    private record PendingUnlock(GearTier tier, long timestamp) {}
}
