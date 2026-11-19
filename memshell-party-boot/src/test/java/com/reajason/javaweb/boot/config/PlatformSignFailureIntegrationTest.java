package com.reajason.javaweb.boot.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "memshell-party.auth.mode=PLATFORM")
class PlatformSignFailureIntegrationTest {

    private static final PlatformSignServer SIGN_SERVER = new PlatformSignServer();

    private final HttpClient httpClient = HttpClient.newHttpClient();

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
    void responsesWithoutTheMarkerAreRejected() throws Exception {
        assertPlatformFailure(request("/api/config?x_token=" + PlatformSignServer.NO_MARKER_TOKEN));
    }

    @Test
    void serverErrorsAreRejectedNotPropagated() throws Exception {
        assertPlatformFailure(request("/api/config?x_token=" + PlatformSignServer.SERVER_ERROR_TOKEN));
    }

    @Test
    void tokensThePlatformRejectsAreRejected() throws Exception {
        assertPlatformFailure(request("/api/config?x_token=platform-rejected-token"));
    }

    @Test
    void staticAssetsStayAnonymousWhenVerificationFails() throws Exception {
        assertEquals(200, send(request("/favicon.ico")).statusCode());
        assertNotEquals(403, send(request("/assets/not-found.js")).statusCode());
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
