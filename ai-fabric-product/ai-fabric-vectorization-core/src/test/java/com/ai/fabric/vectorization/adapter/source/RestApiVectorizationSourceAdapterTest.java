package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationRunReason;
import com.ai.fabric.vectorization.model.VectorizationRunnerMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestApiVectorizationSourceAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestApiVectorizationSourceAdapter adapter = new RestApiVectorizationSourceAdapter(objectMapper);

    @Test
    void buildUriAllowsPublicHttpsAndEncodesQueryParams() {
        assertThat(VectorizationSourceAdapterSupport.buildUri(
            "https://93.184.216.34/",
            "api/knowledge-export",
            Map.of("cursor", "page 2")
        ).toString())
            .isEqualTo("https://93.184.216.34/api/knowledge-export?cursor=page+2");
    }

    @Test
    void buildUriRejectsHttpScheme() {
        assertThatThrownBy(() -> VectorizationSourceAdapterSupport.buildUri(
            "http://93.184.216.34",
            "/api/knowledge-export",
            Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTPS");
    }

    @Test
    void buildUriRejectsLocalhostAndPrivateIpTargets() {
        assertThatThrownBy(() -> VectorizationSourceAdapterSupport.buildUri(
            "https://localhost",
            "/api/knowledge-export",
            Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");

        assertThatThrownBy(() -> VectorizationSourceAdapterSupport.buildUri(
            "https://10.0.0.1",
            "/api/knowledge-export",
            Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("private address");
    }

    @Test
    void buildUriRejectsAbsoluteRelativePath() {
        assertThatThrownBy(() -> VectorizationSourceAdapterSupport.buildUri(
            "https://93.184.216.34",
            "https://169.254.169.254/latest/meta-data",
            Map.of()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path must be relative");
    }

    @Test
    void authHeadersRejectMultilineBearerTokenWithoutLeakingValue() throws Exception {
        JsonNode config = objectMapper.readTree("""
            {"baseUrl": "http://localhost:1", "path": "/api/knowledge-export"}
            """);
        VectorizationExecutionBundle bundle = bundle(
            config,
            "BEARER",
            new ResolvedSourceAuthMaterial(Map.of("token", "secret-line-1\nsecret-line-2"), Map.of())
        );

        assertThatThrownBy(() -> VectorizationSourceAdapterSupport.authHeaders(
            bundle.connectionDescriptor(),
            bundle.sourceAuthMaterial(),
            config
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid header value")
            .hasMessageNotContaining("secret-line");
    }

    @Test
    void connectorAuthDescriptorsMaskSecretsInToString() {
        ResolvedSourceAuthMaterial sourceAuth = new ResolvedSourceAuthMaterial(
            Map.of("token", "secret-token"),
            Map.of("apiKey", "SECRET_ENV")
        );
        TargetConnectionDescriptor targetConnection = new TargetConnectionDescriptor(
            "RUNTIME_DATA_SYNC",
            "https://runtime.example",
            "/batch",
            "/spaces",
            "X-Key",
            "runtime-secret"
        );

        assertThat(sourceAuth.toString()).doesNotContain("secret-token", "SECRET_ENV");
        assertThat(targetConnection.toString()).doesNotContain("runtime-secret");
    }

    private VectorizationExecutionBundle bundle(JsonNode connectionConfig) {
        return bundle(connectionConfig, "NONE", new ResolvedSourceAuthMaterial(Map.of(), Map.of()));
    }

    private VectorizationExecutionBundle bundle(JsonNode connectionConfig,
                                                String authMode,
                                                ResolvedSourceAuthMaterial authMaterial) {
        JsonNode empty = objectMapper.createObjectNode();
        return new VectorizationExecutionBundle(
            "dep-1",
            "run-1",
            VectorizationRunReason.DISCOVERY,
            "vpr-1",
            VectorizationRunnerMode.PLATFORM_MANAGED_AUTO,
            "vcn-1",
            List.of("produs-safe-knowledge"),
            empty,
            empty,
            new ConnectionDescriptor("vcn-1", "ProdUS", "REST_API", authMode, connectionConfig, empty),
            authMaterial,
            empty,
            new TargetConnectionDescriptor("RUNTIME_DATA_SYNC", "http://runtime.example", "/batch", "/spaces", "X-Key", "secret")
        );
    }
}
