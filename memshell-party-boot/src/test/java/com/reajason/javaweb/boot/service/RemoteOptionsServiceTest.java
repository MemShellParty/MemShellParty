package com.reajason.javaweb.boot.service;

import com.reajason.javaweb.GenerationException;
import com.reajason.javaweb.InvalidInputException;
import com.reajason.javaweb.ShellGenerator;
import com.reajason.javaweb.boot.config.AuthProperties;
import com.reajason.javaweb.boot.config.PlatformAuthContext;
import com.reajason.javaweb.boot.config.RemoteOptionsProperties;
import com.reajason.javaweb.boot.config.WebConfig;
import com.reajason.javaweb.boot.dto.RemoteOption;
import com.reajason.javaweb.memshell.MemShellRegistry;
import com.reajason.javaweb.memshell.config.ConfigField;
import com.reajason.javaweb.memshell.config.RemoteOptions;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteOptionsServiceTest {

    private static final String TOOL = "RemoteOptionsServiceFixture";
    private static final String FIELD = "profile";

    private HttpServer server;
    private String responseBody;
    private int responseStatus;
    private String lastQuery;
    private String lastRestToken;
    private String lastMcpToken;
    private final AtomicInteger profileCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        responseBody = """
                {"data":[
                  {"id":"profile-a","name":"Profile A","secret":"secret-a"},
                  {"id":7,"name":"Profile 7","secret":"secret-7"}
                ]}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/profiles", exchange -> {
            profileCalls.incrementAndGet();
            lastQuery = exchange.getRequestURI().getRawQuery();
            lastRestToken = exchange.getRequestHeaders().getFirst("x_token");
            lastMcpToken = query(exchange, "app_key");
            respond(exchange, responseStatus, responseBody);
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/profiles");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        MemShellRegistry registry = MemShellRegistry.getInstance();
        registry.reset();
        registry.init();
        registry.registerTool(TOOL, new ShellToolProvider() {
            @Override
            public ToolMapping getToolMapping() {
                return ToolMapping.builder().build();
            }

            @Override
            public Class<? extends ShellToolConfig> getConfigClass() {
                return RemoteProfileConfig.class;
            }

            @Override
            public Class<? extends ShellGenerator> getGeneratorClass() {
                return null;
            }
        });
    }

    @AfterEach
    void tearDown() {
        PlatformAuthContext.clear();
        MemShellRegistry.getInstance().reset();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void restUsesHeaderAndReturnsOnlyLabelAndIdentifier() {
        PlatformAuthContext.set("rest-token");

        List<RemoteOption> options = service("/profiles").listOptions(
                TOOL, FIELD, RemoteOptionsChannel.REST);

        assertEquals(List.of(new RemoteOption("Profile A", "profile-a"),
                new RemoteOption("Profile 7", "7")), options);
        assertFalse(options.toString().contains("secret-a"));
        assertEquals("rest-token", lastRestToken);
        assertNull(lastMcpToken);
    }

    @Test
    void mcpUsesEncodedQueryCredential() {
        PlatformAuthContext.set("mcp token/+?");

        service("/profiles").listOptions(TOOL, FIELD, RemoteOptionsChannel.MCP);

        assertEquals("mcp token/+?", lastMcpToken);
        assertNull(lastRestToken);
        assertTrue(lastQuery.contains("app_key=mcp%20token%2F%2B%3F"), lastQuery);
    }

    @Test
    void objectReferenceIsResolvedWithoutMutatingRequestAndInlineMapStillWorks() {
        PlatformAuthContext.set("rest-token");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(FIELD, "profile-a");

        Map<String, Object> resolved = service("/profiles").resolveReferences(
                TOOL, params, RemoteOptionsChannel.REST);

        assertEquals("profile-a", params.get(FIELD));
        assertTrue(resolved.get(FIELD) instanceof Map);
        assertEquals("secret-a", ((Map<?, ?>) resolved.get(FIELD)).get("secret"));

        Map<String, Object> inline = new LinkedHashMap<>();
        inline.put("id", "inline");
        params.put(FIELD, inline);
        resolved = service("/profiles").resolveReferences(TOOL, params, RemoteOptionsChannel.REST);
        assertSame(params, resolved, "unchanged parameters avoid a defensive copy");
        assertSame(inline, resolved.get(FIELD));
        assertEquals(1, profileCalls.get(), "inline objects do not trigger a remote request");
    }

    @Test
    void unknownReferenceIsRejected() {
        PlatformAuthContext.set("rest-token");
        RemoteOptionsService service = service("/profiles");

        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> service.resolveReferences(TOOL, Map.of(FIELD, "missing"), RemoteOptionsChannel.REST));
        assertTrue(exception.getMessage().contains("missing"), exception.getMessage());
    }

    @Test
    void duplicateIdentifiersFailClosed() {
        PlatformAuthContext.set("rest-token");
        responseBody = "{\"data\":[{\"id\":\"same\",\"name\":\"a\"},{\"id\":\"same\",\"name\":\"b\"}]}";

        GenerationException exception = assertThrows(GenerationException.class,
                () -> service("/profiles").listOptions(TOOL, FIELD, RemoteOptionsChannel.REST));
        assertTrue(exception.getMessage().contains("duplicate"), exception.getMessage());
    }

    @Test
    void missingChannelForwardingFailsBeforeAnyRemoteRequest() {
        PlatformAuthContext.set("rest-token");
        RemoteOptionsService service = service("/profiles", false, true);

        GenerationException exception = assertThrows(GenerationException.class,
                () -> service.listOptions(TOOL, FIELD, RemoteOptionsChannel.REST));
        assertTrue(exception.getMessage().contains("REST"), exception.getMessage());
        assertEquals(0, profileCalls.get());
    }

    @Test
    void malformedAndNon2xxResponsesDoNotLeakRemoteBodies() {
        PlatformAuthContext.set("rest-token");
        responseBody = "not-json-secret";
        GenerationException malformed = assertThrows(GenerationException.class,
                () -> service("/profiles").listOptions(TOOL, FIELD, RemoteOptionsChannel.REST));
        assertTrue(malformed.getMessage().contains("not valid JSON"), malformed.getMessage());
        assertFalse(malformed.getMessage().contains(responseBody), malformed.getMessage());

        responseStatus = 403;
        responseBody = "private-upstream-error";
        GenerationException rejected = assertThrows(GenerationException.class,
                () -> service("/profiles").listOptions(TOOL, FIELD, RemoteOptionsChannel.REST));
        assertTrue(rejected.getMessage().contains("403"), rejected.getMessage());
        assertFalse(rejected.getMessage().contains(responseBody), rejected.getMessage());
    }

    @Test
    void redirectsAreNotFollowed() {
        PlatformAuthContext.set("rest-token");

        GenerationException exception = assertThrows(GenerationException.class,
                () -> service("/redirect").listOptions(TOOL, FIELD, RemoteOptionsChannel.REST));
        assertTrue(exception.getMessage().contains("302"), exception.getMessage());
        assertEquals(0, profileCalls.get());
    }

    private RemoteOptionsService service(String path) {
        return service(path, true, true);
    }

    private RemoteOptionsService service(String path, boolean restEnabled, boolean mcpEnabled) {
        RemoteOptionsProperties.Source source = new RemoteOptionsProperties.Source();
        source.setTool(TOOL);
        source.setField(FIELD);
        source.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + path);
        if (restEnabled) {
            RemoteOptionsProperties.Channel rest = new RemoteOptionsProperties.Channel();
            rest.setAuthHeader("x_token");
            source.setRest(rest);
        }
        if (mcpEnabled) {
            RemoteOptionsProperties.Channel mcp = new RemoteOptionsProperties.Channel();
            mcp.setAuthParam("app_key");
            source.setMcp(mcp);
        }
        RemoteOptionsProperties properties = new RemoteOptionsProperties();
        properties.setSources(List.of(source));
        AuthProperties auth = new AuthProperties();
        auth.setMode(AuthProperties.Mode.PLATFORM);
        return new RemoteOptionsService(properties, auth, new WebConfig().remoteOptionsRestTemplate());
    }

    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if (name.equals(key)) {
                return URLDecoder.decode(equals < 0 ? "" : pair.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }

    @Getter
    @SuperBuilder
    @NoArgsConstructor
    public static class RemoteProfileConfig extends ShellToolConfig {
        @ConfigField
        @RemoteOptions(urlQueryParam = "url", itemsPath = "data", labelPath = "name",
                keyPath = "id", required = true)
        private Map<String, Object> profile;
    }
}
