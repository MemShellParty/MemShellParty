package com.reajason.javaweb.memshell;

import com.reajason.javaweb.ShellGenerator;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.AbstractServer;
import com.reajason.javaweb.memshell.server.InjectorMapping;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 服务端可以给已注册的工具补充 shell 模板，覆盖同名类型；注册顺序不应影响结果。
 *
 * @author ReaJason
 */
class MemShellRegistryTest {

    private static final String TOOL = "Tool";
    private static final String INJECTOR_TEMPLATE = MemShellRegistryTest.class.getName();

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        MemShellRegistry.getInstance().reset();
    }

    @Test
    void serverContributesShellTypeForExistingTool() {
        registerTool(Collections.singletonMap("Filter", "builtin.FilterShell"));
        registerServer(new TestServer(Collections.singletonMap("AkkaHttp", "plugin.GodzillaAkkaHttp"),
                "Filter", "AkkaHttp"));

        AbstractServer server = MemShellRegistry.getInstance().getServer("PluginServer");

        assertEquals(new LinkedHashSet<>(Arrays.asList("Filter", "AkkaHttp")), server.getSupportedShellTypes(TOOL));
        assertEquals("plugin.GodzillaAkkaHttp", server.getShellInjectorPair(TOOL, "AkkaHttp").getLeft());
        assertEquals(INJECTOR_TEMPLATE, server.getShellInjectorPair(TOOL, "AkkaHttp").getRight());
        assertEquals("builtin.FilterShell", server.getShellInjectorPair(TOOL, "Filter").getLeft());
    }

    @Test
    void serverOverrideWinsForSameShellType() {
        registerTool(Collections.singletonMap("Filter", "builtin.FilterShell"));
        registerServer(new TestServer(Collections.singletonMap("Filter", "plugin.CustomFilterShell"),
                "Filter"));

        AbstractServer server = MemShellRegistry.getInstance().getServer("PluginServer");

        assertEquals("plugin.CustomFilterShell", server.getShellInjectorPair(TOOL, "Filter").getLeft());
    }

    @Test
    void overrideWithoutInjectorIsNotAdvertised() {
        registerTool(Collections.singletonMap("Filter", "builtin.FilterShell"));
        registerServer(new TestServer(Collections.singletonMap("AkkaHttp", "plugin.GodzillaAkkaHttp"),
                "Filter"));

        AbstractServer server = MemShellRegistry.getInstance().getServer("PluginServer");

        assertEquals(Collections.singleton("Filter"), server.getSupportedShellTypes(TOOL));
    }

    @Test
    void mergesWhenToolRegistersAfterServer() {
        registerServer(new TestServer(Collections.singletonMap("AkkaHttp", "plugin.GodzillaAkkaHttp"),
                "Filter", "AkkaHttp"));
        registerTool(Collections.singletonMap("Filter", "builtin.FilterShell"));

        AbstractServer server = MemShellRegistry.getInstance().getServer("PluginServer");

        assertEquals(new LinkedHashSet<>(Arrays.asList("Filter", "AkkaHttp")), server.getSupportedShellTypes(TOOL));
    }

    private static void registerServer(TestServer server) {
        MemShellRegistry.getInstance().registerServer("PluginServer", () -> server);
    }

    private static void registerTool(Map<String, String> shellClassMap) {
        MemShellRegistry.getInstance().registerTool(TOOL, new ShellToolProvider() {
            @Override
            public ToolMapping getToolMapping() {
                return new ToolMapping(shellClassMap);
            }

            @Override
            public Class<? extends ShellToolConfig> getConfigClass() {
                return ShellToolConfig.class;
            }

            @Override
            public Class<? extends ShellGenerator> getGeneratorClass() {
                return null;
            }
        });
    }

    private static final class TestServer extends AbstractServer {
        private final Map<String, String> shellOverrides;
        private final Set<String> injectorTypes;

        private TestServer(Map<String, String> shellOverrides, String... injectorTypes) {
            this.shellOverrides = shellOverrides;
            this.injectorTypes = new LinkedHashSet<>(Arrays.asList(injectorTypes));
        }

        @Override
        public InjectorMapping getShellInjectorMapping() {
            InjectorMapping.InjectorMappingBuilder builder = InjectorMapping.builder();
            for (String shellType : injectorTypes) {
                builder.addInjector(shellType, INJECTOR_TEMPLATE);
            }
            return builder.build();
        }

        @Override
        public Map<String, ToolMapping> getToolMappingOverrides() {
            Map<String, ToolMapping> overrides = new LinkedHashMap<>();
            overrides.put(TOOL, new ToolMapping(shellOverrides));
            return overrides;
        }
    }
}
