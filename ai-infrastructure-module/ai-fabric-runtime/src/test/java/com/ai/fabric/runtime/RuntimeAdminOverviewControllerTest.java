package com.ai.fabric.runtime;

import com.ai.fabric.runtime.web.admin.RuntimeAdminOverviewController;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.connector.AIActionCatalogProperties;
import com.ai.infrastructure.rag.VectorDatabaseService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeAdminOverviewControllerTest {

    @Test
    void overviewIncludesVectorScopeDiagnostics() {
        AIActionRegistry actionRegistry = mock(AIActionRegistry.class);
        AIEntityConfigurationLoader entityConfigurationLoader = mock(AIEntityConfigurationLoader.class);
        VectorDatabaseService vectorDatabaseService = mock(VectorDatabaseService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(actionRegistry.getAllMetadata()).thenReturn(java.util.List.of());
        when(entityConfigurationLoader.getSupportedEntityTypes()).thenReturn(Set.of("product", "policy", "review"));
        when(vectorDatabaseService.supportsVectorScan()).thenReturn(true);

        Map<String, Object> vectorScope = new LinkedHashMap<>();
        vectorScope.put("sharedStorage", true);
        vectorScope.put("scopeType", "NAMESPACE_PREFIX");
        vectorScope.put("rootResourceValue", "shared-index");
        vectorScope.put("scopePrefix", "customer-a--tenant-b");
        vectorScope.put("scopePattern", "customer-a--tenant-b__<entity-type>");
        when(vectorDatabaseService.adminDiagnostics()).thenReturn(vectorScope);

        RuntimeAdminOverviewController controller = new RuntimeAdminOverviewController(
            actionRegistry,
            new AIActionCatalogProperties(),
            entityConfigurationLoader,
            vectorDatabaseService
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
    }
}
