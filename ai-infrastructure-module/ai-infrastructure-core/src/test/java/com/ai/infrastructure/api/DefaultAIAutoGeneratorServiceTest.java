package com.ai.infrastructure.api;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DefaultAIAutoGeneratorServiceTest {

    private AICoreService aiCoreService;
    private DefaultAIAutoGeneratorService service;

    @BeforeEach
    void setUp() {
        aiCoreService = Mockito.mock(AICoreService.class);
        Mockito.when(aiCoreService.generateContent(ArgumentMatchers.any(AIGenerationRequest.class)))
            .thenAnswer(invocation -> {
                AIGenerationRequest request = invocation.getArgument(0);
                return AIGenerationResponse.builder()
                    .content("Generated " + request.getGenerationType() + " for " + request.getEntityType())
                    .build();
            });

        service = new DefaultAIAutoGeneratorService(aiCoreService, AIServiceConfig.builder().build());
    }

    @Test
    void generateEndpointsCreatesCanonicalCrudSet() {
        List<APIEndpointDefinition> endpoints = service.generateEndpoints("product");

        assertEquals(5, endpoints.size());
        assertTrue(service.isEndpointRegistered("product:list"));
        assertTrue(endpoints.stream().allMatch(e -> e.getPath() != null));
    }

    @Test
    void registerAndUnregisterEndpointManagesStore() {
        APIEndpointDefinition definition = APIEndpointDefinition.builder()
            .id("custom-endpoint")
            .method("PATCH")
            .path("/api/products/special")
            .metadata(Map.of("entityType", "product"))
            .build();

        service.registerEndpoint(definition);
        assertTrue(service.isEndpointRegistered("custom-endpoint"));

        service.unregisterEndpoint("custom-endpoint");
        assertFalse(service.isEndpointRegistered("custom-endpoint"));
    }

    @Test
    void generateSpecificationProducesMetadata() {
        service.generateEndpoints("order");

        APISpecification specification = service.generateSpecification("order");

        assertEquals("ORDER API", specification.getTitle());
        assertEquals(5, specification.getEndpoints().size());
        assertNotNull(specification.getGeneratedAt());
    }

    @Test
    void generateOpenApiSpecificationReturnsJson() throws Exception {
        service.generateEndpoints("invoice");

        String json = service.generateOpenAPISpecification("invoice");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode parsed = mapper.readTree(json);

        assertEquals("INVOICE API", parsed.get("title").asText());
    }

    @Test
    void enableAndDisableEndpointToggleFlag() {
        service.generateEndpoints("user");

        service.disableEndpoint("user:list");
        assertFalse(service.getEndpoint("user:list").getEnabled());

        service.enableEndpoint("user:list");
        assertTrue(service.getEndpoint("user:list").getEnabled());
    }

    @Test
    void refreshEndpointsRecreatesSet() {
        service.generateEndpoints("catalog");
        service.refreshEndpoints("catalog");

        List<APIEndpointDefinition> endpoints = service.getEndpointsByEntityType("catalog");
        assertEquals(5, endpoints.size());
    }

    @Test
    void generateCompleteSpecificationAggregatesAllEndpoints() {
        service.generateEndpoints("product");
        service.generateEndpoints("order");

        APISpecification specification = service.generateCompleteAPISpecification();
        assertThat(specification.getEndpoints()).hasSize(10);
    }

    @Test
    void getMetricsReflectsOperations() {
        service.generateEndpoints("product");
        Map<String, Object> metrics = service.getMetrics();

        assertEquals(5, metrics.get("totalEndpoints"));
        assertEquals(1, metrics.get("entityCount"));
    }

    @Test
    void generateClientSdkReturnsPlaceholder() {
        service.generateEndpoints("product");
        APISpecification specification = service.generateSpecification("product");

        String sdk = service.generateClientSDK("typescript", specification);

        assertTrue(sdk.contains("typescript"));
        assertTrue(sdk.contains("PRODUCT API"));
    }

    @Test
    void validateEndpointDetectsMissingFields() {
        assertFalse(service.validateEndpoint(APIEndpointDefinition.builder().build()));
        assertTrue(service.validateEndpoint(APIEndpointDefinition.builder()
            .id("valid")
            .path("/test")
            .method("GET")
            .build()));
    }
}
