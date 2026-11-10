package com.ai.fabric.runtime.tasks;

import static com.ai.fabric.runtime.tasks.ManagedTaskFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import ai.fabric.execution.action.ActionProposalDecision;
import ai.fabric.execution.action.ActionProposalReceiptStatus;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.review.ReviewDecisionGateway;
import ai.fabric.execution.review.TrustedReviewerContext;
import ai.fabric.execution.review.decision.ReviewDecisionRequest;
import ai.fabric.execution.review.decision.ReviewDecisionType;
import ai.fabric.intent.action.AIActionHandler;
import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.AIActionParamSchema;
import ai.fabric.intent.action.AIActionParamType;
import ai.fabric.intent.action.AIActionRegistryContributor;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionPayload;
import ai.fabric.intent.action.ActionResult;
import ai.fabric.intent.action.ActionSideEffectLevel;
import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Explicit opt-in. Real inference; the only write receiver is an in-process fixture. */
@EnabledIfEnvironmentVariable(named = "LOOMAI_RUN_GOVERNED_TASK_REAL_API", matches = "true")
@SpringBootTest(classes = {AIFabricRuntimeApplication.class, ManagedTaskGovernedRealApiIT.ReceiverConfig.class},
    args = "--spring.config.import=classpath:test-runtime-entity-config.yml",
    properties = {
        "spring.datasource.url=jdbc:h2:mem:task-governed-live;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop", "OPENAI_ENABLED=true", "AI_VECTOR_DB_TYPE=memory",
        "AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS=false", "AI_EXECUTION_MANIFESTS_ENABLED=true",
        "ai.execution.capabilities.allowed-actions=update_preference,confirm_preference",
        "ai.execution.receipts.enabled=true", "ai.execution.receipts.repository=IN_MEMORY",
        "ai.execution.receipts.encryption-secret=disposable-test-encryption-secret-32-bytes",
        "ai.execution.receipts.fingerprint-secret=disposable-test-fingerprint-secret-32-bytes",
        "ai.execution.reviews.enabled=true", "ai.execution.reviews.repository=IN_MEMORY",
        "ai.execution.reviews.encryption-secret=disposable-review-encryption-secret-32-bytes",
        "ai.execution.reviews.fingerprint-secret=disposable-review-fingerprint-secret-32-bytes",
        "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=fixture-backend-key",
        "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=fixture-signing-key",
        "AI_FABRIC_RUNTIME_AUTHZ_MODE=ALLOW_VERIFIED",
        "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=backend-test", "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=dep-123",
        "AI_FABRIC_RUNTIME_DEPLOYMENT_ID=dep-123", "AI_FABRIC_RUNTIME_TENANT_ID=tenant-123",
        "AI_FABRIC_RUNTIME_CUSTOMER_ID=customer-123", "PLATFORM_DEPLOYMENT_VERSION_ID=version-123",
        "logging.level.ai.fabric=WARN"
    })
@DirtiesContext
class ManagedTaskGovernedRealApiIT {
    @Autowired ManagedTaskService tasks;
    @Autowired ReviewDecisionGateway reviews;
    @Autowired Receiver receiver;

    @DynamicPropertySource static void published(DynamicPropertyRegistry properties) throws Exception {
        var fixture = taskFixture("save-preference", "update_preference", false);
        for (var extra : List.of(taskFixture("confirm-preference", "confirm_preference", false),
            taskFixture("review-preference", "confirm_preference", true))) {
            ((ArrayNode) fixture.path("resources")).addAll((ArrayNode) extra.path("resources"));
            ((ArrayNode) fixture.path("tasks")).addAll((ArrayNode) extra.path("tasks"));
        }
        byte[] bytes = MAPPER.writeValueAsBytes(fixture);
        properties.add("LOOMAI_MANAGED_TASKS_BUNDLE_BASE64", () -> Base64.getEncoder().encodeToString(bytes));
        properties.add("LOOMAI_MANAGED_TASKS_BUNDLE_HASH", () -> hash(bytes));
    }

    private static ObjectNode taskFixture(String code, String action, boolean review) {
        var fixture = writeFixture();
        var ref = "task-" + code;
        for (var resource : fixture.path("resources")) {
            var metadata = (ObjectNode) resource.path("metadata");
            metadata.put("name", ref + metadata.path("name").asText().substring("task-assess-request".length()));
        }
        var spec = (ObjectNode) resource(fixture, "Specialist").path("spec");
        ((ObjectNode) spec.path("input")).put("schemaRef", ref + "-input@1");
        ((ObjectNode) spec.path("output")).put("schemaRef", ref + "-output@1");
        ((ObjectNode) spec.path("instructions")).put("promptProfileRef", ref + "@1")
            .put("objective", "Update the disposable preference when requested.");
        var actions = (ObjectNode) spec.at("/capabilities/actions");
        actions.putArray("visible").add(action);
        actions.putArray("proposableWrites").add(action);
        ((ObjectNode) resource(fixture, "SpecialistPromptProfile").path("spec")).put("constraints",
            "Read the application input. When asked to change a preference, use the configured " + action
                + " action with the requested value. Respect its confirmation policy. Never claim success without a real action result.");
        var task = (ObjectNode) fixture.at("/tasks/0");
        task.put("code", code).put("description", "Update the disposable preference when requested.").put("specialistRef", ref + "@1");
        task.putArray("writeActions").add(action);
        task.putArray("requiredScopes").add("action:" + action).add("preferences:write").add("specialist:" + ref + "@1");
        if (review) task.put("confirmationMode", "HUMAN_REVIEW");
        return fixture;
    }

