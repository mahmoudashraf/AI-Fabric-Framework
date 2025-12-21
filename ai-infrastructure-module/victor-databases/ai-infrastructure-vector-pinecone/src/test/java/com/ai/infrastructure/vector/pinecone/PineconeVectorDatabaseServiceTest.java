package com.ai.infrastructure.vector.pinecone;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.vector.pinecone.PineconeVectorDatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PineconeVectorDatabaseServiceTest {

    private PineconeVectorDatabaseService service;
    private MockRestServiceServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AIProviderConfig config = new AIProviderConfig();
        AIProviderConfig.PineconeConfig pinecone = config.getPinecone();
        pinecone.setEnabled(true);
        pinecone.setApiKey("test-key");
        pinecone.setApiHost("https://mock-pinecone.test");
        pinecone.setIndexName("test-index");
        pinecone.setEnvironment("test-env");
        pinecone.setDimensions(3);

        RestTemplate restTemplate = new RestTemplate();
        service = new PineconeVectorDatabaseService(config, restTemplate);
        service.initializeClient();

        server = MockRestServiceServer.bindTo(restTemplate)
            .ignoreExpectOrder(true)
            .build();
    }

    @Test
    void storeVectorCallsUpsertEndpoint() {
        server.expect(ExpectedCount.once(), requestTo("https://mock-pinecone.test/vectors/upsert"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String vectorId = service.storeVector("product", "123", "Luxury watch", List.of(0.1, 0.2, 0.3), Map.of("category", "watches"));

        assertEquals("product::123", vectorId);
        server.verify();
    }

    @Test
    void searchReturnsFilteredResults() throws Exception {
        Map<String, Object> responseBody = Map.of(
            "matches", List.of(
                Map.of(
                    "id", "product::123",
                    "score", 0.92,
                    "metadata", Map.of("entityId", "123", "entityType", "product", "content", "Luxury watch")
                ),
                Map.of(
                    "id", "product::456",
                    "score", 0.4,
                    "metadata", Map.of("entityId", "456", "entityType", "product")
                )
            )
        );

        server.expect(ExpectedCount.once(), requestTo("https://mock-pinecone.test/query"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(objectMapper.writeValueAsString(responseBody), MediaType.APPLICATION_JSON));

        AISearchResponse response = service.search(
            List.of(0.2, 0.3, 0.4),
            AISearchRequest.builder()
                .query("luxury")
                .entityType("product")
                .limit(5)
                .threshold(0.5)
                .build()
        );

        assertEquals(1, response.getTotalResults());
        assertEquals("product::123", response.getResults().get(0).get("id"));
        server.verify();
    }

    @Test
    void getVectorFetchesFromApi() throws Exception {
        Map<String, Object> responseBody = Map.of(
            "vectors", Map.of(
                "product::123", Map.of(
                    "id", "product::123",
                    "values", List.of(0.1, 0.3, 0.5),
                    "metadata", Map.of("entityId", "123", "entityType", "product", "content", "Luxury watch")
                )
            )
        );

        server.expect(ExpectedCount.once(), requestTo("https://mock-pinecone.test/vectors/fetch?ids=product::123&namespace=product"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(objectMapper.writeValueAsString(responseBody), MediaType.APPLICATION_JSON));

        Optional<VectorRecord> record = service.getVector("product::123");

        assertTrue(record.isPresent());
        assertEquals("123", record.get().getEntityId());
        assertEquals(3, record.get().getEmbedding().size());
        server.verify();
    }
}
