package net.opmasterleo.combat.api;

import java.util.UUID;

public interface MasterCombatAPI {

    /**
     * Tag a player for combat.
     *
     * @param uuid the player's UUID
     */
    void tagPlayer(UUID uuid);

    /**
     * Untag a player from combat.
     *
     * @param uuid the player's UUID
     */
    void untagPlayer(UUID uuid);
}
