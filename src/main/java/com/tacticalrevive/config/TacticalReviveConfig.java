package com.tacticalrevive.config;

import com.tacticalrevive.TacticalRevive;

/**
 * Configuration for TacticalRevive.
 * Simple static configuration - can be extended to use file-based config later.
 */
public final class TacticalReviveConfig {

    private TacticalReviveConfig() {
        // Configuration class
    }

    // Bleeding settings
    private static int bleedingTime = 1200; // 60 seconds in ticks
    private static float bleedingHealth = 10.0f; // 5 hearts
    private static boolean showBleedingMessage = true;
    private static boolean shouldGlow = false;
    private static int initialDamageCooldown = 10; // ticks

    // Revival settings
    private static float requiredReviveProgress = 100.0f;
    private static float progressPerPlayer = 1.0f;
    private static double maxReviveDistance = 3.0;
    private static float healthAfterRevive = 4.0f; // 2 hearts
    private static boolean haltBleedTime = false;
    private static boolean resetProgress = true;

    // Damage protection
    private static boolean disableMobDamage = false;
    private static boolean disablePlayerDamage = false;

    public static void load() {
        // TODO: Load from config file if needed
        TacticalRevive.LOGGER.info("Configuration loaded with defaults");
    }

    // Getters
    public static int getBleedingTime() {
        return bleedingTime;
    }

    public static float getBleedingHealth() {
        return bleedingHealth;
    }

    public static boolean shouldShowBleedingMessage() {
        return showBleedingMessage;
    }

    public static boolean shouldGlow() {
        return shouldGlow;
    }

    public static int getInitialDamageCooldown() {
        return initialDamageCooldown;
    }

    public static float getRequiredReviveProgress() {
        return requiredReviveProgress;
    }

    public static float getProgressPerPlayer() {
        return progressPerPlayer;
    }

    public static double getMaxReviveDistance() {
        return maxReviveDistance;
    }

    public static float getHealthAfterRevive() {
        return healthAfterRevive;
    }

    public static boolean shouldHaltBleedTime() {
        return haltBleedTime;
    }

    public static boolean shouldResetProgress() {
        return resetProgress;
    }

    public static boolean shouldDisableMobDamage() {
        return disableMobDamage;
    }

    public static boolean shouldDisablePlayerDamage() {
        return disablePlayerDamage;
    }
}
