package unprotesting.com.github.data;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.EconomyUtil;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The class that represents a Loan.
 */
@Builder
@AllArgsConstructor
public class Loan implements Serializable {

    private static final long serialVersionUID = -5882241259956156012L;

    @Getter @Setter private double value;
    @Getter private final double base;
    @Getter private final UUID player;
    @Getter @Setter private boolean paid;




    public Loan(ResultSet rs) throws SQLException {
        this.value = rs.getDouble("value");
        this.base = rs.getDouble("base");
        this.player = UUID.fromString(rs.getString("player_uuid"));
        this.paid = rs.getBoolean("paid");
    }

    /**
     * Pay back the given loan.
     *
     * @return Whether or not the loan was paid back.
     */
    public boolean payBack() {
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player);
        double balance = EconomyUtil.getEconomy().getBalance(offPlayer);

        if (balance < value) {
            return false;
        }

        EconomyUtil.getEconomy().withdrawPlayer(offPlayer, value);
        paid = true;
        EconomyDataUtil.increaseEconomyData("LOSS", value - base);
        return true;
    }

    /**
     * Update the value of the loan.
     */
    public void update() {
        value += value * 0.01 * Config.get().getInterest();
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player);
        double balance = EconomyUtil.getEconomy().getBalance(offPlayer);

        if (balance <= value + value * 0.01 * Config.get().getInterest()) {
            payBack();
        }
    }


}
