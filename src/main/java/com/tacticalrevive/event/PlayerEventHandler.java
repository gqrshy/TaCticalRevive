package com.tacticalrevive.event;

import com.tacticalrevive.bleeding.BleedingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Handles player-related events for the bleeding and revival system.
 */
public final class PlayerEventHandler {

    private PlayerEventHandler() {
    }

    /**
     * Called every server tick.
     * Processes bleeding state for all players.
     */
    public static void onServerTick(MinecraftServer server) {
        // Defensive copy to prevent ConcurrentModificationException if player list changes during iteration
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (BleedingManager.isBleeding(player)) {
                BleedingManager.tickBleeding(player);
            }
        }
    }

    /**
     * Called when a player interacts with an entity.
     * Handles starting revival when right-clicking a bleeding player.
     */
    public static InteractionResult onUseEntity(Player helper, Level world, InteractionHand hand,
                                                 Entity target, EntityHitResult hitResult) {
        // Only process on server side
        if (world.isClientSide) {
            return InteractionResult.PASS;
        }

        // Only main hand interactions
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        // Target must be a player
        if (!(target instanceof Player targetPlayer)) {
            return InteractionResult.PASS;
        }

        // Target must be bleeding
        if (!BleedingManager.isBleeding(targetPlayer)) {
            return InteractionResult.PASS;
        }

        // Helper cannot be bleeding themselves
        if (BleedingManager.isBleeding(helper)) {
            return InteractionResult.PASS;
        }

        // Start helping
        BleedingManager.addHelper(targetPlayer, helper);

        return InteractionResult.SUCCESS;
    }

    /**
     * Called when a player disconnects.
     * Kill the player if they disconnect while bleeding.
     */
    public static void onPlayerDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        ServerPlayer player = handler.getPlayer();

        // Remove as helper from all bleeding players
        BleedingManager.removeHelperFromAll(player);

        // Kill if bleeding
        if (BleedingManager.isBleeding(player)) {
            BleedingManager.kill(player);
        }
    }

    /**
     * Called after a player respawns.
     * Reset bleeding state.
     */
    public static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        var bleeding = BleedingManager.getBleeding(newPlayer);
        if (bleeding != null) {
            bleeding.reset();
        }
    }
}
