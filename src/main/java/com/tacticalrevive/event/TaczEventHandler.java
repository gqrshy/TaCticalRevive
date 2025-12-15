package com.tacticalrevive.event;

import com.tacticalrevive.TacticalRevive;
import com.tacticalrevive.bleeding.BleedingManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Handles TACZ-specific events for damage interception.
 * Uses reflection to avoid hard dependency on TACZ.
 *
 * <p>This handler addresses three key issues with TACZ integration:
 * <ol>
 *   <li><b>Double damage</b>: TACZ calls hurt() twice per bullet (normal + armor-piercing).
 *       We cancel the Pre event for downed players to prevent tacAttackEntity from running.</li>
 *   <li><b>invulnerableTime reset</b>: TACZ sets invulnerableTime=0 before each hurt() call.
 *       By cancelling Pre, tacAttackEntity never runs, so no reset occurs.</li>
 *   <li><b>EntityKillByGunEvent timing</b>: TACZ fires kill event based on isDeadOrDying().
 *       Since we set health > 0 on downed state, this check should return false.</li>
 * </ol>
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
            // Register Pre event handler - this is the main damage interception point
            TaczEventBridge.registerPreEventHandler(TaczEventHandler::onEntityHurtByGunPre);
            TacticalRevive.LOGGER.info("TACZ EntityHurtByGunEvent.Pre handler registered");

            // Register Kill event handler - for debugging false positives
            TaczEventBridge.registerKillEventHandler(TaczEventHandler::onEntityKillByGun);
            TacticalRevive.LOGGER.info("TACZ EntityKillByGunEvent handler registered");

            // Register Post event handler - for initial damage detection
            TaczEventBridge.registerPostEventHandler(TaczEventHandler::onEntityHurtByGunPost);
            TacticalRevive.LOGGER.info("TACZ EntityHurtByGunEvent.Post handler registered");
        } catch (Exception e) {
            TacticalRevive.LOGGER.error("Failed to register TACZ event handlers", e);
        }
    }

    /**
     * Handle TACZ EntityHurtByGunEvent.Pre
     * Cancel damage to downed players to prevent:
     * - Double hurt() calls
     * - invulnerableTime resets
     * - Any additional gun damage
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
            TacticalRevive.LOGGER.debug("Cancelling TACZ damage ({}) to downed player: {}",
                    damage, player.getName().getString());
            return true; // Cancel the event - prevents tacAttackEntity from running
        }

        return false; // Allow damage - player not downed
    }

    /**
     * Handle TACZ EntityHurtByGunEvent.Post
     * This fires after damage is applied if the entity survived.
     * Used for logging and potential future enhancements.
     *
     * @param hurtEntity the entity that was hurt
     * @param damage the damage amount
     * @return null (no return value needed)
     */
    public static Void onEntityHurtByGunPost(Entity hurtEntity, float damage) {
        if (!(hurtEntity instanceof Player player)) {
            return null;
        }

        // Log if a downed player somehow received Post event (shouldn't happen)
        if (BleedingManager.isBleeding(player)) {
            TacticalRevive.LOGGER.warn("Unexpected: Downed player {} received Post event with damage {}",
                    player.getName().getString(), damage);
        }

        return null;
    }

    /**
     * Handle TACZ EntityKillByGunEvent
     * This fires when isDeadOrDying() returns true after damage.
     * For downed players, this might fire briefly before health is set,
     * but the actual death is prevented by our ALLOW_DEATH handler.
     *
     * @param killedEntity the entity that was "killed"
     */
    public static void onEntityKillByGun(LivingEntity killedEntity) {
        if (!(killedEntity instanceof Player player)) {
            return;
        }

        // Check if this is a downed player (false positive kill event)
        if (BleedingManager.isBleeding(player)) {
            TacticalRevive.LOGGER.debug("TACZ KillEvent for downed player {} (health: {}) - this is expected if player just entered downed state",
                    player.getName().getString(), player.getHealth());
        }
    }

    /**
     * @return true if TACZ is available
     */
    public static boolean isTaczAvailable() {
        return taczAvailable;
    }
}
