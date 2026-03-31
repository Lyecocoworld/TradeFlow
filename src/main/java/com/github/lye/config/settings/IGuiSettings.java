package com.github.lye.config.settings;

import java.util.List;
import java.util.Set;

public interface IGuiSettings {
    String getBackground();
    List<String> getThemeColors();
    String getMarketDisplayName();
    String getGuiLockedStyle();
    boolean getGuiLockedTipsMetalsHint();
    Set<String> getSectionIds();
}
