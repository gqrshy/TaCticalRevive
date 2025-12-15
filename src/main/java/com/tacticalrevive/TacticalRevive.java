package com.tacticalrevive;

import com.tacticalrevive.bleeding.BleedingManager;
import com.tacticalrevive.config.TacticalReviveConfig;
import com.tacticalrevive.event.DamageEventHandler;
import com.tacticalrevive.event.PlayerEventHandler;
import com.tacticalrevive.event.TaczEventHandler;
import com.tacticalrevive.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TacticalRevive implements ModInitializer {
    public static final String MOD_ID = "tacticalrevive";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("TacticalRevive initializing...");

        // Load configuration
        TacticalReviveConfig.load();

        // Initialize networking
        NetworkHandler.registerServerPackets();

        // Register event handlers
        registerEvents();

        // Try to register TACZ compatibility
        TaczEventHandler.tryRegister();

        LOGGER.info("TacticalRevive initialized successfully!");
    }

    private void registerEvents() {
        // Player death interception
        ServerLivingEntityEvents.ALLOW_DEATH.register(DamageEventHandler::onAllowDeath);

        // Player tick for bleeding state management
        ServerTickEvents.END_SERVER_TICK.register(PlayerEventHandler::onServerTick);

        // Player interaction for revival
        UseEntityCallback.EVENT.register(PlayerEventHandler::onUseEntity);

        // Player disconnect - kill bleeding players
        ServerPlayConnectionEvents.DISCONNECT.register(PlayerEventHandler::onPlayerDisconnect);

        // Player respawn - reset bleeding state
        ServerPlayerEvents.AFTER_RESPAWN.register(PlayerEventHandler::onPlayerRespawn);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
