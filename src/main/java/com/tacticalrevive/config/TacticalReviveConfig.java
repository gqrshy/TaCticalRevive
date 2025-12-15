package com.tacticalrevive.config;

import com.tacticalrevive.TacticalRevive;

/**
 * Configuration for TacticalRevive.
 * Simple static configuration - can be extended to use file-based config later.
 *
 * <p>Important constraints for TACZ compatibility:
 * <ul>
 *   <li>{@code bleedingHealth} must be > 0 to ensure isDeadOrDying() returns false
 *       after entering downed state, preventing false EntityKillByGunEvent.</li>
 *   <li>{@code initialDamageCooldown} should be >= 2 to handle TACZ's double hurt() calls.</li>
 * </ul>
 */
public final class TacticalReviveConfig {

    // Minimum values to ensure correct behavior
    private static final float MIN_BLEEDING_HEALTH = 1.0f;
    private static final int MIN_INITIAL_DAMAGE_COOLDOWN = 2;

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

        // Validate configuration values
        validateConfig();
    }

    /**
     * Validate configuration values and adjust if necessary.
     * This ensures TACZ compatibility and correct behavior.
     */
    private static void validateConfig() {
        // Ensure bleedingHealth is above minimum
        // This is critical for TACZ - if health is 0 after downed state,
        // isDeadOrDying() returns true and EntityKillByGunEvent fires
        if (bleedingHealth < MIN_BLEEDING_HEALTH) {
            TacticalRevive.LOGGER.warn("bleedingHealth ({}) is below minimum ({}), adjusting",
                    bleedingHealth, MIN_BLEEDING_HEALTH);
            bleedingHealth = MIN_BLEEDING_HEALTH;
        }

        // Ensure initialDamageCooldown is above minimum
        // This is critical for TACZ - it makes two hurt() calls per bullet
        if (initialDamageCooldown < MIN_INITIAL_DAMAGE_COOLDOWN) {
            TacticalRevive.LOGGER.warn("initialDamageCooldown ({}) is below minimum ({}), adjusting",
                    initialDamageCooldown, MIN_INITIAL_DAMAGE_COOLDOWN);
            initialDamageCooldown = MIN_INITIAL_DAMAGE_COOLDOWN;
        }

        // Ensure other values are sane
        if (bleedingTime <= 0) {
            TacticalRevive.LOGGER.warn("bleedingTime ({}) is invalid, using default 1200", bleedingTime);
            bleedingTime = 1200;
        }

        if (requiredReviveProgress <= 0) {
            TacticalRevive.LOGGER.warn("requiredReviveProgress ({}) is invalid, using default 100",
                    requiredReviveProgress);
            requiredReviveProgress = 100.0f;
        }

        if (progressPerPlayer <= 0) {
            TacticalRevive.LOGGER.warn("progressPerPlayer ({}) is invalid, using default 1.0",
                    progressPerPlayer);
            progressPerPlayer = 1.0f;
        }

        if (maxReviveDistance <= 0) {
            TacticalRevive.LOGGER.warn("maxReviveDistance ({}) is invalid, using default 3.0",
                    maxReviveDistance);
            maxReviveDistance = 3.0;
        }

        if (healthAfterRevive <= 0) {
            TacticalRevive.LOGGER.warn("healthAfterRevive ({}) is invalid, using default 4.0",
                    healthAfterRevive);
            healthAfterRevive = 4.0f;
        }

        TacticalRevive.LOGGER.debug("Configuration validated: bleedingHealth={}, initialDamageCooldown={}",
                bleedingHealth, initialDamageCooldown);
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
