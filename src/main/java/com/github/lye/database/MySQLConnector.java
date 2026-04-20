package com.github.lye.database;

import com.github.lye.config.settings.IPluginSettings;
import com.github.lye.resilience.CircuitBreaker;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * MySQL connection pool with HikariCP and Circuit Breaker protection.
 *
 * @author lye
 * @since 0.1
 */
public class MySQLConnector {

    private static final Logger LOGGER = Logger.getLogger(MySQLConnector.class.getName());
    private final HikariDataSource dataSource;
    private final CircuitBreaker circuitBreaker;

    public MySQLConnector(IPluginSettings settings) throws Exception {
        HikariConfig config = new HikariConfig();

        String host = settings.getDatabaseHost();
        int port = settings.getDatabasePort();
        String dbName = settings.getDatabaseName();
        String user = settings.getDatabaseUser();
        String pass = settings.getDatabasePassword();
        boolean useSSL = settings.isDatabaseSslEnabled();
        boolean allowPublicKeyRetrieval = settings.isDatabasePublicKeyRetrieval();

        InetAddress resolvedAddress = InetAddress.getByName(host);

        config.setPoolName("TradeFlow-Pool");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=%s", 
                resolvedAddress.getHostAddress(), port, dbName, useSSL, allowPublicKeyRetrieval);
        config.setJdbcUrl(jdbcUrl);
        
        config.setUsername(user);
        config.setPassword(pass);

        // Performance Tuning from config
        config.setMaximumPoolSize(settings.getDatabasePoolSize());
        config.setMinimumIdle(settings.getDatabaseMinIdle());
        config.setConnectionTimeout(10000); // 10s
        config.setIdleTimeout(300000);      // 5m
        config.setMaxLifetime(900000);      // 15m

        // Connection validation and health check
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(3000);   // 3s validation timeout

        // MySQL specific optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        this.dataSource = new HikariDataSource(config);

        // Initialize circuit breaker for database operations
        this.circuitBreaker = CircuitBreaker.builder()
                .name("mysql")
                .failureThreshold(3)
                .openTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public Connection getConnection() throws SQLException {
        try {
            return circuitBreaker.execute(() -> {
                try {
                    return dataSource.getConnection();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            LOGGER.warning("Circuit breaker OPEN - blocking database access");
            throw new SQLException("Database circuit breaker is open: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw new SQLException(e.getMessage(), e);
        } catch (Exception e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    /**
     * Gets the circuit breaker for monitoring/management.
     *
     * @return the circuit breaker
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Gets the current circuit breaker metrics.
     *
     * @return the metrics string
     */
    public String getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}