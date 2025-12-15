package com.tacticalrevive.client;

import com.tacticalrevive.TacticalRevive;
import com.tacticalrevive.network.packet.BleedingUpdatePacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Client-side initialization for TacticalRevive.
 */
@Environment(EnvType.CLIENT)
public class TacticalReviveClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TacticalRevive.LOGGER.info("TacticalRevive client initializing...");

        // Register client packet handlers
        registerPacketHandlers();

        // Register HUD renderer
        HudRenderCallback.EVENT.register(BleedingHudRenderer::render);

        TacticalRevive.LOGGER.info("TacticalRevive client initialized!");
    }

    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(
                BleedingUpdatePacket.TYPE,
                (packet, context) -> {
                    // Update client-side bleeding state cache
                    ClientBleedingState.updateState(
                            packet.playerUuid(),
                            packet.isBleeding(),
                            packet.timeLeft(),
                            packet.reviveProgress()
                    );
                }
        );
    }
}
