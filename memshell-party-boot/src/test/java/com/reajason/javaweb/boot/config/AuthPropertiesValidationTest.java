package com.reajason.javaweb.boot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPropertiesValidationTest {

    private static final String SUPPLIED_SECRET = "validation-secret-must-not-leak";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WebConfig.class);

    @Test
    void platformRequiresNameAndRejectsBlankSecret() {
        assertStartupFailure("memshell-party.auth.platform.secret",
                "memshell-party.auth.mode=PLATFORM",
                "memshell-party.auth.platform.secret=   ",
                "memshell-party.auth.platform.name=X-Credential");
        assertStartupFailure("memshell-party.auth.platform.name",
                "memshell-party.auth.mode=PLATFORM",
                "memshell-party.auth.platform.secret=" + SUPPLIED_SECRET);
        assertStartupFailure("memshell-party.auth.platform.name",
                "memshell-party.auth.mode=PLATFORM",
                "memshell-party.auth.platform.secret=" + SUPPLIED_SECRET,
                "memshell-party.auth.platform.name=   ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad name", "bad name", "bad&name", "bad=name", "bad#name", "bad\nname"})
    void platformRejectsInvalidCredentialNames(String name) {
        assertStartupFailure("memshell-party.auth.platform.name",
                "memshell-party.auth.mode=PLATFORM",
                "memshell-party.auth.platform.secret=" + SUPPLIED_SECRET,
                "memshell-party.auth.platform.name=" + name);
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
            assertFalse(failureText.contains(SUPPLIED_SECRET), "startup failure exposed the platform secret");
        });
    }
}
