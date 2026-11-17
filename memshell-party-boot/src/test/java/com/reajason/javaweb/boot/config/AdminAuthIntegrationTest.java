package com.reajason.javaweb.boot.config;

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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "memshell-party.auth.mode=ADMIN")
@ExtendWith(OutputCaptureExtension.class)
class AdminAuthIntegrationTest {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "Generated admin password for user 'admin': ([A-Za-z0-9_-]{43})");
    private static final String CHALLENGE = "Basic realm=\"memshell-party\", charset=\"UTF-8\"";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void adminProtectsRestUiAndMcpWithTheGeneratedPassword(CapturedOutput output) throws Exception {
        Matcher matcher = PASSWORD_PATTERN.matcher(output.getAll());
        assertTrue(matcher.find(), "startup output did not contain the generated admin password");
        String password = matcher.group(1);
        assertFalse(matcher.find(), "the admin password must be logged only once");
        String authorization = basic("admin", password);

        assertAdminFailure(request("/api/auth/client-config").GET());
        assertAdminFailure(request("/api/config").GET());
        assertAdminFailure(request("/api/config")
                .header(HttpHeaders.AUTHORIZATION, "Basic not-base64")
                .GET());
        assertAdminFailure(request("/api/config")
                .header(HttpHeaders.AUTHORIZATION, basic("admin", "wrong-password"))
                .GET());
        assertAdminFailure(request("/api/config")
                .header(HttpHeaders.AUTHORIZATION, basic("other", password))
                .GET());
        assertAdminFailure(request("/api/config")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(
                        "admin-no-colon".getBytes(StandardCharsets.UTF_8)))
                .GET());
        assertAdminFailure(request("/api/config")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .GET());
        assertAdminFailure(request("/api/config").method("OPTIONS", HttpRequest.BodyPublishers.noBody()));

        HttpResponse<String> config = send(request("/api/config")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .GET());
        assertEquals(200, config.statusCode());
        assertFalse(config.body().contains(password));

        HttpResponse<String> clientConfig = send(request("/api/auth/client-config")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .GET());
        assertEquals(200, clientConfig.statusCode());
        assertEquals("no-store", clientConfig.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null));
        JsonNode clientConfigJson = objectMapper.readTree(clientConfig.body());
        assertEquals("ADMIN", clientConfigJson.path("mode").asText());
        assertEquals(authorization, clientConfigJson.path("authorization").asText());

        HttpResponse<String> ui = send(request("/ui")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .GET());
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("<html"));

        assertAdminFailure(request("/mcp")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.header(HttpHeaders.AUTHORIZATION, authorization))
                .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            assertTrue(tools.tools().stream().anyMatch(tool -> "generate_memshell".equals(tool.name())));
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertAdminFailure(HttpRequest.Builder request) throws Exception {
        HttpResponse<String> response = send(request);
        assertEquals(401, response.statusCode());
        assertEquals(CHALLENGE, response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElse(null));
        assertEquals("no-store", response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null));
        assertEquals("", response.body());
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
