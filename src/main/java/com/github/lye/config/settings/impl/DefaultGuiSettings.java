package com.github.lye.config.settings.impl;

import com.github.lye.config.Config;
import com.github.lye.config.settings.IGuiSettings;
import com.github.lye.util.TradeFlowLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

public class DefaultGuiSettings implements IGuiSettings {

    private final String background;
    private final List<String> themeColors;
    private final String marketDisplayName;
    private final String guiLockedStyle;
    private final boolean guiLockedTipsMetalsHint;
    private final TradeFlowLogger logger;

    public DefaultGuiSettings(YamlConfiguration configYml, TradeFlowLogger logger) {
        this.logger = logger;
        
        this.background = configYml.getString("gui.background-material", "BLACK_STAINED_GLASS_PANE");
        this.themeColors = configYml.getStringList("gui.theme-colors");
        this.marketDisplayName = configYml.getString("gui.market-display-name", "TradeFlow");
        
        logger.finer("Background: " + background);
        logger.finer("Theme Colors: " + themeColors);
        logger.finer("Market Display Name: " + marketDisplayName);
        this.guiLockedStyle = configYml.getString("gui.locked-style", "ghost");
        logger.finer("GUI Locked Style: " + guiLockedStyle);
        this.guiLockedTipsMetalsHint = configYml.getBoolean("gui.locked-tips.metals_hint", true);
        logger.finer("GUI Locked Tips Metals Hint: " + guiLockedTipsMetalsHint);
    }

    @Override
    public String getBackground() {
        return background;
    }

    @Override
    public List<String> getThemeColors() {
        return themeColors != null ? themeColors : Collections.emptyList();
    }

    @Override
    public String getMarketDisplayName() {
        return (marketDisplayName == null || marketDisplayName.isBlank()) ? "TradeFlow" : marketDisplayName;
    }

    @Override
    public String getGuiLockedStyle() {
        return guiLockedStyle;
    }

    @Override
    public boolean getGuiLockedTipsMetalsHint() {
        return guiLockedTipsMetalsHint;
    }

    @Override
    public Set<String> getSectionIds() {
        if (Config.getShopsConfig().getConfigurationSection("sections") != null) {
            return Config.getShopsConfig().getConfigurationSection("sections").getKeys(false);
        }
        return new HashSet<>();
    }
}
