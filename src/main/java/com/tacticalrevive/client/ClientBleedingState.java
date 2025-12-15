package com.tacticalrevive.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of bleeding states for all players.
 */
@Environment(EnvType.CLIENT)
public final class ClientBleedingState {

    private static final Map<UUID, BleedingStateData> states = new ConcurrentHashMap<>();

    private ClientBleedingState() {
    }

    /**
     * Update the bleeding state for a player.
     */
    public static void updateState(UUID playerUuid, boolean isBleeding, int timeLeft, float reviveProgress) {
        if (isBleeding) {
            states.put(playerUuid, new BleedingStateData(timeLeft, reviveProgress));
        } else {
            states.remove(playerUuid);
        }
    }

    /**
     * Check if a player is bleeding (client-side).
     */
    public static boolean isBleeding(UUID playerUuid) {
        return states.containsKey(playerUuid);
    }

    /**
     * Get the bleeding state for a player.
     */
    public static BleedingStateData getState(UUID playerUuid) {
        return states.get(playerUuid);
    }

    /**
     * Clear all cached states.
     */
    public static void clear() {
        states.clear();
    }

    /**
     * Data class for bleeding state.
     */
    public record BleedingStateData(int timeLeft, float reviveProgress) {
    }
}
