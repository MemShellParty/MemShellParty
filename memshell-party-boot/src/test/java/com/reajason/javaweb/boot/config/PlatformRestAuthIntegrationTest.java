package com.reajason.javaweb.boot.config;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "memshell-party.auth.mode=PLATFORM")
class PlatformRestAuthIntegrationTest {

    private static final String AUTH_PARAM = "x_token";
    private static final String SUCCESS_MARKER = "\"code\":1";
    private static final PlatformSignServer SIGN_SERVER = new PlatformSignServer();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("memshell-party.auth.platform.rest.auth-param", () -> AUTH_PARAM);
        registry.add("memshell-party.auth.platform.rest.sign-url", () -> SIGN_SERVER.url("/rest-check"));
        registry.add("memshell-party.auth.platform.rest.sign-param", () -> AUTH_PARAM);
        registry.add("memshell-party.auth.platform.rest.sign-success-res", () -> SUCCESS_MARKER);

        registry.add("memshell-party.auth.platform.mcp.auth-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-url", () -> SIGN_SERVER.url("/mcp-check"));
        registry.add("memshell-party.auth.platform.mcp.sign-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-success-res", () -> SUCCESS_MARKER);
    }

    @AfterAll
    static void stopSignServer() {
        SIGN_SERVER.close();
    }

    @Test
    void missingBlankDuplicateAndMalformedCredentialsNeverReachThePlatform() throws Exception {
        int callsBefore = SIGN_SERVER.calls("/rest-check");

        assertPlatformFailure(request("/api/config").GET());
        assertPlatformFailure(request("/api/config?" + AUTH_PARAM + "=").GET());
        assertPlatformFailure(request("/api/config?" + AUTH_PARAM + "=%20%20").GET());
        assertPlatformFailure(request("/api/config?" + AUTH_PARAM + "=" + PlatformSignServer.VALID_TOKEN
                + "&" + AUTH_PARAM + "=" + PlatformSignServer.VALID_TOKEN).GET());
        assertPlatformFailure(request("/api/config?" + AUTH_PARAM + "=%FF").GET());
        assertPlatformFailure(request("/api/config")
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(AUTH_PARAM + "=" + PlatformSignServer.VALID_TOKEN)));
        assertEquals(callsBefore, SIGN_SERVER.calls("/rest-check"));

        assertPlatformFailure(request("/api/config?" + AUTH_PARAM + "=unknown-token").GET());
        assertEquals(callsBefore + 1, SIGN_SERVER.calls("/rest-check"));
    }

    @Test
    void validQueryAndHeaderCredentialsAreDelegated() throws Exception {
        assertEquals(200, send(request("/api/config?" + AUTH_PARAM + "=" + PlatformSignServer.VALID_TOKEN)).statusCode());
        assertEquals(200, send(request("/api/config")
                .header(AUTH_PARAM, PlatformSignServer.VALID_TOKEN)).statusCode());
    }

    @Test
    void headerTakesPrecedenceOverTheQuery() throws Exception {
        assertEquals(200, send(request("/api/config?" + AUTH_PARAM + "=wrong-token")
                .header(AUTH_PARAM, PlatformSignServer.VALID_TOKEN)).statusCode());
        assertPlatformFailure(request("/api/config?" + AUTH_PARAM + "=" + PlatformSignServer.VALID_TOKEN)
                .header(AUTH_PARAM, "wrong-token"));
    }

    @Test
    void staticAssetsStayAnonymous() throws Exception {
        assertEquals(200, send(request("/favicon.ico")).statusCode());
        assertNotEquals(403, send(request("/assets/not-found.js")).statusCode());
    }

    @Test
    void rootRedirectKeepsTheQueryCredential() throws Exception {
        HttpResponse<String> root = send(request("/?" + AUTH_PARAM + "=" + PlatformSignServer.VALID_TOKEN));
        assertEquals(302, root.statusCode());
        assertEquals("http://localhost:" + port + "/ui?" + AUTH_PARAM + "=" + PlatformSignServer.VALID_TOKEN,
                root.headers().firstValue(HttpHeaders.LOCATION).orElse(null));
    }

    @Test
    void clientConfigReportsPlatformModeWithoutEchoingCredentials() throws Exception {
        HttpResponse<String> response = send(request("/api/auth/client-config?" + AUTH_PARAM + "="
                        + PlatformSignServer.VALID_TOKEN)
                .header(HttpHeaders.AUTHORIZATION, "Basic must-not-echo"));

        assertEquals(200, response.statusCode());
        assertEquals("no-store", response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null));
        JsonNode json = objectMapper.readTree(response.body());
        assertEquals("PLATFORM", json.path("mode").asText());
        assertFalse(json.has("authorization"));
        assertFalse(response.body().contains(PlatformSignServer.VALID_TOKEN));
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
