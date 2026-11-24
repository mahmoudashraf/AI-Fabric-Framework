package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.gateway.AIExecutionGateway;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.manifest.SpecialistManifestRuntimeStatus;
import ai.fabric.execution.state.DurableExecutionRepository;
import ai.fabric.execution.state.JdbcDurableExecutionRepository;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.action.ActionAccessMode;
import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.web.ConversationHandoverController;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import ai.fabric.core.AICoreService;
import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.intent.orchestration.workflow.WorkflowModelCallScope;
import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport.addPrivateRuntimeHeaders;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = AIFabricRuntimeApplication.class,
    args = "--spring.config.import=classpath:test-runtime-entity-config.yml", properties = {
        "spring.profiles.active=configured-authoring",
        "spring.datasource.url=jdbc:h2:mem:configured-authoring;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop", "OPENAI_ENABLED=false",
        "loomai.provider-budget.enabled=false", "loomai.configuration-assistant.platform-base-url=https://platform.example.test",
        "loomai.runtime.release-id=release-test", "AI_FABRIC_RUNTIME_AUTHZ_MODE=ALLOW_VERIFIED",
        "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=runtime-authoring-test-key",
        "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=runtime-authoring-test-signing-key",
        "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=backend-test", "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=dep-test",
        "ai.execution.async.encryption-secret=workflow-test-encryption-secret-at-least-32-characters",
        "ai.execution.async.fingerprint-secret=workflow-test-fingerprint-secret-at-least-32-characters",
        "loomai.conversation-handover.callback-signing-secret=workflow-test-conversation-owner-secret-at-least-32-characters",
        "loomai.conversation-handover.encryption-secret=workflow-test-conversation-encryption-secret-at-least-32-characters",
        "logging.level.ai.fabric=WARN"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfiguredAuthoringStartupTest {
    @Autowired WorkflowAssistanceBundle bundle;
    @Autowired SpecialistRegistry specialists;
    @Autowired SpecialistManifestRuntimeStatus manifests;
    @Autowired AIActionRegistry actions;
    @Autowired AIExecutionGateway gateway;
    @Autowired DurableExecutionRepository repository;
    @Autowired RuntimeConfiguredWorkflowDispatcher dispatcher;
    @Autowired ConversationHandoverController conversations;
    @Autowired com.ai.fabric.runtime.web.ChatRuntimeController chat;
    @Autowired RuntimeAuthProperties authProperties;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoSpyBean AICoreService core;
    @MockitoSpyBean AuthoringPlatformClient platform;
    @DynamicPropertySource static void published(DynamicPropertyRegistry properties) throws Exception {
        JsonNode fixture = WorkflowAssistanceBundleTest.fixture();
        byte[] bytes = WorkflowAssistanceBundleTest.MAPPER.writeValueAsBytes(fixture.path("compiled"));
        properties.add("LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_BASE64", () -> Base64.getEncoder().encodeToString(bytes));
        properties.add("LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_HASH", () -> fixture.path("contentHash").asText());
    }
    @Test void ordinaryRuntimeLoadsAllPublishedProfilesAndGovernedActionsUsingExistingJdbcState() {
        assertThat(bundle.enabled()).isTrue();
        assertThat(manifests.ready()).as("native manifest diagnostics %s", manifests.diagnostics()).isTrue();
        assertThat(repository).isInstanceOf(JdbcDurableExecutionRepository.class);
        assertThat(gateway).isNotNull(); assertThat(dispatcher).isNotNull();
        for (var profile : bundle.content().path("profiles")) {
            var definition = specialists.require(SpecialistId.parse(profile.path("specialistRef").asText()));
            assertThat(definition.executionProfile().workflowPolicy().enabled()).isTrue();
            assertThat(definition.executionProfile().writeEnabled()).isEqualTo(!"guide".equals(profile.path("id").asText()));
            assertThat(definition.inputAdapter().recordValidatedTurns()).isFalse();
        }
        for (String name : AuthoringPlatformClient.WRITES) {
            var metadata = actions.findMetadata(name).orElseThrow();
            assertThat(metadata.getAccessMode()).isEqualTo(ActionAccessMode.WRITE_ONLY);
            assertThat(metadata.isConfirmationRequired()).isFalse();
            assertThat(metadata.isAnonymousAllowed()).isFalse();
            assertThat(metadata.getParameterSchemas().get("operationId").getVisibility()).isEqualTo("INTERNAL");
        }
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void canonicalTurnUsesConfiguredPlanningAndRealGovernedWritesWithoutDuplicateHistoryOrReplay(boolean completionFeedback) throws Exception {
        String suffix = UUID.randomUUID().toString(); String session = "cas-" + suffix; String turn = "cat-" + suffix;
        String conversation = "authoring-" + suffix;
        var profile = bundle.select("BEHAVIOUR", null);
        var scopes = new ArrayList<>(profile.requiredScopes()); scopes.addAll(List.of("chat:query", "chat:messages", "chat:conversations"));
        var auth = RuntimeAuthContext.builder().subjectId("authoring:" + session).sessionId("authoring:" + session)
            .subjectType(RuntimeAuthSubjectType.END_USER).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).tenantId("tenant-test").customerId("customer-test").deploymentId("dep-test")
            .issuer("backend-test").audiences(List.of("dep-test")).expiresAt(Instant.now().plusSeconds(180)).grantedScopes(scopes).build();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        addPrivateRuntimeHeaders(request, authProperties, auth);
        var decisions = new ArrayList<String>();
        decisions.add("{\"type\":\"ACTION\",\"action\":\"authoring_get_context\",\"parameters\":{}}");
        if (completionFeedback) decisions.addAll(List.of(
            "{\"type\":\"ACTION\",\"action\":\"authoring_check_candidate\",\"parameters\":{\"changes\":[{\"path\":\"/promptConfig/systemPrompt\",\"operation\":\"SET\",\"value\":\"Welcome\"}]}}",
            "{\"type\":\"COMPLETE\",\"message\":\"The draft is saved.\"}"));
        decisions.addAll(List.of(
            "{\"type\":\"ACTION\",\"action\":\"authoring_update_draft\",\"parameters\":{\"operationId\":\"forged\",\"changes\":[{\"path\":\"/promptConfig/systemPrompt\",\"operation\":\"SET\",\"value\":\"Welcome\"}]}}",
            "{\"type\":\"COMPLETE\",\"message\":\"The requested greeting is saved in the draft.\"}"));
        var replies = new ArrayDeque<>(decisions);
        var feedbackObserved = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> {
            ai.fabric.dto.AIGenerationRequest generated = call.getArgument(0);
            assertThat(generated.getSystemPrompt()).contains("APPLICATION-OWNED SPECIALIST CONTRACT",
                specialists.require(profile.specialist()).instructions().render().trim());
            var modelContext = WorkflowAssistanceBundleTest.MAPPER.readTree(generated.getPrompt());
            assertThat(modelContext.path("actions").toString()).contains("authoring_update_draft", "authoring_get_context");
            if ("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED".equals(modelContext.path("previousFinding").asText())) {
                assertThat(modelContext.path("previousFindingDetail").asText()).contains("no later EXECUTED save receipt");
                feedbackObserved.set(true);
            }
            WorkflowModelCallScope.beforeModelCall();
            return AIGenerationResponse.builder().content(replies.remove()).build();
        }).when(core).generateContent(any(), any());
        var actualOperations = new ArrayList<String>();
        doAnswer(call -> {
            String action = call.getArgument(0); Map<String, Object> params = call.getArgument(1);
            var value = WorkflowAssistanceBundleTest.MAPPER.createObjectNode();
            if ("authoring_get_context".equals(action)) value.putObject("configuration").putObject("promptConfig").put("systemPrompt", "Manual");
            else if ("authoring_check_candidate".equals(action))
                value.put("draftSaveRequested", true).put("hasChanges", true).put("mutationFingerprint", "checked-candidate");
            else {
                assertThat(action).isEqualTo("authoring_update_draft");
                assertThat(params.get("operationId")).isEqualTo(turn + ":step:" + (completionFeedback ? 3 : 2));
                actualOperations.add(params.get("operationId").toString());
                value.put("operationId", params.get("operationId").toString()).put("status", "EXECUTED").put("published", false)
                    .put("parameterFingerprint", "checked-candidate");
            }
            return new AuthoringPlatformClient.Reply(200, value);
        }).when(platform).call(anyString(), anyMap(), any());
        var body = new ChatQueryRequest(); body.setQuery("Set the greeting to Welcome.");
        body.setConfigurationWork(new ChatQueryRequest.ConfigurationWork(session, turn, "BEHAVIOUR", profile.id(),
            "release-test", bundle.contentHash(), false, 1));
        var result = conversations.message(conversation, turn, body, request);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).as("canonical result %s", result.getBody()).isTrue();
        assertThat(result.getBody().getAnswer()).contains("saved in the draft");
        assertThat(result.getBody().getMetadata()).containsKey("workflow");
        var publicWork = WorkflowAssistanceBundleTest.MAPPER.valueToTree(result.getBody().getMetadata()).path("workflow");
        assertThat(publicWork.at("/selection/profileId").asText()).isEqualTo(profile.id());
        assertThat(publicWork.at("/selection/name").asText()).isEqualTo(profile.name());
        assertThat(publicWork.at("/limits/modelCalls").asInt()).isEqualTo(16);
        assertThat(publicWork.at("/limits/corrections").asInt()).isEqualTo(1);
        assertThat(actualOperations).containsExactly(turn + ":step:" + (completionFeedback ? 3 : 2));
        assertThat(feedbackObserved.get()).isEqualTo(completionFeedback);
        assertThat(publicWork.at("/usage/corrections").asInt()).isEqualTo(completionFeedback ? 1 : 0);
        assertThat(repository.findById(turn).orElseThrow().protectedWorkflowCheckpoint()).isNotBlank().doesNotContain("Welcome");
        var replay = conversations.message(conversation, turn, body, request);
        assertThat(replay.getBody().getAnswer()).isEqualTo(result.getBody().getAnswer());
        assertThat(actualOperations).hasSize(1);
        assertThat(replies).isEmpty();
        var storedBodies = jdbc.queryForList("SELECT content FROM ai_conversation_message WHERE conversation_id=?", String.class, conversation);
        assertThat(storedBodies).hasSizeGreaterThanOrEqualTo(2);
        assertThat(storedBodies).allSatisfy(content -> assertThat(content).doesNotContain("Welcome", "greeting", "saved in the draft"));
        assertThat(chat.deleteConversation(conversation, "delete-" + turn, request).getStatusCode().value()).isEqualTo(204);
        assertThat(repository.findById(turn)).isEmpty();
        var late = conversations.message(conversation, turn, body, request);
        assertThat(late.getStatusCode().value()).isEqualTo(409);
        assertThat(actualOperations).hasSize(1);
        assertThat(jdbc.queryForList("SELECT content FROM ai_conversation_message WHERE conversation_id=?", String.class, conversation)).isEmpty();
    }
}
