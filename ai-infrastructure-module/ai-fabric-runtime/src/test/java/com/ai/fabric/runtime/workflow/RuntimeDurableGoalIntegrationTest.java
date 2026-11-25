package com.ai.fabric.runtime.workflow;

import ai.fabric.core.AICoreService;
import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.execution.gateway.*;
import ai.fabric.execution.state.*;
import ai.fabric.intent.orchestration.workflow.WorkflowModelCallScope;
import com.ai.fabric.budget.*;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.ai.fabric.budget.openai.LoopbackGuardedOpenAi;
import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.budget.*;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.web.ConversationHandoverController;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport.addPrivateRuntimeHeaders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = AIFabricRuntimeApplication.class,
    args = "--spring.config.import=classpath:test-runtime-entity-config.yml", properties = {
        "spring.profiles.active=configured-authoring", "spring.datasource.url=jdbc:h2:mem:durable-goal-host;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop", "OPENAI_ENABLED=false", "loomai.provider-budget.enabled=false",
        "loomai.configuration-assistant.platform-base-url=https://platform.example.test", "loomai.runtime.release-id=release-test",
        "AI_FABRIC_RUNTIME_AUTHZ_MODE=ALLOW_VERIFIED", "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=runtime-authoring-test-key",
        "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=runtime-authoring-test-signing-key",
        "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=backend-test", "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=dep-test",
        "ai.execution.async.encryption-secret=goal-host-test-encryption-secret-at-least-32-characters",
        "ai.execution.async.fingerprint-secret=goal-host-test-fingerprint-secret-at-least-32-characters",
        "ai.execution.async.recovery-interval=PT0.1S",
        "loomai.conversation-handover.callback-signing-secret=goal-host-conversation-owner-secret-at-least-32-characters",
        "loomai.conversation-handover.encryption-secret=goal-host-conversation-encryption-secret-at-least-32-characters",
        "logging.level.ai.fabric=WARN"
    })
