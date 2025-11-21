package com.example.batch;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Optional;

public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);

    // Environment Variable Keys
    private static final String ENV_DB_URL = "DB_URL";
    private static final String ENV_DB_USER = "DB_USER";
    private static final String ENV_DB_PASSWORD = "DB_PASSWORD";
    private static final String ENV_POOL_SIZE = "DB_POOL_SIZE";
    private static final String ENV_DB_QUERY = "DB_QUERY";

    public String getQuery() {
        String q = getEnv(ENV_DB_QUERY, "SELECT 1").trim();
        if (q.length() > 200 || q.contains(";")) {
            throw new IllegalArgumentException("Invalid DB_QUERY: Must be short and single statement.");
        }
        return q;
    }

    public DataSource createDataSource() {
        // Default to trustServerCertificate=false for security
        String jdbcUrl = getEnv(ENV_DB_URL, "jdbc:sqlserver://localhost:1433;databaseName=master;encrypt=true;trustServerCertificate=false;");
        String username = getEnv(ENV_DB_USER, "db_user");
        String password = getEnv(ENV_DB_PASSWORD, "yourStrong(!)Password");
        int poolSize = Integer.parseInt(getEnv(ENV_POOL_SIZE, "2"));

        // Mask sensitive info in logs
        logger.info("Configuring HikariCP with URL: {}, User: {}, PoolSize: {}", maskUrl(jdbcUrl), username, poolSize);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Best practices for HikariCP
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(1);
        config.setPoolName("BatchJobPool");
        
        // Connection freshness and timeouts
        config.setConnectionTimeout(30000); // 30s to get a connection
        config.setValidationTimeout(5000);  // 5s to validate connection
        config.setIdleTimeout(600000);      // 10m idle
        config.setMaxLifetime(1800000);     // 30m max lifetime (less than typical DB timeouts)
        
        // Keepalive to prevent network drops (Azure SQL idle timeout is often 4-30 mins)
        config.setKeepaliveTime(300000);    // 5m keepalive

        return new HikariDataSource(config);
    }

    private String maskUrl(String url) {
        // Simple masking to avoid logging secrets
        return url.replaceAll("password=.*?;", "password=***;");
    }

    private String getEnv(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key)).orElse(defaultValue);
    }
}
