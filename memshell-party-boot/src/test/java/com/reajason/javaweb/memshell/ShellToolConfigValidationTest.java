package com.reajason.javaweb.memshell;

import com.reajason.javaweb.GenerationException;
import com.reajason.javaweb.InvalidInputException;
import com.reajason.javaweb.Server;
import com.reajason.javaweb.ShellGenerator;
import com.reajason.javaweb.memshell.config.ConfigField;
import com.reajason.javaweb.memshell.config.ShellConfig;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Config validation and reflective generator-construction failure contracts. */
class ShellToolConfigValidationTest {

    private static final String TEST_TOOL = "ValidatingConfigFixture";

    @BeforeEach
    void registerFixture() {
        MemShellRegistry registry = MemShellRegistry.getInstance();
        registry.reset();
        registry.init();
        registry.registerTool(TEST_TOOL, new ShellToolProvider() {
            @Override
            public ToolMapping getToolMapping() {
                return ToolMapping.builder().addShellClass(ShellType.SERVLET, "example.ValidatingShell").build();
            }

            @Override
            public Class<? extends ShellToolConfig> getConfigClass() {
                return ValidatingConfig.class;
            }

            @Override
            public Class<? extends ShellGenerator> getGeneratorClass() {
                return ThrowingGenerator.class;
            }
        });
    }

    @AfterEach
    void resetRegistry() {
        MemShellRegistry.getInstance().reset();
    }

    @Test
    void validationRunsAfterParamsAreBound() {
        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> generate(Collections.singletonMap("profile", "  ")));
        assertEquals("profile is required", exception.getMessage());
    }

    @Test
    void validationRunsForDefaultAndDirectConfigs() {
        InvalidInputException fromDefaults = assertThrows(InvalidInputException.class,
                () -> generate(Collections.emptyMap()));
        assertEquals("profile is required", fromDefaults.getMessage());

        InvalidInputException fromDirectConfig = assertThrows(InvalidInputException.class,
                () -> new MemShellGenerator().generate(shellConfig(), null, ValidatingConfig.builder().build()));
        assertEquals("profile is required", fromDirectConfig.getMessage());
    }

    @Test
    void constructorInvalidInputIsUnwrapped() {
        InvalidInputException exception = assertThrows(InvalidInputException.class,
                () -> generate(Collections.singletonMap("profile", "present")));
        assertEquals("constructor invalid", exception.getMessage());
    }

    @Test
    void constructorFailureKeepsItsCauseAndMessage() {
        GenerationException exception = assertThrows(GenerationException.class,
                () -> generate(Collections.singletonMap("profile", "explode")));
        assertTrue(!(exception instanceof InvalidInputException));
        assertEquals("shell generate failed constructor exploded", exception.getMessage());
        assertSame(IllegalStateException.class, exception.getCause().getClass());
    }

    private MemShellResult generate(Map<String, ?> params) {
        return new MemShellGenerator().generate(shellConfig(), null, params);
    }

    private ShellConfig shellConfig() {
        return ShellConfig.builder()
                .server(Server.Tomcat)
                .shellTool(TEST_TOOL)
                .shellType(ShellType.SERVLET)
                .targetJreVersion(Opcodes.V1_8)
                .build();
    }

    @Getter
    @SuperBuilder
    @NoArgsConstructor
    public static class ValidatingConfig extends ShellToolConfig {
        @ConfigField
        private String profile;

        @Override
        public void validate() {
            if (profile == null || profile.trim().isEmpty()) {
                throw new InvalidInputException("profile is required");
            }
        }
    }

    public static class ThrowingGenerator implements ShellGenerator {
        public ThrowingGenerator(ShellConfig shellConfig, ValidatingConfig config) {
            if ("explode".equals(config.getProfile())) {
                throw new IllegalStateException("constructor exploded");
            }
            throw new InvalidInputException("constructor invalid");
        }

        @Override
        public byte[] getBytes() {
            return new byte[0];
        }
    }
}
