package com.github.lye.gui;

import com.github.lye.config.Config;
import com.github.lye.util.Format;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Small cache for GUI text components to avoid repeated MiniMessage parsing for
 * frequently used static patterns (e.g. bold item names).
 */
public final class GuiTextCache {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Map<String, Component> BOLD_NAME_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Component> TITLE_CACHE = new ConcurrentHashMap<>();

    private static final Cache<String, Component> LORE_BUTTON_CACHE = Caffeine.newBuilder()
            .maximumSize(512)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private static final Cache<String, Component> THEMED_CACHE = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private GuiTextCache() {
        // no-op
    }

    /**
     * Returns a cached bold display name component for a shop/item identifier.
     * The underlying string is {@code "<b>" + prettifyName(id) + "</b>"}.
     *
     * Callers may still adjust decorations (e.g. remove italics) or colors.
     */
    public static Component boldDisplayName(String id) {
        if (id == null || id.isBlank()) {
            id = "unknown";
        }
        String key = id.toLowerCase();
        return BOLD_NAME_CACHE.computeIfAbsent(key, k -> {
            String pretty = Format.prettifyName(k);
            return MINI_MESSAGE.deserialize("<b>" + pretty + "</b>")
                    .decoration(TextDecoration.ITALIC, false);
        });
    }

    /**
     * Returns a cached GUI title component: centered, bold, no color.
     * Uses pixel-width estimation for better centering accuracy.
     */
    public static Component title(String raw) {
        String text = (raw == null || raw.isBlank()) ? "TradeFlow" : raw.trim();
        return TITLE_CACHE.computeIfAbsent(text, key -> {
            // Strip existing MiniMessage tags to ensure pure text width calculation
            String strippedText = MINI_MESSAGE.stripTags(key);

            // Pixel width calculation
            int pixelWidth = getPixelWidth(strippedText);
            // Standard Minecraft Chest GUI title width is roughly 176 pixels.
            // Center point is around 88px.
            // However, titles start with a slight left margin (approx 8px).
            // Effective drawable center relative to start is ~80px.
            int centerPos = 80; 
            int paddingPixels = Math.max(0, centerPos - (pixelWidth / 2));
            
            // Space width is 4px (default font) + 1px spacing = ~4-5px depending on renderer context.
            // In titles, space is usually 4px.
            int spaceWidth = 4; 
            int spaces = paddingPixels / spaceWidth;

            String padded = " ".repeat(spaces) + strippedText;

            // Centered, bold, and ensuring no italics
            return MINI_MESSAGE.deserialize("<b>" + padded + "</b>")
                    .decoration(TextDecoration.ITALIC, false);
        });
    }

