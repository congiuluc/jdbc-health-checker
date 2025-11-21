package com.example.batch;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

public class BatchApplication {
    private static final Logger logger = LoggerFactory.getLogger(BatchApplication.class);
    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int HEALTH_PORT = 8080;

    public static void main(String[] args) {
        logger.info("Starting SQL Batch Job...");

        // 1. Initialize Configuration & DataSource
        AppConfig config = new AppConfig();
        DataSource dataSource = config.createDataSource();
        String query = config.getQuery();
        DatabaseClient dbClient = new DatabaseClient(dataSource, query);

        // 2. Start Health Server for K8s Probes
        HealthServer healthServer = new HealthServer(HEALTH_PORT, dbClient);
        healthServer.start();

        // 2b. Start Metrics (Prometheus) server on port 9090 by default
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MetricsServer metricsServer = new MetricsServer(9090, registry);
        try {
            metricsServer.start();
            logger.info("Metrics server started on port 9090");
        } catch (IOException e) {
            logger.warn("Failed to start metrics server: {}", e.getMessage());
        }

        // 3. Schedule the Batch Job
        int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        try {
            intervalSeconds = Integer.parseInt(System.getenv().getOrDefault("INTERVAL_SECONDS", String.valueOf(DEFAULT_INTERVAL_SECONDS)));
        } catch (NumberFormatException nfe) {
            logger.warn("Invalid INTERVAL_SECONDS env var, using default {}", DEFAULT_INTERVAL_SECONDS);
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        
        Runnable task = () -> {
            try {
                logger.info("Starting scheduled query execution...");
                boolean success = dbClient.runHealthQuery();
                if (!success) {
                    logger.warn("Scheduled query failed or returned no results.");
                }
            } catch (Exception e) {
                logger.error("Unexpected error during scheduled task", e);
            }
        };

        logger.info("Scheduling query to run every {} seconds", intervalSeconds);
        // Use scheduleWithFixedDelay to prevent overlap
        scheduler.scheduleWithFixedDelay(task, 0, intervalSeconds, TimeUnit.SECONDS);

        // 4. Register Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down application...");
            healthServer.stop();
            scheduler.shutdown();
            try {
                // Wait longer for tasks to complete
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }

            if (dataSource instanceof HikariDataSource) {
                ((HikariDataSource) dataSource).close();
                logger.info("DataSource closed.");
            }
            metricsServer.stop();
            logger.info("Shutdown complete.");
        }));
    }
}
