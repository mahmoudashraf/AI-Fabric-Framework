package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.relationship.it.RelationshipQueryIntegrationTestApplication;
import com.ai.infrastructure.relationship.it.config.BackendEnvTestConfiguration;
import com.ai.infrastructure.relationship.it.entity.BrandEntity;
import com.ai.infrastructure.relationship.it.entity.ProductEntity;
import com.ai.infrastructure.relationship.it.repository.BrandRepository;
import com.ai.infrastructure.relationship.it.repository.ProductRepository;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("realapi")
@Import({BackendEnvTestConfiguration.class, OrchestratorAccessPolicyRealApiIntegrationTest.PolicyConfig.class})
@TestPropertySource(properties = {
    "ai.infrastructure.relationship.enable-orchestrator-integration=true"
})
class OrchestratorAccessPolicyRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;

    @Autowired(required = false)
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private RecordingEntityAccessPolicy accessPolicy;

    private String blueRunnerId;

    @BeforeEach
    void setUp() {
        searchableEntityRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        brandRepository.deleteAllInBatch();
        if (vectorDatabaseService != null) {
            try {
                vectorDatabaseService.clearVectors();
            } catch (Exception ignored) {
            }
        }
        seedCatalog();
        accessPolicy.reset();
    }

    @Test
    void orchestratorShouldInvokePolicyAndExecuteRelationshipQuery() {
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("orch-user")
            .sessionId("orch-session")
            .metadata(Map.of("channel", "test"))
            .build();

        OrchestrationResult result = orchestrator.orchestrate(
            "relationship query: find blue running shoes under $120 from Nike",
            context
        );

        assertThat(accessPolicy.getCallCount()).isGreaterThan(0);
        assertThat(accessPolicy.getLastUser()).isEqualTo("orch-user");
        assertThat(accessPolicy.getLastContext()).containsEntry("resourceId", "rag:intent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);

        Object actionResultObj = result.getData().get("actionResult");
        assertThat(actionResultObj).isInstanceOf(ActionResult.class);
        ActionResult actionResult = (ActionResult) actionResultObj;
        assertThat(actionResult.isSuccess()).isTrue();
        assertThat(actionResult.getData()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) actionResult.getData();
        assertThat(payload).containsKey("documents");

        @SuppressWarnings("unchecked")
        List<RAGResponse.RAGDocument> documents = (List<RAGResponse.RAGDocument>) payload.get("documents");
        assertThat(documents).isNotNull();
        assertThat(documents).anySatisfy(doc -> assertThat(doc.getId()).isEqualTo(blueRunnerId));
    }

    private void seedCatalog() {
        BrandEntity nike = new BrandEntity();
        nike.setName("Nike");
        nike = brandRepository.save(nike);

        ProductEntity blueRunner = product("Blue Runner", "blue", BigDecimal.valueOf(95), "ACTIVE", nike);
        ProductEntity redRunner = product("Red Runner", "red", BigDecimal.valueOf(110), "ACTIVE", nike);

        productRepository.saveAll(List.of(blueRunner, redRunner));
        indexProduct(blueRunner);
        indexProduct(redRunner);
        blueRunnerId = blueRunner.getId();
    }

    private ProductEntity product(String name, String color, BigDecimal price, String status, BrandEntity brand) {
        ProductEntity product = new ProductEntity();
        product.setName(name);
        product.setColor(color);
        product.setPrice(price);
        product.setStatus(status);
        product.setBrand(brand);
        brand.getProducts().add(product);
        return product;
    }

    private void indexProduct(ProductEntity product) {
        searchableEntityRepository.save(
            com.ai.infrastructure.entity.AISearchableEntity.builder()
                .entityType("product")
                .entityId(product.getId())
                .searchableContent("%s (%s) - $%s".formatted(product.getName(), product.getColor(), product.getPrice()))
                .metadata("""
                    {"brand":"%s","status":"%s"}
                    """.formatted(product.getBrand().getName(), product.getStatus()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build()
        );
    }

    @TestConfiguration
    static class PolicyConfig {

        @Bean
        RecordingEntityAccessPolicy recordingEntityAccessPolicy() {
            return new RecordingEntityAccessPolicy();
        }
    }

    static class RecordingEntityAccessPolicy implements EntityAccessPolicy {

        private final AtomicInteger callCount = new AtomicInteger();
        private final AtomicReference<String> lastUser = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> lastContext = new AtomicReference<>();

        @Override
        public boolean canUserAccessEntity(String userId, Map<String, Object> entity) {
            callCount.incrementAndGet();
            lastUser.set(userId);
            lastContext.set(Map.copyOf(entity));
            return true;
        }

        int getCallCount() {
            return callCount.get();
        }

        String getLastUser() {
            return lastUser.get();
        }

        Map<String, Object> getLastContext() {
            return lastContext.get();
        }

        void reset() {
            callCount.set(0);
            lastUser.set(null);
            lastContext.set(null);
        }
    }
}
