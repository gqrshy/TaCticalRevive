package com.tacticalrevive.mixin;

import com.tacticalrevive.bleeding.BleedingData;
import com.tacticalrevive.bleeding.BleedingManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to attach bleeding data to players and modify behavior while bleeding.
 * Restricts various actions while downed.
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements BleedingManager.IBleedingAccessor {

    @Unique
    private final BleedingData tacticalrevive$bleedingData = new BleedingData();

    @Override
    public BleedingData tacticalrevive$getBleedingData() {
        return tacticalrevive$bleedingData;
    }

    /**
     * Prevent bleeding players from being seen as enemies by mobs initially.
     */
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void tacticalrevive$canBeSeenAsEnemy(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;

        if (BleedingManager.isBleeding(self)) {
            var bleeding = BleedingManager.getBleeding(self);
            if (bleeding != null) {
                // Hide from mobs during initial cooldown
                int cooldown = com.tacticalrevive.config.TacticalReviveConfig.getInitialDamageCooldown();
                if (bleeding.getDownedTime() < cooldown) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    /**
     * Prevent bleeding players from being pushed.
     */
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void tacticalrevive$isPushable(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;

        if (BleedingManager.isBleeding(self)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Prevent bleeding players from attacking entities.
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void tacticalrevive$onAttack(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;

        if (BleedingManager.isBleeding(self)) {
            ci.cancel();
        }
    }

    /**
     * Prevent bleeding players from interacting with entities.
     */
    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void tacticalrevive$onInteractOn(Entity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Player self = (Player) (Object) this;

        if (BleedingManager.isBleeding(self)) {
            // Allow interaction with other players (for reviving)
            if (entity instanceof Player) {
                return;
            }
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }

    /**
     * Prevent bleeding players from dropping items.
     */
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void tacticalrevive$onDrop(boolean dropAll, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;

        if (BleedingManager.isBleeding(self)) {
            cir.setReturnValue(false);
        }
    }
}
