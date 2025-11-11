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
import com.github.lye.messages.MessageManager; // Import MessageManager

/**
 * The class for formatting messages.
 */
@UtilityClass
public class Format {

    // The currency format.
    private static NumberFormat currency;
    // The percentage format.
    private static NumberFormat percent;
    // The decimal format.
    private static NumberFormat decimal;
    // The number format.
    private static NumberFormat number;
    // The date format
    private static DateFormat date;
    // The logger
    @Getter
    private static TradeFlowLogger log;

    /**
     * Loads the locale and formats.
     * @param localeString the locale string
     */
    public static void loadLocale(@NotNull String localeString) {
        String[] localeSplit = localeString.split("_");
        // The locale to use.
        Locale locale = new Locale(localeSplit[0], localeSplit[1]);
        currency = NumberFormat.getCurrencyInstance(locale);
        percent = NumberFormat.getPercentInstance(locale);
        percent.setMaximumFractionDigits(2);
        decimal = NumberFormat.getNumberInstance(locale);
        decimal.setMaximumFractionDigits(2);
        number = NumberFormat.getNumberInstance(locale);
        date = DateFormat.getDateInstance(DateFormat.SHORT, locale);
    }

    /**
     * Loads the logger.
     */
    public static void loadLogger(@NotNull Level level) {
        log = new TradeFlowLogger(TradeFlow.getInstance());
        log.setLevel(level);
        log.info("Logger loaded with level " + log.getLevel().toString());
    }

    /**
     * Format a number to a currency string.
     * @param amount the amount to format
     * @return the formatted currency string
     */
    public static String currency(double amount) {
        return currency.format(amount);
    }

    /**
     * Format a number to a percentage string.
     * @param amount the amount to format
     * @return the formatted percentage string
     */
    public static String percent(double amount) {
        return percent.format(amount);
    }

    /**
     * Format a number to a decimal string.
     * @param amount the amount to format
     * @return the formatted decimal string
     */
    public static String decimal(double amount) {
        return decimal.format(amount);
    }

    /**
     * Format a number to a number string.
     * @param amount the amount to format
     * @return the formatted number string
     */
    public static String number(double amount) {
        return number.format(amount);
    }

    /**
     * Format a millis long to a date.
     * @param time the time to format
     * @return the formatted date string
     */
    public static String date(long time) {
        return date.format(time);
    }

    /**
     * Send a message to a player using the MiniMessage API and a tag resolver.
     * @param player   the player to send the message to
     * @param messageKey  the message key to send
     * @param resolvers the tag resolvers
     */
    public static void sendMessage(@NotNull Player player, @NotNull String messageKey,
        TagResolver... resolvers) { // Changed to varargs
        MessageManager.sendMessage(player, messageKey, resolvers);
    }

    /**
     * Send a message to a player using the MiniMessage API.
     * @param player  the player to send the message to
     * @param messageKey the message key to send
     */
    public static void sendMessage(@NotNull Player player, @NotNull String messageKey) {
        MessageManager.sendMessage(player, messageKey);
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
        MessageManager.sendMessage(sender, messageKey, resolvers);
    }

    /**
     * Send a message to a CommandSender using the MiniMessage API.
     * @param sender  The command sender
     * @param messageKey The message key to send
     */
    public static void sendMessage(@NotNull CommandSender sender, @NotNull String messageKey) {
        MessageManager.sendMessage(sender, messageKey);
    }

    /**
     * Get the component of a message using the MiniMessage API and a tag resolver.
     */
    public static Component getComponent(@NotNull String messageKey, TagResolver... resolvers) { // Changed to varargs
        return MessageManager.getComponent(messageKey, resolvers);
    }

    /**
     * Get the component of a message using the MiniMessage API.
     */
    public static Component getComponent(@NotNull String messageKey) {
        return MessageManager.getComponent(messageKey);
    }

    public static void sendRawMessage(@NotNull CommandSender sender, @NotNull String rawMessage, TagResolver... resolvers) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(rawMessage, resolvers));
    }


}
