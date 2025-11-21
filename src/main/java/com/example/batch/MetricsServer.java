package com.example.batch;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import io.prometheus.client.exporter.PushGateway;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricsServer {
    private final PrometheusMeterRegistry registry;
    private HttpServer server;
    private final int port;
    private final ScheduledExecutorService persistScheduler = Executors.newSingleThreadScheduledExecutor();
    private final String pushGatewayUrl;
    private final String persistFile;

    public MetricsServer(int port, PrometheusMeterRegistry registry) {
        this.port = port;
        this.registry = registry;
        this.pushGatewayUrl = System.getenv("METRICS_PUSHGATEWAY_URL");
        this.persistFile = System.getenv("METRICS_PERSIST_FILE");
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/metrics", new MetricsHandler(registry));
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
        persistScheduler.shutdownNow();
    }

    static class MetricsHandler implements HttpHandler {
        private final PrometheusMeterRegistry registry;

        MetricsHandler(PrometheusMeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = registry.scrape();
            exchange.getResponseHeaders().add("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private void schedulePersistence() {
        if ((pushGatewayUrl == null || pushGatewayUrl.isBlank()) && (persistFile == null || persistFile.isBlank())) return;

        Runnable task = () -> {
            try {
                String scraped = registry.scrape();
                if (persistFile != null && !persistFile.isBlank()) {
                    try (PrintWriter out = new PrintWriter(new FileWriter(persistFile, false))) {
                        out.write(scraped);
                    }
                }
                if (pushGatewayUrl != null && !pushGatewayUrl.isBlank()) {
                    PushGateway pg = new PushGateway(pushGatewayUrl);
                    // push with job name 'db-health-check'
                    pg.pushAdd(io.prometheus.client.CollectorRegistry.defaultRegistry, "db-health-check");
                }
            } catch (Exception e) {
                System.err.println("Metrics persistence failed: " + e.getMessage());
            }
        };

        persistScheduler.scheduleWithFixedDelay(task, 0, 60, TimeUnit.SECONDS);
    }

    public void startPersistence() {
        schedulePersistence();
    }
}
