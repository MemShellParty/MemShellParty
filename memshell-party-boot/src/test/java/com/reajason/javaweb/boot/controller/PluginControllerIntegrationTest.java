package com.reajason.javaweb.boot.controller;

import com.reajason.javaweb.memshell.PluginBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /api/plugins} 的序列化契约：字段名与只读插件目录保持一致。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PluginControllerIntegrationTest {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> PLUGIN_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    @LocalServerPort
    private int port;

    @TempDir
    Path tempDir;

    private RestClient restClient;

    @BeforeEach
    void setUp() throws Exception {
        Path jar = tempDir.resolve("demo-plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("META-INF/memshell-party-plugin.properties"));
            output.write(("name=Demo Plugin\n"
                    + "version=1.2.3\n"
                    + "author=Alice\n"
                    + "description=Demo description\n"
                    + "homepage=https://example.com\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        System.setProperty(PluginBootstrap.DIRECTORY_PROPERTY, tempDir.toString());
        PluginBootstrap.load();
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterEach
    void clearPluginDirectory() {
        System.clearProperty(PluginBootstrap.DIRECTORY_PROPERTY);
    }

    @Test
    void returnsPluginMetadata() {
        ResponseEntity<List<Map<String, Object>>> response = restClient.get()
                .uri("/api/plugins")
                .retrieve()
                .toEntity(PLUGIN_LIST_TYPE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        Map<String, Object> plugin = response.getBody().get(0);
        assertEquals("demo-plugin.jar", plugin.get("jarFileName"));
        assertEquals("Demo Plugin", plugin.get("name"));
        assertEquals("1.2.3", plugin.get("version"));
        assertEquals("Alice", plugin.get("author"));
        assertEquals("Demo description", plugin.get("description"));
        assertEquals("https://example.com", plugin.get("homepage"));
        assertEquals(true, plugin.get("loaded"));
        assertTrue(((List<?>) plugin.get("spiServices")).isEmpty());
    }
}