    /**
     * Returns a Component that is pseudo-centered, preserving its original formatting.
     * This is useful for item display names or messages that need centering but retain colors.
     */
    public static Component centerFormattedText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Component.empty();
        }
        String strippedText = MINI_MESSAGE.stripTags(rawText);
        int pixelWidth = getPixelWidth(strippedText);
        // Standard lore width is wider, let's assume ~160px visual center for chat/lore or keep legacy char-based if simpler
        // But for consistency, let's use pixel width targeting ~80px center like titles if intended for GUI
        int centerPos = 80; 
        int paddingPixels = Math.max(0, centerPos - (pixelWidth / 2));
        int spaceWidth = 4;
        int spaces = paddingPixels / spaceWidth;
        
        String padded = " ".repeat(spaces) + rawText; 
        return MINI_MESSAGE.deserialize(padded)
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Applique le preset de couleurs violet sur les tags MiniMessage codés en dur.
     * Règle: only gold/yellow basculent en dark_purple/light_purple.
     */
    public static String themed(String miniMessageText) {
        if (miniMessageText == null) {
            return "";
        }
        String theme = "default";
        try {
            if (Config.getGeneralModule() != null) {
                theme = Config.getGeneralModule().getString("gui.theme", "default");
            }
        } catch (Exception ignored) {
            // Keep default when config is not available yet
        }

        if (!"violet".equalsIgnoreCase(theme)) {
            return miniMessageText;
        }

        return miniMessageText
                .replace("<gold>", "<dark_purple>")
                .replace("</gold>", "</dark_purple>")
                .replace("<yellow>", "<light_purple>")
                .replace("</yellow>", "</light_purple>");
    }

    /**
     * Parse MiniMessage with current GUI theme and remove italics by default.
     */
    public static Component themedComponent(String miniMessageText) {
        if (miniMessageText == null || miniMessageText.isBlank()) {
            return Component.empty();
        }
        String themed = themed(miniMessageText);
        return THEMED_CACHE.get(themed, k ->
                MINI_MESSAGE.deserialize(k).decoration(TextDecoration.ITALIC, false));
    }

    private static int getPixelWidth(String text) {
        int width = 0;
        boolean isBold = false; // Titles in this class are usually bolded later, but input 'raw' might not have tags yet.
        // However, the 'title' method wraps in <b>, so we should calculate width assuming Bold (+1px per char usually).
        // Let's assume Bold for 'title' calculation.
        
        for (char c : text.toCharArray()) {
            width += getCharWidth(c) + 1; // +1 for Bold effect assumption
        }
        return width;
    }

    private static int getCharWidth(char c) {
        if (c == ' ') return 4;
        if ("i!,.:;|".indexOf(c) >= 0) return 2;
        if ("l'`".indexOf(c) >= 0) return 3;
        if ("tI[]".indexOf(c) >= 0) return 4;
        if ("fk\"".indexOf(c) >= 0) return 5;
        return 6; // Default width for most chars
    }

    // ==================== STANDARDIZED TEXT FORMATTING ====================

    /**
     * Returns a standardized gold/bold title component.
     * Format: <gold><b>text</b></gold>
     *
     * @param text The title text (without MiniMessage tags)
     * @return A gold, bold, non-italic component
     */
    public static Component goldBoldTitle(String text) {
        if (text == null || text.isBlank()) text = "";
        String tag = "<gold><b>" + text + "</b></gold>";
        return LORE_BUTTON_CACHE.get(tag, k ->
                MINI_MESSAGE.deserialize(k).decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Returns a standardized white text component.
     * Format: <white>text</white>
     *
     * @param text The text content
     * @return A white, non-italic component
     */
    public static Component whiteText(String text) {
        if (text == null || text.isBlank()) text = "";
        String tag = "<white>" + text + "</white>";
        return LORE_BUTTON_CACHE.get(tag, k ->
                MINI_MESSAGE.deserialize(k).decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Returns a standardized gray text component.
     * Format: <gray>text</gray>
     *
     * @param text The text content
     * @return A gray, non-italic component
     */
    public static Component grayText(String text) {
        if (text == null || text.isBlank()) text = "";
        String tag = "<gray>" + text + "</gray>";
        return LORE_BUTTON_CACHE.get(tag, k ->
                MINI_MESSAGE.deserialize(k).decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Returns a standardized yellow/important text component.
     * Format: <yellow>text</yellow>
     * Use this for important information, call-to-action, etc.
     *
     * @param text The important text
     * @return A yellow, non-italic component
     */
    public static Component yellowText(String text) {
        if (text == null || text.isBlank()) text = "";
        String tag = "<yellow>" + text + "</yellow>";
        return LORE_BUTTON_CACHE.get(tag, k ->
                MINI_MESSAGE.deserialize(k).decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Returns a standardized green/success text component.
     * Format: <green><b>text</b></green>
     * Use this for positive actions, confirm buttons, etc.
     *
     * @param text The success text
     * @return A green, bold, non-italic component
     */
    public static Component greenBoldText(String text) {
        if (text == null || text.isBlank()) text = "";
        String tag = "<green><b>" + text + "</b></green>";
        return LORE_BUTTON_CACHE.get(tag, k ->
                MINI_MESSAGE.deserialize(k).decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Returns a standardized red/error text component.
     * Format: <red><b>text</b></red>
     * Use this for dangerous actions, cancel buttons, errors, etc.
     *
     * @param text The error/danger text
     * @return A red, bold, non-italic component
     */
    public static Component redBoldText(String text) {
        if (text == null || text.isBlank()) text = "";
        String tag = "<red><b>" + text + "</b></red>";
        return LORE_BUTTON_CACHE.get(tag, k ->
                MINI_MESSAGE.deserialize(k).decoration(TextDecoration.ITALIC, false));
    }
}
