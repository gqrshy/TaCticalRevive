package com.tacticalrevive.mixin;

import com.tacticalrevive.bleeding.BleedingData;
import com.tacticalrevive.bleeding.BleedingManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for ServerPlayer to handle bleeding data persistence.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    private static final String BLEEDING_DATA_KEY = "TacticalReviveBleeding";

    /**
     * Save bleeding data when player data is saved.
     */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void tacticalrevive$saveData(CompoundTag tag, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        BleedingData data = getBleedingData(self);

        if (data != null) {
            tag.put(BLEEDING_DATA_KEY, data.toNbt());
        }
    }

    /**
     * Load bleeding data when player data is loaded.
     */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void tacticalrevive$loadData(CompoundTag tag, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        BleedingData data = getBleedingData(self);

        if (data != null && tag.contains(BLEEDING_DATA_KEY)) {
            data.fromNbt(tag.getCompound(BLEEDING_DATA_KEY));
        }
    }

    /**
     * Copy bleeding data when player is cloned (e.g., after death).
     */
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void tacticalrevive$copyFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        // Don't copy bleeding state - it should be reset on respawn
        ServerPlayer self = (ServerPlayer) (Object) this;
        BleedingData data = getBleedingData(self);

        if (data != null) {
            data.reset();
        }
    }

    private static BleedingData getBleedingData(ServerPlayer player) {
        if (player instanceof BleedingManager.IBleedingAccessor accessor) {
            return accessor.tacticalrevive$getBleedingData();
        }
        return null;
    }
}
