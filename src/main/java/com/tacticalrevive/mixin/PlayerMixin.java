package com.tacticalrevive.mixin;

import com.tacticalrevive.bleeding.BleedingData;
import com.tacticalrevive.bleeding.BleedingManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to attach bleeding data to players and modify behavior while bleeding.
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
}
