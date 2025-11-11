package com.github.lye.database;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface IPlayerCollectionData {
    void createTable();
    Map<UUID, Set<String>> loadPlayerCollections();
    void addPlayerCollection(UUID playerUUID, String itemKey);
    boolean hasPlayerCollected(UUID playerUUID, String itemKey);
}
