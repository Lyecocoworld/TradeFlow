package com.github.lye.database;

import com.github.lye.TradeFlow;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class FileServerCollectionData implements IServerCollectionData {

    private final TradeFlow plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    private Set<String> serverCollections;

    public FileServerCollectionData(TradeFlow plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "server_collections.yml");
        this.serverCollections = new HashSet<>(); // Initialize here
        loadServerCollections(); // Call to load data into the initialized set
    }

    @Override
    public void createTable() {
        // Not applicable for file-based storage
    }

    @Override
    public Set<String> loadServerCollections() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create server_collections.yml!", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Clear existing data before loading to prevent duplicates on reload
        serverCollections.clear();

        List<String> collections = dataConfig.getStringList("collections");
        this.serverCollections.addAll(collections);
        plugin.getLogger().info("Loaded " + this.serverCollections.size() + " server collections from server_collections.yml.");
        return this.serverCollections;
    }

    public void saveData() {
        dataConfig = new YamlConfiguration(); // Clear existing config to write fresh data
        dataConfig.set("collections", new java.util.ArrayList<>(serverCollections));
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save server_collections.yml!", e);
        }
    }

    public Set<String> getServerCollections() {
        return serverCollections;
    }

    public void addServerCollection(String itemKey) {
        serverCollections.add(itemKey);
        saveData();
    }

    public boolean hasServerCollected(String itemKey) {
        return serverCollections.contains(itemKey);
    }
}
