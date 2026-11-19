package com.reajason.javaweb.boot.config;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "memshell-party.auth.mode=PLATFORM")
class PlatformSignUnreachableIntegrationTest {

    private static final String UNREACHABLE_URL = "http://127.0.0.1:1/check";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("memshell-party.auth.platform.rest.auth-param", () -> "x_token");
        registry.add("memshell-party.auth.platform.rest.sign-url", () -> UNREACHABLE_URL);
        registry.add("memshell-party.auth.platform.rest.sign-param", () -> "x_token");
        registry.add("memshell-party.auth.platform.rest.sign-success-res", () -> "\"code\":1");

        registry.add("memshell-party.auth.platform.mcp.auth-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-url", () -> UNREACHABLE_URL);
        registry.add("memshell-party.auth.platform.mcp.sign-param", () -> "mcp_token");
        registry.add("memshell-party.auth.platform.mcp.sign-success-res", () -> "\"code\":1");
    }

    @Test
    void unreachableVerifierFailsClosedWithoutAServerError() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/config?x_token=any-token"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode());
        assertTrue(response.statusCode() < 500);
        assertTrue(response.headers().firstValue(HttpHeaders.WWW_AUTHENTICATE).isEmpty());
        assertEquals("no-store", response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null));
        assertEquals("", response.body());
    }
}
