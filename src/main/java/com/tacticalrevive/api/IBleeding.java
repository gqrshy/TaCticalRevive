package com.tacticalrevive.api;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * Interface for managing a player's bleeding state.
 * When a player takes fatal damage, they enter a bleeding state
 * instead of dying immediately.
 */
public interface IBleeding {

    /**
     * @return true if the player is currently bleeding out
     */
    boolean isBleeding();

    /**
     * @return true if the bleeding time has expired
     */
    boolean hasBledOut();

    /**
     * @return the remaining time in ticks before the player dies
     */
    int getTimeLeft();

    /**
     * @return time spent in bleeding state in ticks
     */
    int getDownedTime();

    /**
     * @return current revival progress (0.0 to requiredProgress)
     */
    float getReviveProgress();

    /**
     * @return true if revival progress is sufficient
     */
    boolean canBeRevived();

    /**
     * Start bleeding state for the player.
     *
     * @param player the player entering bleeding state
     * @param source the damage source that caused the knockout
     */
    void knockOut(Player player, DamageSource source);

    /**
     * Complete revival and restore player to normal state.
     */
    void revive();

    /**
     * Force the player to bleed out immediately.
     */
    void forceBledOut();

    /**
     * Process one tick of the bleeding state.
     *
     * @param player the bleeding player
     */
    void tick(Player player);

    /**
     * @return list of UUIDs of players currently helping with revival
     */
    List<UUID> getRevivingPlayerIds();

    /**
     * Add a player as a helper for revival.
     *
     * @param helperId UUID of the helping player
     */
    void addHelper(UUID helperId);

    /**
     * Remove a player from the helper list.
     *
     * @param helperId UUID of the helper to remove
     */
    void removeHelper(UUID helperId);

    /**
     * @return the original damage source that caused bleeding, or null
     */
    DamageSource getOriginalDamageSource();

    /**
     * Reset all bleeding state data.
     */
    void reset();
}
