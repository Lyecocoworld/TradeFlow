package unprotesting.com.github.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.config.Config;

public class MessageManager {

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public static void sendMessage(@NotNull CommandSender sender, @NotNull String messageKey, TagResolver... resolvers) {
        String message = Config.get().getMessage(messageKey);
        if (message == null || message.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<red>Error: Message key '" + messageKey + "' not found in config.</red>"));
            return;
        }
        sender.sendMessage(miniMessage.deserialize(message, resolvers));
    }

    public static Component getComponent(@NotNull String messageKey, TagResolver... resolvers) {
        String message = Config.get().getMessage(messageKey);
        if (message == null || message.isEmpty()) {
            return miniMessage.deserialize("<red>Error: Message key '" + messageKey + "' not found in config.</red>");
        }
        return miniMessage.deserialize(message, resolvers);
    }

    // Add more utility methods for specific message types if needed
}