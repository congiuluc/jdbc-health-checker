package com.example.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseClient {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseClient.class);
    private final DataSource dataSource;
    private final String query;

    public DatabaseClient(DataSource dataSource, String query) {
        this.dataSource = dataSource;
        this.query = query;
    }

    public boolean runHealthQuery() {
        logger.debug("Executing query: {}", query);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                int result = resultSet.getInt(1);
                logger.info("Query executed successfully. Result: {}", result);
                return true;
            } else {
                logger.warn("Query executed but returned no rows.");
                return false;
            }

        } catch (SQLException e) {
            logger.error("Failed to execute query: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean ping(int timeoutSeconds) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setQueryTimeout(timeoutSeconds);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            logger.debug("Ping failed: {}", e.getMessage());
            return false;
        }
    }
}
