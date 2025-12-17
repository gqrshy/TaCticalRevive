package com.tacticalrevive.bleeding;

import com.tacticalrevive.api.IBleeding;
import com.tacticalrevive.config.TacticalReviveConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of IBleeding that stores bleeding state data.
 * Attached to players via Mixin.
 */
public class BleedingData implements IBleeding {

    private boolean bleeding = false;
    private int timeLeft = 0;
    private int downedTime = 0;
    private float reviveProgress = 0.0f;
    private final List<UUID> revivingPlayers = new ArrayList<>();
    private DamageSource originalSource = null;

    @Override
    public boolean isBleeding() {
        return bleeding;
    }

    @Override
    public boolean hasBledOut() {
        return bleeding && timeLeft <= 0;
    }

    @Override
    public int getTimeLeft() {
        return timeLeft;
    }

    @Override
    public int getDownedTime() {
        return downedTime;
    }

    @Override
    public float getReviveProgress() {
        return reviveProgress;
    }

    @Override
    public boolean canBeRevived() {
        return reviveProgress >= TacticalReviveConfig.getRequiredReviveProgress();
    }

    @Override
    public void knockOut(Player player, DamageSource source) {
        this.bleeding = true;
        this.timeLeft = TacticalReviveConfig.getBleedingTime();
        this.downedTime = 0;
        this.reviveProgress = 0.0f;
        this.originalSource = source;
        this.revivingPlayers.clear();

        // Set player health to bleeding health
        player.setHealth(TacticalReviveConfig.getBleedingHealth());

        // Force swimming pose (crawling)
        player.setPose(Pose.SWIMMING);

        // Apply initial effects
        applyBleedingEffects(player);
    }

    @Override
    public void revive() {
        this.bleeding = false;
        this.timeLeft = 0;
        this.downedTime = 0;
        this.reviveProgress = 0.0f;
        this.originalSource = null;
        this.revivingPlayers.clear();
    }

    @Override
    public void forceBledOut() {
        this.timeLeft = 0;
    }

    @Override
    public void tick(Player player) {
        if (!bleeding) {
            return;
        }

        downedTime++;

        // Maintain forced pose
        player.setPose(Pose.SWIMMING);

        // Apply bleeding effects every tick
        applyBleedingEffects(player);

        // Process helpers and revive progress
        cleanupDistantHelpers(player);

        int helperCount = revivingPlayers.size();
        if (helperCount > 0) {
            // Add progress based on helper count
            reviveProgress += helperCount * TacticalReviveConfig.getProgressPerPlayer();

            // Optionally halt bleed time while being revived
            if (!TacticalReviveConfig.shouldHaltBleedTime()) {
                timeLeft--;
            }
        } else {
            // No helpers - countdown continues
            timeLeft--;

            // Reset progress if configured
            if (TacticalReviveConfig.shouldResetProgress()) {
                reviveProgress = 0.0f;
            }
        }

        // Clamp time
        if (timeLeft < 0) {
            timeLeft = 0;
        }
    }

    private void applyBleedingEffects(Player player) {
        // Slowness effect
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                40, // 2 seconds
                1,  // Level II
                false,
                false,
                true
        ));

        // Optional glowing
        if (TacticalReviveConfig.shouldGlow()) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING,
                    40,
                    0,
                    false,
                    false,
                    true
            ));
        }
    }

    private void cleanupDistantHelpers(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        double maxDistance = TacticalReviveConfig.getMaxReviveDistance();
        double maxDistSq = maxDistance * maxDistance;

        revivingPlayers.removeIf(helperId -> {
            Player helper = serverPlayer.serverLevel().getPlayerByUUID(helperId);
            if (helper == null) {
                return true;
            }
            return player.distanceToSqr(helper) > maxDistSq;
        });
    }

    @Override
    public List<UUID> getRevivingPlayerIds() {
        return new ArrayList<>(revivingPlayers);
    }

    @Override
    public void addHelper(UUID helperId) {
        if (!revivingPlayers.contains(helperId)) {
            revivingPlayers.add(helperId);
        }
    }

    @Override
    public void removeHelper(UUID helperId) {
        revivingPlayers.remove(helperId);
    }

    @Override
    public DamageSource getOriginalDamageSource() {
        return originalSource;
    }

    @Override
    public void reset() {
        this.bleeding = false;
        this.timeLeft = 0;
        this.downedTime = 0;
        this.reviveProgress = 0.0f;
        this.originalSource = null;
        this.revivingPlayers.clear();
    }

    // NBT Serialization
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("bleeding", bleeding);
        tag.putInt("timeLeft", timeLeft);
        tag.putInt("downedTime", downedTime);
        tag.putFloat("reviveProgress", reviveProgress);

        ListTag helpersList = new ListTag();
        for (UUID uuid : revivingPlayers) {
            helpersList.add(NbtUtils.createUUID(uuid));
        }
        tag.put("helpers", helpersList);

        return tag;
    }

    public void fromNbt(CompoundTag tag) {
        try {
            this.bleeding = tag.getBoolean("bleeding");
            this.timeLeft = tag.getInt("timeLeft");
            this.downedTime = tag.getInt("downedTime");
            this.reviveProgress = tag.getFloat("reviveProgress");

            this.revivingPlayers.clear();
            ListTag helpersList = tag.getList("helpers", Tag.TAG_INT_ARRAY);
            for (Tag t : helpersList) {
                this.revivingPlayers.add(NbtUtils.loadUUID(t));
            }
        } catch (Exception e) {
            // Reset to safe state if NBT data is corrupted
            reset();
        }
    }
}
