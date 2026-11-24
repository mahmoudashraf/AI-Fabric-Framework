package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** Real encrypted facade/receipts; canonical runtime and resource receivers are explicit test fixtures. */
@SpringBootTest(properties = {"platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "platform.assistant.enabled=true", "platform.assistant.configured-brain-enabled=true",
    "platform.assistant.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
class ConfigurationAssistantIntegrationTest {
    @Autowired ConfigurationAssistantService assistant;
    @Autowired AuthoringDraftOperations operations;
    @Autowired AuthoringSessionRepository sessions;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformUserRepository users;
    @Autowired AuthoringChanges changes;
    @Autowired ObjectMapper mapper;
    @MockBean AuthoringBrainBindingService bindings;
    @MockBean ConfiguredAuthoringRuntimeClient runtime;
    @MockBean AuthoringResourceService resources;
    @MockBean AuthoringDraftLock draftLock;
    String customerId, resourceId, userId, actor, sessionId;
    AtomicReference<ObjectNode> configuration = new AtomicReference<>();
    @BeforeEach void prepare() throws Exception {
        String suffix = UUID.randomUUID().toString(); customerId = "customer-" + suffix; resourceId = "dep-" + suffix;
        userId = "user-" + suffix; actor = suffix + "@example.test";
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setSlug(customerId); customer.setName("Fixture");
        customer.setStatus("ACTIVE"); customer.setCreatedAt(Instant.now()); customer.setUpdatedAt(Instant.now()); customers.saveAndFlush(customer);
        var user = new PlatformUserEntity(); user.setId(userId); user.setEmail(actor); user.setDisplayName("Fixture author");
        user.setRole("PLATFORM_ADMIN"); user.setStatus("ACTIVE"); user.setPasswordHash("not-a-login-credential");
        user.setCreatedAt(Instant.now()); user.setUpdatedAt(Instant.now()); users.saveAndFlush(user); signIn(actor);
        configuration.set((ObjectNode) mapper.readTree("{\"promptConfig\":{\"systemPrompt\":\"Manual\",\"unrelated\":true},\"actionsConfig\":{\"actions\":[]}}"));
        var scopes = new ArrayList<>(List.of("configuration:author"));
        AuthoringNativeCapabilities.READS.forEach(action -> scopes.add("action:" + action));
        AuthoringNativeCapabilities.WRITES.forEach(action -> scopes.add("action:" + action));
        when(bindings.resolve(anyString())).thenReturn(new AuthoringBrainBindingService.Binding("assistant-brain", "release", "https://runtime.example.test", "responses",
            "responses@1", "sha256:fixture", 3, 2, List.copyOf(scopes)));
        when(bindings.conversationBinding()).thenAnswer(call -> bindings.resolve("CONFIGURATION"));
        when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString())).thenAnswer(call -> snapshot(call.getArgument(2)));
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode().put("valid", true).put("publishReady", true));
        when(resources.save(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString(), any())).thenAnswer(call -> {
            var result = changes.apply(configuration.get(), call.getArgument(3), snapshot(call.getArgument(2)).allowedPaths());
            configuration.set(result.candidate()); return saved(result);
        });
        when(resources.undo(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString(), any())).thenAnswer(call -> {
            var result = changes.undo(configuration.get(), call.getArgument(3), snapshot(call.getArgument(2)).allowedPaths());
            configuration.set(result.candidate()); return saved(result);
        });
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any()))
            .thenAnswer(call -> {
                if (call.getArgument(1, String.class) == null || call.getArgument(1, String.class).isBlank()) return null;
                var caller = caller(call.getArgument(1), call.getArgument(3)); operations.context(caller);
                if (call.getArgument(6, Boolean.class)) operations.check(caller, change("AI result"));
                else operations.save(caller, call.getArgument(3) + ":step:2", change("AI result"));
                return response("COMPLETE");
            });
    }
    @AfterEach void cleanup() {
        if (sessionId != null) { try { assistant.cancel(sessionId); } catch (Exception ignored) { } sessions.findById(sessionId).ifPresent(sessions::delete); }
        SecurityContextHolder.clearContext(); users.deleteById(userId); customers.deleteById(customerId);
    }
    void signIn(String who) {
        var principal = new PlatformPrincipal(who, PlatformRole.PLATFORM_ADMIN, "Fixture author", "SESSION", userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))));
    }
    AuthoringResourceService.Snapshot snapshot(String task) {
        return new AuthoringResourceService.Snapshot(customerId, "Fixture", configuration.get(), mapper.createObjectNode(),
            "BRAIN_SETUP".equals(task) ? List.of("/actionsConfig", "/promptConfig")
                : List.of("ACTIONS".equals(task) ? "/actionsConfig" : "/promptConfig"), "draft", true, "");
    }
    AuthoringResourceService.Saved saved(AuthoringChanges.Result result) {
        return new AuthoringResourceService.Saved(result.diff(), mapper.createObjectNode().put("valid", true),
            mapper.createObjectNode().put("draftId", "draft").put("published", false), result.candidate());
    }
    AuthoringDraftOperations.Caller caller(String id, String turn) { return new AuthoringDraftOperations.Caller(id, turn, "assistant-brain", "release"); }
    JsonNode change(String value) { return mapper.createArrayNode().add(mapper.createObjectNode().put("operation", "SET").put("path", "/promptConfig/systemPrompt").put("value", value)); }
    JsonNode response(String state) {
        var result = mapper.createObjectNode().put("success", true).put("answer", "Fixture model message");
        result.putObject("metadata").putObject("workflow").put("state", state).putArray("questions").add("Which credential reference should be used?");
        return result;
    }
    String create(String task) { return sessionId = assistant.create(new ConfigurationAssistantService.CreateSession("DEPLOYMENT_DRAFT", resourceId, task)).path("id").asText(); }
    ConfigurationAssistantService.TurnRequest request(boolean preview, Boolean feedback) {
        return new ConfigurationAssistantService.TurnRequest("Set a welcoming greeting.", preview, List.of(), List.of(),
            new AuthoringFeedbackPolicy(Boolean.TRUE.equals(feedback), Boolean.TRUE.equals(feedback) ? 2 : 0), null, null);
    }
    JsonNode await() throws Exception {
        for (int i = 0; i < 500; i++) { var state = assistant.get(sessionId); if (!"RUNNING".equals(state.path("status").asText())) return state; Thread.sleep(10); }
        throw new AssertionError("Assistant fixture did not finish");
    }
    @Test void requestedEditUsesOneCanonicalTurnAndActualReceiptWithUndo() throws Exception {
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "save"); var state = await();
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("SAVED"); assertThat(state.at("/turns/0/operations")).hasSize(1);
        assertThat(state.toString()).doesNotContain("brainWork", "principal", "passwordHash");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("AI result");
        assertThat(configuration.get().at("/promptConfig/unrelated").asBoolean()).isTrue();
        assistant.turn(sessionId, request(false, true), "save");
        verify(runtime, times(1)).message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any());
        assistant.proposal(sessionId, state.at("/turns/0/id").asText(), "undo");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void needsInputStopsForBothCorrectionPreferences(boolean feedback) throws Exception {
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenReturn(response("NEEDS_INPUT"));
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, feedback), "need-input"); var state = await();
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(state.at("/turns/0/questions/0/question").asText()).contains("credential reference");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
        verify(runtime, times(1)).message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any());
    }
    @Test void previewRemainsUnsavedUntilExplicitApplyAndThenCanBeUndone() throws Exception {
        create("BEHAVIOUR"); assistant.turn(sessionId, request(true, true), "preview"); var state = await();
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("PREVIEW"); verify(resources, never()).save(anyString(), anyString(), anyString(), any());
        String turn = state.at("/turns/0/id").asText(); assistant.proposal(sessionId, turn, "apply");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("AI result");
        assistant.proposal(sessionId, turn, "undo"); assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void unfinishedPreviewDoesNotBecomeAnApplicableCandidate(boolean feedback) throws Exception {
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            operations.check(caller(call.getArgument(1), call.getArgument(3)), change("Incomplete preview"));
            return response("NEEDS_INPUT");
        });
        create("BEHAVIOUR"); assistant.turn(sessionId, request(true, feedback), "preview-needs-input"); var state = await();
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(state.at("/turns/0/questions")).isNotEmpty();
        assertThatThrownBy(() -> assistant.proposal(sessionId, state.at("/turns/0/id").asText(), "apply"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void narrationFailureRetainsSavedEffectsAndNeverRetriesTransport() throws Exception {
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            operations.save(caller(call.getArgument(1), call.getArgument(3)), call.getArgument(3) + ":step:1", change("Saved before failure"));
            throw new IllegalStateException("Fixture provider failure");
        });
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "partial"); var state = await();
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("FAILED"); assertThat(state.at("/turns/0/operations")).hasSize(1);
        assertThat(state.at("/turns/0/notice").asText()).contains("saved before");
        assistant.proposal(sessionId, state.at("/turns/0/id").asText(), "undo"); assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
    }
    @Test void twoGenuineWritesRemainIndividuallyVisibleAndGroupUndoReversesBoth() throws Exception {
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            var who = caller(call.getArgument(1), call.getArgument(3));
            operations.save(who, call.getArgument(3) + ":step:1", change("First"));
            operations.save(who, call.getArgument(3) + ":step:2", change("Corrected")); return response("COMPLETE");
        });
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "two-writes"); var state = await();
        assertThat(state.at("/turns/0/operations")).hasSize(2);
        assistant.proposal(sessionId, state.at("/turns/0/id").asText(), "undo");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
    }
    @Test void explicitlySelectedWholeBrainSetupCanCoordinateAreasWithoutChangingAnotherResource() throws Exception {
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            assertThat(call.getArgument(4, String.class)).isEqualTo("BRAIN_SETUP");
            var who = caller(call.getArgument(1), call.getArgument(3));
            operations.save(who, call.getArgument(3) + ":step:1", change("Consultative assistant"));
            var action = mapper.createArrayNode().add(mapper.createObjectNode().put("operation", "UPSERT")
                .put("path", "/actionsConfig/actions").put("key", "read_products")
                .set("value", mapper.createObjectNode().put("name", "read_products").put("accessMode", "READ")));
            operations.save(who, call.getArgument(3) + ":step:2", action); return response("COMPLETE");
        });
        create("BRAIN_SETUP"); assistant.turn(sessionId, request(false, true), "whole-brain"); var state = await();
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("SAVED");
        assertThat(state.at("/turns/0/operations")).hasSize(2);
        assertThat(configuration.get().at("/actionsConfig/actions/0/name").asText()).isEqualTo("read_products");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Consultative assistant");
        assertThat(configuration.get().at("/promptConfig/unrelated").asBoolean()).isTrue();
    }
    @Test void readOnlyResourceAutomaticallyUsesNonmutatingPreview() throws Exception {
        when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString())).thenAnswer(call -> {
            var current = snapshot(call.getArgument(2));
            return new AuthoringResourceService.Snapshot(current.customerId(), current.displayName(), current.configuration(), current.catalogue(),
                current.allowedPaths(), current.version(), false, "This active resource is preview-only.");
        });
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "readonly"); var state = await();
        assertThat(state.at("/turns/0/status").asText()).isEqualTo("PREVIEW");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void cancellationBlocksALateWriteAndInterruptsTheCanonicalTurn() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1); var finished = new java.util.concurrent.CountDownLatch(1);
        var rejected = new AtomicBoolean();
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            entered.countDown();
            try { Thread.sleep(10000); } catch (InterruptedException expectedCancellation) { }
            try { operations.save(caller(call.getArgument(1), call.getArgument(3)), call.getArgument(3) + ":step:1", change("Too late")); }
            catch (org.springframework.web.server.ResponseStatusException denied) { rejected.set(true); }
            finally { finished.countDown(); }
            return response("COMPLETE");
        });
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "cancel");
        assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); assistant.cancel(sessionId);
        assertThat(finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); assertThat(rejected).isTrue();
        assertThat(assistant.get(sessionId).at("/turns/0/status").asText()).isEqualTo("CANCELLED");
        verify(runtime).interrupt(any(), eq(sessionId), anyString(), anyString());
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void selectedOperationsAndPageIntentNeedNoDetailedUserPrompt() throws Exception {
        create("ACTIONS"); var imported = assistant.addSource(sessionId, new ConfigurationAssistantService.SourceRequest("API", "application/json", """
            {"openapi":"3.0.3","info":{"title":"Generic API","version":"1"},"servers":[{"url":"https://catalog.example"}],
            "paths":{"/products":{"get":{"operationId":"listProducts","responses":{"200":{"description":"Products"}}}},
            "/private-notes":{"get":{"operationId":"privateNotes","responses":{"200":{"description":"Notes"}}}}}}
            """, null, null));
        String source = imported.at("/sources/0/id").asText(); String selected = null;
        for (var op : imported.at("/sources/0/operations")) if ("/products".equals(op.path("path").asText())) selected = op.path("id").asText();
        assertThat(selected).isNotBlank(); var observed = new AtomicReference<JsonNode>();
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            var who = caller(call.getArgument(1), call.getArgument(3)); observed.set(operations.selectedSources(who));
            assertThat(operations.context(who).at("/preparationScope/documentedOperationCount").asInt()).isEqualTo(1);
            assertThat(call.getArgument(5, String.class)).startsWith("Prepare"); return response("COMPLETE");
        });
        assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("", true, List.of(source), List.of(selected), new AuthoringFeedbackPolicy(true, 2),
            "PREPARE", new AuthoringTaskIntent.EditorContext("ACTIONS", List.of())), "selected");
        await(); assertThat(observed.get().toString()).contains("listProducts", "https://catalog.example").doesNotContain("privateNotes");
    }
    @Test void saveRebasesOnFreshStateAndPreservesUnrelatedManualEdits() throws Exception {
        var seen = new AtomicReference<JsonNode>();
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            ((ObjectNode) configuration.get().path("promptConfig")).put("manualNote", "Keep this");
            var who = caller(call.getArgument(1), call.getArgument(3)); operations.save(who, call.getArgument(3) + ":step:1", change("Requested edit"));
            seen.set(operations.context(who)); return response("COMPLETE");
        });
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "merge"); await();
        assertThat(configuration.get().at("/promptConfig/manualNote").asText()).isEqualTo("Keep this");
        assertThat(seen.get().at("/configuration/promptConfig/systemPrompt").asText()).isEqualTo("Requested edit");
    }
    @Test void sourceRemovalErasesCanonicalHistoryWithoutUndoingSavedChanges() throws Exception {
        create("BEHAVIOUR"); var imported = assistant.addSource(sessionId, new ConfigurationAssistantService.SourceRequest("Guide", "text/plain", "Welcoming prose", null, null));
        assistant.turn(sessionId, request(false, true), "save"); await(); assistant.removeSource(sessionId, imported.at("/sources/0/id").asText());
        verify(runtime).close(any(), eq(sessionId), anyString()); assertThat(assistant.get(sessionId).path("turns")).isEmpty();
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("AI result");
    }
    @Test void failedErasureRetainsEncryptedReferenceForRetry() throws Exception {
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "save"); await();
        doThrow(new IllegalStateException("erasure unavailable")).when(runtime).close(any(), eq(sessionId), anyString());
        assertThatThrownBy(() -> assistant.delete(sessionId)).hasMessageContaining("erasure unavailable"); assertThat(sessions.findById(sessionId)).isPresent();
        doNothing().when(runtime).close(any(), eq(sessionId), anyString()); assistant.delete(sessionId);
        assertThat(sessions.findById(sessionId)).isEmpty(); sessionId = null;
    }
    @Test void anotherActorCannotReadOrContinueTheConversation() {
        create("BEHAVIOUR"); signIn("other@example.test");
        try { assertThatThrownBy(() -> assistant.get(sessionId)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class); }
        finally { signIn(actor); }
    }
    @Test void importIsEncryptedAndDoesNotInvokeTheBrain() {
        create("BEHAVIOUR"); assistant.addSource(sessionId, new ConfigurationAssistantService.SourceRequest("Guide", "text/plain", "Use a helpful greeting.", null, null));
        assertThat(sessions.findById(sessionId).orElseThrow().encryptedContent).doesNotContain("helpful greeting"); verifyNoInteractions(runtime);
    }
}
