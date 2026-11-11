package com.ai.fabric.product.mcp.gateway.service;

import com.ai.fabric.product.mcp.gateway.client.McpStreamableHttpClient;
import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpSchemaHashCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpGatewayExecutionService service = new McpGatewayExecutionService(mock(McpStreamableHttpClient.class), mapper,
        new McpGatewayProperties("test", "test", "internal", "X-MCP-GATEWAY-API-KEY", "2025-11-25", List.of(), List.of(),
            false, "MCP_SECRET_", Duration.ofSeconds(1), Duration.ofSeconds(3)));

    @ParameterizedTest
    @MethodSource("releasedFixtureHashes")
    void preservesReleasedHashesForKnownUnaffectedFixtureSchemas(String name, String input, String releasedHash) throws Exception {
        String actual = ReflectionTestUtils.invokeMethod(service, "schemaHash", name, mapper.readTree(input), mapper.createObjectNode());
        assertThat(actual).isEqualTo(releasedHash);
    }

    static Stream<Arguments> releasedFixtureHashes() {
        // Digests from the released algorithm at ceda2468, not a second production acceptance path.
        return Stream.of(
            Arguments.of("produs.scan.status", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:042277b5d76cb20e91b88e5c35a28ce04a1a7be59759dc3d78591660c9d9d2b9"),
            Arguments.of("produs.finding.inspect", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:114a679a1beedb74ff7d163c9996fc44946715c4a51d5e5329c737d1c03c0134"),
            Arguments.of("produs.evidence.list", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:254dcf1df13cf01f7c0b30bfbbe2f9b22fc2dd2c13c0d94f885b3862bf1053c0"),
            Arguments.of("produs.product.list", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:5626f542d5e4cedf3271750cf242c7c7872cd5ce3171f6eea46d4c56114a1bc4"),
            Arguments.of("produs.milestone.review_evidence", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:d38001fe8c85037f33e4cd8d9d5c31f6aa83e67eca348bd35211b3943357c13f"),
            Arguments.of("produs.workspace.inspect", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:d4500fd6064ce7b4c3aa5aace8d2db3a8f3ad54e3675531717692dbf0e275225"),
            Arguments.of("produs.package.inspect", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:fea02a93974e1524c1cc6cb401e3996138b65c039af571c046606604714d6e7e"),
            Arguments.of("produs.catalog.search", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:8ecfdfbf88f6eb92bba8c68ed60c2bd602450f4d02a0cbe3b2a3078f431598a6"),
            Arguments.of("produs.catalog.export", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:6a6662e33300cf4e9e570766a36ff5b2addf450c7143ed88dab014c7c68b37bd"),
            Arguments.of("produs.productization_project.create", "{\"type\":\"object\",\"additionalProperties\":true}",
                "sha256:6a64c636165a0e6c92e7fefd41fad8e53132f411f2aa7d107a992c6e517867c0"),
            Arguments.of("search_catalog", "{\"type\":\"object\"}",
                "sha256:e865aa232d4f3aa93699dab0fe7d9fb8d52d0fc340adf9c230673e566d1cb556"),
            Arguments.of("inventory.search", """
                {"type":"object","required":["query","limit"],"properties":{"query":{"description":"Text","type":"string"},"limit":{"type":"integer"}}}
                """, "sha256:9580e81a17f327dddf4d6222817a70bb3dcc5a0318cc81bf4fbe70f0dc25fcc3"),
            Arguments.of("inventory.search", """
                {"type":"object","required":["q"],"properties":{"q":{"type":"string"}}}
                """, "sha256:3520cdce537d7a19542889f2fd43bf4aa70877d78fd1febed7a323602e7b78ec"),
            Arguments.of("inventory.search", """
                {"type":"object","required":["query"],"properties":{"query":{"type":"string"}}}
                """, "sha256:b41c913334f7f69805a3dfe3535b5e10db0a39be54d0dcc4bc14cc70bc05b75f")
        );
    }

    @Test
    void nullLiteralIsNotHashedAsAnEmptyObjectLiteral() throws Exception {
        String withNull = ReflectionTestUtils.invokeMethod(service, "schemaHash", "literal", mapper.readTree("{\"const\":null}"), mapper.createObjectNode());
        String withObject = ReflectionTestUtils.invokeMethod(service, "schemaHash", "literal", mapper.readTree("{\"const\":{}}"), mapper.createObjectNode());
        assertThat(withNull).isNotEqualTo(withObject);
    }
}
