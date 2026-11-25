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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/** Real encrypted facade/receipts; canonical runtime and resource receivers are explicit test fixtures. */
@SpringBootTest(properties = {"platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "spring.datasource.url=jdbc:h2:mem:configuration-assistant-goals;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    @Autowired AuthoringCrypto crypto;
    @SpyBean AuthoringGoalService goals;
    @Autowired AuthoringGoalRepository goalRecords;
    @Autowired AuthoringOperationRepository operationRecords;
    @Autowired AuthoringGoalDirectiveRepository directiveRecords;
    @MockBean AuthoringGoalLoginAuthority logins;
    @MockBean AuthoringBrainBindingService bindings;
    @MockBean ConfiguredAuthoringRuntimeClient runtime;
    @MockBean AuthoringResourceService resources;
    @MockBean AuthoringDraftLock draftLock;
    String customerId, resourceId, userId, actor, sessionId;
    AtomicReference<ObjectNode> configuration = new AtomicReference<>();
    List<String> secretReferences = List.of();
    @BeforeEach void prepare() throws Exception {
        // Tests drive refresh explicitly; background polling must not race mock replacement.
        doNothing().when(goals).refresh();
        String suffix = UUID.randomUUID().toString(); customerId = "customer-" + suffix; resourceId = "dep-" + suffix;
        userId = "user-" + suffix; actor = suffix + "@example.test";
        secretReferences = List.of();
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
            "responses@1", "sha256:fixture", 3, 2, List.copyOf(scopes), true));
        when(logins.capture(any())).thenAnswer(call -> mapper.createObjectNode().put("validUntil", Instant.now().plusSeconds(3600).toString()).put("loginSessionId", "fixture-login"));
        when(bindings.conversationBinding()).thenAnswer(call -> bindings.resolve("CONFIGURATION"));
        when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString())).thenAnswer(call -> snapshot(call.getArgument(2)));
        when(resources.check(anyString(), anyString(), any(), any())).thenReturn(mapper.createObjectNode().put("valid", true).put("publishReady", true));
        when(resources.save(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString(), any())).thenAnswer(call -> {
            var snapshot = snapshot(call.getArgument(2));
            var result = changes.apply(configuration.get(), call.getArgument(3), snapshot.allowedPaths(), snapshot.catalogue().path("secretReferences"));
            configuration.set(result.candidate()); return saved(result);
        });
        when(resources.undo(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString(), any())).thenAnswer(call -> {
            var snapshot = snapshot(call.getArgument(2));
            var result = changes.undo(configuration.get(), call.getArgument(3), snapshot.allowedPaths(), snapshot.catalogue().path("secretReferences"));
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
        if (sessionId != null) {
            try { assistant.cancel(sessionId); } catch (Exception ignored) { }
            for (var goal : goalRecords.findBySessionId(sessionId)) {
                directiveRecords.deleteAll(directiveRecords.findByGoalId(goal.id));
                operationRecords.deleteAll(operationRecords.findByGoalIdOrderByCreatedAtAscIdAsc(goal.id, org.springframework.data.domain.PageRequest.of(0, 1000)));
                goalRecords.delete(goal);
            }
            sessions.findById(sessionId).ifPresent(sessions::delete);
        }
        SecurityContextHolder.clearContext(); users.deleteById(userId); customers.deleteById(customerId);
    }
    void signIn(String who) {
        var principal = new PlatformPrincipal(who, PlatformRole.PLATFORM_ADMIN, "Fixture author", "SESSION", userId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))));
    }
    AuthoringResourceService.Snapshot snapshot(String task) {
        var catalogue = mapper.createObjectNode(); catalogue.set("secretReferences", mapper.valueToTree(secretReferences));
        return new AuthoringResourceService.Snapshot(customerId, "Fixture", configuration.get(), catalogue,
            "BRAIN_SETUP".equals(task) ? List.of("/actionsConfig", "/routingConfig", "/promptConfig")
                : "ACTIONS".equals(task) ? List.of("/actionsConfig", "/routingConfig") : List.of("/promptConfig"), "draft", true, "");
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
    JsonNode startGoal() {
        create("BEHAVIOUR");
        var nativeView = mapper.createObjectNode().put("workId", "exec-goal-fixture");
        nativeView.putObject("goal").put("state", "RUNNING").put("segment", 1);
        when(runtime.startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(nativeView);
        when(runtime.work(any(), anyString(), anyString(), anyString())).thenReturn(nativeView);
        return assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("Set the greeting.", false, null, null, null, null, null, "AUTONOMOUS"), "goal-start").path("goal");
    }
    @Test void foreignAndMissingGoalControlsHaveTheSameDenialWithoutReceiverEffects() {
        var goal = startGoal();
        String goalId = goal.path("id").asText();
        var originalGoal = goalRecords.findById(goalId).orElseThrow();
        long originalEpoch = originalGoal.cancellationEpoch;
        String originalState = originalGoal.state;
        var originalConfiguration = configuration.get().deepCopy();
        clearInvocations(runtime, resources, logins);
        signIn("other-owner@example.test");
        try {
            for (String command : List.of("PAUSE", "RESUME", "CANCEL")) {
                for (String selectedSession : List.of(sessionId, "cas-does-not-exist")) {
                    var failure = catchThrowableOfType(() -> goals.control(selectedSession, goalId,
                        new AuthoringGoalService.Control(command)), org.springframework.web.server.ResponseStatusException.class);
                    assertThat(failure.getStatusCode().value()).isEqualTo(403);
                    assertThat(failure.getReason()).isEqualTo("AUTHORING_GOAL_ACCESS_DENIED");
                }
            }
            for (String selectedSession : List.of(sessionId, "cas-does-not-exist")) {
                var failure = catchThrowableOfType(() -> assistant.get(selectedSession),
                    org.springframework.web.server.ResponseStatusException.class);
                assertThat(failure.getStatusCode().value()).isEqualTo(404);
                assertThat(failure.getReason()).isEqualTo("Assistant session not found.");
            }
            signIn(actor);
            for (String command : List.of("PAUSE", "RESUME", "CANCEL")) {
                var missing = catchThrowableOfType(() -> goals.control(sessionId, "cag-does-not-exist",
                    new AuthoringGoalService.Control(command)), org.springframework.web.server.ResponseStatusException.class);
                assertThat(missing.getStatusCode().value()).isEqualTo(403);
                assertThat(missing.getReason()).isEqualTo("AUTHORING_GOAL_ACCESS_DENIED");
            }
            var expired = sessions.findById(sessionId).orElseThrow();
            expired.expiresAt = Instant.now().minusSeconds(1); sessions.saveAndFlush(expired);
            for (String command : List.of("PAUSE", "RESUME", "CANCEL")) {
                var failure = catchThrowableOfType(() -> goals.control(sessionId, goalId,
                    new AuthoringGoalService.Control(command)), org.springframework.web.server.ResponseStatusException.class);
                assertThat(failure.getStatusCode().value()).isEqualTo(403);
                assertThat(failure.getReason()).isEqualTo("AUTHORING_GOAL_ACCESS_DENIED");
            }
            verifyNoInteractions(runtime, resources, logins);
            var after = goalRecords.findById(goalId).orElseThrow();
            assertThat(after.cancellationEpoch).isEqualTo(originalEpoch);
            assertThat(after.state).isEqualTo(originalState);
            assertThat(configuration.get()).isEqualTo(originalConfiguration);
            assertThat(operationRecords.findByGoalIdOrderByCreatedAtAscIdAsc(goalId,
                org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        } finally { signIn(actor); }
    }
    AuthoringDraftOperations.Caller goalCaller(JsonNode goal) {
        return new AuthoringDraftOperations.Caller(sessionId, goal.path("turnId").asText(), "assistant-brain", "release", null, true,
            goal.path("id").asText(), goal.path("requestRevision").asLong(), goal.path("cancellationEpoch").asLong(), goal.path("segment").asInt());
    }
    @Test void goalAdmissionUsesOneCanonicalAcknowledgementAndPollsWithoutNewUserTurns() {
        var goal = startGoal();
        assertThat(goal.path("state").asText()).isEqualTo("RUNNING");
        assertThat(goal.path("originalRequest").asText()).isEqualTo("Set the greeting.");
        assertThat(goal.has("latestRequestUpdate")).isFalse();
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
        assertThat(goal.toString()).doesNotContain("principal", "loginSessionId", "runtimeHeaders");
        assistant.get(sessionId); assistant.get(sessionId);
        verify(runtime, times(1)).startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(runtime, never()).message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any());
        assertThat(sessions.findById(sessionId).orElseThrow().deadline).isAfter(Instant.now().plusSeconds(1000));
    }
    @Test void toolGoalFactsStayEqualAcrossRenewalAndPollingButRealChangesRemainVisible() {
        var goal = startGoal(); var first = operations.context(goalCaller(goal)); String id = goal.path("id").asText();
        goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 1, 0, 2));
        var nativeView = mapper.createObjectNode().put("workId", goal.path("workId").asText());
        var state = nativeView.putObject("goal").put("state", "YIELDED").put("segment", 2).put("nextEligibleAt", Instant.now().plusSeconds(3).toString());
        state.putObject("usage").put("modelCalls", 7); state.putObject("progress").put("summary", "A changing native summary.");
        state.putObject("providerDiagnostics").putArray("observations").addObject().put("outputTokens", 123);
        when(runtime.work(any(), anyString(), anyString(), anyString())).thenReturn(nativeView);
        var record = goalRecords.findById(id).orElseThrow(); record.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(record);
        goals.refresh(id);
        var view = assistant.get(sessionId).path("goal"); var who = goalCaller(view);
        assertThat(operations.context(who)).isEqualTo(first);
        for (String field : List.of("state", "segment", "updatedAt", "usage", "progress", "providerDiagnostics", "nextEligibleAt"))
            assertThat(first.path("goal").has(field)).as(field).isFalse();
        assertThat(view.path("state").asText()).isEqualTo("YIELDED");
        assertThat(view.at("/usage/modelCalls").asInt()).isEqualTo(7);
        assertThat(view.path("providerDiagnostics").isObject()).isTrue();
        configuration.get().withObject("/promptConfig").put("systemPrompt", "A real manual edit.");
        var manual = operations.context(who); assertThat(manual).isNotEqualTo(first);
        operations.save(who, goal.path("workId").asText() + ":step:1", change("A real saved effect."));
        var saved = operations.context(who); assertThat(saved).isNotEqualTo(manual);
        assertThat(saved.at("/goal/operationCount").asInt()).isEqualTo(1);
        assertThat(saved.at("/goal/operations/0").has("savedAt")).isFalse();
        goals.directive(sessionId, id, new AuthoringGoalService.Directive("Keep the legal name.", null, null), "stable-directive");
        goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 2, 1, 2));
        var revised = operations.context(goalCaller(assistant.get(sessionId).path("goal")));
        assertThat(revised).isNotEqualTo(saved);
        assertThat(revised.at("/goal/latestRequestUpdate/query").asText()).isEqualTo("Keep the legal name.");
        assertThat(revised.at("/goal/latestRequestUpdate").has("createdAt")).isFalse();
        assertThat(revised.at("/goal/originalRequest").asText()).isEqualTo("Set the greeting.");
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
    }
    @Test void parsedPrivateApiRequiresAnOwnedReferenceThroughCheckSaveAndUndo() {
        secretReferences = List.of("OWNED_API_KEY"); create("ACTIONS");
        assistant.addSource(sessionId, new ConfigurationAssistantService.SourceRequest("Private API", "application/json", """
            {"openapi":"3.0.3","info":{"title":"Private API","version":"1"},"servers":[{"url":"https://api.example.test"}],
             "security":[{"LabKey":[]}],"components":{"securitySchemes":{"LabKey":{"type":"apiKey","in":"header","name":"X-Lab-Key"}}},
             "paths":{"/private":{"get":{"operationId":"privateLookup","responses":{"200":{"description":"OK"}}}}}}
            """, null, null));
        var nativeView = mapper.createObjectNode().put("workId", "exec-private-goal"); nativeView.putObject("goal").put("state", "RUNNING");
        when(runtime.startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(nativeView);
        var goal = assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("", false, null, null, null,
            "PREPARE", new AuthoringTaskIntent.EditorContext("ACTIONS", List.of()), "AUTONOMOUS"), "private-goal").path("goal");
        var who = goalCaller(goal); var setup = mapper.createArrayNode();
        setup.addObject().put("path", "/actionsConfig/actions").put("operation", "UPSERT").put("key", "privateLookup")
            .putObject("value").put("name", "privateLookup").put("accessMode", "READ");
        setup.addObject().put("path", "/routingConfig/actions/privateLookup").put("operation", "SET")
            .putObject("value").put("method", "GET").put("url", "https://api.example.test/private");
        operations.save(who, "exec-private-goal:step:1", setup);
        assertThat(operations.context(who).at("/sourceCoverage/complete").asBoolean()).isFalse();
        var select = mapper.createArrayNode().add(mapper.createObjectNode().put("path", "/routingConfig/actions/privateLookup/headers/X-Lab-Key")
            .put("operation", "SET").put("value", "${secret:OWNED_API_KEY}"));
        assertThat(operations.check(who, select).at("/sourceCoverage/complete").asBoolean()).isTrue();
        assertThat(assistant.get(sessionId).at("/goal/progress/coverage/completed").asInt()).isZero();
        operations.save(who, "exec-private-goal:step:2", select);
        assertThat(configuration.get().at("/routingConfig/actions/privateLookup/headers/X-Lab-Key").asText()).isEqualTo("${OWNED_API_KEY}");
        assertThat(operations.context(who).at("/configuration/routingConfig/actions/privateLookup/headers/X-Lab-Key").asText()).isEqualTo("${OWNED_API_KEY}");
        assertThat(operations.status(who).at("/sourceCoverage/complete").asBoolean()).isTrue();
        assertThat(assistant.get(sessionId).at("/goal/progress/coverage/completed").asInt()).isEqualTo(1);
        operations.undo(who, "exec-private-goal:step:3", "exec-private-goal:step:2");
        assertThat(operations.context(who).at("/sourceCoverage/complete").asBoolean()).isFalse();
        verify(runtime, never()).message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any());
    }
    @Test void rootReceiptsSurviveSegmentsWithNoThreeWriteLifetimeLimitOrReplay() {
        var goal = startGoal(); var who = goalCaller(goal); String root = goal.path("workId").asText();
        var first = operations.save(who, root + ":step:1", change("First"));
        operations.save(who, root + ":step:2", change("Second")); operations.save(who, root + ":step:3", change("Third"));
        assertThatThrownBy(() -> operations.save(who, root + ":step:4", change("Fourth"))).hasMessageContaining("WRITE_LIMIT");
        var renewed = goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 0, 2, "exec-goal-fixture"));
        var next = new AuthoringDraftOperations.Caller(sessionId, goal.path("turnId").asText(), "assistant-brain", "release", null, true, goal.path("id").asText(), 1, 0, 2);
        assertThat(renewed.path("segment").asInt()).isEqualTo(2);
        assertThat(operations.save(next, root + ":step:1", change("First"))).isEqualTo(first);
        operations.save(next, root + ":step:4", change("Fourth"));
        assertThat(assistant.get(sessionId).at("/goal/operations")).hasSize(4);
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
        assertThat(operations.status(next, new AuthoringDraftOperations.ReceiptRead(root + ":step:4", null, null)).path("operations")).hasSize(1);
        assertThatThrownBy(() -> operations.save(who, root + ":step:5", change("Old segment"))).hasMessageContaining("FENCED");
        assertThatThrownBy(() -> operations.save(next, root + ":step:1", change("Changed replay"))).hasMessageContaining("REPLAY_CONFLICT");
        assertThatThrownBy(() -> goals.renew(caller(sessionId, goal.path("turnId").asText()),
            new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 0, 2, "exec-other"))).hasMessageContaining("ACCESS_DENIED");
        assertThatThrownBy(() -> operations.save(next, goal.path("turnId").asText() + ":step:6", change("Wrong namespace")))
            .hasMessageContaining("ACCESS_DENIED");
    }
    @Test void delayedOldCancellationCannotClearANewerGoalActiveTurn() {
        var old = startGoal(); String id = old.path("id").asText();
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("Delivery unavailable"));
        assistant.cancel(sessionId);
        var nativeView = mapper.createObjectNode().put("workId", "exec-next-goal");
        nativeView.putObject("goal").put("state", "RUNNING");
        when(runtime.startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(nativeView);
        var next = assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("A new explicit goal.", false, null, null, null, null, null, "AUTONOMOUS"), "next-goal").path("goal");
        assertThat(next.path("id").asText()).isNotEqualTo(id);
        var cancelled = mapper.createObjectNode().put("workId", old.path("workId").asText());
        cancelled.putObject("goal").put("state", "CANCELLED").put("terminal", true);
        doReturn(cancelled).when(runtime).controlWork(any(), anyString(), anyString(), anyString(), any());
        var record = goalRecords.findById(id).orElseThrow(); record.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(record);
        goals.refresh(id);
        var session = sessions.findById(sessionId).orElseThrow();
        assertThat(session.activeTurnId).isEqualTo(next.path("turnId").asText());
        assertThat(session.status).isEqualTo("RUNNING");
        assertThat(operations.context(goalCaller(next)).path("task").asText()).isEqualTo("BEHAVIOUR");
        assertThat(assistant.get(sessionId).path("turns")).hasSize(2);
    }
    @Test void pauseAndLogoutFenceEveryLaterWriteAndResumeRequiresFreshLogin() {
        var goal = startGoal(); var who = goalCaller(goal); String root = goal.path("workId").asText();
        operations.save(who, root + ":step:1", change("Committed"));
        goals.control(sessionId, goal.path("id").asText(), new AuthoringGoalService.Control("PAUSE"));
        assertThatThrownBy(() -> operations.save(who, root + ":step:2", change("Late"))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(assistant.get(sessionId).at("/goal/operations")).hasSize(1);
        goals.control(sessionId, goal.path("id").asText(), new AuthoringGoalService.Control("RESUME"));
        assertThat(assistant.get(sessionId).at("/goal/cancellationEpoch").asLong()).isEqualTo(2);
        goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 2, 1));
        var resumed = goalCaller(assistant.get(sessionId).path("goal"));
        operations.save(resumed, root + ":step:2", change("After resume"));
        goals.revokeActor(actor);
        assertThat(assistant.get(sessionId).at("/goal/reasonCode").asText()).isEqualTo("LOGOUT_REAUTHENTICATION_REQUIRED");
        assertThat(assistant.get(sessionId).at("/goal/cancellationEpoch").asLong()).isEqualTo(3);
        assertThatThrownBy(() -> goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 2, 2)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(logins, times(2)).capture(any());
    }
    @ParameterizedTest @ValueSource(strings = {"PAUSED", "WAITING_FOR_INPUT", "BLOCKED"})
    void eachFreshResumeAfterNativeOperationalPauseAdvancesTheExistingFence(String nativeState) {
        var goal = startGoal(); String id = goal.path("id").asText(); String root = goal.path("workId").asText();
        var delivered = new ArrayList<JsonNode>();
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            JsonNode control = call.getArgument(4); delivered.add(control.deepCopy());
            var response = mapper.createObjectNode().put("workId", root);
            response.putObject("goal").put("state", "RUNNING").put("segment", control.path("segment").asInt());
            return response;
        });
        goals.control(sessionId, id, new AuthoringGoalService.Control("PAUSE"));
        var first = goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME"));
        assertThat(first.path("cancellationEpoch").asLong()).isEqualTo(2);
        goals.refresh(id);
        var operationalPause = mapper.createObjectNode().put("workId", root);
        operationalPause.putObject("goal").put("state", nativeState).put("reasonCode", "NATIVE_OPERATIONAL_PAUSE")
            .put("canResume", true).put("segment", 2);
        when(runtime.work(any(), anyString(), anyString(), anyString())).thenReturn(operationalPause);
        var record = goalRecords.findById(id).orElseThrow(); record.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(record);
        goals.refresh(id);
        var paused = assistant.get(sessionId).path("goal");
        assertThat(paused.path("state").asText()).isEqualTo(nativeState);
        assertThat(paused.path("cancellationEpoch").asLong()).isEqualTo(2);
        var second = goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME"));
        assertThat(second.path("cancellationEpoch").asLong()).isEqualTo(3);
        goals.refresh(id);
        assertThat(delivered).hasSize(2);
        assertThat(delivered.get(0).path("cancellationEpoch").asLong()).isEqualTo(2);
        assertThat(delivered.get(1).path("cancellationEpoch").asLong()).isEqualTo(3);
        assertThat(delivered.get(0).path("segment").asInt()).isEqualTo(1);
        assertThat(delivered.get(1).path("segment").asInt()).isEqualTo(2);
        for (var control : delivered) {
            assertThat(control.path("command").asText()).isEqualTo("RESUME");
            assertThat(control.path("requestRevision").asLong()).isEqualTo(1);
            assertThat(control.path("goalId").asText()).isEqualTo(id);
            assertThat(control.path("turnId")).isEqualTo(goal.path("turnId"));
        }
        assertThatThrownBy(() -> goals.renew(caller(sessionId, goal.path("turnId").asText()),
            new AuthoringGoalService.Renewal(id, 1, 2, 2, root))).hasMessageContaining("AUTHORING_GOAL_FENCED");
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
        assertThat(assistant.get(sessionId).at("/goal/operationCount").asInt()).isZero();
        verify(runtime, times(1)).startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
        verify(logins, times(3)).capture(any());
    }
    @Test void resumeDeliveryRetryAndPollingKeepTheSamePersistedIntentFenceAndPayload() {
        var goal = startGoal(); String id = goal.path("id").asText(); String root = goal.path("workId").asText();
        goals.control(sessionId, id, new AuthoringGoalService.Control("PAUSE"));
        var resumed = goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME"));
        assertThat(resumed.path("cancellationEpoch").asLong()).isEqualTo(2);
        var delivered = new ArrayList<JsonNode>();
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            delivered.add(call.getArgument(4, JsonNode.class).deepCopy());
            if (delivered.size() == 1) throw new IllegalStateException("Acknowledgement unavailable");
            var response = mapper.createObjectNode().put("workId", root);
            response.putObject("goal").put("state", "RUNNING").put("segment", 1); return response;
        });
        goals.refresh(id);
        var pending = goalRecords.findById(id).orElseThrow();
        assertThat(pending.pendingControl).isEqualTo("RESUME");
        assertThat(pending.reasonCode).isEqualTo("NATIVE_STATUS_UNAVAILABLE");
        assertThat(pending.cancellationEpoch).isEqualTo(2);
        assertThatThrownBy(() -> goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME")))
            .hasMessageContaining("AUTHORING_GOAL_NOT_PAUSED");
        pending.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(pending);
        goals.refresh(id);
        assertThat(delivered).hasSize(2); assertThat(delivered.get(1)).isEqualTo(delivered.get(0));
        var running = goalRecords.findById(id).orElseThrow();
        assertThat(running.cancellationEpoch).isEqualTo(2); assertThat(running.pendingControl).isNull();
        running.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(running);
        goals.refresh(id);
        assertThat(goalRecords.findById(id).orElseThrow().cancellationEpoch).isEqualTo(2);
        verify(runtime, times(2)).controlWork(any(), anyString(), anyString(), anyString(), any());
        verify(logins, times(2)).capture(any());
    }
    @ParameterizedTest @ValueSource(strings = {"ACTIVE", "NATIVE_NOT_RESUMABLE", "NATIVE_TERMINAL",
        "LOGIN_CAPTURE_FAILED", "FRESH_AUTHORITY_FAILED", "FOREIGN_ACTOR"})
    void rejectedResumeNeverAdvancesFenceOrRetainsUnvalidatedFreshAuthority(String rejection) {
        var goal = startGoal(); String id = goal.path("id").asText();
        if (!"ACTIVE".equals(rejection)) {
            goals.control(sessionId, id, new AuthoringGoalService.Control("PAUSE"));
            var acknowledgement = mapper.createObjectNode().put("workId", goal.path("workId").asText());
            acknowledgement.putObject("goal").put("state", "PAUSED").put("canResume", !"NATIVE_NOT_RESUMABLE".equals(rejection))
                .put("terminal", "NATIVE_TERMINAL".equals(rejection));
            when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenReturn(acknowledgement);
            goals.refresh(id);
        }
        var before = goalRecords.findById(id).orElseThrow(); clearInvocations(runtime);
        if ("LOGIN_CAPTURE_FAILED".equals(rejection)) when(logins.capture(any())).thenThrow(
            new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHORING_REAUTHENTICATION_REQUIRED"));
        if ("FRESH_AUTHORITY_FAILED".equals(rejection)) {
            when(logins.capture(any())).thenReturn(mapper.createObjectNode().put("loginSessionId", "unvalidated-fresh-login")
                .put("validUntil", Instant.now().plusSeconds(7200).toString()));
            doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "AUTHORING_REAUTHENTICATION_REQUIRED")).when(logins).requireCurrent(any(), any());
        }
        if ("FOREIGN_ACTOR".equals(rejection)) signIn("other-owner@example.test");
        try {
            String expected = switch (rejection) {
                case "ACTIVE" -> "AUTHORING_GOAL_NOT_PAUSED";
                case "NATIVE_NOT_RESUMABLE" -> "AUTHORING_NATIVE_GOAL_NOT_RESUMABLE";
                case "NATIVE_TERMINAL" -> "AUTHORING_GOAL_TERMINAL";
                case "FOREIGN_ACTOR" -> "AUTHORING_GOAL_ACCESS_DENIED";
                default -> "AUTHORING_REAUTHENTICATION_REQUIRED";
            };
            assertThatThrownBy(() -> goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME"))).hasMessageContaining(expected);
            var after = goalRecords.findById(id).orElseThrow();
            assertThat(after.cancellationEpoch).isEqualTo(before.cancellationEpoch);
            assertThat(after.requestRevision).isEqualTo(before.requestRevision); assertThat(after.segment).isEqualTo(before.segment);
            assertThat(after.state).isEqualTo(before.state); assertThat(after.reasonCode).isEqualTo(before.reasonCode);
            assertThat(after.pendingControl).isEqualTo(before.pendingControl);
            assertThat(after.validUntil).isEqualTo(before.validUntil); assertThat(after.encryptedAuthority).isEqualTo(before.encryptedAuthority);
            verifyNoInteractions(runtime);
        } finally { signIn(actor); }
    }
    @Test void logoutPauseAcknowledgementPreservesReauthenticationReasonAndFreshLoginResumesTheSameRoot() throws Exception {
        var goal = startGoal(); String id = goal.path("id").asText(); String root = goal.path("workId").asText();
        var originalCaller = goalCaller(goal);
        operations.save(originalCaller, root + ":step:1", change("Saved before logout"));
        var acknowledgement = mapper.createObjectNode().put("workId", root);
        acknowledgement.putObject("goal").put("state", "PAUSED").put("reasonCode", "GOAL_USER_PAUSED").put("canResume", true);
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenReturn(acknowledgement);
        goals.revokeActor(actor); goals.refresh(id);
        var stored = goalRecords.findById(id).orElseThrow();
        assertThat(stored.state).isEqualTo("PAUSED");
        assertThat(stored.reasonCode).isEqualTo("LOGOUT_REAUTHENTICATION_REQUIRED");
        assertThat(stored.pendingControl).isNull(); assertThat(stored.nextPollAt).isNull();
        var paused = assistant.get(sessionId).path("goal");
        assertThat(paused.path("reasonCode").asText()).isEqualTo("LOGOUT_REAUTHENTICATION_REQUIRED");
        assertThat(paused.path("operationCount").asInt()).isEqualTo(1);
        assertThatThrownBy(() -> operations.save(originalCaller, root + ":step:2", change("Late old authority")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        when(logins.capture(any())).thenReturn(mapper.createObjectNode().put("loginSessionId", "fresh-login")
            .put("validUntil", Instant.now().plusSeconds(3600).toString()));
        var resumed = mapper.createObjectNode().put("workId", root);
        resumed.putObject("goal").put("state", "RUNNING").put("reasonCode", "GOAL_RESUME_REQUESTED");
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenReturn(resumed);
        goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME")); goals.refresh(id);
        var current = assistant.get(sessionId).path("goal");
        assertThat(current.path("id")).isEqualTo(goal.path("id"));
        assertThat(current.path("workId")).isEqualTo(goal.path("workId"));
        assertThat(current.path("state").asText()).isEqualTo("RUNNING");
        assertThat(current.path("reasonCode").asText()).isEqualTo("GOAL_RESUME_REQUESTED");
        var authority = mapper.readTree(crypto.decrypt(id, goalRecords.findById(id).orElseThrow().encryptedAuthority));
        assertThat(authority.at("/login/loginSessionId").asText()).isEqualTo("fresh-login");
        assertThat(current.path("cancellationEpoch").asLong()).isEqualTo(2);
        goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 1, 2, 2, root));
        operations.save(goalCaller(assistant.get(sessionId).path("goal")), root + ":step:2", change("Saved with fresh authority"));
        assertThat(assistant.get(sessionId).at("/goal/operationCount").asInt()).isEqualTo(2);
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
        assertThatThrownBy(() -> operations.save(originalCaller, root + ":step:3", change("Still old authority")))
            .hasMessageContaining("FENCED");
        verify(logins, times(2)).capture(any());
        verify(runtime, times(1)).startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }
    @Test void pauseAndCancelAcknowledgementsKeepControlReasonsIdempotencyAndCommittedReceipts() {
        var goal = startGoal(); String id = goal.path("id").asText(); String root = goal.path("workId").asText();
        var originalCaller = goalCaller(goal);
        var receipt = operations.save(originalCaller, root + ":step:1", change("Committed before controls"));
        var paused = goals.control(sessionId, id, new AuthoringGoalService.Control("PAUSE"));
        var acknowledgement = mapper.createObjectNode().put("workId", root);
        acknowledgement.putObject("goal").put("state", "PAUSED").put("reasonCode", "GOAL_USER_PAUSED");
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenReturn(acknowledgement);
        goals.refresh(id);
        var repeated = goals.control(sessionId, id, new AuthoringGoalService.Control("PAUSE"));
        assertThat(repeated.path("reasonCode").asText()).isEqualTo("USER_PAUSED");
        assertThat(repeated.path("cancellationEpoch")).isEqualTo(paused.path("cancellationEpoch"));
        assertThat(goalRecords.findById(id).orElseThrow().pendingControl).isNull();
        var cancelled = goals.control(sessionId, id, new AuthoringGoalService.Control("CANCEL"));
        var late = mapper.createObjectNode().put("workId", root);
        late.putObject("goal").put("state", "COMPLETE").put("reasonCode", "NATIVE_COMPLETED_BEFORE_CANCEL").put("terminal", true);
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenReturn(late);
        goals.refresh(id);
        var terminal = goals.control(sessionId, id, new AuthoringGoalService.Control("CANCEL"));
        assertThat(terminal.path("state").asText()).isEqualTo("CANCELLED");
        assertThat(terminal.path("reasonCode").asText()).isEqualTo("USER_CANCELLED");
        assertThat(terminal.path("cancellationEpoch")).isEqualTo(cancelled.path("cancellationEpoch"));
        assertThat(terminal.path("operations")).hasSize(1);
        assertThat(terminal.at("/operations/0")).isEqualTo(changes.project(receipt));
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Committed before controls");
        assertThat(goalRecords.findById(id).orElseThrow().pendingControl).isNull();
        assertThatThrownBy(() -> operations.save(originalCaller, root + ":step:2", change("Late write")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(runtime, times(2)).controlWork(any(), anyString(), anyString(), anyString(), any());
    }
    @ParameterizedTest @ValueSource(strings = {"PAUSED", "CANCELLED", "YIELDED"})
    void nativeOperationalReasonStillFlowsWhenItsStateIsAdopted(String state) {
        var goal = startGoal(); String id = goal.path("id").asText();
        var nativeView = mapper.createObjectNode().put("workId", goal.path("workId").asText());
        nativeView.putObject("goal").put("state", state).put("reasonCode", "NATIVE_OPERATIONAL_REASON");
        when(runtime.work(any(), anyString(), anyString(), anyString())).thenReturn(nativeView);
        var record = goalRecords.findById(id).orElseThrow(); record.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(record);
        goals.refresh(id);
        var projected = assistant.get(sessionId).path("goal");
        assertThat(projected.path("state").asText()).isEqualTo(state);
        assertThat(projected.path("reasonCode").asText()).isEqualTo("NATIVE_OPERATIONAL_REASON");
    }
    @Test void expiredAuthorityPausesAndFreshAuthenticationResumesTheSameRootAndReceipts() {
        var goal = startGoal(); String id = goal.path("id").asText(); String operation = goal.path("workId").asText() + ":step:1";
        operations.save(goalCaller(goal), operation, change("Saved before expiry"));
        var original = goalRecords.findById(id).orElseThrow(); original.validUntil = Instant.now().minusSeconds(1);
        original.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(original);
        goals.refresh(id);
        var paused = assistant.get(sessionId).path("goal");
        assertThat(paused.path("state").asText()).isEqualTo("PAUSED");
        assertThat(paused.path("reasonCode").asText()).isEqualTo("REAUTHENTICATION_REQUIRED");
        assertThat(paused.path("operationCount").asInt()).isEqualTo(1);
        assertThatThrownBy(() -> goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 1, 0, 1)))
            .hasMessageContaining("FENCED");
        goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME"));
        var renewed = goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 1, 2, 1, goal.path("workId").asText()));
        assertThat(renewed.path("workId")).isEqualTo(goal.path("workId"));
        assertThat(renewed.path("requestRevision").asInt()).isEqualTo(1);
        assertThat(renewed.path("cancellationEpoch").asLong()).isEqualTo(2);
        var resumed = goalCaller(assistant.get(sessionId).path("goal"));
        operations.save(resumed, operation, change("Saved before expiry"));
        operations.save(resumed, goal.path("workId").asText() + ":step:2", change("After reauthentication"));
        assertThat(assistant.get(sessionId).at("/goal/operationCount").asInt()).isEqualTo(2);
        verify(runtime, times(1)).startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }
    @Test void directivesAreImmutableIdempotentAndFenceOldRevisionWithoutNewTranscriptTurn() {
        var goal = startGoal(); String id = goal.path("id").asText(); var who = goalCaller(goal);
        var request = new AuthoringGoalService.Directive("Use a concise greeting.", List.of(), List.of());
        goals.directive(sessionId, id, request, "directive"); goals.directive(sessionId, id, request, "directive");
        var revised = assistant.get(sessionId).path("goal"); assertThat(revised.path("requestRevision").asLong()).isEqualTo(2);
        assertThat(revised.at("/latestRequestUpdate/query").asText()).isEqualTo(request.query());
        assertThat(revised.at("/latestRequestUpdate/requestRevision").asLong()).isEqualTo(2);
        assertThat(Instant.parse(revised.at("/latestRequestUpdate/createdAt").asText())).isNotNull();
        assertThat(revised.path("originalRequest").asText()).isEqualTo("Set the greeting.");
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
        assertThat(directiveRecords.findByGoalId(id)).hasSize(1);
        assertThatThrownBy(() -> operations.context(who)).hasMessageContaining("FENCED");
        assertThatThrownBy(() -> goals.directive(sessionId, id, new AuthoringGoalService.Directive("Different", null, null), "directive"))
            .hasMessageContaining("REPLAY_CONFLICT");
        var renewed = goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 2, 1, 1));
        assertThat(renewed.at("/directive/query").asText()).isEqualTo("Use a concise greeting.");
    }
    @Test void pendingRequestUpdatesKeepOrderedVerifiedContextAndLatestProjectionWithoutNewTurns() {
        var goal = startGoal(); String id = goal.path("id").asText();
        goals.directive(sessionId, id, new AuthoringGoalService.Directive("Preserve the formal name.", null, null), "update-one");
        var latest = goals.directive(sessionId, id, new AuthoringGoalService.Directive("Also keep it concise.", null, null), "update-two");
        assertThat(latest.at("/latestRequestUpdate/query").asText()).isEqualTo("Also keep it concise.");
        assertThat(latest.at("/latestRequestUpdate/requestRevision").asLong()).isEqualTo(3);
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            var control = call.getArgument(4, JsonNode.class);
            if ("DIRECTIVE".equals(control.path("command").asText())) {
                assertThat(control.at("/directive/requestUpdates")).hasSize(2);
                assertThat(control.at("/directive/requestUpdates/0/query").asText()).isEqualTo("Preserve the formal name.");
                assertThat(control.at("/directive/requestUpdates/0/requestRevision").asLong()).isEqualTo(2);
                assertThat(control.at("/directive/requestUpdates/1/query").asText()).isEqualTo("Also keep it concise.");
                assertThat(control.at("/directive/requestUpdates/1/requestRevision").asLong()).isEqualTo(3);
            }
            return mapper.createObjectNode().put("workId", goal.path("workId").asText()).set("goal", mapper.createObjectNode().put("state", "RUNNING"));
        });
        goals.refresh(id);
        verify(runtime).controlWork(any(), anyString(), anyString(), anyString(), argThat(control -> "DIRECTIVE".equals(control.path("command").asText())));
        var renewed = goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 3, 2, 1));
        assertThat(renewed.at("/directive/requestUpdates")).hasSize(2);
        assertThat(renewed.at("/directive/requestRevision").asLong()).isEqualTo(3);
        assertThat(renewed.at("/directive/query").asText()).isEqualTo("Also keep it concise.");
        assertThat(assistant.get(sessionId).at("/goal/latestRequestUpdate/query").asText()).isEqualTo("Also keep it concise.");
        assertThat(directiveRecords.findByGoalId(id)).hasSize(2);
        verify(runtime, times(1)).startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }
    @Test void requestUpdateCountLimitRejectsExplicitlyRatherThanDroppingOlderRevisions() {
        var goal = startGoal(); String id = goal.path("id").asText();
        for (int index = 0; index < AuthoringGoalService.MAX_REQUEST_UPDATES; index++)
            goals.directive(sessionId, id, new AuthoringGoalService.Directive("Requirement " + index, null, null), "update-" + index);
        assertThatThrownBy(() -> goals.directive(sessionId, id, new AuthoringGoalService.Directive("Overflow", null, null), "overflow"))
            .hasMessageContaining("AUTHORING_DIRECTIVE_CONTEXT_LIMIT");
        assertThat(directiveRecords.findByGoalId(id)).hasSize(AuthoringGoalService.MAX_REQUEST_UPDATES);
        assertThat(assistant.get(sessionId).at("/goal/latestRequestUpdate/query").asText()).isEqualTo("Requirement 31");
    }
    @Test void requestUpdateUtf8ByteLimitKeepsTheLastAcceptedRevision() {
        var goal = startGoal(); String id = goal.path("id").asText();
        var accepted = new AuthoringGoalService.Directive("x".repeat(8000), null, null);
        for (int index = 0; index < 4; index++) goals.directive(sessionId, id, accepted, "bytes-" + index);
        assertThatThrownBy(() -> goals.directive(sessionId, id, new AuthoringGoalService.Directive("\u20ac".repeat(257), null, null), "bytes-overflow"))
            .hasMessageContaining("AUTHORING_DIRECTIVE_CONTEXT_LIMIT");
        assertThat(directiveRecords.findByGoalId(id)).hasSize(4);
        assertThat(assistant.get(sessionId).at("/goal/latestRequestUpdate/requestRevision").asLong()).isEqualTo(5);
    }
    @Test void sourceErasureClearsProtectedGoalAndDirectiveBodiesButKeepsActualReceipts() {
        var goal = startGoal(); String id = goal.path("id").asText();
        String canary = "Protected follow-up source facts";
        operations.check(goalCaller(goal), change(canary));
        var saved = operations.save(goalCaller(goal), goal.path("workId").asText() + ":step:1", change(canary));
        goals.directive(sessionId, id, new AuthoringGoalService.Directive(canary, null, null), "erase-followup");
        goals.erase(sessionId);
        var erased = assistant.get(sessionId).path("goal");
        assertThat(erased.path("state").asText()).isEqualTo("CANCELLED");
        assertThat(erased.path("operations")).hasSize(1);
        assertThat(erased.toString()).doesNotContain(canary, "Set the greeting.");
        assertThat(erased.has("latestRequestUpdate")).isFalse();
        assertThat(erased.at("/operations/0/status").asText()).isEqualTo("EXECUTED");
        assertThat(erased.at("/operations/0/parameterFingerprint")).isEqualTo(saved.path("parameterFingerprint"));
        assertThat(erased.at("/operations/0/protectedBodyErased").asBoolean()).isTrue();
        assertThat(erased.at("/operations/0").has("diff")).isFalse();
        assertThat(erased.at("/operations/0").has("findings")).isFalse();
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo(canary);
        assertThat(goals.operationPage(sessionId, id, 0, 20).toString()).doesNotContain(canary);
        assertThat(assistant.get(sessionId).toString()).doesNotContain(canary);
        assertThat(crypto.decrypt(sessionId, sessions.findById(sessionId).orElseThrow().encryptedContent)).doesNotContain(canary);
        var record = goalRecords.findById(id).orElseThrow();
        assertThat(crypto.decrypt(id, record.encryptedAuthority)).doesNotContain(canary, "Set the greeting.");
        assertThat(crypto.decrypt(id, record.encryptedProjection)).doesNotContain(canary);
        for (var directive : directiveRecords.findByGoalId(id)) assertThat(crypto.decrypt(directive.id, directive.encryptedRequest)).doesNotContain(canary);
        for (var operation : operationRecords.findByGoalIdOrderByCreatedAtAscIdAsc(id, org.springframework.data.domain.PageRequest.of(0, 100)))
            assertThat(crypto.decrypt(operation.id, operation.encryptedReceipt)).doesNotContain(canary);
        assertThatThrownBy(() -> goals.control(sessionId, id, new AuthoringGoalService.Control("RESUME")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var late = mapper.createObjectNode().put("workId", goal.path("workId").asText());
        late.putObject("goal").put("state", "CANCELLED").put("answer", "Protected follow-up source facts")
            .putObject("progress").put("summary", "Protected follow-up source facts");
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenReturn(late);
        goals.refresh(id);
        assertThat(assistant.get(sessionId).path("goal").toString()).doesNotContain("Protected follow-up source facts");
    }
    @Test void goalRevalidationRejectsRevokedActorAndReadOnlyResourceStillCannotWrite() {
        var goal = startGoal(); var who = goalCaller(goal);
        var user = users.findById(userId).orElseThrow(); user.setStatus("DISABLED"); users.saveAndFlush(user);
        assertThatThrownBy(() -> operations.context(who)).hasMessageContaining("ACCESS_DENIED");
        assertThatThrownBy(() -> goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 0, 1)))
            .hasMessageContaining("ACCESS_DENIED");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void draftWriteGoalPausesWhenTheResourceLifecycleBecomesReadOnly() {
        var goal = startGoal(); String id = goal.path("id").asText();
        when(resources.load(eq("DEPLOYMENT_DRAFT"), eq(resourceId), anyString())).thenAnswer(call ->
            new AuthoringResourceService.Snapshot(customerId, "Fixture", configuration.get(), mapper.createObjectNode(),
                snapshot(call.getArgument(2)).allowedPaths(), "draft", false, "Archived resource."));
        assertThatThrownBy(() -> goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(id, 1, 0, 1)))
            .hasMessageContaining("AUTHORING_RESOURCE_LIFECYCLE_CHANGED");
        var record = goalRecords.findById(id).orElseThrow(); record.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(record);
        goals.refresh(id);
        assertThat(assistant.get(sessionId).at("/goal/state").asText()).isEqualTo("PAUSED");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void sourcedGoalUsesStoredSourceIdAndStableVersionAcrossRenewalAndPaginatedReads() {
        create("BEHAVIOUR");
        var imported = assistant.addSource(sessionId, new ConfigurationAssistantService.SourceRequest("Greeting notes", "text/plain",
            "Use welcoming and concise wording from this selected source.", null, null));
        String source = imported.at("/sources/0/id").asText();
        var goal = startGoal();
        assertThat(goal.path("sourceIds").get(0).asText()).isEqualTo(source);
        var renewed = goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 0, 1));
        var page = operations.selectedSources(goalCaller(goal), new AuthoringDraftOperations.SourceRead(source, null, 0, 1, 256, 0, false));
        assertThat(page.at("/items/0/sourceId").asText()).isEqualTo(source);
        assertThat(page.at("/items/0/sourceVersion")).isEqualTo(renewed.path("sourceVersions").path(source));
        assertThat(page.at("/content/text").asText()).contains("welcoming");
        assertThatThrownBy(() -> operations.selectedSources(goalCaller(goal), new AuthoringDraftOperations.SourceRead("foreign-source", null, 0, 1, 256, 0, false)))
            .hasMessageContaining("ACCESS_DENIED");
        assistant.removeSource(sessionId, source);
        assertThat(assistant.get(sessionId).path("sources")).isEmpty();
        assertThat(assistant.get(sessionId).at("/goal/state").asText()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 0, 2)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void goalAvailabilityComesFromPublishedPolicyAndDoesNotDisableBoundedEditing() {
        assertThat(assistant.status().path("autonomousEnabled").asBoolean()).isTrue();
        var current = bindings.resolve("CONFIGURATION");
        when(bindings.resolve(anyString())).thenReturn(new AuthoringBrainBindingService.Binding(current.deploymentId(), current.releaseId(), current.runtimeUrl(),
            current.profileId(), current.specialistId(), current.bundleHash(), current.maxWrites(), current.maxCorrections(), current.scopes(), false));
        var status = assistant.status(); assertThat(status.path("ready").asBoolean()).isTrue();
        assertThat(status.path("autonomousEnabled").asBoolean()).isFalse();
        assertThat(status.path("executionModes")).hasSize(1); assertThat(status.path("autonomousUnavailableReason").asText()).isNotBlank();
    }
    @Test void waitingQuestionsAndTerminalNativeExpiryAreVisibleWithoutEndlessPolling() {
        var goal = startGoal(); var record = goalRecords.findById(goal.path("id").asText()).orElseThrow();
        var waiting = mapper.createObjectNode().put("workId", goal.path("workId").asText());
        waiting.putObject("goal").put("state", "WAITING_FOR_INPUT").put("answer", "Which stored credential reference should I use?")
            .put("reasonMessage", "A stored credential reference is missing.").put("canResume", true).put("terminal", false)
            .putArray("questions").add("Which stored credential reference should I use?");
        when(runtime.work(any(), anyString(), anyString(), anyString())).thenReturn(waiting);
        record.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(record); goals.refresh(record.id);
        var view = assistant.get(sessionId);
        assertThat(view.at("/turns/0/status").asText()).isEqualTo("NEEDS_INPUT");
        assertThat(view.at("/turns/0/message").asText()).contains("stored credential reference");
        assertThat(view.at("/turns/0/questions/0/question").asText()).contains("stored credential reference");
        assertThat(view.at("/goal/reasonMessage").asText()).contains("missing");
        var expired = mapper.createObjectNode().put("workId", goal.path("workId").asText());
        expired.putObject("goal").put("state", "EXPIRED").put("terminal", true).put("canResume", false);
        when(runtime.controlWork(any(), anyString(), anyString(), anyString(), any())).thenReturn(expired);
        goals.control(sessionId, record.id, new AuthoringGoalService.Control("RESUME")); goals.refresh(record.id);
        assertThat(assistant.get(sessionId).at("/goal/state").asText()).isEqualTo("FAILED");
        assertThat(goalRecords.findById(record.id).orElseThrow().nextPollAt).isNull();
    }
    @Test void unsupportedNativeTerminalGoalCanStillBeCancelledLocallyAndErased() {
        var goal = startGoal(); String id = goal.path("id").asText();
        var blocked = mapper.createObjectNode().put("workId", goal.path("workId").asText());
        blocked.putObject("goal").put("state", "BLOCKED").put("terminal", true).put("canResume", false)
            .put("reasonCode", "UNSUPPORTED_WORK");
        when(runtime.work(any(), anyString(), anyString(), anyString())).thenReturn(blocked);
        var record = goalRecords.findById(id).orElseThrow(); record.nextPollAt = Instant.now().minusSeconds(1); goalRecords.saveAndFlush(record);
        goals.refresh(id);
        assertThat(assistant.get(sessionId).at("/goal/terminal").asBoolean()).isTrue();
        assertThat(assistant.cancel(sessionId).at("/goal/state").asText()).isEqualTo("BLOCKED");
        assistant.delete(sessionId);
        assertThat(sessions.findById(sessionId)).isEmpty();
        assertThat(goalRecords.findById(id)).isEmpty();
    }
    @Test void appAndPluginRevisionChangesDoNotBlockTheNextAuthorizedGoalSave() {
        for (String kind : List.of("ENGAGE_APP", "PRIVATE_DATA_PLUGIN")) {
            var revision = new AtomicInteger(1);
            when(resources.load(eq(kind), eq(resourceId), anyString())).thenAnswer(call -> new AuthoringResourceService.Snapshot(customerId,
                "Editable fixture", configuration.get(), mapper.createObjectNode(), List.of("/promptConfig"), Integer.toString(revision.get()), true, ""));
            when(resources.save(eq(kind), eq(resourceId), anyString(), any())).thenAnswer(call -> {
                var changed = changes.apply(configuration.get(), call.getArgument(3), List.of("/promptConfig"));
                configuration.set(changed.candidate()); revision.incrementAndGet(); return saved(changed);
            });
            sessionId = assistant.create(new ConfigurationAssistantService.CreateSession(kind, resourceId, "BEHAVIOUR")).path("id").asText();
            var nativeView = mapper.createObjectNode().put("workId", "exec-" + kind);
            nativeView.putObject("goal").put("state", "RUNNING").put("segment", 1);
            when(runtime.startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(nativeView);
            var goal = assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("Set the greeting.", false, null, null, null, null, null, "AUTONOMOUS"), "revision-goal").path("goal");
            operations.save(goalCaller(goal), goal.path("workId").asText() + ":step:1", change("First save"));
            revision.incrementAndGet(); configuration.get().withObject("/promptConfig").put("systemPrompt", "Manual refinement");
            operations.save(goalCaller(goal), goal.path("workId").asText() + ":step:2", change("Requested replacement"));
            assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Requested replacement");
            assistant.delete(sessionId); sessionId = null;
        }
    }
    @Test void longGoalKeepsMoreThanOneHundredDurableReceiptsPageableWithoutGrowingTheSessionJournal() {
        var goal = startGoal(); int segment = 1; var caller = goalCaller(goal);
        for (int step = 1; step <= 105; step++) {
            if (step > 1 && (step - 1) % 3 == 0) {
                segment++;
                goals.renew(caller(sessionId, goal.path("turnId").asText()), new AuthoringGoalService.Renewal(goal.path("id").asText(), 1, 0, segment));
                caller = new AuthoringDraftOperations.Caller(sessionId, goal.path("turnId").asText(), "assistant-brain", "release", null, true,
                    goal.path("id").asText(), 1, 0, segment);
            }
            operations.save(caller, goal.path("workId").asText() + ":step:" + step, change("Revision " + step));
        }
        var first = goals.operationPage(sessionId, goal.path("id").asText(), 0, 100);
        var second = goals.operationPage(sessionId, goal.path("id").asText(), 100, 100);
        assertThat(first.path("operations")).hasSize(100); assertThat(first.path("hasMore").asBoolean()).isTrue();
        assertThat(second.path("operations")).hasSize(5); assertThat(second.path("hasMore").asBoolean()).isFalse();
        assertThat(second.at("/operations/4/operationId").asText()).endsWith(":step:105");
        assertThat(second.path("total").asLong()).isEqualTo(105);
        assertThat(assistant.get(sessionId).at("/goal/operationCount").asLong()).isEqualTo(105);
        var encrypted = sessions.findById(sessionId).orElseThrow().encryptedContent;
        assertThat(assistant.get(sessionId).path("turns")).hasSize(1);
        assertThat(encrypted.length()).isLessThan(30000);
        signIn("another-actor@example.test");
        try { assertThatThrownBy(() -> goals.operationPage(sessionId, goal.path("id").asText(), 0, 20)).hasMessageContaining("ACCESS_DENIED"); }
        finally { signIn(actor); }
    }
    @Test void formalGoalProgressComesFromCurrentDraftNotModelClaimsAndSupportsAlreadySatisfiedState() {
        create("ACTIONS");
        assistant.addSource(sessionId, new ConfigurationAssistantService.SourceRequest("API", "application/json", """
            {"openapi":"3.0.3","info":{"title":"Catalogue","version":"1"},"servers":[{"url":"https://api.example.test"}],
             "paths":{"/products":{"get":{"operationId":"products","responses":{"200":{"description":"OK"}}}},
                      "/delivery":{"get":{"operationId":"delivery","responses":{"200":{"description":"OK"}}}}}}
            """, null, null));
        var nativeView = mapper.createObjectNode().put("workId", "exec-coverage");
        nativeView.putObject("goal").put("state", "RUNNING").putObject("progress").putObject("coverage").put("completed", 999).put("total", 999);
        when(runtime.startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(nativeView);
        var goal = assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("", false, null, null, null,
            "PREPARE", new AuthoringTaskIntent.EditorContext("ACTIONS", List.of()), "AUTONOMOUS"), "coverage-goal").path("goal");
        assertThat(goal.at("/progress/coverage/completed").asInt()).isZero();
        assertThat(goal.at("/progress/coverage/total").asInt()).isEqualTo(2);
        assertThat(goal.at("/progress/remaining")).hasSize(2);
        configuration.get().withArray("/actionsConfig/actions").addObject().put("name", "products");
        configuration.get().withObject("/routingConfig/actions/products").put("method", "GET").put("url", "https://api.example.test/products");
        var partial = assistant.get(sessionId).path("goal");
        assertThat(partial.at("/progress/coverage/completed").asInt()).isEqualTo(1);
        assertThat(partial.at("/progress/remaining")).hasSize(1);
        configuration.get().withArray("/actionsConfig/actions").addObject().put("name", "delivery");
        configuration.get().withObject("/routingConfig/actions/delivery").put("method", "GET").put("url", "https://api.example.test/delivery");
        var satisfied = assistant.get(sessionId).path("goal");
        assertThat(satisfied.at("/progress/coverage/completed").asInt()).isEqualTo(2);
        assertThat(satisfied.at("/progress/remaining")).isEmpty();
        assertThat(satisfied.path("operationCount").asInt()).isZero();
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
    }
    @Test void autonomousPreviewExposesCheckedCandidateWithoutGrantingOrClaimingASave() {
        create("BEHAVIOUR");
        when(runtime.startGoal(any(), anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            JsonNode authority = call.getArgument(6);
            var who = new AuthoringDraftOperations.Caller(sessionId, call.getArgument(3), "assistant-brain", "release", null, true,
                authority.path("goalId").asText(), 1, 0, 1);
            var checked = operations.check(who, change("Preview wording"));
            assertThat(checked.path("sourceCoverage").path("evidence").asText()).isEqualTo("CANDIDATE_NOT_SAVED");
            assertThat(operations.status(who).at("/lastCandidateCoverage/evidence").asText()).isEqualTo("CANDIDATE_NOT_SAVED");
            var result = mapper.createObjectNode().put("workId", "exec-preview");
            result.putObject("goal").put("state", "COMPLETE").put("terminal", true).put("answer", "Prepared the preview.");
            return result;
        });
        var view = assistant.turn(sessionId, new ConfigurationAssistantService.TurnRequest("Preview a greeting.", true, null, null, null, null, null, "AUTONOMOUS"), "preview-goal");
        assertThat(view.at("/turns/0/status").asText()).isEqualTo("PREVIEW");
        assertThat(view.at("/turns/0/diff")).hasSize(1);
        assertThat(view.at("/goal/operationCount").asInt()).isZero();
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
        verify(resources, never()).save(anyString(), anyString(), anyString(), any());
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
    @Test void checkedCandidateDiagnosticsRemainVisibleWithoutClaimingSavedEffectsOrExposingWorkAuthority() throws Exception {
        when(runtime.message(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), any())).thenAnswer(call -> {
            operations.check(caller(call.getArgument(1), call.getArgument(3)), change("Checked but not saved"));
            return response("STOPPED");
        });
        create("BEHAVIOUR"); assistant.turn(sessionId, request(false, true), "checked-diagnostics");
        var turn = await().at("/turns/0");
        assertThat(turn.path("status").asText()).isEqualTo("FAILED");
        assertThat(turn.at("/diagnostics/checkedCandidate/status").asText()).isEqualTo("CHECKED_NOT_SAVED");
        assertThat(turn.at("/diagnostics/checkedCandidate/saved").asBoolean(true)).isFalse();
        assertThat(turn.at("/diagnostics/checkedCandidate/diff")).isNotEmpty();
        assertThat(turn.at("/diagnostics/checkedCandidate/findings").isObject()).isTrue();
        assertThat(turn.path("operations")).isEmpty(); assertThat(turn.has("brainWork")).isFalse();
        assertThat(turn.path("diagnostics").toString()).doesNotContain("principal", "allowedActions", "resourceFingerprint");
        assertThat(configuration.get().at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual");
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
            assertThat(call.getArgument(5, String.class)).startsWith("Create or update").contains("when not preview-only"); return response("COMPLETE");
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
