package com.tacticalrevive.event;

import com.tacticalrevive.TacticalRevive;
import com.tacticalrevive.bleeding.BleedingManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Handles TACZ-specific events for damage interception.
 * Uses reflection to avoid hard dependency on TACZ.
 */
public final class TaczEventHandler {

    private static boolean taczAvailable = false;

    private TaczEventHandler() {
    }

    /**
     * Try to register TACZ event handlers.
     * This uses reflection to check if TACZ is loaded and register appropriately.
     */
    public static void tryRegister() {
        try {
            // Check if TACZ is available by trying to load its event class
            Class<?> eventClass = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent");
            taczAvailable = true;
            TacticalRevive.LOGGER.info("TACZ detected! Registering compatibility handlers...");
            registerTaczEvents();
        } catch (ClassNotFoundException e) {
            TacticalRevive.LOGGER.info("TACZ not detected. Gun damage interception disabled.");
            taczAvailable = false;
        } catch (Exception e) {
            TacticalRevive.LOGGER.warn("Error registering TACZ compatibility: {}", e.getMessage());
            taczAvailable = false;
        }
    }

    /**
     * Register TACZ event handlers.
     * This method is only called if TACZ is detected.
     */
    private static void registerTaczEvents() {
        try {
            // Register Pre event handler using TACZ's event system
            TaczEventBridge.registerPreEventHandler(TaczEventHandler::onEntityHurtByGunPre);
            TacticalRevive.LOGGER.info("TACZ EntityHurtByGunEvent.Pre handler registered");
        } catch (Exception e) {
            TacticalRevive.LOGGER.error("Failed to register TACZ event handlers", e);
        }
    }

    /**
     * Handle TACZ EntityHurtByGunEvent.Pre
     * Cancel damage to downed players.
     *
     * @param hurtEntity the entity being hurt
     * @param damage the damage amount
     * @return true to cancel the event
     */
    public static boolean onEntityHurtByGunPre(Entity hurtEntity, float damage) {
        if (!(hurtEntity instanceof Player player)) {
            return false; // Don't cancel for non-players
        }

        // Cancel damage if player is downed
        if (BleedingManager.isBleeding(player)) {
            TacticalRevive.LOGGER.debug("Cancelling TACZ damage to downed player: {}",
                    player.getName().getString());
            return true; // Cancel the event
        }

        return false; // Allow damage
    }

    /**
     * @return true if TACZ is available
     */
    public static boolean isTaczAvailable() {
        return taczAvailable;
    }
}
