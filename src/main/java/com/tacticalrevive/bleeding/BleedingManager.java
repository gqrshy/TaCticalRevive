package com.tacticalrevive.bleeding;

import com.tacticalrevive.TacticalRevive;
import com.tacticalrevive.api.IBleeding;
import com.tacticalrevive.config.TacticalReviveConfig;
import com.tacticalrevive.network.NetworkHandler;
import com.tacticalrevive.network.packet.BleedingUpdatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * Static API for managing player bleeding states.
 * Provides methods for querying and manipulating bleeding status.
 */
public final class BleedingManager {

    private BleedingManager() {
        // Utility class
    }

    /**
     * Get the bleeding data for a player.
     *
     * @param player the player
     * @return the bleeding data, or null if not available
     */
    public static IBleeding getBleeding(Player player) {
        if (player instanceof IBleedingAccessor accessor) {
            return accessor.tacticalrevive$getBleedingData();
        }
        return null;
    }

    /**
     * Check if a player is currently bleeding.
     *
     * @param player the player to check
     * @return true if bleeding
     */
    public static boolean isBleeding(Player player) {
        IBleeding bleeding = getBleeding(player);
        return bleeding != null && bleeding.isBleeding();
    }

    /**
     * Start the downed state for a player.
     *
     * @param player the player
     * @param source the damage source
     */
    public static void startBleeding(Player player, DamageSource source) {
        IBleeding bleeding = getBleeding(player);
        if (bleeding == null) {
            return;
        }

        bleeding.knockOut(player, source);

        // Broadcast message
        if (TacticalReviveConfig.shouldShowBleedingMessage() && player instanceof ServerPlayer serverPlayer) {
            Component message = Component.translatable("tacticalrevive.message.downed",
                    player.getDisplayName());
            serverPlayer.server.getPlayerList().broadcastSystemMessage(message, false);
        }

        // Sync to clients
        syncBleedingState(player);

        TacticalRevive.LOGGER.debug("Player {} entered downed state", player.getName().getString());
    }

    /**
     * Revive a downed player.
     *
     * @param player the player to revive
     */
    public static void revive(Player player) {
        IBleeding bleeding = getBleeding(player);
        if (bleeding == null || !bleeding.isBleeding()) {
            return;
        }

        bleeding.revive();

        // Reset pose
        player.setForcedPose(null);

        // Set health after revival
        player.setHealth(TacticalReviveConfig.getHealthAfterRevive());

        // Broadcast revive message
        if (TacticalReviveConfig.shouldShowBleedingMessage() && player instanceof ServerPlayer serverPlayer) {
            Component message = Component.translatable("tacticalrevive.message.revived",
                    player.getDisplayName());
            serverPlayer.server.getPlayerList().broadcastSystemMessage(message, false);
        }

        // Sync to clients
        syncBleedingState(player);

        TacticalRevive.LOGGER.debug("Player {} was revived", player.getName().getString());
    }

    /**
     * Kill a downed player (when they bleed out or give up).
     *
     * @param player the player to kill
     */
    public static void kill(Player player) {
        IBleeding bleeding = getBleeding(player);
        if (bleeding == null) {
            return;
        }

        DamageSource originalSource = bleeding.getOriginalDamageSource();

        // Broadcast death message before killing
        if (TacticalReviveConfig.shouldShowBleedingMessage() && player instanceof ServerPlayer serverPlayer) {
            Component message = Component.translatable("tacticalrevive.message.died",
                    player.getDisplayName());
            serverPlayer.server.getPlayerList().broadcastSystemMessage(message, false);
        }

        // Reset state before killing
        bleeding.reset();
        player.setForcedPose(null);

        // Apply death
        if (originalSource != null) {
            player.hurt(originalSource, Float.MAX_VALUE);
        } else {
            // Fallback - use generic damage
            player.kill();
        }

        // Sync to clients
        syncBleedingState(player);

        TacticalRevive.LOGGER.debug("Player {} could not be saved", player.getName().getString());
    }

    /**
     * Process a tick for a bleeding player.
     *
     * @param player the player
     */
    public static void tickBleeding(Player player) {
        IBleeding bleeding = getBleeding(player);
        if (bleeding == null || !bleeding.isBleeding()) {
            return;
        }

        bleeding.tick(player);

        // Check if player can be revived
        if (bleeding.canBeRevived()) {
            revive(player);
            return;
        }

        // Check if player has bled out
        if (bleeding.hasBledOut()) {
            kill(player);
            return;
        }

        // Sync periodically (every 5 ticks)
        if (bleeding.getDownedTime() % 5 == 0) {
            syncBleedingState(player);
        }
    }

    /**
     * Add a helper to revive a player.
     *
     * @param target the bleeding player
     * @param helper the player helping
     */
    public static void addHelper(Player target, Player helper) {
        IBleeding bleeding = getBleeding(target);
        if (bleeding == null || !bleeding.isBleeding()) {
            return;
        }

        // Remove helper from any other player they might be helping
        removeHelperFromAll(helper);

        bleeding.addHelper(helper.getUUID());
        syncBleedingState(target);
    }

    /**
     * Remove a helper from all bleeding players.
     *
     * @param helper the helper to remove
     */
    public static void removeHelperFromAll(Player helper) {
        if (!(helper instanceof ServerPlayer serverPlayer)) {
            return;
        }

        for (ServerPlayer player : serverPlayer.server.getPlayerList().getPlayers()) {
            IBleeding bleeding = getBleeding(player);
            if (bleeding != null && bleeding.isBleeding()) {
                bleeding.removeHelper(helper.getUUID());
            }
        }
    }

    /**
     * Sync bleeding state to all tracking clients.
     *
     * @param player the player whose state to sync
     */
    public static void syncBleedingState(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        IBleeding bleeding = getBleeding(player);
        if (bleeding == null) {
            return;
        }

        BleedingUpdatePacket packet = new BleedingUpdatePacket(
                player.getUUID(),
                bleeding.isBleeding(),
                bleeding.getTimeLeft(),
                bleeding.getReviveProgress()
        );

        NetworkHandler.sendToTracking(serverPlayer, packet);
        NetworkHandler.sendToPlayer(serverPlayer, packet);
    }

    /**
     * Interface for accessing bleeding data via Mixin.
     */
    public interface IBleedingAccessor {
        BleedingData tacticalrevive$getBleedingData();
    }
}
