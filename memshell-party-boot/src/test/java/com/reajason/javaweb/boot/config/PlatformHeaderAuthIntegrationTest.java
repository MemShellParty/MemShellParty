package com.reajason.javaweb.boot.config;

import com.reajason.javaweb.packer.Packers;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
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
        properties = {
                "memshell-party.auth.mode=PLATFORM",
                "memshell-party.auth.platform.secret=header-test-secret",
                "memshell-party.auth.platform.name=X-Platform-Credential"
        })
@ExtendWith(OutputCaptureExtension.class)
class PlatformHeaderAuthIntegrationTest {

    private static final String SECRET = "header-test-secret";
    private static final String CREDENTIAL_NAME = "X-Platform-Credential";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void headerCredentialProtectsRestMcpAndFollowOnRest(CapturedOutput output) throws Exception {
        assertPlatformFailure(request("/api/config").GET());
        assertPlatformFailure(request("/api/config").header(CREDENTIAL_NAME, "").GET());
        assertPlatformFailure(request("/api/config").header(CREDENTIAL_NAME, "wrong-secret").GET());
        assertPlatformFailure(request("/api/config")
                .header(CREDENTIAL_NAME, SECRET)
                .header(CREDENTIAL_NAME, SECRET)
                .GET());

        String credentialQuery = "?" + CREDENTIAL_NAME + "=" + SECRET;
        assertPlatformFailure(request("/api/config" + credentialQuery).header(CREDENTIAL_NAME, "").GET());
        assertPlatformFailure(request("/api/config" + credentialQuery)
                .header(CREDENTIAL_NAME, "wrong-secret")
                .GET());
        assertPlatformFailure(request("/api/config" + credentialQuery)
                .header(CREDENTIAL_NAME, SECRET)
                .header(CREDENTIAL_NAME, SECRET)
                .GET());

        HttpResponse<String> queryConfig = send(request("/api/config" + credentialQuery).GET());
        assertEquals(200, queryConfig.statusCode());
        HttpResponse<String> config = send(request("/api/config?X-Platform-Credential=wrong-secret")
                .header(CREDENTIAL_NAME, SECRET)
                .GET());
        assertEquals(200, config.statusCode());
        assertFalse(output.getAll().contains(SECRET));
        assertFalse(output.getAll().contains("Generated platform credential"));

        HttpResponse<String> clientConfig = send(request("/api/auth/client-config")
                .header(CREDENTIAL_NAME, SECRET)
                .header(HttpHeaders.AUTHORIZATION, "Basic platform-must-not-echo")
                .GET());
        assertEquals(200, clientConfig.statusCode());
        assertEquals("no-store", clientConfig.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null));
        JsonNode clientConfigJson = objectMapper.readTree(clientConfig.body());
        assertEquals("PLATFORM", clientConfigJson.path("mode").asText());
        assertFalse(clientConfigJson.has("authorization"));
        assertFalse(clientConfig.body().contains(SECRET));

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.header(CREDENTIAL_NAME, SECRET))
                .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            assertTrue(tools.tools().stream().anyMatch(tool -> "generate_probe_shell".equals(tool.name())));

            JsonNode plan = callProbePlan(client);
            assertFalse(plan.toString().contains(SECRET));
            String endpoint = plan.path("endpoint").asText();
            assertTrue(endpoint.endsWith("/api/probe/generate"), endpoint);
            assertFalse(endpoint.contains("?"), endpoint);

            assertPlatformFailure(HttpRequest.newBuilder(URI.create(endpoint))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(plan.path("body").toString())));

            HttpResponse<String> generated = send(HttpRequest.newBuilder(URI.create(endpoint))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(CREDENTIAL_NAME, SECRET)
                    .POST(HttpRequest.BodyPublishers.ofString(plan.path("body").toString())));
            assertEquals(200, generated.statusCode());
            assertFalse(generated.body().isBlank());
        }
    }

    private JsonNode callProbePlan(McpSyncClient client) throws Exception {
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
