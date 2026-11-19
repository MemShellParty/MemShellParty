package com.reajason.javaweb.boot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPropertiesValidationTest {

    private static final String PREFIX = "memshell-party.auth.platform.";
    private static final String REST = PREFIX + "rest.";
    private static final String MCP = PREFIX + "mcp.";
    private static final String MODE_PLATFORM = "memshell-party.auth.mode=PLATFORM";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WebConfig.class);

    @Test
    void platformRequiresBothRestAndMcpSections() {
        assertStartupFailure(PREFIX + "rest", platform(new String[0], mcp()));
        assertStartupFailure(PREFIX + "mcp", platform(rest(), new String[0]));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad name", "bad&name", "bad=name", "bad#name", "bad\nname"})
    void platformRejectsInvalidAuthParams(String name) {
        assertStartupFailure(REST + "auth-param",
                platform(rest(REST + "auth-param=" + name), mcp()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "/relative/check", "ftp://127.0.0.1/check", "http://",
            "http://127.0.0.1/check#fragment"})
    void platformRejectsInvalidSignUrls(String url) {
        assertStartupFailure(REST + "sign-url",
                platform(rest(REST + "sign-url=" + url), mcp()));
    }

    @Test
    void platformRequiresExactlyOneOutboundCredentialLocation() {
        assertStartupFailure(REST + "sign-param",
                platform(rest(REST + "sign-header=X-Platform-Token"), mcp()));
        assertStartupFailure(REST + "sign-param",
                platform(rest(REST + "sign-param="), mcp()));
    }

    @Test
    void platformRejectsInvalidSignParamNames() {
        assertStartupFailure(REST + "sign-param",
                platform(rest(REST + "sign-param=bad name"), mcp()));
    }

    @Test
    void platformRejectsInvalidSignHeaderNames() {
        assertStartupFailure(MCP + "sign-header",
                platform(rest(), mcp(MCP + "sign-header=bad name")));
    }

    @Test
    void platformRequiresSuccessMarker() {
        assertStartupFailure(REST + "sign-success-res",
                platform(rest(REST + "sign-success-res="), mcp()));
        assertStartupFailure(MCP + "sign-success-res",
                platform(rest(), mcp(MCP + "sign-success-res=   ")));
    }

    @Test
    void unknownModesFailBinding() {
        assertStartupFailure("memshell-party.auth.mode", "memshell-party.auth.mode=");
        assertStartupFailure("memshell-party.auth.mode", "memshell-party.auth.mode=UNKNOWN");
    }

    @Test
    void offAndAdminDoNotRequirePlatformConfiguration() {
        contextRunner.run(context -> assertTrue(context.isRunning()));
        contextRunner.withPropertyValues("memshell-party.auth.mode=ADMIN")
                .run(context -> assertTrue(context.isRunning()));
    }

    private void assertStartupFailure(String expectedProperty, String... properties) {
        contextRunner.withPropertyValues(properties).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure, "expected context startup to fail");
            StringWriter stackTrace = new StringWriter();
            failure.printStackTrace(new PrintWriter(stackTrace));
            String failureText = stackTrace.toString();
            assertTrue(failureText.contains(expectedProperty), () -> failureText);
        });
    }

    private static String[] platform(String[] restProperties, String[] mcpProperties) {
        List<String> properties = new ArrayList<>();
        properties.add(MODE_PLATFORM);
        properties.addAll(Arrays.asList(restProperties));
        properties.addAll(Arrays.asList(mcpProperties));
        return properties.toArray(String[]::new);
    }

    private static String[] rest(String... overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(REST + "auth-param", "x_token");
        values.put(REST + "sign-url", "http://127.0.0.1:1/check");
        values.put(REST + "sign-param", "x_token");
        values.put(REST + "sign-success-res", "\"code\":1");
        return withOverrides(values, overrides);
    }

    private static String[] mcp(String... overrides) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(MCP + "auth-param", "mcp_token");
        values.put(MCP + "sign-url", "http://127.0.0.1:1/check");
        values.put(MCP + "sign-header", "X-Platform-Token");
        values.put(MCP + "sign-success-res", "\"code\":1");
        return withOverrides(values, overrides);
    }

    private static String[] withOverrides(Map<String, String> values, String... overrides) {
        for (String override : overrides) {
            int equals = override.indexOf('=');
            values.put(override.substring(0, equals), override.substring(equals + 1));
        }
        return values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }
}
