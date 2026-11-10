package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real encrypted sessions/transactions and async lifecycle; inference/resource endpoints are explicit fixtures. */
@SpringBootTest(properties = {"platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "platform.assistant.enabled=true", "platform.assistant.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
class ConfigurationAssistantIntegrationTest {
    @Autowired ConfigurationAssistantService assistant;
    @Autowired AuthoringSessionRepository sessions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired ObjectMapper mapper;
    @Autowired AuthoringChanges changes;
    @MockBean AuthoringRuntimeClient runtime;
    @MockBean AuthoringResourceService resources;
    final AtomicReference<JsonNode> configuration = new AtomicReference<>();
    String customerId;
    String resourceId;
    String actor;

    @BeforeEach void prepare() throws Exception {
        customerId = "authoring-test-" + UUID.randomUUID(); resourceId = "dep-" + UUID.randomUUID(); actor = "actor-" + UUID.randomUUID();
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setSlug(customerId); customer.setName("Authoring test");
        customer.setStatus("ACTIVE"); customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now()); customers.saveAndFlush(customer);
        signIn(actor); configuration.set(mapper.readTree("{\"promptConfig\":{\"systemPrompt\":\"Manual\",\"other\":true}}"));
        when(runtime.configured()).thenReturn(true);
        when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"))).thenAnswer(call -> snapshot());
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode().put("publishReady", true));
        when(resources.save(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"), any())).thenAnswer(call -> {
            var result = changes.apply(configuration.get(), call.getArgument(3), List.of("/promptConfig"));
            configuration.set(result.candidate());
            return new AuthoringResourceService.Saved(result.diff(), mapper.createObjectNode().put("publishReady", true),
                mapper.createObjectNode().put("draftId", "draft-test").put("published", false), result.candidate());
        });
        when(resources.undo(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"), any())).thenAnswer(call -> {
            var result = changes.undo(configuration.get(), call.getArgument(3), List.of("/promptConfig"));
            configuration.set(result.candidate());
            return new AuthoringResourceService.Saved(result.diff(), mapper.createObjectNode(),
                mapper.createObjectNode().put("draftId", "draft-test").put("published", false), result.candidate());
        });
        when(runtime.generate(any())).thenReturn(output("READY"));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void requestedAiEditSavesWithoutAcceptanceThenUndoUsesSameDraftPath() throws Exception {
        String id = create();
        assistant.turn(id, request(false), "save-one");
        var state = await(id);
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("SAVED");
        assertThat(state.at("/turns/0/receipt/published").asBoolean()).isFalse();
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("AI result");
        assertThat(configuration.get().at("/promptConfig/other").asBoolean()).isTrue();
        assistant.proposal(id, state.at("/turns/0/id").asText(), "undo");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
    }
    @Test void retryingTheSameTurnDoesNotBillOrSaveTwice() throws Exception {
        String id = create(); assistant.turn(id, request(false), "same-command"); await(id);
        assistant.turn(id, request(false), "same-command");
        verify(runtime, times(1)).generate(any()); verify(resources, times(1)).save(anyString(), anyString(), anyString(), any());
        assertThatThrownBy(() -> assistant.turn(id,
            new ConfigurationAssistantService.TurnRequest("Different request", false, null, null), "same-command"))
            .hasMessageContaining("different request");
    }
    @Test void inferenceReceivesActualCurrentFindingsWithoutLeakingDeclaredSecretValues() throws Exception {
        ((ObjectNode) configuration.get()).putObject("routingConfig").putObject("upstream").putObject("auth")
            .put("value", "private-current-secret");
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode()
            .put("valid", false).put("message", "The saved source mapping is incomplete; private-current-secret must remain private."));
        String id = create(); assistant.turn(id, request(false), "current-findings"); await(id);
        var captured = org.mockito.ArgumentCaptor.forClass(JsonNode.class); verify(runtime).generate(captured.capture());
        assertThat(captured.getValue().at("/catalogue/currentValidation/valid").asBoolean()).isFalse();
        assertThat(captured.getValue().at("/catalogue/currentValidation/message").asText()).contains("saved source mapping", "[REDACTED]");
        assertThat(captured.getValue().toString()).doesNotContain("private-current-secret");
    }
    @Test void aCheckAuthorizationFailureStopsBeforeInference() throws Exception {
        when(resources.check(anyString(), anyString(), any(), any())).thenThrow(
            new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Access revoked"));
        String id = create(); assistant.turn(id, request(false), "revoked-at-check");
        assertThat(await(id).at("/turns/0/status").asText()).isEqualTo("FAILED");
        verify(runtime, never()).generate(any());
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void previewRequiresExplicitApplyButNormalConfigurationDoesNot() throws Exception {
        String id = create(); assistant.turn(id, request(true), "preview-one"); var preview = await(id);
        assertThat(preview.at("/turns/0/status").asText()).isEqualTo("PREVIEW");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
        assistant.proposal(id, preview.at("/turns/0/id").asText(), "apply");
        assertThat(assistant.get(id).at("/turns/0/status").asText()).isEqualTo("SAVED");
    }
    @Test void sourceAndQueryAreEncryptedAndDeclaredCredentialsAreRedacted() throws Exception {
        String id = create();
        assistant.addSource(id, new ConfigurationAssistantService.SourceRequest("API notes", "text/plain",
            "Private example\nAuthorization: Bearer actual-credential", null, null));
        var stored = sessions.findById(id).orElseThrow();
        assertThat(stored.encryptedContent).doesNotContain("Private example", "actual-credential");
        assertThat(assistant.get(id).path("sources").get(0).has("content")).isFalse();
        assistant.turn(id, new ConfigurationAssistantService.TurnRequest("Use the imported documentation", false, null, null), "with-document"); await(id);
        var captured = org.mockito.ArgumentCaptor.forClass(JsonNode.class); verify(runtime).generate(captured.capture());
        assertThat(captured.getValue().toString()).doesNotContain("actual-credential").contains("[REDACTED]");
    }
    @Test void deselectingAllSourcesReallySendsNoDocuments() throws Exception {
        String id = create();
        assistant.addSource(id, new ConfigurationAssistantService.SourceRequest("Not selected", "text/plain", "Private unselected notes", null, null));
        assistant.turn(id, request(false), "no-document"); await(id);
        var captured = org.mockito.ArgumentCaptor.forClass(JsonNode.class); verify(runtime).generate(captured.capture());
        assertThat(captured.getValue().path("sources")).isEmpty();
        assertThat(captured.getValue().toString()).doesNotContain("Private unselected notes");
    }
    @Test void anotherActorCannotReadEditCancelOrDeleteTheSession() {
        String id = create(); signIn("another-actor");
        assertThatThrownBy(() -> assistant.get(id)).hasMessageContaining("404");
        assertThatThrownBy(() -> assistant.cancel(id)).hasMessageContaining("404");
        assertThatThrownBy(() -> assistant.delete(id)).hasMessageContaining("404");
        assertThatThrownBy(() -> assistant.turn(id, request(false), "cross-actor")).hasMessageContaining("404");
        verify(runtime, never()).generate(any());
    }
    @Test void cancelledLateInferenceCannotSaveTheDraft() throws Exception {
        var started = new CountDownLatch(1); var finish = new CountDownLatch(1);
        when(runtime.generate(any())).thenAnswer(call -> { started.countDown(); finish.await(3, TimeUnit.SECONDS); return output("READY"); });
        String id = create(); assistant.turn(id, request(false), "cancel-one");
        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> assistant.cancel(id, "older-turn")).hasMessageContaining("newer assistant turn");
        assertThat(assistant.get(id).path("status").asText()).isEqualTo("RUNNING");
        assistant.cancel(id, assistant.get(id).path("activeTurnId").asText()); finish.countDown(); Thread.sleep(80);
        assertThat(assistant.get(id).at("/turns/0/status").asText()).isEqualTo("CANCELLED");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void reusesOnlyAuthorizedSameWorkspaceSourcesWithoutSharingThemAutomatically() throws Exception {
        String first = create();
        assistant.addSource(first, new ConfigurationAssistantService.SourceRequest("Shared API", "text/plain", "Authorized API notes", null, null));
        when(resources.load("DEPLOYMENT_DRAFT", "another-resource", "BEHAVIOUR")).thenAnswer(call -> snapshot());
        String second = assistant.create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", "another-resource", "BEHAVIOUR")).path("id").asText();
        assertThat(assistant.reusableSources(second)).hasSize(1);
        String sourceId = assistant.get(first).at("/sources/0/id").asText();
        assistant.reuseSources(second, new ConfigurationAssistantService.ReuseSources(first, List.of(sourceId)));
        assertThat(assistant.sourceContent(second, sourceId).path("content").asText()).contains("Authorized API notes");
        assistant.delete(first);
        assertThat(assistant.get(second).path("sources")).hasSize(1);
        signIn("another-actor");
        assertThatThrownBy(() -> assistant.sourceContent(second, sourceId)).hasMessageContaining("404");
    }
    @Test void malformedOrUnauthorizedOutputIsVisibleAndDoesNotSave() throws Exception {
        var invalid = output("READY"); ((ObjectNode) invalid.at("/changes/0")).put("path", "/providerConfig/apiKey");
        when(runtime.generate(any())).thenReturn(invalid);
        String id = create(); assistant.turn(id, request(false), "invalid-one");
        var result = await(id);
        assertThat(result.at("/turns/0/status").asText()).isEqualTo("FAILED");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void validationFailureRetainsGeneratedProposalAndChargedRuntimeWithoutChangingTheSavedDraft() throws Exception {
        assertGeneratedFailureRetained(false);
    }
    @Test void saveFailureRetainsGeneratedProposalAndChargedRuntimeWithoutClaimingItWasSaved() throws Exception {
        assertGeneratedFailureRetained(true);
    }
    private void assertGeneratedFailureRetained(boolean failSave) throws Exception {
        var generated = output("READY");
        generated.putArray("questions").add("Which profile should this use?");
        generated.putArray("assumptions").add("Keep existing unrelated behaviour settings.");
        generated.putArray("sourceReferences").addObject().put("sourceId", "source-fixture").put("operationId", "wording");
        generated.putObject("diagnostics").put("providerResponse", "HTTP_200").put("apiKey", "must-not-be-disclosed");
        var runtimeReceipt = generated.putObject("runtime").put("requestId", "paid-generation")
            .put("modelCalls", 1).put("accountingPath", "RUNTIME_PROVIDER_BUDGET").put("providerStatus", "SUCCESS");
        runtimeReceipt.putObject("usage").put("promptTokens", 123).put("completionTokens", 57);
        runtimeReceipt.putObject("accountingReceipt").put("newlyCounted", true).put("eventId", "charged-event");
        when(runtime.generate(any())).thenReturn(generated);
        String error = failSave ? "The draft could not be saved." : "AI behaviour tone requires a presetId and instructions string.";
        var failure = new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, error);
        if (failSave) when(resources.save(anyString(), anyString(), anyString(), any())).thenThrow(failure);
        else when(resources.check(anyString(), anyString(), any(), any())).thenThrow(failure);
        var before = configuration.get().deepCopy();
        String id = create();
        String key = failSave ? "save-failure" : "check-failure";
        assistant.turn(id, request(false), key);
        var state = await(id);
        var turn = state.at("/turns/0");
        assertThat(turn.path("status").asText()).isEqualTo("FAILED");
        assertThat(turn.path("message").asText()).isEqualTo(error);
        assertThat(turn.path("notice").asText()).contains("proposal was not saved", "unverified", "last saved draft is unchanged");
        assertThat(turn.path("unverifiedModelMessage")).isEqualTo(generated.path("message"));
        for (String field : List.of("changes", "questions", "assumptions", "sourceReferences", "runtime"))
            assertThat(turn.path(field)).as(field).isEqualTo(generated.path(field));
        assertThat(turn.at("/diagnostics/providerResponse").asText()).isEqualTo("HTTP_200");
        assertThat(turn.at("/diagnostics/apiKey").asText()).isEqualTo("[REDACTED]");
        assertThat(state.toString()).doesNotContain("must-not-be-disclosed");
        assertThat(turn.has("receipt")).isFalse();
        assertThat(turn.has("diff")).isFalse();
        assertThat(configuration.get()).isEqualTo(before);
        assertThat(state.path("status").asText()).isEqualTo("IDLE");
        assertThat(assistant.get(id).at("/turns/0")).isEqualTo(turn);
        verify(resources, times(failSave ? 1 : 0)).save(anyString(), anyString(), anyString(), any());
        assistant.turn(id, request(false), key);
        verify(runtime, times(1)).generate(any());
        verify(resources, times(failSave ? 1 : 0)).save(anyString(), anyString(), anyString(), any());
    }
    @Test void deletingSessionErasesSourcesAndDoesNotUndoSavedConfiguration() throws Exception {
        String id = create(); assistant.turn(id, request(false), "save-before-delete"); await(id); assistant.delete(id);
        assertThat(sessions.findById(id)).isEmpty();
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("AI result");
    }
    @Test void explicitFeedbackUsesRealFindingsAndCandidateThenSavesOnceWithOneUndo() throws Exception {
        validationFixture();
        var first = feedbackOutput("Invalid draft", "attempt-one");
        first.withArray("changes").addObject().put("path", "/promptConfig/newField").put("operation", "SET").put("value", "Keep this addition");
        when(runtime.generate(any())).thenReturn(first, feedbackOutput("Corrected draft", "attempt-two"));
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "feedback-success");
        var state = await(id); var turn = state.at("/turns/0");
        assertThat(turn.path("status").asText()).isEqualTo("SAVED");
        assertThat(turn.path("feedbackStopReason").asText()).isEqualTo("VALIDATION_PASSED");
        assertThat(turn.path("attempts")).hasSize(2);
        assertThat(turn.at("/attempts/0/status").asText()).isEqualTo("VALIDATION_ERRORS");
        assertThat(turn.at("/attempts/1/status").asText()).isEqualTo("CHECKED");
        assertThat(turn.at("/attempts/0/runtime/requestId").asText()).isEqualTo("attempt-one");
        assertThat(turn.at("/attempts/1/runtime/requestId").asText()).isEqualTo("attempt-two");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Corrected draft");
        assertThat(configuration.get().at("/promptConfig/newField").asText()).isEqualTo("Keep this addition");
        var inputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
        verify(runtime, times(2)).generate(inputs.capture());
        var next = inputs.getAllValues().get(1);
        assertThat(next.at("/currentConfiguration/promptConfig/systemPrompt").asText()).isEqualTo("Invalid draft");
        assertThat(next.at("/catalogue/validationFeedback/previousFindings/valid").asBoolean()).isFalse();
        assertThat(next.path("query").asText()).isEqualTo("Make the requested wording change");
        assertThat(next.path("turnId").asText()).endsWith("-attempt-2");
        assertThat(next.at("/catalogue/validationFeedback/deadlineAt").asText()).isNotBlank();
        verify(resources, times(1)).save(anyString(), anyString(), anyString(), any());
        assistant.proposal(id, turn.path("id").asText(), "undo");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
        assertThat(configuration.get().at("/promptConfig/newField").isMissingNode()).isTrue();
    }
    @Test void warningsDoNotStartCorrectionAndMissingPolicyIsSingleAttempt() throws Exception {
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode()
            .put("publishReady", true).put("errorCount", 0).put("warningCount", 1));
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "warnings-only");
        assertThat(await(id).at("/turns/0/attempts")).hasSize(1);
        verify(runtime, times(1)).generate(any());
        validationFixture(); when(runtime.generate(any())).thenReturn(feedbackOutput("Invalid draft", "single"));
        assistant.turn(id, request(false), "no-policy");
        var turn = await(id).at("/turns/1");
        assertThat(turn.path("attempts")).hasSize(1);
        assertThat(turn.path("feedbackStopReason").asText()).isEqualTo("DISABLED");
        assertThat(turn.path("findings").path("valid").asBoolean()).isFalse();
        verify(runtime, times(2)).generate(any());
    }
    @Test void correctionLimitStopsAndRetainsEveryInvalidCandidateFinding() throws Exception {
        validationFixture(); when(runtime.generate(any())).thenReturn(feedbackOutput("Invalid draft", "paid-attempt"));
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "exhausted");
        var turn = await(id).at("/turns/0");
        assertThat(turn.path("status").asText()).isEqualTo("SAVED");
        assertThat(turn.path("feedbackStopReason").asText()).isEqualTo("CORRECTION_LIMIT_REACHED");
        assertThat(turn.path("attempts")).hasSize(3);
        assertThat(turn.at("/findings/valid").asBoolean()).isFalse();
        for (var attempt : turn.path("attempts")) assertThat(attempt.path("findings").path("valid").asBoolean()).isFalse();
        verify(runtime, times(3)).generate(any()); verify(resources, times(1)).save(anyString(), anyString(), anyString(), any());
    }
    @Test void needsInputDuringCorrectionStopsWithoutSavingEarlierCandidate() throws Exception {
        validationFixture(); var needsInput = feedbackOutput("Ignored", "ask-user");
        needsInput.put("status", "NEEDS_INPUT"); needsInput.putArray("changes");
        needsInput.putArray("questions").addObject().put("id", "binding").put("question", "Which authorized connection should I use?");
        when(runtime.generate(any())).thenReturn(feedbackOutput("Invalid draft", "first"), needsInput);
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "needs-information");
        var turn = await(id).at("/turns/0");
        assertThat(turn.path("status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(turn.path("feedbackStopReason").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(turn.path("attempts")).hasSize(2);
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void needsInputWithPartialChangesRetainsProposalWithoutSavingWhenFeedbackIsEnabled() throws Exception {
        var needsInput = feedbackOutput("Proposed, not saved", "partial-needs-input");
        needsInput.put("status", "NEEDS_INPUT");
        needsInput.putArray("questions").addObject().put("id", "binding").put("question", "Which authorized connection should I use?");
        when(runtime.generate(any())).thenReturn(needsInput);
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "partial-needs-information");
        var turn = await(id).at("/turns/0");
        assertThat(turn.path("status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(turn.path("attempts")).hasSize(1);
        assertThat(turn.at("/attempts/0/changes")).hasSize(1);
        assertThat(turn.path("notice").asText()).contains("no draft change was saved");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
        verify(runtime, times(1)).generate(any());
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void previewCorrectionAndRepeatedTurnKeyDoNotMutateOrChargeAgain() throws Exception {
        validationFixture(); when(runtime.generate(any())).thenReturn(feedbackOutput("Invalid draft", "one"), feedbackOutput("Corrected draft", "two"));
        String id = create(); var request = feedbackRequest(true, 2);
        assistant.turn(id, request, "same-feedback"); var state = await(id);
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("PREVIEW");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
        assistant.turn(id, request, "same-feedback"); verify(runtime, times(2)).generate(any());
        assistant.proposal(id, state.at("/turns/0/id").asText(), "apply");
        assertThat(assistant.get(id).at("/turns/0/status").asText()).isEqualTo("SAVED");
        verify(resources, times(1)).save(anyString(), anyString(), anyString(), any());
    }
    @Test void onlyTheTypedInvalidOutputCategoryMayRegenerateNotProviderOrBudgetFailures() throws Exception {
        var receipt = mapper.createObjectNode().put("requestId", "invalid-json-paid").put("modelCalls", 1);
        when(runtime.generate(any())).thenThrow(new AuthoringRuntimeClient.AuthoringRuntimeFailure("AUTHORING_INVALID_OUTPUT", receipt))
            .thenReturn(feedbackOutput("Corrected draft", "valid-json-paid"));
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "invalid-json");
        var turn = await(id).at("/turns/0");
        assertThat(turn.path("status").asText()).isEqualTo("SAVED");
        assertThat(turn.at("/attempts/0/status").asText()).isEqualTo("INVALID_OUTPUT");
        assertThat(turn.at("/attempts/0/runtime/requestId").asText()).isEqualTo("invalid-json-paid");
        var inputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class); verify(runtime, times(2)).generate(inputs.capture());
        assertThat(inputs.getAllValues().get(1).at("/catalogue/validationFeedback/previousFindings/issues/0/code").asText())
            .isEqualTo("AUTHORING_INVALID_OUTPUT");
        reset(runtime); when(runtime.configured()).thenReturn(true);
        when(runtime.generate(any())).thenThrow(new AuthoringRuntimeClient.AuthoringRuntimeFailure("PROVIDER_BUDGET_INTERRUPTED", receipt));
        assistant.turn(id, feedbackRequest(false, 2), "budget-stops");
        assertThat(await(id).at("/turns/1/status").asText()).isEqualTo("FAILED");
        verify(runtime, times(1)).generate(any());
    }
    @Test void unauthorizedChangeStopsWithoutUsingCorrectionToExpandItsScope() throws Exception {
        var invalid = feedbackOutput("Invalid draft", "unauthorized");
        ((ObjectNode) invalid.at("/changes/0")).put("path", "/providerConfig/apiKey");
        when(runtime.generate(any())).thenReturn(invalid);
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "outside-scope");
        assertThat(await(id).at("/turns/0/status").asText()).isEqualTo("FAILED");
        verify(runtime, times(1)).generate(any()); verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void cancelDuringSecondAttemptPreventsAnyDraftSave() throws Exception {
        validationFixture(); var secondStarted = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        when(runtime.generate(any())).thenAnswer(call -> {
            if (counter.incrementAndGet() == 1) return feedbackOutput("Invalid draft", "one");
            secondStarted.countDown(); finish.await(3, TimeUnit.SECONDS); return feedbackOutput("Corrected draft", "two");
        });
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "cancel-correction");
        assertThat(secondStarted.await(3, TimeUnit.SECONDS)).isTrue();
        assistant.cancel(id); finish.countDown(); Thread.sleep(100);
        assertThat(assistant.get(id).at("/turns/0/status").asText()).isEqualTo("CANCELLED");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void cancelledInvalidOutputRetainsPaidReceiptWithoutRetryingOrSaving() throws Exception {
        var started = new CountDownLatch(1); var finish = new CountDownLatch(1);
        var receipt = mapper.createObjectNode().put("requestId", "cancelled-invalid-json-paid").put("modelCalls", 1);
        when(runtime.generate(any())).thenAnswer(call -> {
            started.countDown();
            try { finish.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException cancelled) { /* The provider's paid receipt may arrive after cancellation. */ }
            throw new AuthoringRuntimeClient.AuthoringRuntimeFailure("AUTHORING_INVALID_OUTPUT", receipt);
        });
        String id = create(); assistant.turn(id, feedbackRequest(false, 2), "cancel-invalid-output");
        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
        assistant.cancel(id); finish.countDown();
        JsonNode turn = null;
        for (int i = 0; i < 160; i++) {
            turn = assistant.get(id).at("/turns/0");
            if (turn.at("/attempts/0/runtime/requestId").asText().equals("cancelled-invalid-json-paid")) break;
            Thread.sleep(25);
        }
        assertThat(turn.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(turn.at("/attempts/0/status").asText()).isEqualTo("CANCELLED");
        assertThat(turn.at("/attempts/0/runtime/requestId").asText()).isEqualTo("cancelled-invalid-json-paid");
        verify(runtime, times(1)).generate(any());
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void excessiveCorrectionBudgetIsRejectedBeforeGeneration() {
        String id = create();
        assertThatThrownBy(() -> assistant.turn(id, feedbackRequest(false, 3), "too-many"))
            .hasMessageContaining("between one and 2");
        verify(runtime, never()).generate(any());
        assertThat(assistant.get(id).path("turns")).isEmpty();
    }
    private ConfigurationAssistantService.TurnRequest feedbackRequest(boolean preview, int maximum) {
        return new ConfigurationAssistantService.TurnRequest("Make the requested wording change", preview, List.of(), List.of(),
            new AuthoringFeedbackPolicy(true, maximum));
    }
    private ObjectNode feedbackOutput(String value, String requestId) throws Exception {
        var result = output("READY"); ((ObjectNode) result.at("/changes/0")).put("value", value);
        result.putObject("runtime").put("requestId", requestId).put("modelCalls", 1);
        return result;
    }
    private JsonNode checkCandidate(JsonNode candidate) {
        boolean valid = !"Invalid draft".equals(candidate.at("/promptConfig/systemPrompt").asText());
        var result = mapper.createObjectNode().put("valid", valid).put("errorCount", valid ? 0 : 1).put("warningCount", 0);
        if (!valid) result.putArray("issues").addObject().put("severity", "ERROR").put("code", "PROFILE_CONTRACT_INVALID")
            .put("path", "/promptConfig/systemPrompt").put("message", "This fixture requires the corrected profile contract.");
        return result;
    }
    private void validationFixture() {
        when(resources.check(anyString(), anyString(), any(), any())).thenAnswer(call -> checkCandidate(call.getArgument(3)));
        when(resources.save(eq("DEPLOYMENT_DRAFT"), eq(resourceId), eq("BEHAVIOUR"), any())).thenAnswer(call -> {
            var result = changes.apply(configuration.get(), call.getArgument(3), List.of("/promptConfig"));
            configuration.set(result.candidate());
            return new AuthoringResourceService.Saved(result.diff(), checkCandidate(result.candidate()),
                mapper.createObjectNode().put("draftId", "draft-test").put("published", false), result.candidate());
        });
    }
    private String create() { return assistant.create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", resourceId, "BEHAVIOUR")).path("id").asText(); }
    private ConfigurationAssistantService.TurnRequest request(boolean preview) { return new ConfigurationAssistantService.TurnRequest("Make the requested wording change", preview, List.of(), List.of()); }
    private AuthoringResourceService.Snapshot snapshot() {
        return new AuthoringResourceService.Snapshot(customerId, "Test Brain", configuration.get(), mapper.createObjectNode(), List.of("/promptConfig"), "draft-test", true, "");
    }
    private ObjectNode output(String status) throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"contractVersion":"LOOMAI_CONFIGURATION_AUTHORING_V1","status":"%s","message":"Updated the requested draft wording.",
             "changes":[{"path":"/promptConfig/systemPrompt","operation":"SET","value":"AI result"}],"questions":[],"assumptions":[],"sourceReferences":[]}
            """.formatted(status));
    }
    private JsonNode await(String id) throws Exception {
        for (int i = 0; i < 160; i++) { var state = assistant.get(id); if (!"RUNNING".equals(state.path("status").asText())) return state; Thread.sleep(25); }
        throw new AssertionError("Assistant turn did not finish");
    }
    private void signIn(String id) {
        var auth = new UsernamePasswordAuthenticationToken(new PlatformPrincipal(id, PlatformRole.PLATFORM_ADMIN, "Fixture", "SESSION"), null,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth); SecurityContextHolder.setContext(context);
    }
}
