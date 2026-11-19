package com.reajason.javaweb.boot.mcp;

import com.reajason.javaweb.packer.PackerSupport;
import com.reajason.javaweb.packer.Packers;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author ReaJason
 * @since 2026/9/30
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemShellMcpServerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Test
    void generateToolsReturnGeneratedShellsDirectly() {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://localhost:" + port)
                .endpoint("/mcp")
                .build();
        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();

            McpSchema.ListToolsResult tools = client.listTools();
            List<String> toolNames = tools.tools().stream().map(McpSchema.Tool::name).toList();
            assertTrue(toolNames.containsAll(List.of("memshell_capabilities", "generate_memshell", "generate_probe_shell")),
                    "unexpected tools: " + toolNames);

            // shellToolConfig 是各工具自定义（插件还能自己加字段）的参数 map，schema 里只能是开放对象：
            // 具体字段靠 memshell_capabilities 的 shellToolConfigs 发现，那句描述就是 agent 的唯一入口
            String generateMemShellSchema = String.valueOf(tools.tools().stream()
                    .filter(tool -> "generate_memshell".equals(tool.name()))
                    .findFirst()
                    .orElseThrow()
                    .inputSchema());
            assertTrue(generateMemShellSchema.contains("shellToolConfig"), generateMemShellSchema);
            assertTrue(generateMemShellSchema.contains("shellToolConfigs in memshell_capabilities"),
                    generateMemShellSchema);

            McpSchema.CallToolResult capabilities = client.callTool(
                    new McpSchema.CallToolRequest("memshell_capabilities", Map.of()));
            assertNotEquals(Boolean.TRUE, capabilities.isError());
            assertFalse(capabilities.content().isEmpty());

            JsonNode memShell = callTool(client, "generate_memshell", Map.of(
                    "shellConfig", Map.of(
                            "server", "Tomcat",
                            "shellTool", "Godzilla",
                            "shellType", "Filter",
                            "obfuscation", Map.of("enabled", true)),
                    "shellToolConfig", Map.of(
                            "pass", "pass",
                            "key", "key"),
                    "packer", Packers.SpELSpringGzipJDK17));
            assertFalse(memShell.has("endpoint"), memShell.toString());
            assertFalse(memShell.path("packResult").asText().isBlank(), memShell.toString());
            String injectorClassName = memShell.path("memShellResult")
                    .path("injectorConfig").path("injectorClassName").asText();
            assertTrue(PackerSupport.isSpringExpressionClassName(injectorClassName), injectorClassName);

            JsonNode probeShell = callTool(client, "generate_probe_shell", Map.of(
                    "probeConfig", Map.of(
                            "probeMethod", "ResponseBody",
                            "probeContent", "Command",
                            "targetJreVersion", 50),
                    "probeContentConfig", Map.of(
                            "server", "Tomcat",
                            "reqParamName", "cmd"),
                    "packer", Packers.DefaultBase64));
            assertFalse(probeShell.has("endpoint"), probeShell.toString());
            assertFalse(probeShell.path("packResult").asText().isBlank(), probeShell.toString());
            assertFalse(probeShell.path("probeShellResult").path("shellClassName").asText().isBlank(),
                    probeShell.toString());
        }
    }

    private JsonNode callTool(McpSyncClient client, String toolName, Map<String, Object> request) {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(toolName, Map.of("request", request)));
        assertNotEquals(Boolean.TRUE, result.isError(), () -> String.valueOf(result.content()));
        assertFalse(result.content().isEmpty());
        return objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
    }
}
