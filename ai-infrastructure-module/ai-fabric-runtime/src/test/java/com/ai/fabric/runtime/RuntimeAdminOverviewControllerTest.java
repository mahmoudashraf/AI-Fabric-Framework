package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
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
        authProperties.getIngress().setLegacyRequestIdentityEnabled(false);
        authProperties.getIngress().setRejectConflictingRequestIdentity(true);
        authProperties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
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
            null,
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
        assertThat(auth).containsEntry("legacyRequestIdentityEnabled", false);
        assertThat(auth).containsEntry("rejectConflictingRequestIdentity", true);
        assertThat(auth).containsEntry("trustedBackendConfigured", true);
        assertThat(auth).containsEntry("publicTokenValidationConfigured", true);
        assertThat(auth).containsEntry("publicTokenIssuer", "runtime-public-bootstrap");
        assertThat(auth).containsEntry("publicDefaultAudience", "storefront-chat");
        assertThat(auth.get("publicAcceptedIssuers")).isEqualTo(List.of("shopify-app"));
        assertThat(auth.get("publicAcceptedAudiences")).isEqualTo(List.of("storefront-chat"));
        assertThat(auth.get("verifiedContextHeaders")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> verifiedHeaders = (Map<String, Object>) auth.get("verifiedContextHeaders");
        assertThat(verifiedHeaders).containsEntry("subjectId", "X-AIFABRIC-AUTH-SUBJECT-ID");
        assertThat(verifiedHeaders).containsEntry("deploymentId", "X-AIFABRIC-AUTH-DEPLOYMENT-ID");
        assertThat(auth.get("publicBootstrap")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> publicBootstrap = (Map<String, Object>) auth.get("publicBootstrap");
        assertThat(publicBootstrap).containsEntry("enabled", true);
        assertThat(publicBootstrap.get("allowedOrigins")).isEqualTo(List.of("https://storefront.example"));
    }

    private RuntimeAdminOverviewController instantiateController(AIActionRegistry actionRegistry,
                                                                 Object actionCatalogProperties,
                                                                 AIEntityConfigurationLoader entityConfigurationLoader,
                                                                 VectorDatabaseService vectorDatabaseService,
                                                                 RuntimeAuthProperties authProperties) {
        try {
            Constructor<?> constructor = RuntimeAdminOverviewController.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (RuntimeAdminOverviewController) constructor.newInstance(
                actionRegistry,
                actionCatalogProperties,
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
