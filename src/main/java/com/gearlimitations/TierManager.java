package com.gearlimitations;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TierManager {

    private final GearLimitations plugin;
    private final Set<GearTier> lockedTiers;
    private final Map<GearTier, LocalDate> unlockDates;
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public enum GearTier {
        WOODEN(false),
        STONE(false),
        IRON(false),
        GOLDEN(true),
        COPPER(true),
        DIAMOND(true),
        NETHERITE(true);

        private final boolean lockableByDefault;

        GearTier(boolean lockableByDefault) {
            this.lockableByDefault = lockableByDefault;
        }

        public boolean isLockableByDefault() {
            return lockableByDefault;
        }
    }

    private static final Map<GearTier, Set<Material>> TIER_MATERIALS = new EnumMap<>(GearTier.class);

    static {
        TIER_MATERIALS.put(GearTier.WOODEN, EnumSet.of(
                Material.WOODEN_SWORD, Material.WOODEN_PICKAXE, Material.WOODEN_AXE,
                Material.WOODEN_SHOVEL, Material.WOODEN_HOE
        ));

        TIER_MATERIALS.put(GearTier.STONE, EnumSet.of(
                Material.STONE_SWORD, Material.STONE_PICKAXE, Material.STONE_AXE,
                Material.STONE_SHOVEL, Material.STONE_HOE
        ));

        TIER_MATERIALS.put(GearTier.IRON, EnumSet.of(
                Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
                Material.IRON_SHOVEL, Material.IRON_HOE,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                Material.IRON_LEGGINGS, Material.IRON_BOOTS
        ));

        TIER_MATERIALS.put(GearTier.GOLDEN, EnumSet.of(
                Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE,
                Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE,
                Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
                Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE
        ));

        TIER_MATERIALS.put(GearTier.COPPER, EnumSet.of(
                Material.BRUSH, Material.COPPER_BLOCK, Material.CUT_COPPER,
                Material.LIGHTNING_ROD, Material.SPYGLASS
        ));

        TIER_MATERIALS.put(GearTier.DIAMOND, EnumSet.of(
                Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE,
                Material.DIAMOND_SHOVEL, Material.DIAMOND_HOE,
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
        ));

        TIER_MATERIALS.put(GearTier.NETHERITE, EnumSet.of(
                Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE,
                Material.NETHERITE_SHOVEL, Material.NETHERITE_HOE,
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
        ));
    }

    public TierManager(GearLimitations plugin) {
        this.plugin = plugin;
        this.lockedTiers = EnumSet.noneOf(GearTier.class);
        this.unlockDates = new EnumMap<>(GearTier.class);
        loadFromConfig();
    }

    private void loadFromConfig() {
        FileConfiguration config = plugin.getConfig();
        
        List<String> locked = config.getStringList("locked-tiers");
        for (String tierName : locked) {
            try {
                GearTier tier = GearTier.valueOf(tierName.toUpperCase());
                lockedTiers.add(tier);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid tier in config: " + tierName);
            }
        }

        if (config.isConfigurationSection("unlock-dates")) {
            for (String tierName : config.getConfigurationSection("unlock-dates").getKeys(false)) {
                try {
                    GearTier tier = GearTier.valueOf(tierName.toUpperCase());
                    String dateStr = config.getString("unlock-dates." + tierName);
                    if (dateStr != null && !dateStr.equals("null")) {
                        LocalDate date = LocalDate.parse(dateStr, DATE_FORMAT);
                        unlockDates.put(tier, date);
                    }
                } catch (IllegalArgumentException | DateTimeParseException ignored) {
                }
            }
        }
    }

    public void saveToConfig() {
        FileConfiguration config = plugin.getConfig();
        
        List<String> lockedList = new ArrayList<>();
        for (GearTier tier : lockedTiers) {
            lockedList.add(tier.name());
        }
        config.set("locked-tiers", lockedList);

        for (GearTier tier : GearTier.values()) {
            if (tier.isLockableByDefault()) {
                LocalDate date = unlockDates.get(tier);
                config.set("unlock-dates." + tier.name(), date != null ? date.format(DATE_FORMAT) : null);
            }
        }

        plugin.saveConfig();
    }

    public boolean isTierLocked(GearTier tier) {
        return lockedTiers.contains(tier);
    }

    public boolean isItemLocked(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        
        GearTier tier = getTierForMaterial(item.getType());
        return tier != null && isTierLocked(tier);
    }

    public GearTier getTierForMaterial(Material material) {
        for (Map.Entry<GearTier, Set<Material>> entry : TIER_MATERIALS.entrySet()) {
            if (entry.getValue().contains(material)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    public boolean isToolOrWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_PICKAXE") ||
               name.endsWith("_AXE") || name.endsWith("_SHOVEL") ||
               name.endsWith("_HOE") || material == Material.BRUSH ||
               material == Material.SPYGLASS;
    }

    public UnlockResult unlockTier(GearTier tier, LocalDate date) {
        if (!lockedTiers.contains(tier)) {
            return UnlockResult.ALREADY_UNLOCKED;
        }

        if (tier == GearTier.DIAMOND || tier == GearTier.NETHERITE) {
            boolean goldenUnlocked = unlockDates.containsKey(GearTier.GOLDEN);
            boolean copperUnlocked = unlockDates.containsKey(GearTier.COPPER);
            
            if (!goldenUnlocked && !copperUnlocked) {
                return UnlockResult.PREREQUISITE_NOT_MET;
            }
        }

        for (Map.Entry<GearTier, LocalDate> entry : unlockDates.entrySet()) {
            if (entry.getValue().equals(date)) {
                return UnlockResult.SAME_DATE_EXISTS;
            }
        }

        lockedTiers.remove(tier);
        unlockDates.put(tier, date);
        saveToConfig();
        
        return UnlockResult.SUCCESS;
    }

    public boolean isValidDateFormat(String dateStr) {
        try {
            LocalDate.parse(dateStr, DATE_FORMAT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DATE_FORMAT);
    }

    public Set<GearTier> getLockedTiers() {
        return EnumSet.copyOf(lockedTiers);
    }

    public Map<GearTier, LocalDate> getUnlockDates() {
        return new EnumMap<>(unlockDates);
    }

    public List<GearTier> getLockableTiers() {
        List<GearTier> lockable = new ArrayList<>();
        for (GearTier tier : GearTier.values()) {
            if (tier.isLockableByDefault()) {
                lockable.add(tier);
            }
        }
        return lockable;
    }

    public enum UnlockResult {
        SUCCESS,
        ALREADY_UNLOCKED,
        PREREQUISITE_NOT_MET,
        SAME_DATE_EXISTS,
        INVALID_TIER
    }
}