@Import(RuntimeDurableGoalIntegrationTest.Funding.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuntimeDurableGoalIntegrationTest {
    @Autowired WorkflowAssistanceBundle bundle;
    @Autowired ConversationHandoverController conversations;
    @Autowired ChatRuntimeController chat;
    @Autowired RuntimeAuthProperties authProperties;
    @Autowired RuntimePrivateAssertionService assertions;
    @MockitoSpyBean RuntimeDurableGoalHost durableHost;
    @Autowired DurableExecutionRepository repository;
    @Autowired DurableExecutionSecurity security;
    @Autowired AIExecutionGateway gateway;
    @Autowired ai.fabric.execution.specialist.SpecialistRegistry specialists;
    @Autowired ai.fabric.chat.control.ConversationControlGateway canonical;
    @Autowired JdbcTemplate jdbc;
    @Autowired RuntimeBudgetBindingService funded;
    @Autowired RuntimeBudgetTurnScopes goalScopes;
    @Autowired Funding funding;
    @Autowired org.springframework.web.context.WebApplicationContext web;
    @MockitoSpyBean AICoreService core;
    @MockitoSpyBean AuthoringPlatformClient platform;
    static final ObjectMapper MAPPER = WorkflowAssistanceBundleTest.MAPPER.copy().findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    static final String OFFLINE_KEY = "offline-goal-budget-fixture-not-a-provider-key";

    @DynamicPropertySource static void published(DynamicPropertyRegistry properties) throws Exception {
        ObjectNode compiled;
        try (var input = new ClassPathResource("workflow-assistance-goal-compiler-fixture.json").getInputStream()) {
            compiled = (ObjectNode) MAPPER.readTree(input).path("compiled");
        }
        for (var resource : compiled.path("resources")) if ("Specialist".equals(resource.path("kind").asText())
            && "workflow-api".equals(resource.path("metadata").path("name").asText())) {
            var workflow = (ObjectNode) resource.path("spec").path("execution").path("workflow");
            workflow.put("maxActions", 2).put("maxWrites", 1);
            workflow.putObject("goal").put("enabled", true).put("maxElapsed", "PT1H").put("continuationDelay", "PT0S")
                .put("maxConsecutiveNoProgress", 3).put("maxConsecutivePlannerFailures", 4).putArray("delegatedProfiles");
        }
        byte[] bytes = MAPPER.writeValueAsBytes(compiled); String hash = WorkflowAssistanceBundleTest.hash(bytes);
        properties.add("LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_BASE64", () -> Base64.getEncoder().encodeToString(bytes));
        properties.add("LOOMAI_WORKFLOW_ASSISTANCE_BUNDLE_HASH", () -> hash);
    }

    @Test void explicitPauseResumeDoesNotDependOnQueuedLifecycleNotificationBeforeTheRealSchedulerClaims() throws Exception {
        var run = pausedHttpGoal(); var client = run.client();
        doAnswer(call -> {
            var snapshot = call.getArgument(0, ExecutionSnapshot.class);
            if (snapshot.handle().status() == ExecutionHandleStatus.QUEUED) return null;
            return call.callRealMethod();
        }).when(durableHost).executionUpdated(any(), eq(client.goal()));
        try {
            var paused = repository.findById(run.id()).orElseThrow();
            var scope = new ai.fabric.chat.control.ConversationScope("tenant-test", "dep-test", client.conversation(), "authoring:" + client.session());
            var root = storedRoot(scope, client.goal());
            var resume = new RuntimeDurableGoalHost.Control("RESUME", client.goal(), client.goal(), client.session(), client.turn(), 1, 1, 1, null);
            authoringPostOverHttp(client, "/work/" + run.id() + "/control", "explicit-resume", resume, 200);
            await(run.id(), client, ExecutionHandleStatus.SUCCEEDED);
            authoringPostOverHttp(client, "/work/" + run.id() + "/control", "explicit-resume", resume, 200);
            assertThat(run.calls()).hasValue(3); assertThat(run.writes()).containsExactly(run.id() + ":step:1");
            assertThat(repository.findById(run.id()).orElseThrow().requestFingerprint()).isEqualTo(paused.requestFingerprint());
            assertThat(storedRoot(scope, client.goal()).budgetReceipt()).isEqualTo(root.budgetReceipt());
            assertThat(funded.readCurrent().chatAttempts()).isEqualTo(run.before().chatAttempts() + 1);
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'",
                Integer.class, client.conversation()) == 0 && System.nanoTime() < until) Thread.sleep(20);
            for (String role : List.of("USER", "AI")) assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role=?", Integer.class, client.conversation(), role)).isEqualTo(1);
        } finally { chat.deleteConversation(client.conversation(), "erase-normal-resume", client.request()); }
    }

    @Test void rolledBackControlAdmissionCannotPublishEitherQueuedRowOrAuthorizeWorkerEffects() throws Exception {
        var run = pausedHttpGoal(); var client = run.client();
        try {
            var transaction = new org.springframework.transaction.support.TransactionTemplate(
                web.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                conversations.controlWork(client.conversation(), run.id(), "resume-rollback", new RuntimeDurableGoalHost.Control("RESUME",
                    client.goal(), client.goal(), client.session(), client.turn(), 1, 1, 1, null), client.request());
                assertThat(repository.findById(run.id()).orElseThrow().status()).isEqualTo(ExecutionHandleStatus.QUEUED);
                assertThat(goalState(client).path("state").asText()).isEqualTo("QUEUED");
                assertThat(java.util.concurrent.CompletableFuture.supplyAsync(() -> repository.findById(run.id()).orElseThrow().status()).join())
                    .isEqualTo(ExecutionHandleStatus.PAUSED);
                assertThat(run.calls()).hasValue(1); assertThat(run.writes()).isEmpty();
                throw new IllegalStateException("fixture-control-publication-rollback");
            })).isInstanceOf(IllegalStateException.class).hasMessage("fixture-control-publication-rollback");
            assertThat(repository.findById(run.id()).orElseThrow().status()).isEqualTo(ExecutionHandleStatus.PAUSED);
            assertThat(goalState(client).path("state").asText()).isEqualTo("PAUSED");
            assertThat(goalState(client).path("lastControlKey").asText()).isEqualTo("explicit-pause");
            assertThat(run.calls()).hasValue(1); assertThat(run.writes()).isEmpty();
            authoringPostOverHttp(client, "/work/" + run.id() + "/control", "resume-rollback", new RuntimeDurableGoalHost.Control("RESUME",
                client.goal(), client.goal(), client.session(), client.turn(), 1, 1, 1, null), 200);
            await(run.id(), client, ExecutionHandleStatus.SUCCEEDED);
            assertThat(run.calls()).hasValue(3); assertThat(run.writes()).containsExactly(run.id() + ":step:1");
            assertThat(funded.readCurrent().chatAttempts()).isEqualTo(run.before().chatAttempts() + 1);
        } finally { chat.deleteConversation(client.conversation(), "erase-rollback-resume", client.request()); }
    }

    @Test void newerPauseFencesAnOlderResumeEvenWhenItsAuthorityRenewalAlreadySucceeded() throws Exception {
        var run = pausedHttpGoal(); var client = run.client();
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var oldGrant = lease(client, List.of(), List.of("source-one"));
        var blocked = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> {
            var work = call.getArgument(0, ChatQueryRequest.ConfigurationWork.class);
            var grant = oldGrant.apply(work, call.getArgument(1));
            if (work.cancellationEpoch() == 1 && blocked.compareAndSet(false, true)) {
                entered.countDown(); assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
            return grant;
        }).when(platform).renew(any(), nullable(String.class));
        java.util.concurrent.CompletableFuture<?> resuming = null;
        try {
            resuming = java.util.concurrent.CompletableFuture.runAsync(() -> conversations.controlWork(client.conversation(), run.id(),
                "older-resume", new RuntimeDurableGoalHost.Control("RESUME", client.goal(), client.goal(), client.session(), client.turn(), 1, 1, 1, null), client.request()));
            assertThat(entered.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            authoringPostOverHttp(client, "/work/" + run.id() + "/control", "newer-pause", new RuntimeDurableGoalHost.Control("PAUSE",
                client.goal(), client.goal(), client.session(), client.turn(), 1, 2, 1, null), 200);
            release.countDown();
            var rejected = catchThrowableOfType(resuming::join, java.util.concurrent.CompletionException.class);
            assertThat(rejected.getCause()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(((org.springframework.web.server.ResponseStatusException) rejected.getCause()).getStatusCode().value()).isEqualTo(409);
            assertThat(goalState(client).path("state").asText()).isEqualTo("PAUSED");
            assertThat(goalState(client).path("cancellationEpoch").asLong()).isEqualTo(2);
            assertThat(repository.findById(run.id()).orElseThrow().status()).isEqualTo(ExecutionHandleStatus.PAUSED);
            assertThat(run.calls()).hasValue(1); assertThat(run.writes()).isEmpty();
            authoringPostOverHttp(client, "/work/" + run.id() + "/control", "fresh-resume", new RuntimeDurableGoalHost.Control("RESUME",
                client.goal(), client.goal(), client.session(), client.turn(), 1, 2, 1, null), 200);
            await(run.id(), client, ExecutionHandleStatus.SUCCEEDED);
            assertThat(run.calls()).hasValue(3); assertThat(run.writes()).containsExactly(run.id() + ":step:1");
            assertThat(funded.readCurrent().chatAttempts()).isEqualTo(run.before().chatAttempts() + 1);
        } finally {
            release.countDown(); if (resuming != null) resuming.handle((ignored, failure) -> null).join();
            chat.deleteConversation(client.conversation(), "erase-raced-resume", client.request());
        }
    }

    @Test void canonicalHttpBoundedSenderRunsOnGoalEnabledProfileWithoutStartingADurableGoal() throws Exception {
        var client = client(); var profile = bundle.select("ACTIONS", null);
        assertThat(specialists.require(profile.specialist()).executionProfile().workflowPolicy().goalMode()).isTrue();
        var calls = new AtomicInteger(); var writes = new ConcurrentLinkedQueue<String>();
        var decisions = new ConcurrentLinkedQueue<>(List.of(save("The bounded edit is saved."),
            "{\"type\":\"COMPLETE\",\"message\":\"The bounded edit is saved in the draft.\"}"));
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall(); calls.incrementAndGet();
            return AIGenerationResponse.builder().content(Objects.requireNonNull(decisions.poll())).build();
        }).when(core).generateContent(any(), any());
        doAnswer(call -> {
            String action = call.getArgument(0); Map<String, Object> parameters = call.getArgument(1);
            ai.fabric.intent.action.ActionContext context = call.getArgument(2);
            assertThat(context.authContext().getSubjectId()).isEqualTo("authoring:" + client.session());
            assertThat(context.authContext().getAuthMode()).isEqualTo("PRIVATE_RUNTIME_BACKEND_MEDIATED");
            assertThat(context.authContext().getCallerType()).isEqualTo("TRUSTED_BACKEND");
            assertThat(context.authContext().getGrantedScopes()).contains("configuration:author");
            assertThat(context.metadata()).containsEntry("authoringSessionId", client.session())
                .containsEntry("authoringTurnId", client.turn()).doesNotContainKey("authoringGoalId");
            var result = MAPPER.createObjectNode();
            if ("authoring_update_draft".equals(action)) {
                assertThat(parameters.get("operationId")).isEqualTo(client.turn() + ":step:1");
                writes.add(String.valueOf(parameters.get("operationId")));
                result.put("operationId", String.valueOf(parameters.get("operationId"))).put("status", "EXECUTED").put("published", false);
            } else result.putObject("sourceCoverage").put("required", false);
            return new AuthoringPlatformClient.Reply(200, result);
        }).when(platform).call(anyString(), anyMap(), any());
        // Match ConfiguredAuthoringRuntimeClient.message, including explicit primitive defaults.
        var body = MAPPER.createObjectNode().put("query", "Make this bounded edit and save its draft.");
        var work = body.putObject("configurationWork").put("sessionId", client.session()).put("turnId", client.turn())
            .put("task", "ACTIONS").put("profileId", profile.id()).put("releaseId", "release-test")
            .put("bundleHash", bundle.contentHash()).put("previewOnly", false).put("maxCorrections", 2)
            .put("goalMode", false).put("requestRevision", 0).put("cancellationEpoch", 0).put("segment", 0);
        assertThat(work.fieldNames()).toIterable().containsExactlyInAnyOrder("sessionId", "turnId", "task", "profileId",
            "releaseId", "bundleHash", "previewOnly", "maxCorrections", "goalMode", "requestRevision", "cancellationEpoch", "segment");
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
        var converters = web.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class).getMessageConverters();
        assertThat(converters.stream().filter(converter -> converter.canRead(ChatQueryRequest.class,
            org.springframework.http.MediaType.APPLICATION_JSON)).findFirst().orElseThrow().getClass().getName())
            .contains("JacksonJsonHttpMessageConverter");
        java.util.function.Function<JsonNode, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> post = value -> {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/chat/me/conversations/{conversation}/messages", client.conversation())
                .contentType("application/json").content(value.toString()).header("Idempotency-Key", client.turn());
            client.request().getHeaderNames().asIterator().forEachRemaining(name -> request.header(name, client.request().getHeader(name)));
            return request;
        };
        var before = funded.readCurrent(); var binding = funded.readStore().binding();
        for (String field : List.of("goalMode", "requestRevision", "cancellationEpoch", "segment")) {
            var omitted = body.deepCopy(); ((ObjectNode) omitted.path("configurationWork")).remove(field);
            mvc.perform(post.apply(omitted)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
            var explicitNull = body.deepCopy(); ((ObjectNode) explicitNull.path("configurationWork")).putNull(field);
            mvc.perform(post.apply(explicitNull)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/chat/me/conversations/{conversation}/messages", client.conversation()).contentType("application/json")
            .content(body.toString()).header("Idempotency-Key", client.turn()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is4xxClientError());
        assertThat(calls).hasValue(0); assertThat(writes).isEmpty(); assertThat(funded.readCurrent()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=?", Integer.class, client.conversation())).isZero();
        var response = mvc.perform(post.apply(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var result = MAPPER.readTree(response.getResponse().getContentAsByteArray());
        assertThat(result.path("success").asBoolean()).as("bounded native HTTP response %s", result).isTrue();
        assertThat(result.path("type").asText()).isNotEqualTo("WORK_ACCEPTED");
        assertThat(result.path("workId").isMissingNode()).isTrue(); assertThat(result.path("goal").isMissingNode()).isTrue();
        var workflow = result.path("metadata").path("workflow");
        assertThat(workflow.path("state").asText()).isEqualTo("COMPLETE");
        assertThat(workflow.path("usage").path("modelCalls").asInt()).isEqualTo(2);
        assertThat(workflow.path("usage").path("writes").asInt()).isEqualTo(1);
        assertThat(calls).hasValue(2); assertThat(writes).containsExactly(client.turn() + ":step:1");
        var bounded = repository.findById(client.turn()).orElseThrow();
        assertThat(bounded.status()).isEqualTo(ExecutionHandleStatus.SUCCEEDED); assertThat(bounded.goalExecution()).isFalse();
        assertThat(repository.findById(client.goal())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_specialist_execution WHERE goal_authority_reference=?", Integer.class, client.goal())).isZero();
        var scope = new ai.fabric.chat.control.ConversationScope("tenant-test", "dep-test", client.conversation(), "authoring:" + client.session());
        canonical.orchestrationState(scope, 0).ifPresent(state -> assertThat(MAPPER.valueToTree(state.metadata()).has("authoringGoalRoots")).isFalse());
        verify(platform, never()).renew(any(), nullable(String.class));
        var replay = mvc.perform(post.apply(body)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        assertThat(MAPPER.readTree(replay.getResponse().getContentAsByteArray())).isEqualTo(result);
        assertThat(calls).hasValue(2); assertThat(writes).hasSize(1);
        assertThat(funded.readCurrent().chatAttempts()).isEqualTo(before.chatAttempts() + 1);
        assertThat(funded.readCurrent().providerAttempts()).isEqualTo(before.providerAttempts());
        assertThat(funded.readCurrent().accountedUsage()).isEqualTo(before.accountedUsage());
        assertThat(funded.readCurrent().approval()).isEqualTo(before.approval());
        assertThat(funded.readCurrent().effectiveLimits()).isEqualTo(before.effectiveLimits());
        assertThat(funded.readStore().binding()).isEqualTo(binding);
        for (String role : List.of("USER", "AI")) assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role=?", Integer.class, client.conversation(), role)).isEqualTo(1);
        chat.deleteConversation(client.conversation(), "erase-bounded-http", client.request());
        assertThat(repository.findById(client.turn())).isEmpty();
    }

    @Test void canonicalHttpAdmissionSurvivesUnavailableRenewalAndResumesTheSameNativeRoot() throws Exception {
        var client = client(); var unavailable = new java.util.concurrent.atomic.AtomicBoolean(true);
        var calls = new AtomicInteger(); var writes = new ConcurrentLinkedQueue<String>();
        renew(client, null);
        var successfulRenewal = lease(client, List.of(), List.of("source-one"));
        doAnswer(call -> {
            assertThat(call.getArgument(1, String.class)).startsWith("exec-");
            if (unavailable.get()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            var lease = successfulRenewal.apply(call.getArgument(0), call.getArgument(1));
            ((ObjectNode) lease.path("sourceVersions")).put("source-one", "a".repeat(64));
            return lease;
        }).when(platform).renew(any(), nullable(String.class));
        var decisions = new ConcurrentLinkedQueue<>(List.of(save("Saved after authority restoration"),
            "{\"type\":\"COMPLETE\",\"message\":\"The requested draft operation is saved.\"}"));
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall(); calls.incrementAndGet();
            return AIGenerationResponse.builder().content(Objects.requireNonNull(decisions.poll())).build();
        }).when(core).generateContent(any(), any());
        doAnswer(call -> {
            String action = call.getArgument(0); Map<String, Object> parameters = call.getArgument(1);
            var result = MAPPER.createObjectNode();
            if ("authoring_update_draft".equals(action)) {
                writes.add(String.valueOf(parameters.get("operationId")));
                result.put("operationId", String.valueOf(parameters.get("operationId"))).put("status", "EXECUTED");
            } else result.putObject("sourceCoverage").put("required", false);
            return new AuthoringPlatformClient.Reply(200, result);
        }).when(platform).call(anyString(), anyMap(), any());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build();
        var converters = web.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class).getMessageConverters();
        assertThat(converters.stream().filter(converter -> converter.canWrite(com.ai.fabric.runtime.web.dto.ChatQueryResponse.class,
            org.springframework.http.MediaType.APPLICATION_JSON)).findFirst().orElseThrow().getClass().getName())
            .contains("JacksonJsonHttpMessageConverter");
        var before = funded.readCurrent();
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/chat/me/conversations/{conversation}/messages", client.conversation())
            .contentType("application/json").content(MAPPER.writeValueAsBytes(client.body())).header("Idempotency-Key", client.turn());
        client.request().getHeaderNames().asIterator().forEachRemaining(name -> request.header(name, client.request().getHeader(name)));
        var response = mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted()).andReturn();
        String id = MAPPER.readTree(response.getResponse().getContentAsByteArray()).path("workId").asText();
        assertThat(id).startsWith("exec-"); await(id, client, ExecutionHandleStatus.PAUSED);
        var paused = repository.findById(id).orElseThrow();
        assertThat(paused.failureReason()).isEqualTo("AUTHORING_AUTHORITY_UNAVAILABLE");
        assertThat(paused.protectedWorkflowCheckpoint()).isNull(); assertThat(paused.nextEligibleAt()).isNull();
        assertThat(calls).hasValue(0); assertThat(writes).isEmpty();
        var pausedView = workOverHttp(client, id);
        assertThat(pausedView.path("state").asText()).isEqualTo("PAUSED");
        assertThat(pausedView.path("usage").isNull()).isTrue();
        assertThat(pausedView.path("progress").isNull()).isTrue();
        assertThat(pausedView.path("questions").isArray()).isTrue();
        assertThat(pausedView.path("canResume").isBoolean()).isTrue();
        assertThat(pausedView.path("canResume").asBoolean()).isTrue();
        verify(platform, never()).call(anyString(), anyMap(), any());
        assertThat(funded.readCurrent().chatAttempts()).isEqualTo(before.chatAttempts() + 1);
        assertThat(funded.readCurrent().providerAttempts()).isEqualTo(before.providerAttempts());
        var scope = new ai.fabric.chat.control.ConversationScope("tenant-test", "dep-test", client.conversation(), "authoring:" + client.session());
        var rootBefore = storedRoot(scope, client.goal());
        assertThat(rootBefore.sourceVersions()).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_conversation_ai_interaction WHERE conversation_id=? AND idempotency_key=?",
            String.class, client.conversation(), client.turn())).isEqualTo("COMPLETED");
        var replay = mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isAccepted()).andReturn();
        assertThat(MAPPER.readTree(replay.getResponse().getContentAsByteArray()).path("workId").asText()).isEqualTo(id);
        assertThat(funded.readCurrent().chatAttempts()).isEqualTo(before.chatAttempts() + 1);
        unavailable.set(false);
        var control = new RuntimeDurableGoalHost.Control("RESUME", client.goal(), client.goal(), client.session(), client.turn(), 1, 0, 1, null);
        var resume = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/chat/me/conversations/{conversation}/work/{work}/control", client.conversation(), id)
            .contentType("application/json").content(MAPPER.writeValueAsBytes(control)).header("Idempotency-Key", "resume-original-root");
        client.request().getHeaderNames().asIterator().forEachRemaining(name -> resume.header(name, client.request().getHeader(name)));
        mvc.perform(resume).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        await(id, client, ExecutionHandleStatus.SUCCEEDED);
        var goalView = workOverHttp(client, id);
        assertThat(goalView.path("usage").path("modelCalls").isIntegralNumber()).isTrue();
        assertThat(goalView.path("usage").path("modelCalls").asInt()).isEqualTo(2);
        assertThat(goalView.path("usage").path("writes").asInt()).isEqualTo(1);
        assertThat(goalView.path("workflow").path("state").asText()).isEqualTo("COMPLETE");
        assertThat(goalView.path("providerDiagnostics").path("observations").isArray()).isTrue();
        var nextEligibleAt = repository.findById(id).orElseThrow().nextEligibleAt();
        assertThat(goalView.path("nextEligibleAt")).isEqualTo(nextEligibleAt == null ? MAPPER.nullNode()
            : MAPPER.getNodeFactory().textNode(nextEligibleAt.toString()));
        var rootAfter = storedRoot(scope, client.goal());
        assertThat(rootAfter.budgetReceipt()).isEqualTo(rootBefore.budgetReceipt());
        assertThat(rootAfter.sourceVersions()).isEqualTo(MAPPER.createObjectNode().put("source-one", "a".repeat(64)));
        assertThat(repository.findById(id).orElseThrow().requestFingerprint()).isEqualTo(paused.requestFingerprint());
        assertThat(funded.readCurrent().chatAttempts()).isEqualTo(before.chatAttempts() + 1);
        assertThat(writes).containsExactly(id + ":step:1"); assertThat(calls).hasValue(2);
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, client.conversation()) == 0
            && System.nanoTime() < until) Thread.sleep(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='USER'", Integer.class, client.conversation())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, client.conversation())).isEqualTo(1);
        chat.deleteConversation(client.conversation(), "erase-admission-test", client.request());
    }

    @Test void aRenewalTransportFailurePausesTheRealCheckpointBeforeAnyModelAndResumesWithoutNewIngress() throws Exception {
        var client = client(); var renewal = lease(client, List.of(), List.of("source-one"));
        var calls = new AtomicInteger();
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall(); calls.incrementAndGet();
            return AIGenerationResponse.builder().content("{\"type\":\"COMPLETE\",\"message\":\"The explanation is complete.\"}").build();
        }).when(core).generateContent(any(), any());
        doReturn(new AuthoringPlatformClient.Reply(200, MAPPER.createObjectNode().set("sourceCoverage", MAPPER.createObjectNode().put("required", false))))
            .when(platform).call(eq("authoring_get_work_status"), anyMap(), any());
        doAnswer(call -> {
            String id = call.getArgument(1);
            if (id != null && repository.findById(id).orElseThrow().protectedWorkflowCheckpoint() != null)
                throw new IllegalStateException("AUTHORING_GOAL_AUTHORITY_UNAVAILABLE");
            return renewal.apply(call.getArgument(0), id);
        }).when(platform).renew(any(), nullable(String.class));
        var before = funded.readCurrent();
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        await(id, client, ExecutionHandleStatus.PAUSED);
        var trusted = RuntimeDurableGoalHost.trusted(RuntimeResolvedIdentity.builder().authContext(client.auth()).warnings(List.of()).build(), client.conversation());
        var checkpoint = gateway.findWorkflowCheckpoint(id, trusted).orElseThrow();
        assertThat(checkpoint.state()).isEqualTo(ai.fabric.intent.orchestration.workflow.WorkflowCheckpoint.State.PAUSED);
        assertThat(checkpoint.stopCode()).isEqualTo("AUTHORING_AUTHORITY_UNAVAILABLE");
        assertThat(calls).hasValue(0); assertThat(checkpoint.usage().writes()).isZero();
        assertThat(funded.readCurrent().providerAttempts()).isEqualTo(before.providerAttempts());
        renew(client, null);
        conversations.controlWork(client.conversation(), id, "resume-renewal-transport", new RuntimeDurableGoalHost.Control("RESUME",
            client.goal(), client.goal(), client.session(), client.turn(), 1, 0, 1, null), client.request());
        await(id, client, ExecutionHandleStatus.SUCCEEDED); assertThat(calls).hasValue(1);
        assertThat(funded.readCurrent().chatAttempts()).isEqualTo(before.chatAttempts() + 1);
        chat.deleteConversation(client.conversation(), "erase-renewal-transport", client.request());
    }

    @Test void sourceLessNativeGoalsPinAnEmptyFrozenMapRatherThanRemainingUnadmitted() throws Exception {
        var client = client(); renewSources(client, List.of());
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall();
            return AIGenerationResponse.builder().content("{\"type\":\"COMPLETE\",\"message\":\"The source-less explanation is complete.\"}").build();
        }).when(core).generateContent(any(), any());
        doReturn(new AuthoringPlatformClient.Reply(200, MAPPER.createObjectNode().set("sourceCoverage", MAPPER.createObjectNode().put("required", false))))
            .when(platform).call(eq("authoring_get_work_status"), anyMap(), any());
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        await(id, client, ExecutionHandleStatus.SUCCEEDED);
        var scope = new ai.fabric.chat.control.ConversationScope("tenant-test", "dep-test", client.conversation(), "authoring:" + client.session());
        assertThat(storedRoot(scope, client.goal()).sourceVersions()).isEqualTo(MAPPER.createObjectNode());
        chat.deleteConversation(client.conversation(), "erase-empty-sources", client.request());
    }

    private RuntimeDurableGoalHost.Root storedRoot(ai.fabric.chat.control.ConversationScope scope, String goal) {
        var roots = MAPPER.valueToTree(canonical.orchestrationState(scope, 0).orElseThrow().metadata()).path("authoringGoalRoots");
        return MAPPER.convertValue(security.unprotect(roots.path(goal).asText(), "canonical-goal-root:" + goal).get("root"), RuntimeDurableGoalHost.Root.class);
    }

    @Test void canonicalGoalUsesRealDurableSchedulerFreshScopesOneIngressAndOneFinalThenErasesAllContent() throws Exception {
        String suffix = UUID.randomUUID().toString(), session = "cas-" + suffix, turn = "cat-" + suffix, goal = "cag-" + suffix;
        String conversation = "authoring-" + suffix;
        var profile = bundle.select("ACTIONS", null);
        var scopes = new ArrayList<>(profile.requiredScopes()); scopes.addAll(List.of("chat:query", "chat:messages", "chat:conversations"));
        var auth = RuntimeAuthContext.builder().subjectId("authoring:" + session).sessionId("authoring:" + session)
            .subjectType(RuntimeAuthSubjectType.END_USER).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).tenantId("tenant-test").customerId("customer-test").deploymentId("dep-test")
            .issuer("backend-test").audiences(List.of("dep-test")).expiresAt(Instant.now().plusSeconds(180)).grantedScopes(scopes).build();
        var servlet = new org.springframework.mock.web.MockHttpServletRequest(); addPrivateRuntimeHeaders(servlet, authProperties, auth);
        var renewals = new ConcurrentLinkedQueue<Integer>();
        Instant oldAuthorityExpiry = Instant.now().plusSeconds(8);
        doAnswer(call -> {
            ChatQueryRequest.ConfigurationWork work = call.getArgument(0); String workId = call.getArgument(1);
            if (!Instant.now().isBefore(oldAuthorityExpiry)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Fixture login expired");
            renewals.add(work.segment());
            var lease = MAPPER.createObjectNode().put("authorityId", goal).put("goalId", goal).put("sessionId", session)
                .put("turnId", turn).put("conversationId", conversation).put("profileId", profile.id()).put("releaseId", "release-test")
                .put("bundleHash", bundle.contentHash()).put("previewOnly", false).put("validUntil", oldAuthorityExpiry.toString())
                .put("requestRevision", work.requestRevision()).put("cancellationEpoch", work.cancellationEpoch()).put("segment", work.segment());
            if (workId != null) lease.put("workId", workId).put("operationNamespace", workId);
            lease.putObject("sourceVersions").put("source-one", "sha256:" + "a".repeat(64));
            lease.putObject("runtimeHeaders").put(assertions.authorizationHeaderName(), assertions.tokenScheme() + " "
                + assertions.issueAssertion(auth.toBuilder().expiresAt(oldAuthorityExpiry).build()));
            return lease;
        }).when(platform).renew(any(), nullable(String.class));
        var decisions = new ConcurrentLinkedQueue<>(List.of(
            "{\"type\":\"ACTION\",\"action\":\"authoring_get_context\",\"parameters\":{}}",
            save("First private source-backed value"),
            "{\"type\":\"ACTION\",\"action\":\"authoring_get_context\",\"parameters\":{}}",
            save("Second private source-backed value"),
            "{\"type\":\"COMPLETE\",\"message\":\"Both requested draft operations are saved.\"}"));
        var calls = new AtomicInteger();
        var firstPlanning = new java.util.concurrent.CountDownLatch(1);
        var releaseFirst = new java.util.concurrent.CountDownLatch(1);
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall();
            if (calls.incrementAndGet() == 1) {
                firstPlanning.countDown(); assertThat(releaseFirst.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return AIGenerationResponse.builder().content("{\"type\":\"COMPLETE\",\"message\":\"Obsolete candidate\"}").build();
            }
            ai.fabric.dto.AIGenerationRequest generation = call.getArgument(0);
            var planning = MAPPER.readTree(generation.getPrompt());
            assertThat(planning.path("directiveContext").path("query").asText()).isEqualTo("Keep the same two operations and continue.");
            assertThat(planning.path("request").asText()).contains("Prepare both selected draft operations");
            String decision = decisions.poll(); assertThat(decision).isNotNull();
            return AIGenerationResponse.builder().content(decision).build();
        }).when(core).generateContent(any(), any());
        var actualWrites = new ConcurrentLinkedQueue<String>();
        doAnswer(call -> {
            String action = call.getArgument(0); Map<String, Object> parameters = call.getArgument(1);
            var result = MAPPER.createObjectNode();
            if ("authoring_update_draft".equals(action)) {
                actualWrites.add(String.valueOf(parameters.get("operationId")));
                result.put("operationId", String.valueOf(parameters.get("operationId"))).put("status", "EXECUTED");
            } else if ("authoring_get_work_status".equals(action)) {
                result.putArray("operations"); result.putObject("sourceCoverage").put("required", true).put("complete", actualWrites.size() == 2);
            } else result.putObject("configuration").put("privateSource", "Private source fixture body");
            return new AuthoringPlatformClient.Reply(200, result);
        }).when(platform).call(anyString(), anyMap(), any());
        long beforeIngress = funded.readCurrent().chatAttempts();
        var body = new ChatQueryRequest(); body.setQuery("Prepare both selected draft operations from the private source.");
        body.setConfigurationWork(new ChatQueryRequest.ConfigurationWork(session, turn, "ACTIONS", profile.id(), "release-test",
            bundle.contentHash(), false, 2, true, goal, goal, oldAuthorityExpiry, 1, 0, 1));
        var acknowledgement = conversations.message(conversation, turn, body, servlet);
        assertThat(acknowledgement.getStatusCode().value()).isEqualTo(202);
        String workId = acknowledgement.getBody().getWorkId(); assertThat(workId).startsWith("exec-");
        assertThat(acknowledgement.getBody().getAnswer()).isNull();
        assertThat(firstPlanning.await(10, java.util.concurrent.TimeUnit.SECONDS)).as("native snapshot %s",
            gateway.find(workId, RuntimeDurableGoalHost.trusted(RuntimeResolvedIdentity.builder().authContext(auth).warnings(List.of()).build(), conversation))).isTrue();
        var pause = new RuntimeDurableGoalHost.Control("PAUSE", goal, goal, session, turn, 1, 1, 1, null);
        var paused = conversations.controlWork(conversation, workId, "pause-one", pause, servlet);
        assertThat(paused.getGoal()).containsEntry("state", "PAUSED");
        assertThat(repository.findById(workId).orElseThrow().leaseOwner()).isNull();
        assertThat(repository.findById(workId).orElseThrow().nextEligibleAt()).isNull();
        var canonicalScope = new ai.fabric.chat.control.ConversationScope("tenant-test", "dep-test", conversation, "authoring:" + session);
        assertThat(MAPPER.valueToTree(canonical.orchestrationState(canonicalScope, 0).orElseThrow().metadata()).path("authoringGoal").has("segmentLease")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, conversation)).isZero();
        releaseFirst.countDown();
        while (Instant.now().isBefore(oldAuthorityExpiry.plusMillis(50))) Thread.sleep(20);
        assertThat(calls).hasValue(1);
        assertThat(repository.findById(workId).orElseThrow().deadline()).isAfter(oldAuthorityExpiry.plusSeconds(3000));
        doAnswer(call -> {
            ChatQueryRequest.ConfigurationWork work = call.getArgument(0); String id = call.getArgument(1);
            renewals.add(work.segment());
            var lease = MAPPER.createObjectNode().put("authorityId", goal).put("goalId", goal).put("sessionId", session)
                .put("turnId", turn).put("conversationId", conversation).put("profileId", profile.id()).put("releaseId", "release-test")
                .put("bundleHash", bundle.contentHash()).put("previewOnly", false).put("validUntil", Instant.now().plusSeconds(3600).toString())
                .put("requestRevision", work.requestRevision()).put("cancellationEpoch", work.cancellationEpoch()).put("segment", work.segment());
            if (id != null) lease.put("workId", id).put("operationNamespace", id);
            lease.putObject("directive").put("query", "Keep the same two operations and continue.");
            lease.putObject("sourceVersions").put("source-one", "sha256:" + "a".repeat(64));
            lease.putObject("runtimeHeaders").put(assertions.authorizationHeaderName(), assertions.tokenScheme() + " "
                + assertions.issueAssertion(auth.toBuilder().expiresAt(Instant.now().plusSeconds(180)).build()));
            return lease;
        }).when(platform).renew(any(), nullable(String.class));
        var directive = new RuntimeDurableGoalHost.Control("DIRECTIVE", goal, goal, session, turn, 2, 1, 1,
            MAPPER.createObjectNode().put("query", "Keep the same two operations and continue."));
        conversations.controlWork(conversation, workId, "directive-two", directive, servlet);
        assertThat(repository.findById(workId).orElseThrow().status()).isEqualTo(ExecutionHandleStatus.PAUSED);
        conversations.controlWork(conversation, workId, "resume-two",
            new RuntimeDurableGoalHost.Control("RESUME", goal, goal, session, turn, 2, 1, 1, null), servlet);
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        ExecutionSnapshot finished;
        var identity = RuntimeResolvedIdentity.builder().authContext(auth).warnings(List.of()).build();
        do {
            finished = gateway.find(workId, RuntimeDurableGoalHost.trusted(identity, conversation)).orElseThrow();
            if (Set.of(ExecutionHandleStatus.SUCCEEDED, ExecutionHandleStatus.FAILED, ExecutionHandleStatus.PAUSED).contains(finished.handle().status())) break;
            Thread.sleep(50);
        } while (System.nanoTime() < until);
        assertThat(finished.handle().status()).as("goal result %s", finished).isEqualTo(ExecutionHandleStatus.SUCCEEDED);
        assertThat(actualWrites).hasSize(2).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).startsWith(workId + ":step:"));
        assertThat(calls).hasValue(6); assertThat(renewals).contains(1, 2, 3);
        assertThat(funded.readCurrent().chatAttempts()).isEqualTo(beforeIngress + 1);
        assertThat(gateway.findWorkflowCheckpoint(workId, RuntimeDurableGoalHost.trusted(identity, conversation)).orElseThrow().usage().writes()).isEqualTo(2);
        until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, conversation) == 0
            && System.nanoTime() < until) Thread.sleep(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='USER'", Integer.class, conversation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, conversation)).isEqualTo(1);
        durableHost.executionUpdated(finished, goal); durableHost.executionUpdated(finished, goal);
        assertThat(MAPPER.valueToTree(canonical.orchestrationState(canonicalScope, 0).orElseThrow().metadata()).path("authoringGoal").has("segmentLease")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, conversation)).isEqualTo(1);
        var replay = conversations.message(conversation, turn, body, servlet);
        assertThat(replay.getStatusCode().value()).isEqualTo(202); assertThat(replay.getBody().getWorkId()).isEqualTo(workId);
        assertThat(actualWrites).hasSize(2);
        var protectedRecord = repository.findById(workId).orElseThrow();
        var payload = security.unprotect(protectedRecord.protectedRequest(), workId + ":request");
        assertThat(payload.toString()).doesNotContain("Bearer", "runtime-authoring-test-key", "authExpiresAt", "callerIdentity={");
        assertThat(chat.deleteConversation(conversation, "delete-" + turn, servlet).getStatusCode().value()).isEqualTo(204);
        var erased = repository.findById(workId).orElseThrow();
        assertThat(erased.status()).isEqualTo(ExecutionHandleStatus.CANCELLED);
        assertThat(erased.protectedWorkflowCheckpoint()).isNull(); assertThat(erased.protectedResult()).isNull();
        assertThat(security.unprotect(erased.protectedRequest(), workId + ":request")).containsEntry("erased", true).doesNotContainKey("input");
        assertThat(erased.goalNotificationPending()).isFalse();
        assertThat(jdbc.queryForList("SELECT content FROM ai_conversation_message WHERE conversation_id=?", String.class, conversation)).isEmpty();
        assertThat(funded.readCurrent().chatAttempts()).isEqualTo(beforeIngress + 1);
    }

    @Test void actualQuestionCanResumeTheSameNativeGoalWithoutFabricatedConversationTurns() throws Exception {
        var client = client();
        String firstUpdate = "Use the existing owned connection.", secondUpdate = "Keep that connection and use its approved authentication.";
        renewUpdates(client, List.of(firstUpdate, secondUpdate));
        String question = "Which owned connection should I use?";
        var calls = new AtomicInteger();
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall();
            int number = calls.incrementAndGet();
            if (number == 1) return AIGenerationResponse.builder().content(
                "{\"type\":\"NEEDS_INPUT\",\"questions\":[\"" + question + "\"]}").build();
            ai.fabric.dto.AIGenerationRequest generation = call.getArgument(0);
            var directive = MAPPER.readTree(generation.getPrompt()).path("directiveContext");
            assertThat(directive.path("requestUpdates").size()).isEqualTo(number - 1);
            assertThat(directive.path("requestUpdates").get(0).path("query").asText()).isEqualTo(firstUpdate);
            if (number == 2) return AIGenerationResponse.builder().content(
                "{\"type\":\"NEEDS_INPUT\",\"questions\":[\"Which approved authentication should I use?\"]}").build();
            assertThat(directive.path("query").asText()).isEqualTo(secondUpdate);
            assertThat(directive.path("requestUpdates").get(1).path("query").asText()).isEqualTo(secondUpdate);
            return AIGenerationResponse.builder().content("{\"type\":\"COMPLETE\",\"message\":\"The requested explanation is complete.\"}").build();
        }).when(core).generateContent(any(), any());
        doReturn(new AuthoringPlatformClient.Reply(200, MAPPER.createObjectNode().set("sourceCoverage",
            MAPPER.createObjectNode().put("required", false).put("complete", false))))
            .when(platform).call(eq("authoring_get_work_status"), anyMap(), any());
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        await(id, client, ExecutionHandleStatus.PAUSED);
        var waiting = conversations.work(client.conversation(), id, client.request());
        assertThat(waiting.getGoal()).containsEntry("state", "WAITING_FOR_INPUT").containsEntry("questions", List.of(question))
            .containsEntry("answer", question).containsEntry("nextAction", "ANSWER_AND_RESUME");
        conversations.controlWork(client.conversation(), id, "answer-one", new RuntimeDurableGoalHost.Control("DIRECTIVE",
            client.goal(), client.goal(), client.session(), client.turn(), 2, 1, 1,
            MAPPER.createObjectNode().put("query", firstUpdate)), client.request());
        await(id, client, ExecutionHandleStatus.PAUSED);
        conversations.controlWork(client.conversation(), id, "answer-two", new RuntimeDurableGoalHost.Control("DIRECTIVE",
            client.goal(), client.goal(), client.session(), client.turn(), 3, 2, 1,
            MAPPER.createObjectNode().put("query", secondUpdate)), client.request());
        await(id, client, ExecutionHandleStatus.SUCCEEDED);
        assertThat(calls).hasValue(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='USER'", Integer.class, client.conversation())).isEqualTo(1);
        chat.deleteConversation(client.conversation(), "erase-question", client.request());
    }

    @Test void canonicalInterruptFencesLateNativeOutputAndErasureStillFindsTheClosedEpochWork() throws Exception {
        var client = client(); renew(client, null);
        var started = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall(); calls.incrementAndGet(); started.countDown();
            assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return AIGenerationResponse.builder().content("{\"type\":\"COMPLETE\",\"message\":\"Late cancelled answer\"}").build();
        }).when(core).generateContent(any(), any());
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        assertThat(started.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        conversations.interrupt(client.conversation(), "interrupt-goal", new ConversationHandoverController.Interruption(client.turn()), client.request());
        release.countDown(); await(id, client, ExecutionHandleStatus.CANCELLED); Thread.sleep(100);
        assertThat(calls).hasValue(1);
        assertThat(conversations.work(client.conversation(), id, client.request()).getGoal()).containsEntry("state", "CANCELLED").containsEntry("terminal", true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, client.conversation())).isZero();
        chat.deleteConversation(client.conversation(), "erase-cancelled-goal", client.request());
        var erased = repository.findById(id).orElseThrow();
        assertThat(security.unprotect(erased.protectedRequest(), id + ":request")).containsEntry("erased", true).doesNotContainKey("input");
        assertThat(erased.protectedWorkflowCheckpoint()).isNull(); assertThat(erased.protectedResult()).isNull();
    }

    @Test void nativeTokenAdmissionPausesTheCheckpointAndAuthorizedTopUpResumesTheSameRoot() throws Exception {
        var client = client(); renew(client, null);
        var original = funded.readCurrent();
        UUID hold = UUID.randomUUID();
        funding.ledger.reserveAttempt(original.approval().id(), original.approval().scope(), hold,
            new ProviderBudgetLedger.Usage(original.effectiveLimits().totalTokens() - original.accountedUsage().totalTokens() - 1, 0, 0));
        var providers = new ai.fabric.config.AIProviderConfig();
        providers.getOpenai().setApiKey(OFFLINE_KEY); providers.getOpenai().setModel(ReviewedOpenAiRateCard.CHAT_MODEL);
        providers.getOpenai().setEmbeddingModel(ReviewedOpenAiRateCard.EMBEDDING_MODEL); providers.getOpenai().setMaxTokens(300);
        providers.setEmbeddingProvider("openai");
        try (var nativeFixture = new LoopbackGuardedOpenAi(providers, funding.ledger, original.approval(), goalScopes,
            "{\"type\":\"COMPLETE\",\"message\":\"The requested explanation is complete.\"}")) {
            var resolver = nativeFixture.resolver();
            var calls = new AtomicInteger();
            doAnswer(call -> {
                WorkflowModelCallScope.beforeModelCall();
                calls.incrementAndGet();
                ai.fabric.dto.AIGenerationRequest generation = call.getArgument(0);
                var response = resolver.resolveChatModel("openai", generation).call(new org.springframework.ai.chat.prompt.Prompt("offline denied fixture",
                    resolver.resolveChatOptions("openai", generation)));
                return AIGenerationResponse.builder().content(response.getResult().getOutput().getText()).build();
            }).when(core).generateContent(any(), any());
            doReturn(new AuthoringPlatformClient.Reply(200, MAPPER.createObjectNode().set("sourceCoverage",
                MAPPER.createObjectNode().put("required", false))))
                .when(platform).call(eq("authoring_get_work_status"), anyMap(), any());
            String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
            await(id, client, ExecutionHandleStatus.PAUSED);
            var trusted = RuntimeDurableGoalHost.trusted(RuntimeResolvedIdentity.builder().authContext(client.auth()).warnings(List.of()).build(), client.conversation());
            var checkpoint = gateway.findWorkflowCheckpoint(id, trusted).orElseThrow();
            assertThat(checkpoint.state()).isEqualTo(ai.fabric.intent.orchestration.workflow.WorkflowCheckpoint.State.PAUSED);
            assertThat(checkpoint.stopCode()).isIn("PROVIDER_BUDGET_LIMIT_EXCEEDED", "WORKFLOW_PROVIDER_ADMISSION_DENIED");
            assertThat(calls).hasValue(1); assertThat(checkpoint.usage().modelCalls()).isEqualTo(1);
            assertThat(nativeFixture.sends()).isZero();
            assertThat(repository.findById(id).orElseThrow().leaseOwner()).isNull();
            assertThat(conversations.work(client.conversation(), id, client.request()).getGoal()).containsEntry("canResume", true);
            var binding = funded.readStore().binding();
            var limits = funded.readCurrent().effectiveLimits();
            funded.topUp(new ProviderBudgetLedger.TopUpCommand(1, "topup-" + client.goal(), original.approval().id(), binding.storeUuid(),
                binding.runtimeFingerprint(), limits, new ProviderBudgetLedger.Limits(0, 1000000, 0),
                "offline-test-operator", "offline-approved-credit", "offline-payer", "Restore fixture token allowance"));
            conversations.controlWork(client.conversation(), id, "resume-funded", new RuntimeDurableGoalHost.Control("RESUME",
                client.goal(), client.goal(), client.session(), client.turn(), 1, 0, 1, null), client.request());
            await(id, client, ExecutionHandleStatus.SUCCEEDED);
            var resumed = gateway.findWorkflowCheckpoint(id, trusted).orElseThrow();
            assertThat(resumed.usage().modelCalls()).isEqualTo(2); assertThat(resumed.usage().writes()).isZero();
            assertThat(funded.readCurrent().chatAttempts()).isEqualTo(original.chatAttempts() + 1);
            assertThat(funded.readCurrent().providerAttempts()).isEqualTo(original.providerAttempts() + 2);
            assertThat(funded.readCurrent().approval()).isEqualTo(original.approval());
            assertThat(calls).hasValue(2);
            assertThat(nativeFixture.sends()).isEqualTo(1);
            var goalView = workOverHttp(client, id);
            assertThat(goalView.path("usage").path("modelCalls").asInt()).isEqualTo(2);
            var observed = goalView.path("providerDiagnostics").path("observations");
            assertThat(observed.size()).isEqualTo(1); assertThat(observed.get(0).path("inputTokens").asInt()).isEqualTo(12);
            assertThat(observed.get(0).path("finishReason").asText()).isEqualTo("stop");
            assertThat(observed.get(0).path("contentEmpty").isBoolean()).isTrue();
            assertThat(observed.get(0).path("contentEmpty").asBoolean()).isFalse();
            chat.deleteConversation(client.conversation(), "erase-funded", client.request());
        } finally {
            funding.ledger.settle(original.approval().id(), original.approval().scope(), hold, new ProviderBudgetLedger.Usage(0, 0, 0), "offline-fixture-hold-reconciled");
        }
    }

    @Test void latestNativeSourceReadRetainsTheMappingAtTheEndOfTheActual24kChunk() throws Exception {
        var client = client(); renew(client, null);
        String marker = "REQUIRED_MAPPING customerId -> /parameters/customerId";
        String chunk = "x".repeat(24000 - marker.length()) + marker;
        var calls = new AtomicInteger();
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall();
            if (calls.incrementAndGet() == 1) return AIGenerationResponse.builder().content(
                "{\"type\":\"ACTION\",\"action\":\"authoring_read_selected_sources\",\"parameters\":{\"sourceId\":\"source-one\",\"contentOffset\":0,\"maxCharacters\":24000}}").build();
            ai.fabric.dto.AIGenerationRequest generation = call.getArgument(0);
            var prompt = MAPPER.readTree(generation.getPrompt());
            assertThat(generation.getPrompt()).hasSizeLessThan(120001);
            assertThat(generation.getPrompt()).contains(chunk);
            assertThat(prompt.findValues("nextOffset")).extracting(JsonNode::asInt).contains(24000);
            return AIGenerationResponse.builder().content("{\"type\":\"COMPLETE\",\"message\":\"The required mapping is present in the selected source.\"}").build();
        }).when(core).generateContent(any(), any());
        var read = MAPPER.createObjectNode();
        read.putObject("content").put("sourceId", "source-one").put("offset", 0).put("text", chunk)
            .put("totalCharacters", 30000).put("truncated", true).put("nextOffset", 24000);
        doReturn(new AuthoringPlatformClient.Reply(200, read)).when(platform).call(eq("authoring_read_selected_sources"), anyMap(), any());
        doReturn(new AuthoringPlatformClient.Reply(200, MAPPER.createObjectNode().set("sourceCoverage", MAPPER.createObjectNode().put("required", false))))
            .when(platform).call(eq("authoring_get_work_status"), anyMap(), any());
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        await(id, client, ExecutionHandleStatus.SUCCEEDED); assertThat(calls).hasValue(2);
        chat.deleteConversation(client.conversation(), "erase-source-read", client.request());
    }

    @Test void setupDelegatesAnApiSaveUnderTheSameNativeRootAuthorityAndBudget() throws Exception {
        var client = client("BRAIN_SETUP"); renewSources(client, List.of("source-one", "source-two"));
        client.body().setQuery("Prepare the selected capability and save its editable draft.");
        long before = funded.readCurrent().chatAttempts();
        var calls = new AtomicInteger(); var writes = new ConcurrentLinkedQueue<String>();
        var focusedProfiles = new ConcurrentLinkedQueue<String>(); var focusedInstructions = new ConcurrentLinkedQueue<Boolean>();
        var returnedToRoot = new java.util.concurrent.atomic.AtomicBoolean();
        var toolIdentities = new ConcurrentLinkedQueue<Map<String, Object>>(); var readSources = new ConcurrentLinkedQueue<String>();
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall();
            ai.fabric.dto.AIGenerationRequest generation = call.getArgument(0);
            var prompt = MAPPER.readTree(generation.getPrompt()); int number = calls.incrementAndGet();
            if (number > 1 && number < 5) {
                focusedProfiles.add(prompt.at("/goal/activeDelegation/task/profileId").asText());
                focusedInstructions.add(!prompt.path("delegatedInstructions").asText().isBlank());
            }
            if (number == 5) returnedToRoot.set(prompt.at("/goal/activeDelegation").isNull() || prompt.at("/goal/activeDelegation").isMissingNode());
            String decision = switch (number) {
                case 1 -> delegation("api", generation.getEntityId(), List.of("source-one"));
                case 2 -> "{\"type\":\"ACTION\",\"action\":\"authoring_read_selected_sources\",\"parameters\":{\"sourceId\":\"source-one\"}}";
                case 3 -> save("The selected capability was prepared from its owned source.");
                case 4 -> "{\"type\":\"COMPLETE\",\"message\":\"The delegated API draft is saved.\"}";
                case 5 -> "{\"type\":\"COMPLETE\",\"message\":\"The requested setup draft is saved.\"}";
                default -> throw new IllegalStateException("Unexpected additional planner call");
            };
            return AIGenerationResponse.builder().content(decision).build();
        }).when(core).generateContent(any(), any());
        doAnswer(call -> {
            String action = call.getArgument(0); Map<String, Object> parameters = call.getArgument(1);
            ai.fabric.intent.action.ActionContext context = call.getArgument(2);
            var identity = new LinkedHashMap<String, Object>();
            for (String field : List.of("authoringSessionId", "authoringTurnId", "authoringGoalId")) identity.put(field, context.metadata().get(field));
            identity.put("subjectId", context.authContext() == null ? null : context.authContext().getSubjectId()); toolIdentities.add(identity);
            var result = MAPPER.createObjectNode();
            if ("authoring_read_selected_sources".equals(action)) {
                readSources.add(String.valueOf(parameters.get("sourceId"))); result.put("sourceId", "source-one").put("content", "Selected owned capability contract.");
            } else if ("authoring_update_draft".equals(action)) {
                String operation = String.valueOf(parameters.get("operationId")); writes.add(operation);
                result.put("operationId", operation).put("status", "EXECUTED");
            } else if ("authoring_get_work_status".equals(action)) result.putObject("sourceCoverage").put("required", false);
            else throw new IllegalStateException("Unexpected delegated tool");
            return new AuthoringPlatformClient.Reply(200, result);
        }).when(platform).call(anyString(), anyMap(), any());
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        try {
        await(id, client, ExecutionHandleStatus.SUCCEEDED);
        var trusted = RuntimeDurableGoalHost.trusted(RuntimeResolvedIdentity.builder().authContext(client.auth()).warnings(List.of()).build(), client.conversation());
        var checkpoint = gateway.findWorkflowCheckpoint(id, trusted).orElseThrow();
        assertThat(checkpoint.steps()).hasSize(3);
        assertThat(checkpoint.steps().getFirst().action()).isEqualTo("profile:api");
        assertThat(checkpoint.steps().getFirst().parameterFingerprint()).isEqualTo(specialists.requireRegistered(bundle.byId("api").specialist()).contentHash());
        assertThat(checkpoint.steps()).allSatisfy(step -> assertThat(step.operationId()).startsWith(id + ":step:"));
        assertThat(writes).containsExactly(id + ":step:3");
        assertThat(checkpoint.goal().activeDelegation()).isNull();
        assertThat(checkpoint.usage().modelCalls()).isEqualTo(5); assertThat(checkpoint.usage().writes()).isEqualTo(1);
        assertThat(calls).hasValue(5); assertThat(funded.readCurrent().chatAttempts()).isEqualTo(before + 1);
        assertThat(focusedProfiles).containsExactly("api", "api", "api");
        assertThat(focusedInstructions).containsExactly(true, true, true); assertThat(returnedToRoot).isTrue();
        assertThat(readSources).containsExactly("source-one");
        assertThat(toolIdentities).isNotEmpty().allSatisfy(identity -> assertThat(identity).containsEntry("authoringSessionId", client.session())
            .containsEntry("authoringTurnId", client.turn()).containsEntry("authoringGoalId", client.goal()).containsEntry("subjectId", client.auth().getSubjectId()));
        verify(platform, atLeastOnce()).renew(argThat(work -> "setup".equals(work.profileId()) && "BRAIN_SETUP".equals(work.task())), eq(id));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='USER'", Integer.class, client.conversation())).isEqualTo(1);
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, client.conversation()) == 0
            && System.nanoTime() < until) Thread.sleep(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id=? AND participant_role='AI'", Integer.class, client.conversation())).isEqualTo(1);
        } finally { chat.deleteConversation(client.conversation(), "erase-delegated-goal", client.request()); }
    }

    static java.util.stream.Stream<Arguments> deniedDelegations() {
        return java.util.stream.Stream.of(
            Arguments.of("setup", "authoring_get_context", null, List.of("source-one"), 1),
            Arguments.of("guide", "authoring_update_draft", null, List.of("source-one"), 2),
            Arguments.of("api", "authoring_read_selected_sources", "source-two", List.of("source-one"), 2),
            Arguments.of("api", "authoring_read_selected_sources", null, List.of("source-one"), 2),
            Arguments.of("api", "authoring_read_selected_sources", "source-two", List.of("source-three"), 1));
    }
    @ParameterizedTest @MethodSource("deniedDelegations")
    void forbiddenDelegatedProfilesActionsAndSourceReadsCannotDispatch(String profile, String action, String source,
        List<String> references, int expectedCalls) throws Exception {
        var client = client("BRAIN_SETUP"); renewSources(client, List.of("source-one", "source-two"));
        var calls = new AtomicInteger(); var tools = new AtomicInteger();
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall();
            ai.fabric.dto.AIGenerationRequest generation = call.getArgument(0);
            if (calls.incrementAndGet() == 1) return AIGenerationResponse.builder().content(delegation(profile, generation.getEntityId(), references)).build();
            var decision = MAPPER.createObjectNode().put("type", "ACTION").put("action", action);
            var parameters = decision.putObject("parameters");
            if (source != null) parameters.put("sourceId", source);
            if ("authoring_update_draft".equals(action)) parameters.putArray("changes").addObject().put("operation", "SET").put("path", "/promptConfig/systemPrompt").put("value", "Denied candidate");
            return AIGenerationResponse.builder().content(decision.toString()).build();
        }).when(core).generateContent(any(), any());
        doAnswer(call -> { tools.incrementAndGet(); return new AuthoringPlatformClient.Reply(200, MAPPER.createObjectNode()); })
            .when(platform).call(anyString(), anyMap(), any());
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        await(id, client, ExecutionHandleStatus.FAILED);
        var checkpoint = gateway.findWorkflowCheckpoint(id, RuntimeDurableGoalHost.trusted(
            RuntimeResolvedIdentity.builder().authContext(client.auth()).warnings(List.of()).build(), client.conversation())).orElseThrow();
        assertThat(checkpoint.state()).isEqualTo(ai.fabric.intent.orchestration.workflow.WorkflowCheckpoint.State.STOPPED);
        assertThat(checkpoint.usage().writes()).isZero(); assertThat(tools).hasValue(0); assertThat(calls).hasValue(expectedCalls);
        chat.deleteConversation(client.conversation(), "erase-denied-delegation", client.request());
    }

    @Test void identicalContextReadsPauseForNoProgressAcrossNativeSegmentsBeforeFundingExhaustion() throws Exception {
        var client = client(); renew(client, null);
        var calls = new AtomicInteger(); var reads = new AtomicInteger(); long ingress = funded.readCurrent().chatAttempts();
        var stable = MAPPER.createObjectNode(); stable.putObject("configuration").put("revision", "unchanged-draft")
            .put("wholeDecimal", 1.0).put("fractionalDecimal", 0.5);
        stable.putObject("goal").put("goalId", client.goal()).put("requestRevision", 1).put("cancellationEpoch", 0);
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall(); calls.incrementAndGet();
            return AIGenerationResponse.builder().content("{\"type\":\"ACTION\",\"action\":\"authoring_get_context\",\"parameters\":{}}").build();
        }).when(core).generateContent(any(), any());
        doAnswer(call -> {
            reads.incrementAndGet();
            return new AuthoringPlatformClient.Reply(200, stable);
        })
            .when(platform).call(eq("authoring_get_context"), anyMap(), any());
        String id = conversations.message(client.conversation(), client.turn(), client.body(), client.request()).getBody().getWorkId();
        try {
        await(id, client, ExecutionHandleStatus.PAUSED);
        var checkpoint = gateway.findWorkflowCheckpoint(id, RuntimeDurableGoalHost.trusted(
            RuntimeResolvedIdentity.builder().authContext(client.auth()).warnings(List.of()).build(), client.conversation())).orElseThrow();
        assertThat(checkpoint.state()).isEqualTo(ai.fabric.intent.orchestration.workflow.WorkflowCheckpoint.State.PAUSED);
        assertThat(checkpoint.stopCode()).isEqualTo("WORKFLOW_NO_PROGRESS");
        assertThat(checkpoint.goal().segmentNumber()).isGreaterThan(1);
        assertThat(checkpoint.goal().consecutiveNoProgress()).isEqualTo(3);
        assertThat(reads).hasValue(4); assertThat(calls.get()).isLessThan(10); assertThat(checkpoint.usage().writes()).isZero();
        assertThat(funded.requireActive().chatAttempts()).isEqualTo(ingress + 1);
        assertThat(funded.readCurrent().accountedUsage().totalTokens()).isLessThan(funded.readCurrent().effectiveLimits().totalTokens());
        } finally { chat.deleteConversation(client.conversation(), "erase-no-progress", client.request()); }
    }

    private static String delegation(String profile, String workId, List<String> sources) {
        var decision = MAPPER.createObjectNode().put("type", "DELEGATE");
        var task = decision.putObject("delegation").put("profileId", profile).put("task", "Prepare only the selected owned capability draft.");
        task.putArray("workReferences").add(workId); task.set("sourceReferences", MAPPER.valueToTree(sources));
        return decision.toString();
    }

    private record ResumeRun(Client client, String id, AtomicInteger calls, ConcurrentLinkedQueue<String> writes, ProviderBudgetLedger.Snapshot before) { }
    private ResumeRun pausedHttpGoal() throws Exception {
        var client = client(); renew(client, null);
        var calls = new AtomicInteger(); var writes = new ConcurrentLinkedQueue<String>();
        var decisions = new ConcurrentLinkedQueue<>(List.of("{\"type\":\"NEEDS_INPUT\",\"questions\":[\"Should I continue the saved request?\"]}",
            save("The normally resumed edit is saved."), "{\"type\":\"COMPLETE\",\"message\":\"The resumed edit is saved in the draft.\"}"));
        doAnswer(call -> {
            WorkflowModelCallScope.beforeModelCall(); calls.incrementAndGet();
            return AIGenerationResponse.builder().content(Objects.requireNonNull(decisions.poll())).build();
        }).when(core).generateContent(any(), any());
        doAnswer(call -> {
            String action = call.getArgument(0); Map<String, Object> parameters = call.getArgument(1);
            var result = MAPPER.createObjectNode();
            if ("authoring_update_draft".equals(action)) {
                writes.add(String.valueOf(parameters.get("operationId")));
                result.put("operationId", String.valueOf(parameters.get("operationId"))).put("status", "EXECUTED");
            } else result.putObject("sourceCoverage").put("required", false);
            return new AuthoringPlatformClient.Reply(200, result);
        }).when(platform).call(anyString(), anyMap(), any());
        var before = funded.readCurrent();
        String id = authoringPostOverHttp(client, "/messages", client.turn(), client.body(), 202).path("workId").asText();
        await(id, client, ExecutionHandleStatus.PAUSED);
        authoringPostOverHttp(client, "/work/" + id + "/control", "explicit-pause", new RuntimeDurableGoalHost.Control("PAUSE",
            client.goal(), client.goal(), client.session(), client.turn(), 1, 1, 1, null), 200);
        await(id, client, ExecutionHandleStatus.PAUSED);
        assertThat(calls).hasValue(1); assertThat(writes).isEmpty();
        return new ResumeRun(client, id, calls, writes, before);
    }
    private JsonNode goalState(Client client) {
        var scope = new ai.fabric.chat.control.ConversationScope("tenant-test", "dep-test", client.conversation(), "authoring:" + client.session());
        return MAPPER.valueToTree(canonical.orchestrationState(scope, 0).orElseThrow().metadata()).path("authoringGoal");
    }
    private JsonNode authoringPostOverHttp(Client client, String path, String key, Object body, int expectedStatus) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/api/chat/me/conversations/{conversation}" + path, client.conversation())
            .contentType("application/json").content(MAPPER.writeValueAsBytes(body)).header("Idempotency-Key", key);
        client.request().getHeaderNames().asIterator().forEachRemaining(name -> request.header(name, client.request().getHeader(name)));
        var response = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build().perform(request)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expectedStatus)).andReturn();
        byte[] bytes = response.getResponse().getContentAsByteArray();
        return bytes.length == 0 ? MAPPER.nullNode() : MAPPER.readTree(bytes);
    }

    private record Client(String session, String turn, String goal, String conversation, RuntimeAuthContext auth,
                          org.springframework.mock.web.MockHttpServletRequest request, ChatQueryRequest body) { }
    private Client client() {
        return client("ACTIONS");
    }
    private Client client(String task) {
        String suffix = UUID.randomUUID().toString(), session = "cas-" + suffix, turn = "cat-" + suffix, goal = "cag-" + suffix;
        String conversation = "authoring-" + suffix; var profile = bundle.select(task, null);
        var scopes = new ArrayList<>(profile.requiredScopes()); scopes.addAll(List.of("chat:query", "chat:messages", "chat:conversations"));
        var auth = RuntimeAuthContext.builder().subjectId("authoring:" + session).sessionId("authoring:" + session)
            .subjectType(RuntimeAuthSubjectType.END_USER).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).tenantId("tenant-test").customerId("customer-test").deploymentId("dep-test")
            .issuer("backend-test").audiences(List.of("dep-test")).expiresAt(Instant.now().plusSeconds(180)).grantedScopes(scopes).build();
        var request = new org.springframework.mock.web.MockHttpServletRequest(); addPrivateRuntimeHeaders(request, authProperties, auth);
        var body = new ChatQueryRequest(); body.setQuery("Explain the selected editable draft.");
        body.setConfigurationWork(new ChatQueryRequest.ConfigurationWork(session, turn, task, profile.id(), "release-test",
            bundle.contentHash(), false, 2, true, goal, goal, Instant.now().plusSeconds(3600), 1, 0, 1));
        return new Client(session, turn, goal, conversation, auth, request, body);
    }
    private void renew(Client client, String directive) {
        renewUpdates(client, directive == null ? List.of() : List.of(directive));
    }
    private void renewUpdates(Client client, List<String> updates) {
        renewUpdates(client, updates, List.of("source-one"));
    }
    private void renewSources(Client client, List<String> sources) {
        renewUpdates(client, List.of(), sources);
    }
    private void renewUpdates(Client client, List<String> updates, List<String> sources) {
        var renewal = lease(client, updates, sources);
        doAnswer(call -> renewal.apply(call.getArgument(0), call.getArgument(1))).when(platform).renew(any(), nullable(String.class));
    }
    private java.util.function.BiFunction<ChatQueryRequest.ConfigurationWork, String, ObjectNode> lease(Client client, List<String> updates, List<String> sources) {
        return (work, id) -> {
            var lease = MAPPER.createObjectNode().put("authorityId", client.goal()).put("goalId", client.goal()).put("sessionId", client.session())
                .put("turnId", client.turn()).put("conversationId", client.conversation()).put("profileId", work.profileId()).put("releaseId", "release-test")
                .put("bundleHash", bundle.contentHash()).put("previewOnly", false).put("validUntil", Instant.now().plusSeconds(3600).toString())
                .put("requestRevision", work.requestRevision()).put("cancellationEpoch", work.cancellationEpoch()).put("segment", work.segment());
            if (id != null) lease.put("workId", id).put("operationNamespace", id);
            var pins = lease.putObject("sourceVersions");
            sources.forEach(source -> pins.put(source, "sha256:" + "a".repeat(64)));
            lease.set("sourceIds", MAPPER.valueToTree(sources));
            if (!updates.isEmpty() && work.requestRevision() > 1) {
                var directive = lease.putObject("directive").put("query", updates.get((int) work.requestRevision() - 2))
                    .put("requestRevision", work.requestRevision());
                var revisions = directive.putArray("requestUpdates");
                for (int revision = 2; revision <= work.requestRevision(); revision++) revisions.addObject().put("requestRevision", revision)
                    .put("query", updates.get(revision - 2)).put("createdAt", "2026-11-24T12:00:00Z");
            }
            lease.putObject("runtimeHeaders").put(assertions.authorizationHeaderName(), assertions.tokenScheme() + " "
                + assertions.issueAssertion(client.auth().toBuilder().expiresAt(Instant.now().plusSeconds(180)).build()));
            return lease;
        };
    }
    private ExecutionSnapshot await(String id, Client client, ExecutionHandleStatus expected) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        ExecutionSnapshot result;
        var identity = RuntimeResolvedIdentity.builder().authContext(client.auth()).warnings(List.of()).build();
        do {
            result = gateway.find(id, RuntimeDurableGoalHost.trusted(identity, client.conversation())).orElseThrow();
            if (result.handle().status() == expected) return result;
            if (Set.of(ExecutionHandleStatus.FAILED, ExecutionHandleStatus.EXPIRED, ExecutionHandleStatus.REJECTED).contains(result.handle().status())) break;
            Thread.sleep(20);
        } while (System.nanoTime() < until);
        assertThat(result.handle().status()).as("native goal %s", result).isEqualTo(expected); return result;
    }
    private JsonNode workOverHttp(Client client, String id) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/chat/me/conversations/{conversation}/work/{work}", client.conversation(), id);
        client.request().getHeaderNames().asIterator().forEachRemaining(name -> request.header(name, client.request().getHeader(name)));
        var wire = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).build().perform(request)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(wire).doesNotContain("\"nodeType\"", "\"containerNode\"", "\"missingNode\"");
        return MAPPER.readTree(wire).path("goal");
    }
    private static String save(String value) { return "{\"type\":\"ACTION\",\"action\":\"authoring_update_draft\",\"parameters\":{\"changes\":[{\"operation\":\"SET\",\"path\":\"/promptConfig/systemPrompt\",\"value\":\"" + value + "\"}]}}"; }

    @TestConfiguration(proxyBeanMethods = false)
    static class Funding {
        private java.sql.Connection schemaSession;
        JdbcProviderBudgetLedger ledger;
        @jakarta.annotation.PreDestroy void close() throws Exception { if (schemaSession != null) schemaSession.close(); }
        @Bean RuntimeBudgetBindingService fundedGoalBudget() throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:goal-funding;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
            // The fixture retains the schema session for H2's check-constraint lifetime.
            var connection = source.getConnection();
            schemaSession = connection;
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("com/ai/fabric/budget/provider-budget-schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("com/ai/fabric/runtime/budget/runtime-provider-budget-binding.sql"));
            var scope = new ProviderBudgetLedger.Scope("customer-test", "dep-test", "target-test", "staging", ReviewedOpenAiRateCard.credentialIdentity(OFFLINE_KEY), ReviewedOpenAiRateCard.REFERENCE);
            ledger = new JdbcProviderBudgetLedger(source);
            var service = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, scope, "tenant-test"), ledger, source, Clock.systemUTC());
            var approval = new ProviderBudgetLedger.Approval("goal-test-budget", scope, new ProviderBudgetLedger.Limits(100, 1000000, 2000000000L), Instant.now().plusSeconds(3600));
            var receipt = service.initialize(new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval)));
            service.activate(new RuntimeBudgetBindingService.Activation(receipt.approvalId(), receipt.storeUuid(), receipt.runtimeFingerprint()));
            return service;
        }
        @Bean RuntimeBudgetTurnScopes fundedGoalScopes(RuntimeBudgetBindingService budgets) { return new RuntimeBudgetTurnScopes(budgets); }
    }
}