    @Test void providerSelectedWritesUseTheRegisteredPolicyAndRealReceipts() {
        assertThat(tasks.ready()).isTrue();
        var direct = run("save-preference", "Save the fixture's preferred colour as teal.");
        assertThat(direct.status()).as("direct result: %s; receipts: %s", direct.failure(), direct.actionExecutions()).isEqualTo("SUCCEEDED");
        assertThat(receiver.writes).containsExactly("teal");
        assertThat(direct.actionExecutions()).singleElement().satisfies(receipt -> {
            assertThat(receipt.actionName()).isEqualTo("update_preference");
            assertThat(receipt.status()).isEqualTo("EXECUTED");
        });
        assertThat(direct.actionProposal()).isNull();

        var pending = run("confirm-preference", "Change the fixture preference to amber.");
        assertThat(pending.status()).as("confirmation result: %s", pending.failure()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(receiver.writes).containsExactly("teal");
        var approved = decide(pending, ActionProposalDecision.CONFIRM);
        assertThat(approved.decision().status()).isEqualTo(ActionProposalReceiptStatus.SUCCEEDED);
        assertThat(approved.actionExecutions()).hasSize(1);
        var replay = decide(pending, ActionProposalDecision.CONFIRM);
        assertThat(replay.decision()).isEqualTo(approved.decision());
        assertThat(receiver.writes).containsExactly("teal", "amber");

        var rejected = run("confirm-preference", "Set the disposable fixture preference to violet.");
        assertThat(rejected.status()).isEqualTo("CONFIRMATION_REQUIRED");
        var outcome = decide(rejected, ActionProposalDecision.REJECT);
        assertThat(outcome.decision().status()).isEqualTo(ActionProposalReceiptStatus.REJECTED);
        assertThat(receiver.writes).containsExactly("teal", "amber");

        var review = run("review-preference", "Please update the fixture preference to indigo.");
        assertThat(review.status()).as("review result: %s", review.failure()).isEqualTo("REVIEW_REQUIRED");
        assertThat(receiver.writes).containsExactly("teal", "amber");
        var reviewer = new TrustedReviewerContext(new ExecutionPrincipal("fixture-reviewer", ExecutionPrincipalType.END_USER),
            "tenant-123", Set.of("review:tasks:decide"), null, Instant.now());
        var decision = reviews.decide(new ReviewDecisionRequest(review.review().taskId(), "fixture-approve",
            ReviewDecisionType.APPROVE, review.review().version(), null), reviewer);
        assertThat(decision.succeeded()).as("review decision: %s", decision.failure()).isTrue();
        assertThat(receiver.writes).containsExactly("teal", "amber", "indigo");
    }

    private ManagedTaskResponse run(String code, String request) {
        return tasks.execute(code, new ManagedTaskRequest(MAPPER.createObjectNode().put("request", request), null),
            identity("task:" + code + ":execute")).getBody();
    }

    private ManagedTaskDecisionResponse decide(ManagedTaskResponse response, ActionProposalDecision decision) {
        return tasks.decide(response.taskCode(), response.actionProposal().receiptId(), new ManagedTaskDecisionRequest(decision),
            identity("task:" + response.taskCode() + ":confirm")).getBody();
    }

    @TestConfiguration
    static class ReceiverConfig {
        @Bean Receiver disposablePreferenceReceiver() { return new Receiver(); }
        @Bean AIActionRegistryContributor disposablePreferenceActions(Receiver receiver) {
            return () -> List.of(receiver.handler("update_preference", false), receiver.handler("confirm_preference", true));
        }
    }

    static final class Receiver {
        final List<String> writes = new CopyOnWriteArrayList<>();
        AIActionHandler handler(String name, boolean confirmation) {
            var metadata = AIActionMetaData.builder().name(name).confirmationRequired(confirmation)
                .displayName("Update fixture preference").description("Save the requested value to the disposable fixture preference.")
                .accessMode(ActionAccessMode.WRITE_ONLY).sideEffectLevel(ActionSideEffectLevel.MUTATING)
                .parameters(Map.of("value", "The requested new preference value"))
                .parameterSchemas(Map.of("value", AIActionParamSchema.builder().name("value")
                    .description("The requested new preference value").type(AIActionParamType.STRING).required(true).build()))
                .requiredParameters(Set.of("value")).build();
            return new AIActionHandler() {
                @Override public AIActionMetaData getActionMetadata() { return metadata; }
                @Override public boolean requiresConfirmation() { return confirmation; }
                @Override public String getConfirmationMessage(Map<String, Object> params, ActionContext context) { return "Save the fixture preference?"; }
                @Override public ActionResult executeAction(Map<String, Object> params, ActionContext context) {
                    writes.add((String) params.get("value"));
                    return ActionResult.builder().success(true).message("Preference saved.")
                        .data(ActionPayload.object(Map.of("saved", true))).build();
                }
            };
        }
    }
}
