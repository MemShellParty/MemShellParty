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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "memshell-party.auth.mode=PLATFORM",
                "memshell-party.auth.platform.name=access_token"
        })
@ExtendWith(OutputCaptureExtension.class)
class PlatformQueryAuthIntegrationTest {

    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "Generated platform credential for 'access_token': ([A-Za-z0-9_-]{43})");
    private static final String CREDENTIAL_NAME = "access_token";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void generatedCredentialSupportsHeaderAndQueryWithLegacyLocationIgnored(CapturedOutput output) throws Exception {
        Matcher matcher = CREDENTIAL_PATTERN.matcher(output.getAll());
        assertTrue(matcher.find(), "startup output did not contain the generated platform credential");
        String secret = matcher.group(1);
        assertFalse(matcher.find(), "the generated platform credential must be logged only once");

        assertPlatformFailure(request("/api/config").GET());
        assertPlatformFailure(request("/api/config?access_token=").GET());
        assertPlatformFailure(request("/api/config?access_token=wrong-secret").GET());
        assertPlatformFailure(request("/api/config?access_token=" + secret + "&access_token=" + secret).GET());
        assertPlatformFailure(request("/api/config?access_token=%FF").GET());
        assertEquals(200, send(request("/favicon.ico").GET()).statusCode());
        assertNotEquals(403, send(request("/assets/not-found.js").GET()).statusCode());
        HttpResponse<String> headerConfig = send(request("/api/config").header(CREDENTIAL_NAME, secret).GET());
        assertEquals(200, headerConfig.statusCode());
        assertPlatformFailure(request("/api/config")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(CREDENTIAL_NAME + "=" + secret)));

        HttpResponse<String> config = send(request("/api/config?access_token=" + secret).GET());
        assertEquals(200, config.statusCode());
        HttpResponse<String> root = send(request("/?access_token=" + secret).GET());
        assertEquals(302, root.statusCode());
        assertEquals("http://localhost:" + port + "/ui?access_token=" + secret,
                root.headers().firstValue(HttpHeaders.LOCATION).orElse(null));

        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp?" + CREDENTIAL_NAME + "=" + secret)
                .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            assertTrue(tools.tools().stream().anyMatch(tool -> "generate_probe_shell".equals(tool.name())));

            JsonNode plan = callProbePlan(client);
            assertFalse(plan.toString().contains(secret));
            String endpoint = plan.path("endpoint").asText();
            assertTrue(endpoint.endsWith("/api/probe/generate"), endpoint);
            assertFalse(endpoint.contains("?"), endpoint);

            assertPlatformFailure(HttpRequest.newBuilder(URI.create(endpoint))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(plan.path("body").toString())));

            HttpResponse<String> generated = send(HttpRequest.newBuilder(
                            URI.create(endpoint + "?" + CREDENTIAL_NAME + "=" + secret))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
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
