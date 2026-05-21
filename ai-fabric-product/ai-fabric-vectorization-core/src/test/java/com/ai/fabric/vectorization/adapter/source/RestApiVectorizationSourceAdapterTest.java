package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.integration.discovery.DiscoveryCountMethod;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationRunReason;
import com.ai.fabric.vectorization.model.VectorizationRunnerMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestApiVectorizationSourceAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestApiVectorizationSourceAdapter adapter = new RestApiVectorizationSourceAdapter(objectMapper);

    @Test
    void discoverCanGroupCountsBySourceRecordVectorSpaceAcrossCursorPages() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/knowledge-export", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            boolean secondPage = query != null && query.contains("cursor=page-2");
            byte[] body = (secondPage ? """
                {
                  "records": [
                    {"id":"team-1","vectorSpace":"team-profile","title":"Scanner team"}
                  ],
                  "hasMore": false
                }
                """ : """
                {
                  "records": [
                    {"id":"module-1","vectorSpace":"service-module","title":"Scanner module"},
                    {"id":"module-2","vectorSpace":"service-module","title":"Evidence module"}
                  ],
                  "nextCursor": "page-2",
                  "hasMore": true
                }
                """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            JsonNode config = objectMapper.readTree("""
                {
                  "baseUrl": "http://localhost:%d",
                  "path": "/api/knowledge-export",
                  "itemsPath": "records",
                  "paginationMode": "CURSOR",
                  "cursorParam": "cursor",
                  "pageSizeParam": "limit",
                  "nextCursorPath": "nextCursor",
                  "hasMorePath": "hasMore",
                  "pageSize": 100,
                  "recordVectorSpacePath": "vectorSpace"
                }
                """.formatted(server.getAddress().getPort()));
            VectorizationDiscoveryResult result = adapter.discover(bundle(config), List.of("produs-safe-knowledge"));

            assertThat(result.countsByEntityType())
                .containsEntry("service-module", 2L)
                .containsEntry("team-profile", 1L);
            assertThat(result.countMethodByEntityType())
                .containsEntry("service-module", DiscoveryCountMethod.EXACT)
                .containsEntry("team-profile", DiscoveryCountMethod.EXACT);
        } finally {
            server.stop(0);
        }
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
