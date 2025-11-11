package unprotesting.com.github.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.SQLException;

import java.net.InetAddress;

public class MySQLConnector {

    private final HikariDataSource dataSource;

    public MySQLConnector(FileConfiguration config) throws Exception {
        HikariConfig hikariConfig = new HikariConfig();

        String host = config.getString("database.host", "localhost");
        int port = config.getInt("database.port", 3306);
        String database = config.getString("database.database", "autotune");
        String username = config.getString("database.username", "user");
        String password = config.getString("database.password", "password");
        boolean useSSL = config.getBoolean("database.use-ssl", false);

        // Manually resolve the address to avoid ambiguity
        InetAddress resolvedAddress = InetAddress.getByName(host);

        hikariConfig.setPoolName("Auto-Tune-HikariPool");
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=%s", resolvedAddress.getHostAddress(), port, database, useSSL));
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
