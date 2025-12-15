package com.tacticalrevive.event;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Bridge class for TACZ event registration.
 * This class contains the actual TACZ imports and is only loaded
 * when TACZ is confirmed to be present.
 *
 * <p>TACZ Damage Flow (from EntityKineticBullet.tacAttackEntity):
 * <ol>
 *   <li>EntityHurtByGunEvent.Pre fires - can be cancelled to prevent damage</li>
 *   <li>If not cancelled: tacAttackEntity runs with TWO hurt() calls:
 *       <ul>
 *         <li>invulnerableTime = 0, then hurt(normalDamage)</li>
 *         <li>invulnerableTime = 0, then hurt(armorPiercingDamage)</li>
 *       </ul>
 *   </li>
 *   <li>After damage: isDeadOrDying() check determines Kill vs Post event</li>
 * </ol>
 *
 * <p>For downed players, we cancel Pre event to prevent ALL damage and side effects.
 */
public final class TaczEventBridge {

    private TaczEventBridge() {
    }

    /**
     * Register a handler for EntityHurtByGunEvent.Pre
     * Cancelling this event prevents tacAttackEntity from running,
     * which avoids the double hurt() call and invulnerableTime reset.
     *
     * @param handler function that receives (hurtEntity, damage) and returns true to cancel
     */
    public static void registerPreEventHandler(BiFunction<Entity, Float, Boolean> handler) {
        EntityHurtByGunEvent.PRE.register(event -> {
            Entity hurtEntity = event.getHurtEntity();
            float damage = event.getBaseAmount() * event.getHeadshotMultiplier();

            boolean shouldCancel = handler.apply(hurtEntity, damage);

            if (shouldCancel) {
                event.setCanceled(true);
            }
        });
    }

    /**
     * Register a handler for EntityHurtByGunEvent.Post
     * This fires after damage is applied but only if the entity survived.
     *
     * @param handler function that receives (hurtEntity, damage)
     */
    public static void registerPostEventHandler(BiFunction<Entity, Float, Void> handler) {
        EntityHurtByGunEvent.POST.register(event -> {
            Entity hurtEntity = event.getHurtEntity();
            float damage = event.getBaseAmount() * event.getHeadshotMultiplier();
            handler.apply(hurtEntity, damage);
        });
    }

    /**
     * Register a handler for EntityKillByGunEvent
     * This fires when isDeadOrDying() returns true after damage.
     * Note: This can fire even if the player enters downed state,
     * because isDeadOrDying() checks health <= 0 which might be true
     * briefly during the damage processing.
     *
     * @param handler consumer that receives the killed entity
     */
    public static void registerKillEventHandler(Consumer<LivingEntity> handler) {
        EntityKillByGunEvent.CALLBACK.register(event -> {
            LivingEntity killedEntity = event.getKilledEntity();
            if (killedEntity != null) {
                handler.accept(killedEntity);
            }
        });
    }

    // ========== Gun Action Event Handlers ==========
    // These prevent downed players from performing gun actions

    /**
     * Register a handler for GunShootEvent (trigger pull).
     * Cancelling prevents the shot from being processed.
     *
     * @param shouldCancel predicate that returns true to cancel
     */
    public static void registerShootEventHandler(Predicate<LivingEntity> shouldCancel) {
        GunShootEvent.CALLBACK.register(event -> {
            if (shouldCancel.test(event.getShooter())) {
                event.setCanceled(true);
            }
        });
    }

    /**
     * Register a handler for GunFireEvent (actual bullet firing).
     * This fires for each bullet in burst mode.
     * Cancelling prevents the bullet from being fired.
     *
     * @param shouldCancel predicate that returns true to cancel
     */
    public static void registerFireEventHandler(Predicate<LivingEntity> shouldCancel) {
        GunFireEvent.CALLBACK.register(event -> {
            if (shouldCancel.test(event.getShooter())) {
                event.setCanceled(true);
            }
        });
    }

    /**
     * Register a handler for GunReloadEvent.
     * Cancelling prevents the reload from starting.
     *
     * @param shouldCancel predicate that returns true to cancel
     */
    public static void registerReloadEventHandler(Predicate<LivingEntity> shouldCancel) {
        GunReloadEvent.CALLBACK.register(event -> {
            if (shouldCancel.test(event.getEntity())) {
                event.setCanceled(true);
            }
        });
    }

    /**
     * Register a handler for GunMeleeEvent.
     * Cancelling prevents the melee attack.
     *
     * @param shouldCancel predicate that returns true to cancel
     */
    public static void registerMeleeEventHandler(Predicate<LivingEntity> shouldCancel) {
        GunMeleeEvent.CALLBACK.register(event -> {
            if (shouldCancel.test(event.getShooter())) {
                event.setCanceled(true);
            }
        });
    }
}
