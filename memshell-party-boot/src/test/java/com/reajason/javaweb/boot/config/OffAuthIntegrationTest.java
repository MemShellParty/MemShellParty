package com.reajason.javaweb.boot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OffAuthIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };

    @LocalServerPort
    private int port;

    @Test
    void offReturnsNoAuthorizationEvenWhenRequestHasBasicHeader() {
        ResponseEntity<Map<String, Object>> response = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .get()
                .uri("/api/auth/client-config")
                .header(HttpHeaders.AUTHORIZATION, "Basic off-must-not-echo")
                .retrieve()
                .toEntity(MAP_TYPE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertNotNull(response.getBody());
        assertEquals("OFF", response.getBody().get("mode"));
        assertEquals(Set.of("mode"), response.getBody().keySet());
    }
}
