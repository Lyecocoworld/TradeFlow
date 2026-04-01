package com.github.lye.database;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface IPlayerCollectionData {
    void createTable();
    Map<UUID, Set<String>> loadPlayerCollections();
    void addPlayerCollection(UUID playerUUID, String itemKey);
    boolean hasPlayerCollected(UUID playerUUID, String itemKey);
    
    /**
     * Resets collection data for a player.
     * @param playerUUID The player UUID.
     * @param itemKey The specific item key to reset, or null to reset ALL.
     */
    void resetPlayerCollection(UUID playerUUID, String itemKey);
}
