package com.github.lye.service.impl;

import com.github.lye.data.Shop;
import com.github.lye.service.IMessageService;
import com.github.lye.config.settings.IMessageSettings;
import com.github.lye.util.EconomyUtil;
import com.github.lye.util.Format;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DefaultMessageService implements IMessageService {

    private final IMessageSettings messageSettings;

    public DefaultMessageService(IMessageSettings messageSettings) {
        this.messageSettings = messageSettings;
    }

    @Override
    public void sendPurchaseMessage(Player player, Shop shop, int amount, double price, boolean isBuy) {
        Component display = Shop.getDisplayName(shop.getName(), shop.isEnchantment());
        double balance = EconomyUtil.getEconomy().getBalance(player);
        TagResolver r = getPurchaseTagResolver(display, price, amount, balance);
        String message = isBuy ? messageSettings.getShopPurchase() : messageSettings.getShopSell();
        player.sendMessage(MiniMessage.miniMessage().deserialize(message, r));
    }

    @Override
    public void sendSellMessage(Player player, Component display, double total, int amount) {
        TagResolver finalR = TagResolver.resolver(
                Placeholder.component("item", display),
                Placeholder.parsed("total", Format.currency(total)),
                Placeholder.parsed("price", Format.currency(total / Math.max(1, amount))),
                Placeholder.parsed("amount", Integer.toString(amount)),
                Placeholder.parsed("balance", Format.currency(EconomyUtil.getEconomy().getBalance(player)))
        );
        player.sendMessage(MiniMessage.miniMessage().deserialize(messageSettings.getShopSell(), finalR));
    }

    @Override
    public TagResolver getPurchaseTagResolver(Component display, double price, int amount, double balance) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(TagResolver.resolver(
                Placeholder.component("item", display),
                Placeholder.parsed("total", Format.currency(price * amount)),
                Placeholder.parsed("price", Format.currency(price)),
                Placeholder.parsed("amount", Integer.toString(amount)),
                Placeholder.parsed("balance", Format.currency(balance))));
        return builder.build();
    }

    @Override
    public void sendErrorMessage(CommandSender sender, String configKey, TagResolver resolver) {
        String template = messageSettings.getMessage(configKey);
        if (template == null) {
            template = configKey;
        }
        TagResolver safeResolver = resolver != null ? resolver : TagResolver.empty();
        sender.sendMessage(MiniMessage.miniMessage().deserialize(template, safeResolver));
    }

    @Override
    public void sendInfoMessage(CommandSender sender, String configKey, TagResolver resolver) {
        String template = messageSettings.getMessage(configKey);
        if (template == null) {
            template = configKey;
        }
        TagResolver safeResolver = resolver != null ? resolver : TagResolver.empty();
        sender.sendMessage(MiniMessage.miniMessage().deserialize(template, safeResolver));
    }
}
