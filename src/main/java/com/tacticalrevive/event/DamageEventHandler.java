package com.tacticalrevive.event;

import com.tacticalrevive.bleeding.BleedingManager;
import com.tacticalrevive.config.TacticalReviveConfig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Handles damage-related events for the bleeding system.
 */
public final class DamageEventHandler {

    private DamageEventHandler() {
    }

    /**
     * Called when an entity is about to die.
     * Returns false to prevent death and start bleeding instead.
     *
     * @param entity the entity about to die
     * @param source the damage source
     * @param amount the damage amount
     * @return true to allow death, false to prevent
     */
    public static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Player player)) {
            return true; // Allow non-player death
        }

        // Check if player is already bleeding
        if (BleedingManager.isBleeding(player)) {
            // Already bleeding - check if bled out
            var bleeding = BleedingManager.getBleeding(player);
            if (bleeding != null && bleeding.hasBledOut()) {
                return true; // Allow death - player bled out
            }
            return false; // Prevent death while bleeding
        }

        // Check if revival should be active
        if (!isReviveActive(player)) {
            return true; // Allow death
        }

        // Check for bypass conditions
        if (shouldBypassRevive(player, source, amount)) {
            return true; // Allow death
        }

        // Start bleeding instead of dying
        BleedingManager.startBleeding(player, source);
        return false; // Prevent death
    }

    /**
     * Check if revival system should be active for this player.
     */
    private static boolean isReviveActive(Player player) {
        // Don't activate in creative mode
        if (player.isCreative()) {
            return false;
        }

        // Don't activate in spectator mode
        if (player.isSpectator()) {
            return false;
        }

        // Check if multiplayer (or singleplayer with config)
        if (player.level().players().size() <= 1) {
            // Single player - could add config option
            return false;
        }

        return true;
    }

    /**
     * Check if damage should bypass the revival system.
     */
    private static boolean shouldBypassRevive(Player player, DamageSource source, float amount) {
        // Void damage bypasses revival
        if (source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            return true;
        }

        // Could add more bypass conditions here (overkill damage, specific damage types, etc.)

        return false;
    }

    /**
     * Check if damage should be blocked for a bleeding player.
     *
     * @param player the player
     * @param source the damage source
     * @return true if damage should be blocked
     */
    public static boolean shouldBlockDamage(Player player, DamageSource source) {
        if (!BleedingManager.isBleeding(player)) {
            return false;
        }

        var bleeding = BleedingManager.getBleeding(player);
        if (bleeding == null) {
            return false;
        }

        // Block damage during initial cooldown
        if (bleeding.getDownedTime() < TacticalReviveConfig.getInitialDamageCooldown()) {
            return true;
        }

        // Check mob damage
        if (TacticalReviveConfig.shouldDisableMobDamage() && isMobDamage(source)) {
            return true;
        }

        // Check player damage
        if (TacticalReviveConfig.shouldDisablePlayerDamage() && isPlayerDamage(source)) {
            return true;
        }

        return false;
    }

    private static boolean isMobDamage(DamageSource source) {
        return source.getEntity() != null &&
                !(source.getEntity() instanceof Player) &&
                source.getEntity() instanceof LivingEntity;
    }

    private static boolean isPlayerDamage(DamageSource source) {
        return source.getEntity() instanceof Player;
    }
}
