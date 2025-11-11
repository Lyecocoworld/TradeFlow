package com.github.lye.database;

import com.github.lye.TradeFlow;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import java.util.ArrayList;

public class FilePlayerCollectionData implements IPlayerCollectionData {

    private final TradeFlow plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    private Map<UUID, Set<String>> playerCollections;

    public FilePlayerCollectionData(TradeFlow plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "player_collections.yml");
        this.playerCollections = new HashMap<>(); // Initialize here
        loadPlayerCollections(); // Call to load data into the initialized map
    }

    public void createTable() {
        // Not applicable for file-based storage
    }

    @Override
    public Map<UUID, Set<String>> loadPlayerCollections() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create player_collections.yml!", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Clear existing data before loading to prevent duplicates on reload
        playerCollections.clear();

        for (String uuidString : dataConfig.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidString);
                Set<String> collections = new HashSet<>(dataConfig.getStringList(uuidString));
                this.playerCollections.put(playerUUID, collections);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid UUID found in player_collections.yml: " + uuidString, e);
            }
        }
        plugin.getLogger().info("Loaded " + this.playerCollections.size() + " player collections from player_collections.yml.");
        return this.playerCollections;
    }

    public void saveData() {
        dataConfig = new YamlConfiguration(); // Clear existing config to write fresh data
        for (Map.Entry<UUID, Set<String>> entry : playerCollections.entrySet()) {
            dataConfig.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player_collections.yml!", e);
        }
    }

    public Map<UUID, Set<String>> getPlayerCollections() {
        return playerCollections;
    }

    public void addPlayerCollection(UUID playerUUID, String itemKey) {
        playerCollections.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(itemKey);
        saveData();
    }

    public boolean hasPlayerCollected(UUID playerUUID, String itemKey) {
        return playerCollections.containsKey(playerUUID) && playerCollections.get(playerUUID).contains(itemKey);
    }
}
