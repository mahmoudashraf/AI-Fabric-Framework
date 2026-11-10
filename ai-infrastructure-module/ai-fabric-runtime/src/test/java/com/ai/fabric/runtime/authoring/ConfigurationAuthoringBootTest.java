package com.ai.fabric.runtime.authoring;

import static org.assertj.core.api.Assertions.*;

import ai.fabric.core.AICoreService;
import ai.fabric.core.AIEmbeddingService;
import ai.fabric.core.AISearchService;
import ai.fabric.indexing.worker.IndexingOperationExecutor;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.orchestration.RAGOrchestrator;
import ai.fabric.provider.AIProviderManager;
import ai.fabric.rag.VectorDatabaseService;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimePrivateAssertionService;
import com.ai.fabric.runtime.budget.RuntimeBudgetAllocation;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.ai.fabric.runtime.budget.RuntimeProviderBudgetAdminController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(classes = AIFabricRuntimeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    args = "--spring.profiles.active=configuration-authoring", properties = {
    "LOOMAI_AUTHORING_ENABLED=true", "OPENAI_ENABLED=true", "OPENAI_API_KEY=dedicated-authoring-offline-fixture-key",
    "AI_FABRIC_RUNTIME_CUSTOMER_ID=loomai-internal",
    "AI_FABRIC_RUNTIME_DEPLOYMENT_ID=configuration-authoring-local",
    "AI_FABRIC_RUNTIME_TENANT_ID=loomai-internal",
    "LOOMAI_RUNTIME_TARGET_PROFILE_ID=configuration-authoring-local",
    "LOOMAI_PROVIDER_BUDGET_ENVIRONMENT=staging",
    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=authoring-offline-ingress-secret",
    "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=authoring-offline-assertion-secret",
    "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=platform-authoring-local",
    "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=configuration-authoring-local",
    "spring.datasource.url=jdbc:h2:mem:authoring-boot;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfigurationAuthoringBootTest {
    @DynamicPropertySource static void credential(DynamicPropertyRegistry properties) {
        properties.add("LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY", () ->
            ReviewedOpenAiRateCard.credentialIdentity("dedicated-authoring-offline-fixture-key"));
    }

    @LocalServerPort int port;
    @Autowired ApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired RuntimeBudgetBindingService budgets;
    @Autowired RuntimePrivateAssertionService assertions;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test void realDedicatedBootHasNoBusinessCapabilitiesAndUsesExistingPrivateFundingLifecycle() throws Exception {
        assertThat(context.getBeansOfType(AICoreService.class)).hasSize(1);
        assertThat(context.getBeansOfType(AIProviderManager.class)).hasSize(1);
        assertThat(context.getBeansOfType(BudgetGuardedSpringAiModelResolver.class)).hasSize(1);
        for (var type : List.of(AIEmbeddingService.class, AISearchService.class, VectorDatabaseService.class,
            IndexingOperationExecutor.class, AIActionRegistry.class, RAGOrchestrator.class,
            EntityManagerFactory.class, org.quartz.Scheduler.class)) {
            assertThat(context.getBeansOfType(type)).as("No dedicated-service bean of %s", type.getSimpleName()).isEmpty();
        }
        var mappings = context.getBean("requestMappingHandlerMapping", org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class)
            .getHandlerMethods().keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream()).toList();
        assertThat(mappings).contains(ConfigurationAuthoringController.PATH, "/api/admin/provider-budget/initialize");
        assertThat(mappings).noneMatch(path -> path.startsWith("/api/chat") || path.startsWith("/api/data-sync")
            || path.startsWith("/api/actions") || path.startsWith("/api/documents"));
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(503);
        assertThat(get("/api/chat/me/conversations").statusCode()).isEqualTo(404);

        var proof = Path.of("target", "configuration-authoring-boot-proof",
            context.getEnvironment().getProperty("spring.datasource.url", "").startsWith("jdbc:postgresql:") ? "postgres" : "h2");
        Files.createDirectories(proof);
        // Zero interactive turns make this real boot/funding fixture incapable of paid inference.
        var approval = new Approval("authoring-local-boot-proof", budgets.scope(),
            new Limits(0, 100_000, 1_000_000_000L), Instant.parse("2099-01-01T00:00:00Z"));
        var allocation = new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval));
        var writer = mapper.writerWithDefaultPrettyPrinter().without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var initializeJson = writer.writeValueAsString(allocation);
        Files.writeString(proof.resolve("initialize-request.json"), initializeJson);
        var initialize = post("/api/admin/provider-budget/initialize", initializeJson, RuntimeProviderBudgetAdminController.WRITE_SCOPE);
        assertThat(initialize.statusCode()).isEqualTo(200);
        Files.writeString(proof.resolve("initialize-response.json"), initialize.body());
        JsonNode receipt = mapper.readTree(initialize.body());
        var activation = mapper.createObjectNode().put("approvalId", receipt.path("approvalId").asText())
            .put("storeUuid", receipt.path("storeUuid").asText()).put("runtimeFingerprint", receipt.path("runtimeFingerprint").asText());
        Files.writeString(proof.resolve("activate-request.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(activation));
        var activate = post("/api/admin/provider-budget/activate", activation.toString(), RuntimeProviderBudgetAdminController.WRITE_SCOPE);
        assertThat(activate.statusCode()).isEqualTo(200);
        Files.writeString(proof.resolve("activate-response.json"), activate.body());
        var ready = get("/actuator/health/readiness");
        assertThat(ready.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(ready.body()).path("status").asText()).isEqualTo("UP");
        Files.writeString(proof.resolve("readiness.json"), ready.body());
        var denied = post(ConfigurationAuthoringController.PATH,
            mapper.writeValueAsString(AuthoringTestSupport.request()), "configuration:author");
        assertThat(denied.statusCode()).isEqualTo(429);
        assertThat(mapper.readTree(denied.body()).path("error").path("code").asText()).isEqualTo("PROVIDER_BUDGET_LIMIT_EXCEEDED");
        assertThat(budgets.readCurrent().providerAttempts()).isZero();
        assertThat(budgets.readCurrent().chatAttempts()).isZero();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path, String json, String scope) throws Exception {
        var auth = RuntimeAuthContext.builder().subjectId("authoring:session-1:turn-1").sessionId("authoring:session-1:turn-1")
            .subjectType(RuntimeAuthSubjectType.END_USER).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).customerId("loomai-internal")
            .deploymentId("configuration-authoring-local").tenantId("loomai-internal")
            .issuer("platform-authoring-local").audiences(List.of("configuration-authoring-local"))
            .expiresAt(Instant.now().plusSeconds(120)).grantedScopes(List.of(scope)).build();
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json").header("X-AIFABRIC-RUNTIME-API-KEY", "authoring-offline-ingress-secret")
            .header("X-AIFABRIC-RUNTIME-AUTHORIZATION", "Bearer " + assertions.issueAssertion(auth))
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
