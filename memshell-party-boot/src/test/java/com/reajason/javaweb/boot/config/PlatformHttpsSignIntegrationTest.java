package com.reajason.javaweb.boot.config;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "memshell-party.auth.mode=PLATFORM")
class PlatformHttpsSignIntegrationTest {

    private static final String AUTH_PARAM = "x_token";
    private static final String SUCCESS_MARKER = "\"code\":1";
    private static final PlatformSignServer SIGN_SERVER = new PlatformSignServer(httpsServer(), "https");

    private final HttpClient httpClient = HttpClient.newHttpClient();

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
    void selfSignedHttpsSignUrlAcceptsAValidCredential() throws Exception {
        assertEquals(200, send(request("/api/config?" + AUTH_PARAM + "="
                + PlatformSignServer.VALID_TOKEN)).statusCode());
        assertEquals(200, send(request("/api/config")
                .header(AUTH_PARAM, PlatformSignServer.VALID_TOKEN)).statusCode());
    }

    @Test
    void selfSignedHttpsSignUrlStillRejectsInvalidCredentials() throws Exception {
        HttpResponse<String> response = send(request("/api/config?" + AUTH_PARAM + "=unknown-token"));
        assertEquals(403, response.statusCode());
        assertEquals("", response.body());
        assertEquals("no-store", response.headers().firstValue(HttpHeaders.CACHE_CONTROL).orElse(null));
    }

    @Test
    void theStubCertificateIsNotTrustedByDefault() {
        RestTemplate strict = new RestTemplate();
        assertThrows(ResourceAccessException.class,
                () -> strict.getForEntity(SIGN_SERVER.url("/rest-check"), String.class));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpsServer httpsServer() {
        try {
            Path directory = Files.createTempDirectory("platform-sign-https-");
            Path keystoreFile = directory.resolve("sign.p12");
            Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
            Process process = new ProcessBuilder(
                    keytool.toString(),
                    "-genkeypair",
                    "-alias", "sign",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-validity", "2",
                    "-dname", "CN=platform-sign-test",
                    "-ext", "SAN=dns:localhost",
                    "-storetype", "PKCS12",
                    "-keystore", keystoreFile.toString(),
                    "-storepass", "changeit",
                    "-keypass", "changeit",
                    "-noprompt")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("keytool failed: " + output);
            }

            KeyStore keystore = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(keystoreFile)) {
                keystore.load(input, "changeit".toCharArray());
            }
            Files.deleteIfExists(keystoreFile);
            Files.deleteIfExists(directory);

            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keystore, "changeit".toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers.getKeyManagers(), null, null);

            HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(context));
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot start HTTPS sign server", exception);
        }
    }
}
