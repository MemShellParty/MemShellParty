package com.reajason.javaweb.boot.mcp;

import com.reajason.javaweb.boot.dto.MemShellGenerateResponse;
import com.reajason.javaweb.packer.PackerSupport;
import com.reajason.javaweb.packer.Packers;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void generateToolsReturnRestCallPlansThatGenerateOnPost() {
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

            JsonNode memShellPlan = callPlan(client, "generate_memshell", Map.of(
                    "shellConfig", Map.of(
                            "server", "Tomcat",
                            "shellTool", "Godzilla",
                            "shellType", "Filter",
                            "obfuscation", Map.of("enabled", true)),
                    "shellToolConfig", Map.of(
                            "pass", "pass",
                            "key", "key"),
                    "packer", Packers.SpELSpringGzipJDK17));
            assertEquals("POST", memShellPlan.path("method").asText());
            String memShellEndpoint = memShellPlan.path("endpoint").asText();
            assertTrue(memShellEndpoint.endsWith("/api/memshell/generate"), memShellEndpoint);
            assertTrue(memShellEndpoint.startsWith("http://localhost:" + port), memShellEndpoint);
            assertEquals("Tomcat", memShellPlan.path("body").path("shellConfig").path("server").asText());
            String memShellPlanJson = memShellPlan.toString();
            assertFalse(memShellPlanJson.contains("packResult"), memShellPlanJson);
            assertFalse(memShellPlanJson.contains("shellBytesBase64Str"), memShellPlanJson);
            assertFalse(memShellPlanJson.contains("probeShellResult"), memShellPlanJson);

            ResponseEntity<MemShellGenerateResponse> memShellResponse =
                    postPlan(memShellEndpoint, memShellPlan.path("body"), MemShellGenerateResponse.class);
            assertEquals(HttpStatus.OK, memShellResponse.getStatusCode());
            assertNotNull(memShellResponse.getBody());
            String packResult = memShellResponse.getBody().getPackResult();
            assertNotNull(packResult);
            assertFalse(packResult.isBlank());
            String injectorClassName = memShellResponse.getBody().getMemShellResult()
                    .getInjectorConfig().getInjectorClassName();
            assertTrue(PackerSupport.isSpringExpressionClassName(injectorClassName), injectorClassName);

            JsonNode probeShellPlan = callPlan(client, "generate_probe_shell", Map.of(
                    "probeConfig", Map.of(
                            "probeMethod", "ResponseBody",
                            "probeContent", "Command",
                            "targetJreVersion", 50),
                    "probeContentConfig", Map.of(
                            "server", "Tomcat",
                            "reqParamName", "cmd"),
                    "packer", Packers.DefaultBase64));
            assertEquals("POST", probeShellPlan.path("method").asText());
            String probeShellEndpoint = probeShellPlan.path("endpoint").asText();
            assertTrue(probeShellEndpoint.endsWith("/api/probe/generate"), probeShellEndpoint);
            assertTrue(probeShellEndpoint.startsWith("http://localhost:" + port), probeShellEndpoint);
            String probeShellPlanJson = probeShellPlan.toString();
            assertFalse(probeShellPlanJson.contains("packResult"), probeShellPlanJson);
            assertFalse(probeShellPlanJson.contains("shellBytesBase64Str"), probeShellPlanJson);
            assertFalse(probeShellPlanJson.contains("probeShellResult"), probeShellPlanJson);

            ResponseEntity<String> probeShellResponse =
                    postPlan(probeShellEndpoint, probeShellPlan.path("body"), String.class);
            assertEquals(HttpStatus.OK, probeShellResponse.getStatusCode());
            assertNotNull(probeShellResponse.getBody());
            JsonNode probeShellResult = objectMapper.readTree(probeShellResponse.getBody());
            assertFalse(probeShellResult.path("packResult").asText().isBlank());
            assertFalse(probeShellResult.path("probeShellResult").path("shellClassName").asText().isBlank());
        }
    }

    private JsonNode callPlan(McpSyncClient client, String toolName, Map<String, Object> request) {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(toolName, Map.of("request", request)));
        assertNotEquals(Boolean.TRUE, result.isError(), () -> String.valueOf(result.content()));
        assertFalse(result.content().isEmpty());
        return objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
    }

    private static <T> ResponseEntity<T> postPlan(String endpoint, JsonNode body, Class<T> responseType) {
        return RestClient.builder()
                .build()
                .post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .toEntity(responseType);
    }
}
