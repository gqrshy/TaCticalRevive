package com.tacticalrevive.network;

import com.tacticalrevive.TacticalRevive;
import com.tacticalrevive.network.packet.BleedingUpdatePacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles network packet registration and sending.
 */
public final class NetworkHandler {

    private NetworkHandler() {
    }

    /**
     * Register server-side packets.
     */
    public static void registerServerPackets() {
        // Register S2C packets
        PayloadTypeRegistry.playS2C().register(
                BleedingUpdatePacket.TYPE,
                BleedingUpdatePacket.CODEC
        );

        TacticalRevive.LOGGER.debug("Network packets registered");
    }

    /**
     * Send a packet to a specific player.
     */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    /**
     * Send a packet to all players tracking an entity.
     */
    public static void sendToTracking(ServerPlayer trackedPlayer, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(trackedPlayer)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * Send a packet to all players on the server.
     */
    public static void sendToAll(ServerPlayer source, CustomPacketPayload payload) {
        for (ServerPlayer player : source.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
