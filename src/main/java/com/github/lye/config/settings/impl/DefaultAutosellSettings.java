package com.github.lye.config.settings.impl;

import com.github.lye.config.settings.IAutosellSettings;
import com.github.lye.util.Format;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class DefaultAutosellSettings implements IAutosellSettings {

    private ConfigurationSection autosell;
    private final YamlConfiguration playerdataYml;
    private final File playerdataFile;
    private final TradeFlowLogger logger;

    public DefaultAutosellSettings(YamlConfiguration playerdataYml, File playerdataFile, TradeFlowLogger logger) {
        this.playerdataYml = playerdataYml;
        this.playerdataFile = playerdataFile;
        this.logger = logger;

        this.autosell = playerdataYml.getConfigurationSection("autosell");
        logger.finer("Loaded autosell configuration.");
    }

    @Override
    public ConfigurationSection getAutosell() {
        return autosell;
    }

    @Override
    public void setAutosell(ConfigurationSection section) {
        this.autosell = section;
        playerdataYml.set("autosell", section);
        try {
            playerdataYml.save(playerdataFile);
        } catch (IOException e) {
            logger.severe("Could not save autosell data to playerdata.yml!");
            e.printStackTrace();
        }
    }
}
