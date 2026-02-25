package com.gearlimitations;

import com.gearlimitations.TierManager.GearTier;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class GearListener implements Listener {

    private final GearLimitations plugin;
    private final TierManager tierManager;

    public GearListener(GearLimitations plugin) {
        this.plugin = plugin;
        this.tierManager = plugin.getTierManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        
        if (tierManager.isItemLocked(item)) {
            event.setCancelled(true);
            sendLockedMessage(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (isArmorSlot(event.getSlotType()) || isArmorSlotByNumber(event.getRawSlot())) {
            if (tierManager.isItemLocked(cursorItem)) {
                event.setCancelled(true);
                sendLockedMessage(player, cursorItem);
                return;
            }
        }

        if (event.isShiftClick() && currentItem != null) {
            if (tierManager.isArmor(currentItem.getType()) && tierManager.isItemLocked(currentItem)) {
                event.setCancelled(true);
                sendLockedMessage(player, currentItem);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null) {
            return;
        }

        if (tierManager.isItemLocked(item)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR ||
                event.getAction() == Action.RIGHT_CLICK_BLOCK ||
                event.getAction() == Action.LEFT_CLICK_AIR ||
                event.getAction() == Action.LEFT_CLICK_BLOCK) {
                
                event.setCancelled(true);
                sendLockedMessage(player, item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        
        if (tierManager.isItemLocked(mainHand)) {
            event.setCancelled(true);
            sendLockedMessage(player, mainHand);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = event.getMainHandItem();
        ItemStack offHandItem = event.getOffHandItem();

        if (tierManager.isItemLocked(mainHandItem) || tierManager.isItemLocked(offHandItem)) {
            event.setCancelled(true);
            ItemStack lockedItem = tierManager.isItemLocked(mainHandItem) ? mainHandItem : offHandItem;
            sendLockedMessage(player, lockedItem);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        if (tierManager.isItemLocked(tool) && tierManager.isToolOrWeapon(tool.getType())) {
            event.setCancelled(true);
            sendLockedMessage(player, tool);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) {
            return;
        }

        if (!plugin.getConfig().getBoolean("xp-effect.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        Location location = player.getLocation().add(0, 1, 0);
        
        float volume = (float) plugin.getConfig().getDouble("xp-effect.sound-volume", 0.7);
        float pitch = (float) plugin.getConfig().getDouble("xp-effect.sound-pitch", 1.0);
        int particleCount = plugin.getConfig().getInt("xp-effect.particle-count", 8);

        player.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, volume, pitch);
        
        player.getWorld().spawnParticle(
                Particle.INSTANT_EFFECT,
                location,
                particleCount,
                0.3, 0.3, 0.3,
                0.1
        );
    }

    private boolean isArmorSlot(InventoryType.SlotType slotType) {
        return slotType == InventoryType.SlotType.ARMOR;
    }

    private boolean isArmorSlotByNumber(int rawSlot) {
        return rawSlot >= 36 && rawSlot <= 39;
    }

    private void sendLockedMessage(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        
        GearTier tier = tierManager.getTierForMaterial(item.getType());
        if (tier == null) {
            return;
        }

        String message = plugin.getConfig().getString("messages.tier-locked", 
                "&cYou cannot use %tier% gear yet! This tier is still locked.");
        message = message.replace("%tier%", tier.name());
        
        player.sendMessage(Component.text(colorize(message)));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}
