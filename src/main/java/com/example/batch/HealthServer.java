package com.example.batch;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class HealthServer {
    private static final Logger logger = LoggerFactory.getLogger(HealthServer.class);
    private HttpServer server;
    private final int port;
    private final DatabaseClient dbClient;

    public HealthServer(int port, DatabaseClient dbClient) {
        this.port = port;
        this.dbClient = dbClient;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", new HealthHandler());
            server.createContext("/ready", new ReadyHandler(dbClient));
            server.setExecutor(null); // creates a default executor
            server.start();
            logger.info("Health server started on port {}", port);
        } catch (IOException e) {
            logger.error("Failed to start health server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("Health server stopped");
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "OK";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class ReadyHandler implements HttpHandler {
        private final DatabaseClient dbClient;

        public ReadyHandler(DatabaseClient dbClient) {
            this.dbClient = dbClient;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            if (dbClient.ping(3)) {
                String response = "READY";
                t.sendResponseHeaders(200, response.length());
                try (OutputStream os = t.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                String response = "NOT READY";
                t.sendResponseHeaders(503, response.length());
                try (OutputStream os = t.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
}
