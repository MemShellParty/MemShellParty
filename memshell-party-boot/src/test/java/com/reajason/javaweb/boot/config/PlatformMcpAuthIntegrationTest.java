package com.reajason.javaweb.boot.config;

import com.reajason.javaweb.packer.Packers;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "memshell-party.auth.mode=PLATFORM")
class PlatformMcpAuthIntegrationTest {

    private static final String VALID_TOKEN = PlatformSignServer.VALID_TOKEN;
    private static final PlatformSignServer SIGN_SERVER = new PlatformSignServer();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("memshell-party.auth.platform.rest.auth-param", () -> "x_token");
        registry.add("memshell-party.auth.platform.rest.sign-url", () -> SIGN_SERVER.url("/rest-check"));
        registry.add("memshell-party.auth.platform.rest.sign-param", () -> "x_token");
        registry.add("memshell-party.auth.platform.rest.sign-success-res", () -> "\"code\":1");

        registry.add("memshell-party.auth.platform.mcp.auth-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-url", () -> SIGN_SERVER.url("/mcp-check"));
        registry.add("memshell-party.auth.platform.mcp.sign-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-success-res", () -> "\"code\":1");
    }

    @AfterAll
    static void stopSignServer() {
        SIGN_SERVER.close();
    }

    @Test
    void restAndMcpVerifyAgainstTheirOwnScopes() throws Exception {
        int restBefore = SIGN_SERVER.calls("/rest-check");
        int mcpBefore = SIGN_SERVER.calls("/mcp-check");

        assertEquals(200, send(request("/api/config?x_token=" + VALID_TOKEN)).statusCode());
        assertEquals(restBefore + 1, SIGN_SERVER.calls("/rest-check"));
        assertEquals(mcpBefore, SIGN_SERVER.calls("/mcp-check"));

        assertPlatformFailure(rawJsonRpc("/mcp?x_token=" + VALID_TOKEN));
        assertPlatformFailure(request("/api/config?mcp_token=" + VALID_TOKEN));
        assertEquals(restBefore + 1, SIGN_SERVER.calls("/rest-check"));
        assertEquals(mcpBefore, SIGN_SERVER.calls("/mcp-check"));
    }

    @Test
    void validMcpCredentialGeneratesInTheMcpServer() {
        int restBefore = SIGN_SERVER.calls("/rest-check");
        int mcpBefore = SIGN_SERVER.calls("/mcp-check");

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp?mcp_token=" + VALID_TOKEN)
                .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();
            assertTrue(SIGN_SERVER.calls("/mcp-check") > mcpBefore);
            assertEquals(restBefore, SIGN_SERVER.calls("/rest-check"));

            McpSchema.ListToolsResult tools = client.listTools();
            assertTrue(tools.tools().stream().anyMatch(tool -> "generate_probe_shell".equals(tool.name())));

            JsonNode generated = callProbeShell(client);
            assertFalse(generated.toString().contains(VALID_TOKEN));
            assertFalse(generated.has("endpoint"), generated.toString());
            assertFalse(generated.path("packResult").asText().isBlank(), generated.toString());
            assertFalse(generated.path("probeShellResult").path("shellClassName").asText().isBlank(),
                    generated.toString());
            assertEquals(restBefore, SIGN_SERVER.calls("/rest-check"));
        }
    }

    @Test
    void invalidMcpCredentialIsForbiddenBeforeJsonRpc() throws Exception {
        assertPlatformFailure(rawJsonRpc("/mcp?mcp_token=unknown-token"));
    }

    private JsonNode callProbeShell(McpSyncClient client) {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "generate_probe_shell",
                Map.of("request", Map.of(
                        "probeConfig", Map.of(
                                "probeMethod", "ResponseBody",
                                "probeContent", "Command",
                                "targetJreVersion", 50),
                        "probeContentConfig", Map.of(
                                "server", "Tomcat",
                                "reqParamName", "cmd"),
                        "packer", Packers.DefaultBase64))));
        assertNotEquals(Boolean.TRUE, result.isError(), () -> String.valueOf(result.content()));
        assertFalse(result.content().isEmpty());
        return objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpRequest.Builder rawJsonRpc(String path) {
        return request(path)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                                + "\"clientInfo\":{\"name\":\"raw\",\"version\":\"1\"}}}"));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertPlatformFailure(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = send(request);
        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).isEmpty());
        assertEquals("no-store", response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null));
        assertEquals("", response.body());
    }
}
