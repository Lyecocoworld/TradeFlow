package com.github.lye.util;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.Level;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import com.github.lye.TradeFlow;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.messages.MessageManager; // Import MessageManager

/**
 * The class for formatting messages.
 */
@UtilityClass
public class Format {

    private static volatile Locale cachedLocale;

    private static final ThreadLocal<NumberFormat> currency = ThreadLocal.withInitial(() ->
            NumberFormat.getCurrencyInstance(cachedLocale != null ? cachedLocale : Locale.US));
    private static final ThreadLocal<NumberFormat> percent = ThreadLocal.withInitial(() -> {
        NumberFormat fmt = NumberFormat.getPercentInstance(cachedLocale != null ? cachedLocale : Locale.US);
        fmt.setMaximumFractionDigits(2);
        return fmt;
    });
    private static final ThreadLocal<NumberFormat> decimal = ThreadLocal.withInitial(() -> {
        NumberFormat fmt = NumberFormat.getNumberInstance(cachedLocale != null ? cachedLocale : Locale.US);
        fmt.setMaximumFractionDigits(2);
        return fmt;
    });
    private static final ThreadLocal<NumberFormat> number = ThreadLocal.withInitial(() ->
            NumberFormat.getNumberInstance(cachedLocale != null ? cachedLocale : Locale.US));
    private static final ThreadLocal<DateFormat> date = ThreadLocal.withInitial(() ->
            DateFormat.getDateInstance(DateFormat.SHORT, cachedLocale != null ? cachedLocale : Locale.US));
    private static TradeFlowLogger log;
    private static IMessageSettings messageSettings;

    public static TradeFlowLogger getLog() {
        return log;
    }

    /**
     * Sets the logger instance.
     * @param logger the logger to set
     */
    public static void setLog(TradeFlowLogger logger) {
        Format.log = logger;
    }

    /**
     * Sets the message settings instance for message resolution.
     * @param settings the message settings
     */
    public static void setMessageSettings(IMessageSettings settings) {
        Format.messageSettings = settings;
    }

    /**
     * Loads the locale and formats.
     * @param localeString the locale string
     */
    public static void loadLocale(@NotNull String localeString) {
        String[] localeSplit = localeString.split("_");
        Locale locale = new Locale(localeSplit[0], localeSplit[1]);
        cachedLocale = locale;
    }

    /**
     * Loads the logger.
     */
    public static void init(TradeFlowLogger logger) {
        log = logger;
    }

    /**
     * Format a number to a currency string.
     * @param amount the amount to format
     * @return the formatted currency string
     */
    public static String currency(double amount) {
        return currency.get().format(amount);
    }

    /**
     * Format a number to a percentage string.
     * @param amount the amount to format
     * @return the formatted percentage string
     */
    public static String percent(double amount) {
        return percent.get().format(amount);
    }

    /**
     * Format a number to a decimal string.
     * @param amount the amount to format
     * @return the formatted decimal string
     */
    public static String decimal(double amount) {
        return decimal.get().format(amount);
    }

    /**
     * Format a number to a number string.
     * @param amount the amount to format
     * @return the formatted number string
     */
    public static String number(double amount) {
        return number.get().format(amount);
    }

    /**
     * Format a millis long to a date.
     * @param time the time to format
     * @return the formatted date string
     */
    public static String date(long time) {
        return date.get().format(time);
    }

    /**
     * Send a message to a player using the MiniMessage API and a tag resolver.
     * @param player   the player to send the message to
     * @param messageKey  the message key to send
     * @param resolvers the tag resolvers
     */
    public static void sendMessage(@NotNull Player player, @NotNull String messageKey,
        TagResolver... resolvers) { // Changed to varargs
        MessageManager.sendMessage(messageSettings, player, messageKey, resolvers);
    }

    /**
     * Send a message to a player using the MiniMessage API.
     * @param player  the player to send the message to
     * @param messageKey the message key to send
     */
    public static void sendMessage(@NotNull Player player, @NotNull String messageKey) {
        MessageManager.sendMessage(messageSettings, player, messageKey);
    }

    /**
     * Send a message to a CommandSender using the MiniMessage API and a tag
     * resolver.
     * @param sender   The command sender
     * @param messageKey  The message key to send
     * @param resolvers The tag resolvers
     */
    public static void sendMessage(@NotNull CommandSender sender, @NotNull String messageKey,
            TagResolver... resolvers) { // Changed to varargs
        MessageManager.sendMessage(messageSettings, sender, messageKey, resolvers);
    }

    /**
     * Send a message to a CommandSender using the MiniMessage API.
     * @param sender  The command sender
     * @param messageKey The message key to send
     */
    public static void sendMessage(@NotNull CommandSender sender, @NotNull String messageKey) {
        MessageManager.sendMessage(messageSettings, sender, messageKey);
    }

    /**
     * Get the component of a message using the MiniMessage API and a tag resolver.
     */
    public static Component getComponent(@NotNull String messageKey, TagResolver... resolvers) { // Changed to varargs
        return MessageManager.getComponent(messageSettings, messageKey, resolvers);
    }

    /**
     * Get the component of a message using the MiniMessage API.
     */
    public static Component getComponent(@NotNull String messageKey) {
        return MessageManager.getComponent(messageSettings, messageKey);
    }

    public static void sendRawMessage(@NotNull CommandSender sender, @NotNull String rawMessage, TagResolver... resolvers) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(rawMessage, resolvers));
    }

    public static String prettifyName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "Unknown Item";
        }
        String[] parts = rawName.replace('_', ' ').toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Format a time duration in milliseconds to a string like "12m 30s".
     * @param millis Duration in milliseconds
     * @return Formatted string
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%dm %ds", minutes, seconds);
    }

    /**
     * Format a number with suffixes (k, M, B) or return "∞" if -1.
     * @param value The number to format
     * @return The formatted string
     */
    public static String compactNumber(double value) {
        if (value == -1) return "∞";
        
        // Handle negative numbers just in case, though usually limits are positive
        String sign = value < 0 ? "-" : "";
        double abs = Math.abs(value);

        if (abs < 1000) return sign + decimal.get().format(abs);
        
        int exp = (int) (Math.log(abs) / Math.log(1000));
        String suffix = "";
        switch (exp) {
            case 1: suffix = "k"; break;
            case 2: suffix = "M"; break;
            case 3: suffix = "Md"; break; // French Billion
            case 4: suffix = "T"; break;
            default: suffix = "E" + exp;
        }
        
        return sign + String.format(Locale.US, "%.1f%s", abs / Math.pow(1000, exp), suffix);
    }

    public static String compactNumber(int value) {
        return compactNumber((double) value);
    }
}
