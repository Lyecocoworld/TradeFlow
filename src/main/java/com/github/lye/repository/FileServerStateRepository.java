package com.github.lye.repository;

import com.github.lye.TradeFlow;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * File-based implementation of {@link ServerStateRepository}.
 * Uses server_state.yml to store key-value pairs.
 */
public class FileServerStateRepository implements ServerStateRepository {

    private final TradeFlow plugin;
    private final File file;
    private YamlConfiguration config;

    public FileServerStateRepository(TradeFlow plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "server_state.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create server_state.yml!", e);
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save server_state.yml!", e);
        }
    }

    @Override
    public CompletableFuture<String> getState(String key) {
        return CompletableFuture.completedFuture(config.getString(key));
    }

    @Override
    public CompletableFuture<Void> setState(String key, String value) {
        config.set(key, value);
        save();
        return CompletableFuture.completedFuture(null);
    }
}

