package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import com.ai.fabric.runtime.admin.RuntimeActionCatalogGateway;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.web.admin.RuntimeAdminOverviewController;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.dto.VectorScanPage;
import com.ai.infrastructure.dto.VectorScanRequest;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.rag.VectorDatabaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeAdminOverviewControllerTest {

    @Test
    void overviewIncludesVectorScopeDiagnostics() {
        AIActionRegistry actionRegistry = mock(AIActionRegistry.class);
        AIEntityConfigurationLoader entityConfigurationLoader = mock(AIEntityConfigurationLoader.class);
        VectorDatabaseService vectorDatabaseService = new TestVectorDatabaseService();
        HttpServletRequest request = mock(HttpServletRequest.class);
        RuntimeAuthProperties authProperties = new RuntimeAuthProperties();
        authProperties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        authProperties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        authProperties.getIngress().getPrivateAssertions().setSigningKey("private-assertion-secret");
        authProperties.getIngress().setAcceptedIssuers(List.of("platform-poc:SESSION"));
        authProperties.getIngress().setAcceptedAudiences(List.of("dep-auth"));
        authProperties.getPublicTokens().setSigningKey("public-signing-secret");
        authProperties.getPublicTokens().setIssuer("runtime-public-bootstrap");
        authProperties.getPublicTokens().getAcceptedIssuers().add("shopify-app");
        authProperties.getPublicTokens().getAcceptedAudiences().add("storefront-chat");
        authProperties.getPublicTokens().setDefaultAudience("storefront-chat");
        authProperties.getPublicTokens().getBootstrap().setEnabled(true);
        authProperties.getPublicTokens().getBootstrap().getAllowedOrigins().add("https://storefront.example");

        when(actionRegistry.getAllMetadata()).thenReturn(java.util.List.of());
        when(entityConfigurationLoader.getSupportedEntityTypes()).thenReturn(Set.of("product", "policy", "review"));
        Map<String, Object> vectorScope = new LinkedHashMap<>();
        vectorScope.put("sharedStorage", true);
        vectorScope.put("scopeType", "NAMESPACE_PREFIX");
        vectorScope.put("rootResourceValue", "shared-index");
        vectorScope.put("scopePrefix", "customer-a--tenant-b");
        vectorScope.put("scopePattern", "customer-a--tenant-b__<entity-type>");
        ((TestVectorDatabaseService) vectorDatabaseService).diagnostics = vectorScope;

        RuntimeAdminOverviewController controller = instantiateController(
            actionRegistry,
            mock(RuntimeActionCatalogGateway.class),
            entityConfigurationLoader,
            vectorDatabaseService,
            authProperties
        );
        ReflectionTestUtils.setField(controller, "entityConfigLocation", "https://platform.example/entities");
        ReflectionTestUtils.setField(controller, "promptConfigLocation", "https://platform.example/prompts");
        ReflectionTestUtils.setField(controller, "adminApiKey", "");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        ResponseEntity<?> response = controller.overview(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("supportsVectorScan", true);
        assertThat(body.get("supportedEntityTypes")).isEqualTo(Set.of("product", "policy", "review"));
        assertThat(body.get("vectorScope")).isEqualTo(vectorScope);
        assertThat(body.get("auth")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = (Map<String, Object>) body.get("auth");
        assertThat(auth).containsEntry("ingressMode", "VERIFIED_CONTEXT_REQUIRED");
        assertThat(auth).containsEntry("verifiedContextRequired", true);
        assertThat(auth).containsEntry("trustedBackendConfigured", true);
        assertThat(auth).containsEntry("privateAssertionValidationConfigured", true);
        assertThat(auth).containsEntry("privateAssertionIssuerPolicyConfigured", true);
        assertThat(auth).containsEntry("privateAssertionAudiencePolicyConfigured", true);
        assertThat(auth).containsEntry("publicTokenValidationConfigured", true);
        assertThat(auth).containsEntry("publicTokenIssuer", "runtime-public-bootstrap");
        assertThat(auth).containsEntry("publicDefaultAudience", "storefront-chat");
        assertThat(auth.get("privateAssertionAcceptedIssuers")).isEqualTo(List.of("platform-poc:SESSION"));
        assertThat(auth.get("privateAssertionAcceptedAudiences")).isEqualTo(List.of("dep-auth"));
        assertThat(auth.get("publicAcceptedIssuers")).isEqualTo(List.of("shopify-app"));
        assertThat(auth.get("publicAcceptedAudiences")).isEqualTo(List.of("storefront-chat"));
        assertThat(auth.get("publicAnonymousGrantedScopes"))
            .isEqualTo(List.of("chat:query", "chat:suggestions", "chat:conversations"));
        assertThat(auth.get("publicAuthenticatedDefaultScopes"))
            .isEqualTo(List.of("chat:query", "chat:suggestions", "chat:conversations"));
        assertThat(auth.get("publicAuthenticatedAllowedScopes"))
            .isEqualTo(List.of("chat:query", "chat:suggestions", "chat:conversations"));
        assertThat(auth).containsEntry("publicAnonymousConversationHistoryAllowed", true);
        assertThat(auth).containsEntry("publicAuthenticatedConversationHistoryAllowed", true);
        assertThat(auth).containsEntry("privateAssertionAuthorizationHeader", "X-AIFABRIC-RUNTIME-AUTHORIZATION");
        assertThat(auth).containsEntry("privateAssertionTokenScheme", "Bearer");
        assertThat(auth.get("publicBootstrap")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> publicBootstrap = (Map<String, Object>) auth.get("publicBootstrap");
        assertThat(publicBootstrap).containsEntry("enabled", true);
        assertThat(publicBootstrap.get("allowedOrigins")).isEqualTo(List.of("https://storefront.example"));
        assertThat(auth.get("supportedChatEndpoints"))
            .isEqualTo(List.of(
                "/api/chat/me/query",
                "/api/chat/me/suggestions",
                "/api/chat/me/auth-context",
                "/api/chat/me/conversations",
                "/api/chat/me/conversations/{conversationId}"
            ));
        assertThat(body.get("authWarnings")).isEqualTo(List.of());
    }

    @Test
    void authOverviewSurfacesValidationWarningsAndContractMetadata() {
        RuntimeAuthProperties authProperties = new RuntimeAuthProperties();
        authProperties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        authProperties.getPublicTokens().getBootstrap().setEnabled(true);
        authProperties.getPublicTokens().getBootstrap().setAllowMissingOrigin(true);

        RuntimeAdminOverviewController controller = instantiateController(
            mock(AIActionRegistry.class),
            mock(RuntimeActionCatalogGateway.class),
            mock(AIEntityConfigurationLoader.class),
            new TestVectorDatabaseService(),
            authProperties
        );
        ReflectionTestUtils.setField(controller, "adminApiKey", "");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        ResponseEntity<?> response = controller.authOverview(mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("contractVersion", "RUNTIME_AUTH_OVERVIEW_V1");
        assertThat(body).containsEntry("warningCount", 7);
        assertThat(body.get("warnings")).isEqualTo(List.of(
            "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED but no trusted backend API key is configured. Private-runtime machine authentication will fail until ai.fabric.runtime.auth.ingress.trusted-backend.api-key-value is set.",
            "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED but no private assertion signing key is configured. Signed private-runtime assertions will be rejected until ai.fabric.runtime.auth.ingress.private-assertions.signing-key is set.",
            "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED without ai.fabric.runtime.auth.ingress.accepted-issuers. Signed private-runtime assertions will validate signatures, but issuer policy will remain open until an explicit allowlist is configured.",
            "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED without ai.fabric.runtime.auth.ingress.accepted-audiences. Signed private-runtime assertions will validate signatures, but audience policy will remain open until an explicit allowlist is configured.",
            "Runtime public bootstrap is enabled but no public token signing key is configured. POST /api/public/chat/session will stay unavailable until ai.fabric.runtime.auth.public-tokens.signing-key is set.",
            "Runtime public bootstrap is enabled without any allowed origins. Cross-origin anonymous bootstrap requests will be denied unless allowed origins are configured.",
            "Runtime public bootstrap is enabled with allow-missing-origin=true. Anonymous public bootstrap requests without an Origin header will be accepted; use only when the embedding environment cannot provide origin headers."
        ));
        assertThat(body.get("guidance"))
            .isEqualTo("Runtime auth posture still lacks the full private-runtime contract. Signed private-runtime callers will not succeed until both the trusted backend credential and the private assertion signing key are provisioned.");
        assertThat(body.get("auth")).isInstanceOf(Map.class);
    }

    private RuntimeAdminOverviewController instantiateController(AIActionRegistry actionRegistry,
                                                                 RuntimeActionCatalogGateway actionCatalogGateway,
                                                                 AIEntityConfigurationLoader entityConfigurationLoader,
                                                                 VectorDatabaseService vectorDatabaseService,
                                                                 RuntimeAuthProperties authProperties) {
        try {
            Constructor<?> constructor = RuntimeAdminOverviewController.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (RuntimeAdminOverviewController) constructor.newInstance(
                actionRegistry,
                actionCatalogGateway,
                entityConfigurationLoader,
                vectorDatabaseService,
                authProperties
            );
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestVectorDatabaseService implements VectorDatabaseService {
        private Map<String, Object> diagnostics = Map.of();

        @Override
        public boolean supportsVectorScan() {
            return true;
        }

        @Override
        public Map<String, Object> adminDiagnostics() {
            return diagnostics;
        }

        @Override
        public String storeVector(String entityType, String entityId, String content, List<Double> embedding, Map<String, Object> metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updateVector(String vectorId, String entityType, String entityId, String content, List<Double> embedding, Map<String, Object> metadata) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<VectorRecord> getVector(String vectorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeVector(String entityType, String entityId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeVectorById(String vectorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> batchStoreVectors(List<VectorRecord> vectors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int batchUpdateVectors(List<VectorRecord> vectors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int batchRemoveVectors(List<String> vectorIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<VectorRecord> getVectorsByEntityType(String entityType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getVectorCountByEntityType(String entityType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long clearVectorsByEntityType(String entityType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long clearVectors() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getStatistics() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean vectorExists(String entityType, String entityId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VectorScanPage scan(VectorScanRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
