package com.reajason.javaweb.memshell;

import com.reajason.javaweb.GenerationException;
import com.reajason.javaweb.InvalidInputException;
import com.reajason.javaweb.ShellGenerator;
import com.reajason.javaweb.memshell.config.ConfigField;
import com.reajason.javaweb.memshell.config.RemoteOptions;
import com.reajason.javaweb.memshell.config.ShellToolConfig;
import com.reajason.javaweb.memshell.server.ToolMapping;
import com.reajason.javaweb.memshell.spi.ShellToolProvider;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Remote-select catalog and structured object binding contract.
 */
public class RemoteOptionsTest {

    private static final String TEST_TOOL = "RemoteOptionsFixture";

    @BeforeEach
    void registerFixture() {
        MemShellRegistry registry = MemShellRegistry.getInstance();
        registry.reset();
        registry.init();
        registry.registerTool(TEST_TOOL, providerFor(RemoteConfig.class));
    }

    private static ShellToolProvider providerFor(Class<? extends ShellToolConfig> configClass) {
        return new ShellToolProvider() {
            @Override
            public ToolMapping getToolMapping() {
                return ToolMapping.builder().build();
            }

            @Override
            public Class<? extends ShellToolConfig> getConfigClass() {
                return configClass;
            }

            @Override
            public Class<? extends ShellGenerator> getGeneratorClass() {
                return null;
            }
        };
    }

    @AfterEach
    void resetRegistry() {
        MemShellRegistry.getInstance().reset();
    }

    @Test
    void catalogExposesObjectTypesAndRemoteSelectMetadata() {
        Map<String, ToolConfigFactory.ConfigParam> params = paramsOf(TEST_TOOL);

        ToolConfigFactory.ConfigParam remoteId = params.get("remoteId");
        assertEquals("string", remoteId.getType());
        ToolConfigFactory.RemoteSelectUi idUi = remoteId.getUi();
        assertEquals("remote-select", idUi.getKind());
        assertEquals("q", idUi.getUrlQueryParam());
        assertEquals("data.items", idUi.getItemsPath());
        assertEquals("displayName", idUi.getLabelPath());
        assertEquals("id", idUi.getValuePath());
        assertTrue(idUi.isRequired());

        ToolConfigFactory.ConfigParam remoteObject = params.get("remoteObject");
        assertEquals("object", remoteObject.getType());
        ToolConfigFactory.RemoteSelectUi objectUi = remoteObject.getUi();
        assertEquals("remote-select", objectUi.getKind());
        assertEquals("search", objectUi.getUrlQueryParam());
        assertEquals("", objectUi.getItemsPath());
        assertEquals("name", objectUi.getLabelPath());
        assertEquals("", objectUi.getValuePath());
        assertFalse(objectUi.isRequired());

        ToolConfigFactory.ConfigParam plainObject = params.get("plainObject");
        assertEquals("object", plainObject.getType());
        assertNull(plainObject.getUi());
    }

    @Test
    void wholeObjectIsCopiedAndStringIdIsTrimmed() {
        Map<String, Object> selected = new LinkedHashMap<>();
        selected.put("id", "node-7");
        selected.put("name", "Node 7");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("remoteId", "  node-7  ");
        params.put("remoteObject", selected);
        params.put("plainObject", null);

        RemoteConfig config = (RemoteConfig) ToolConfigFactory.fromParams(TEST_TOOL, params);

        assertEquals("node-7", config.getRemoteId());
        assertNull(config.getPlainObject(), "null is absent and leaves the builder default");
        assertTrue(config.getRemoteObject() instanceof LinkedHashMap);
        assertNotSame(selected, config.getRemoteObject());
        assertEquals(selected, config.getRemoteObject());
        selected.put("later", "mutation");
        assertFalse(config.getRemoteObject().containsKey("later"),
                "the config must not retain the caller's mutable map");
    }

    @Test
    void mapFieldsRejectScalarArrayAndStringValues() {
        List<Object> invalidValues = Arrays.<Object>asList(
                "scalar", "  ", 42, Collections.singletonList("node-7"), new String[]{"node-7"});
        for (Object invalidValue : invalidValues) {
            InvalidInputException exception = assertThrows(InvalidInputException.class, () ->
                    ToolConfigFactory.fromParams(TEST_TOOL,
                            Collections.singletonMap("remoteObject", invalidValue)));
            assertTrue(exception.getMessage().contains("remoteObject"), exception.getMessage());
            assertTrue(exception.getMessage().contains("object"), exception.getMessage());
        }
    }

    @Test
    void stringRemoteFieldRequiresValuePath() {
        MemShellRegistry.getInstance().registerTool("InvalidRemoteOptionsFixture",
                providerFor(InvalidRemoteConfig.class));

        GenerationException exception = assertThrows(GenerationException.class,
                () -> ToolConfigFactory.describeParams("InvalidRemoteOptionsFixture"));
        assertTrue(exception.getMessage().contains("valuePath"), exception.getMessage());
    }

    private static Map<String, ToolConfigFactory.ConfigParam> paramsOf(String tool) {
        Map<String, ToolConfigFactory.ConfigParam> params = new LinkedHashMap<>();
        for (ToolConfigFactory.ConfigParam param : ToolConfigFactory.describeParams(tool)) {
            params.put(param.getName(), param);
        }
        return params;
    }

    @Getter
    @SuperBuilder
    @NoArgsConstructor
    public static class RemoteConfig extends ShellToolConfig {
        @ConfigField
        @RemoteOptions(urlQueryParam = "q", itemsPath = "data.items", labelPath = "displayName",
                valuePath = "id", required = true)
        private String remoteId;

        @ConfigField
        @RemoteOptions(urlQueryParam = "search")
        private Map<String, Object> remoteObject;

        @ConfigField
        private Map<String, Object> plainObject;
    }

    @Getter
    @SuperBuilder
    @NoArgsConstructor
    public static class InvalidRemoteConfig extends ShellToolConfig {
        @ConfigField
        @RemoteOptions(urlQueryParam = "q")
        private String missingValuePath;
    }
}
