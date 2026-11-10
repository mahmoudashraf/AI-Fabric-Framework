package com.ai.fabric.runtime.tasks;

import static com.ai.fabric.runtime.tasks.ManagedTaskFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.fabric.execution.action.ActionProposalCoordinator;
import ai.fabric.execution.action.ActionOutcomeProjectorRegistry;
import ai.fabric.execution.gateway.AIExecutionResult;
import ai.fabric.execution.gateway.AIExecutionStatus;
import ai.fabric.execution.review.ReviewDecisionGateway;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.specialist.client.SpecialistClient;
import ai.fabric.execution.specialist.client.SpecialistClientFactory;
import ai.fabric.execution.specialist.manifest.SpecialistResourceBundle;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.ActionSideEffectLevel;
import ai.fabric.intent.action.AIActionHandler;
import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.AIActionRegistryContributor;
import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.ai.fabric.runtime.specialist.assistance.SpecialistAssistanceBundle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = {AIFabricRuntimeApplication.class, ManagedTaskReferenceStartupTest.AccountAction.class},
    args = "--spring.config.import=classpath:test-runtime-entity-config.yml",
    properties = {
        "spring.datasource.url=jdbc:h2:mem:task-reference;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop", "OPENAI_ENABLED=false", "ai.providers.openai.enabled=false",
        "AI_VECTOR_DB_TYPE=memory", "AI_EXECUTION_MANIFESTS_ENABLED=true",
        "ai.execution.capabilities.registered-vector-spaces=document,product-guide",
        "ai.execution.capabilities.allowed-actions=get_account_status",
        "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=reference-test-key",
        "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=reference-test-signing-key",
        "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=backend-test", "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=dep-123",
        "AI_FABRIC_RUNTIME_DEPLOYMENT_ID=dep-123", "AI_FABRIC_RUNTIME_TENANT_ID=tenant-123",
        "AI_FABRIC_RUNTIME_CUSTOMER_ID=customer-123", "PLATFORM_DEPLOYMENT_VERSION_ID=version-123",
        "logging.level.ai.fabric=WARN"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManagedTaskReferenceStartupTest {
    @TestConfiguration
    static class AccountAction {
        @Bean
        AIActionRegistryContributor accountStatusContributor() {
            var handler = mock(AIActionHandler.class);
            when(handler.getActionMetadata()).thenReturn(AIActionMetaData.builder()
                .name("get_account_status").accessMode(ActionAccessMode.READ)
                .sideEffectLevel(ActionSideEffectLevel.NONE).build());
            when(handler.executeAction(any(), any())).thenReturn(ActionResult.builder().success(true).build());
            return () -> List.of(handler);
        }
    }

    static final ObjectNode TASKS = fixture("managed-tasks-reference-fixture.json");
    static final ObjectNode ASSISTANCE = fixture("managed-tasks-reference-assistance-fixture.json");
    @Autowired ManagedTasksBundle bundle;
    @Autowired SpecialistAssistanceBundle assistance;
    @Autowired SpecialistRegistry registry;
    @Autowired SpecialistResourceBundle resources;
    @Autowired ManagedTaskService service;

    @DynamicPropertySource static void published(DynamicPropertyRegistry properties) throws Exception {
        var tasks = MAPPER.writeValueAsBytes(TASKS);
        var source = MAPPER.writeValueAsBytes(ASSISTANCE);
        properties.add("LOOMAI_MANAGED_TASKS_BUNDLE_BASE64", () -> Base64.getEncoder().encodeToString(tasks));
        properties.add("LOOMAI_MANAGED_TASKS_BUNDLE_HASH", () -> hash(tasks));
        properties.add("LOOMAI_SPECIALIST_ASSISTANCE_BUNDLE_BASE64", () -> Base64.getEncoder().encodeToString(source));
        properties.add("LOOMAI_SPECIALIST_ASSISTANCE_BUNDLE_HASH", () -> hash(source));
    }

    @Test void referenceUsesTheOneExistingRegistrationAndDirectInputContract() throws Exception {
        assertThat(bundle.resources()).isEmpty();
        assertThat(resources.diagnostics()).isEmpty();
        assertThat(service.ready()).isTrue();
        assertThat(registry.require(SpecialistId.parse("assist-area-account@1"))).isNotNull();
        var factory = mock(SpecialistClientFactory.class);
        @SuppressWarnings("unchecked") SpecialistClient<JsonNode, JsonNode> client = mock(SpecialistClient.class);
        when(factory.bind(eq(SpecialistId.parse("assist-area-account@1")), eq(JsonNode.class), eq(JsonNode.class))).thenReturn(client);
        when(client.execute(any())).thenAnswer(call -> {
            var actual = (ai.fabric.execution.specialist.client.SpecialistInvocation<?>) call.getArgument(0);
            assertThat((JsonNode) actual.input()).isEqualTo(MAPPER.createObjectNode().put("question", "What is the account state?"));
            assertThat(actual.trustedExecutionContext().grantedScopes()).contains("action:get_account_status", "account:read")
                .doesNotContain("vector:product-guide", "admin:all");
            return new AIExecutionResult<>("reference-execution", SpecialistId.parse("assist-area-account@1"), AIExecutionStatus.SUCCEEDED,
                MAPPER.createObjectNode().put("summary", "No live account observation.").put("answer", "The account state was not supplied."),
                List.of(), Map.of(), null, Instant.now(), Instant.now());
        });
        try (var workers = Executors.newSingleThreadExecutor()) {
            var tasks = localService(bundle, factory, workers);
            var result = tasks.execute("check-account", new ManagedTaskRequest(MAPPER.createObjectNode().put("question", "What is the account state?"), null),
                identity("task:check-account:execute", "admin:all"));
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody().output().path("answer").asText()).isEqualTo("The account state was not supplied.");
            verify(client, times(1)).execute(any());
        }
    }

    @Test void changedSourceHashCannotSilentlyBindAReferenceToAnotherSpecialistBundle() {
        var changed = TASKS.deepCopy();
        ((ObjectNode) changed.at("/tasks/0")).put("referenceBundleHash", "sha256:" + "0".repeat(64));
        try (var workers = Executors.newSingleThreadExecutor()) {
            assertThat(localService(bundle(changed), mock(SpecialistClientFactory.class), workers).ready()).isFalse();
        }
    }

    ManagedTaskService localService(ManagedTasksBundle tasks, SpecialistClientFactory factory, java.util.concurrent.ExecutorService workers) {
        return new ManagedTaskService(tasks, provider(SpecialistRegistry.class, registry), provider(SpecialistClientFactory.class, factory),
            provider(RuntimeBudgetTurnScopes.class, null), provider(SpecialistAssistanceBundle.class, assistance),
            provider(ActionProposalCoordinator.class, null), provider(ReviewDecisionGateway.class, null), provider(AIActionRegistry.class, null),
            new ManagedTaskGuard(provider(ActionOutcomeProjectorRegistry.class, null)), MAPPER, workers,
            "dep-123", "tenant-123", "customer-123", "version-123");
    }
}
