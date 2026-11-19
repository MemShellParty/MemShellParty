package com.reajason.javaweb.boot.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class PlatformSignServer implements AutoCloseable {

    static final String VALID_TOKEN = "platform-valid-token";
    static final String SERVER_ERROR_TOKEN = "platform-server-error";
    static final String NO_MARKER_TOKEN = "platform-no-marker";
    static final String SUCCESS_BODY = "{\"code\":1,\"message\":\"成功\",\"data\":{}}";

    private final HttpServer server;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    PlatformSignServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        calls.computeIfAbsent(exchange.getRequestURI().getPath(), path -> new AtomicInteger()).incrementAndGet();

        String token = token(exchange);
        int status = 200;
        String body;
        if (VALID_TOKEN.equals(token)) {
            body = SUCCESS_BODY;
        } else if (SERVER_ERROR_TOKEN.equals(token)) {
            status = 500;
            body = SUCCESS_BODY;
        } else if (NO_MARKER_TOKEN.equals(token)) {
            body = "{\"code\":0}";
        } else {
            body = "{\"code\":8,\"message\":\"token无效\",\"data\":null}";
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String token(HttpExchange exchange) {
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        if (query.containsKey("x_token")) {
            return query.get("x_token");
        }
        if (query.containsKey("mcp_token")) {
            return query.get("mcp_token");
        }
        return exchange.getRequestHeaders().getFirst("X-Platform-Token");
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null) {
            return values;
        }
        for (String pair : rawQuery.split("&", -1)) {
            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = decode(equals < 0 ? "" : pair.substring(equals + 1));
            values.putIfAbsent(name, value);
        }
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    int calls(String path) {
        AtomicInteger count = calls.get(path);
        return count == null ? 0 : count.get();
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
