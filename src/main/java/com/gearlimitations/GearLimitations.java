
package com.gearlimitations;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class GearLimitations extends JavaPlugin {

    private static GearLimitations instance;
    private TierManager tierManager;

    @Override
    public void onEnable() {
        instance = this;
        
        saveDefaultConfig();
        
        tierManager = new TierManager(this);
        
        getServer().getPluginManager().registerEvents(new GearListener(this), this);
        
        GearCommand commandExecutor = new GearCommand(this);
        Objects.requireNonNull(getCommand("gl")).setExecutor(commandExecutor);
        Objects.requireNonNull(getCommand("gl")).setTabCompleter(commandExecutor);
        
        getLogger().info("GearLimitations v" + getPluginMeta().getVersion() + " enabled!");
        getLogger().info("Locked tiers: " + tierManager.getLockedTiers());
    }

    @Override
    public void onDisable() {
        saveConfig();
        getLogger().info("GearLimitations disabled!");
    }

    public static GearLimitations getInstance() {
        return instance;
    }

    public TierManager getTierManager() {
        return tierManager;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        tierManager = new TierManager(this);
    }
}
