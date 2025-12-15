package com.tacticalrevive.event;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;

import java.util.function.BiFunction;

/**
 * Bridge class for TACZ event registration.
 * This class contains the actual TACZ imports and is only loaded
 * when TACZ is confirmed to be present.
 */
public final class TaczEventBridge {

    private TaczEventBridge() {
    }

    /**
     * Register a handler for EntityHurtByGunEvent.Pre
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
}
