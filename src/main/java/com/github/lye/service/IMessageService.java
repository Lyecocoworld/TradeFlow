package com.github.lye.service;

import com.github.lye.data.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface IMessageService {
    void sendPurchaseMessage(Player player, Shop shop, int amount, double price, boolean isBuy);
    void sendSellMessage(Player player, Component display, double total, int amount);
    TagResolver getPurchaseTagResolver(Component display, double price, int amount, double balance);
    void sendErrorMessage(CommandSender sender, String configKey, TagResolver resolver);
    void sendInfoMessage(CommandSender sender, String configKey, TagResolver resolver);
}
