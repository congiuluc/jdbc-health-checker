package com.example.batch;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DBHealthCheck {
    public static void main(String[] args) throws IOException {
        AppConfig config = new AppConfig();

        DataSource ds = config.createDataSource();

        String query = config.getQuery();
        DatabaseClient dbClient = new DatabaseClient(ds, query);

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MetricsServer metricsServer = new MetricsServer(9090, registry);
        metricsServer.start();
        metricsServer.startPersistence();

        HealthServer healthServer = new HealthServer(8080, dbClient);
        healthServer.start();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        int interval = 30;
        try {
            String iv = System.getenv("INTERVAL_SECONDS");
            if (iv != null && !iv.isBlank()) {
                interval = Integer.parseInt(iv);
            }
        } catch (Exception e) {
            System.err.println("Invalid INTERVAL_SECONDS, using default 30s");
        }

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                dbClient.runHealthQuery();
            } catch (Exception e) {
                System.err.println("Health check failed: " + e.getMessage());
            }
        }, 0, interval, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown requested, stopping services...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ignored) {
            }
            metricsServer.stop();
            healthServer.stop();
            if (ds instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) ds).close();
                } catch (Exception ignored) {
                }
            }
        }));
    }
}
