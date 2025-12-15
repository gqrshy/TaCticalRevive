package com.tacticalrevive.mixin;

import com.tacticalrevive.bleeding.BleedingManager;
import com.tacticalrevive.event.DamageEventHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept damage to bleeding players.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * Intercept damage to bleeding players.
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void tacticalrevive$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (!(self instanceof Player player)) {
            return;
        }

        // Block damage to bleeding players if configured
        if (DamageEventHandler.shouldBlockDamage(player, source)) {
            cir.setReturnValue(false);
        }
    }
}
