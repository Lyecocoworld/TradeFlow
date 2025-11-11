package unprotesting.com.github.data;

import org.bukkit.entity.Player;

import java.util.UUID;

public class EnchantmentSession {
    private final UUID playerUuid;
    private String enchantmentKey;
    private int level;
    private int quantity;

    public EnchantmentSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.enchantmentKey = null; // Will be set in the level selection GUI
        this.level = 1; // Default level
        this.quantity = 1; // Default quantity
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getEnchantmentKey() {
        return enchantmentKey;
    }

    public void setEnchantmentKey(String enchantmentKey) {
        this.enchantmentKey = enchantmentKey;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}