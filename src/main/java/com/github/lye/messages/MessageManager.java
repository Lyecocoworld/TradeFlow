package com.github.lye.messages;

import com.github.lye.config.settings.IMessageSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import com.github.lye.config.Config;

import java.util.Map;

public class MessageManager {

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Minimal fallbacks to avoid noisy logs when keys are missing.
    // Keep them simple and language-agnostic when possible.
    private static final Map<String, String> FALLBACKS = Map.of(
            "gui-back-to-menu", "<gray>Back</gray>",
            // Expect a <page> placeholder provided by caller
            "gui-go-to-page", "<gray>Page <page></gray>"
    );

    private static String resolveMessageOrFallback(@NotNull IMessageSettings messageSettings, @NotNull String messageKey) {
        String message = messageSettings.getMessage(messageKey);
        if (message == null || message.isEmpty()) {
            return FALLBACKS.get(messageKey);
        }
        return message;
    }

    public static void sendMessage(@NotNull IMessageSettings messageSettings, @NotNull CommandSender sender, @NotNull String messageKey, TagResolver... resolvers) {
        String message = resolveMessageOrFallback(messageSettings, messageKey);
        if (message == null || message.isEmpty()) {
            // Final fallback: explicit error so it's visible but not crashing
            sender.sendMessage(miniMessage.deserialize("<red>" + messageKey + "</red>"));
            return;
        }
        sender.sendMessage(miniMessage.deserialize(message, resolvers));
    }

    public static Component getComponent(@NotNull IMessageSettings messageSettings, @NotNull String messageKey, TagResolver... resolvers) {
        String message = resolveMessageOrFallback(messageSettings, messageKey);
        if (message == null || message.isEmpty()) {
            return miniMessage.deserialize("<gray>" + messageKey + "</gray>");
        }
        return miniMessage.deserialize(message, resolvers);
    }

    // Add more utility methods for specific message types if needed
}
