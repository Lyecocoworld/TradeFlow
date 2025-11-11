package com.github.lye.database;

import java.util.Set;

public interface IServerCollectionData {
    void createTable();
    Set<String> loadServerCollections();
    void addServerCollection(String itemKey);
    boolean hasServerCollected(String itemKey);
}
