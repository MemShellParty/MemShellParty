package com.reajason.javaweb.boot.config;

import com.reajason.javaweb.ShellGenerator;
import com.reajason.javaweb.memshell.MemShellRegistry;
import com.reajason.javaweb.memshell.config.ConfigField;
import com.reajason.javaweb.memshell.config.RemoteOptions;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "memshell-party.auth.mode=PLATFORM")
class RemoteOptionsPlatformIntegrationTest {

    private static final String TOOL = "RemoteOptionsPlatformFixture";
    private static final String TOKEN = PlatformSignServer.VALID_TOKEN;
    private static final PlatformSignServer SIGN_SERVER = new PlatformSignServer();
    private static final HttpServer OPTIONS_SERVER = startOptionsServer();
    private static final AtomicReference<String> REST_FORWARDED = new AtomicReference<>();
    private static final AtomicReference<String> MCP_FORWARDED = new AtomicReference<>();
    private static final AtomicInteger OPTIONS_CALLS = new AtomicInteger();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerFixture();
        registry.add("memshell-party.auth.platform.rest.auth-param", () -> "x_token");
        registry.add("memshell-party.auth.platform.rest.sign-url", () -> SIGN_SERVER.url("/rest-check"));
        registry.add("memshell-party.auth.platform.rest.sign-param", () -> "x_token");
        registry.add("memshell-party.auth.platform.rest.sign-success-res", () -> "\"code\":1");
        registry.add("memshell-party.auth.platform.mcp.auth-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-url", () -> SIGN_SERVER.url("/mcp-check"));
        registry.add("memshell-party.auth.platform.mcp.sign-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-success-res", () -> "\"code\":1");
        registry.add("memshell-party.remote-options.sources[0].tool", () -> TOOL);
        registry.add("memshell-party.remote-options.sources[0].field", () -> "profile");
        registry.add("memshell-party.remote-options.sources[0].url",
                () -> "https://127.0.0.1:" + OPTIONS_SERVER.getAddress().getPort() + "/profiles");
        registry.add("memshell-party.remote-options.sources[0].rest.auth-header", () -> "x_token");
        registry.add("memshell-party.remote-options.sources[0].mcp.auth-param", () -> "app_key");
    }

    @BeforeEach
    void resetObservations() {
        REST_FORWARDED.set(null);
        MCP_FORWARDED.set(null);
        OPTIONS_CALLS.set(0);
    }

    @AfterAll
    static void stopServers() {
        OPTIONS_SERVER.stop(0);
        SIGN_SERVER.close();
    }

    @Test
    void restEndpointForwardsVerifiedCredentialAsHeader() throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + port + "/api/config/shelltool/" + TOOL
                                + "/fields/profile/options"))
                .header("x_token", TOKEN)
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        JsonNode options = objectMapper.readTree(response.body()).path("options");
        assertEquals("profile-a", options.path(0).path("value").asText(), response.body());
        assertEquals("Profile A", options.path(0).path("label").asText(), response.body());
        assertEquals(2, options.size(), response.body());
        assertFalse(response.body().contains("xor_key"), response.body());
        assertFalse(response.body().contains("must-not-reach-model"), response.body());
        assertEquals(TOKEN, REST_FORWARDED.get());
        assertNull(MCP_FORWARDED.get());
        assertEquals(1, OPTIONS_CALLS.get());
    }

    @Test
    void realMcpClientForwardsCredentialAsQueryOnTheToolRequestThread() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp?mcp_token=" + TOKEN)
                .build();
        McpSyncClient client = McpClient.sync(transport).build();
        try {
            client.initialize();
            assertTrue(client.listTools().tools().stream()
                    .anyMatch(tool -> "list_remote_options".equals(tool.name())));

            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                    "list_remote_options", Map.of("shellTool", TOOL, "field", "profile")));

            assertNotEquals(Boolean.TRUE, result.isError(), () -> String.valueOf(result.content()));
            assertFalse(result.content().isEmpty());
            String text = ((McpSchema.TextContent) result.content().get(0)).text();
            JsonNode options = objectMapper.readTree(text).path("options");
            assertEquals("profile-a", options.path(0).path("value").asText(), text);
            assertEquals("7", options.path(1).path("value").asText(), text);
            assertFalse(text.contains("must-not-reach-model"), text);
            assertEquals(TOKEN, MCP_FORWARDED.get());
            assertNull(REST_FORWARDED.get());
            assertEquals(1, OPTIONS_CALLS.get());
        } finally {
            client.closeGracefully();
        }
    }

    private static void registerFixture() {
        MemShellRegistry registry = MemShellRegistry.getInstance();
        registry.init();
        registry.registerTool(TOOL, new ShellToolProvider() {
            @Override
            public ToolMapping getToolMapping() {
                return ToolMapping.builder().build();
            }

            @Override
            public Class<? extends ShellToolConfig> getConfigClass() {
                return RemoteProfileConfig.class;
            }

            @Override
            public Class<? extends ShellGenerator> getGeneratorClass() {
                return null;
            }
        });
    }

    private static HttpServer startOptionsServer() {
        HttpServer server = PlatformHttpsSignIntegrationTest.httpsServer();
        server.createContext("/profiles", RemoteOptionsPlatformIntegrationTest::serveProfiles);
        server.start();
        return server;
    }

    private static void serveProfiles(HttpExchange exchange) throws IOException {
        OPTIONS_CALLS.incrementAndGet();
        REST_FORWARDED.set(exchange.getRequestHeaders().getFirst("x_token"));
        MCP_FORWARDED.set(query(exchange, "app_key"));
        String body = """
                {"data":[
                  {"id":"profile-a","name":"Profile A","xor_key":"must-not-reach-model"},
                  {"id":7,"name":"Profile 7","xor_key":"also-secret"}
                ]}
                """;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if (name.equals(key)) {
                return URLDecoder.decode(equals < 0 ? "" : pair.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @Getter
    @SuperBuilder
    @NoArgsConstructor
    public static class RemoteProfileConfig extends ShellToolConfig {
        @ConfigField
        @RemoteOptions(urlQueryParam = "url", itemsPath = "data", labelPath = "name",
                keyPath = "id", required = true)
        private Map<String, Object> profile;
    }
}
