package com.ai.fabric.runtime.authoring;

import static com.ai.fabric.runtime.authoring.AuthoringTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.core.AICoreService;
import ai.fabric.core.LlmPurpose;
import ai.fabric.dto.AIChatMessage;
import ai.fabric.dto.AIGenerationRequest;
import ai.fabric.dto.AIGenerationRequestContracts;
import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.llm.structured.DefaultStructuredJsonCallExecutor;
import ai.fabric.llm.structured.StructuredJsonCallExecutor;
import ai.fabric.llm.structured.StructuredJsonExtractor;
import ai.fabric.llm.structured.StructuredJsonProviderHints;
import ai.fabric.prompt.PromptRenderer;
import ai.fabric.prompt.PromptTemplateResolver;
import ai.fabric.provider.AIProviderManager;
import ai.fabric.provider.springai.SpringAiChatProvider;
import ai.fabric.provider.springai.SpringAiModelResolver;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingException;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

class ConfigurationAuthoringServiceTest {
    private final AICoreService ai = mock(AICoreService.class);
    private final RuntimeBudgetBindingService budgets = mock(RuntimeBudgetBindingService.class);
    private final RuntimeBudgetTurnScopes turns = new RuntimeBudgetTurnScopes(budgets);
    private final StructuredJsonCallExecutor structured = spy(new DefaultStructuredJsonCallExecutor(new StructuredJsonExtractor(), MAPPER));
    private ThreadPoolExecutor executor;

    @BeforeEach void setup() {
        executor = new ConfigurationAuthoringConfiguration().configurationAuthoringExecutor();
        when(budgets.recordVerifiedAuthoringIngress(any())).thenReturn(new com.ai.fabric.budget.ProviderBudgetLedger.ChatReceipt(UUID.randomUUID(), 1, true));
    }
    @AfterEach void close() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void usesReleasedStructuredApiOnceWithServerSchemaUserDataAndActualUsage() throws Exception {
        when(ai.generateContent(any(), eq(LlmPurpose.GENERATION))).thenReturn(generated(output().toString()));
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = response.getBody().deepCopy();
        var runtime = body.remove("runtime");
        assertThat(body).isEqualTo(output());
        assertThat(runtime.path("modelCalls").asInt()).isEqualTo(1);
        assertThat(runtime.path("usage").path("promptTokens").asInt()).isEqualTo(123);
        assertThat(runtime.path("tokensUsed").asInt()).isEqualTo(180);
        assertThat(runtime.path("deadlineAt").isTextual()).isTrue();
        assertThat(runtime.path("accountingPath").asText()).isEqualTo("RUNTIME_PROVIDER_BUDGET");
        assertThat(runtime.path("metadataRecoveryPolicy").asText()).isEqualTo("REPORT_AND_PRESERVE");
        assertThat(runtime.path("accountingReceipt").path("newlyCounted").asBoolean()).isTrue();
        var capture = ArgumentCaptor.forClass(AIGenerationRequest.class);
        verify(ai).generateContent(capture.capture(), eq(LlmPurpose.GENERATION));
        var generation = capture.getValue();
        assertThat(generation.getSystemPrompt()).contains("Server response schema", "Task: ACTIONS").doesNotContain("Imported document");
        assertThat(generation.getPrompt()).isEqualTo(request().query());
        assertThat(generation.getMessages()).hasSize(3);
        assertThat(generation.getMessages().get(0).getContent()).isEqualTo("Search inventory");
        assertThat(generation.getMessages().getLast().getContent()).isEqualTo(
            "Authoring context data:\n" + new AuthoringJsonContract(MAPPER).input(request()));
        assertThat(generation.getContext()).isNull();
        assertThat(StructuredJsonProviderHints.outputSchema(generation.getParameters())).isEqualTo(AuthoringInstructions.RESPONSE_SCHEMA.trim());
        assertThat(generation.getParameters().toString()).contains("json_object");
        assertThat(generation.getAuthContext().getDeploymentId()).isEqualTo("dedicated-authoring");
        verify(budgets).recordVerifiedAuthoringIngress(any());
        assertUnscoped();
    }

    @Test
    void releasedPipelineKeepsExactCurrentQueryLastAndContextSeparateFromConflictingHistory() throws Exception {
        var config = new AIProviderConfig();
        config.setLlmProvider("openai");
        config.setEnableFallback(false);
        config.getOpenai().setModel("fixture-model");
        var options = ChatOptions.builder().model("fixture-model").build();
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(options);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
            List.of(new Generation(new AssistantMessage(output().toString())))));
        var resolver = mock(SpringAiModelResolver.class);
        when(resolver.isChatAvailable("openai")).thenReturn(true);
        when(resolver.isChatAvailable(eq("openai"), any())).thenReturn(true);
        when(resolver.resolveChatModel(eq("openai"), any())).thenReturn(model);
        when(resolver.resolveChatOptions(eq("openai"), any())).thenReturn(options);
        var provider = spy(new SpringAiChatProvider("openai", resolver));
        var manager = new AIProviderManager(List.of(provider), config);
        manager.initialize();
        var core = new AICoreService(config, manager, mock(ObjectProvider.class), mock(ObjectProvider.class),
            mock(PromptTemplateResolver.class), new PromptRenderer());
        when(ai.generateContent(any(), eq(LlmPurpose.GENERATION)))
            .thenAnswer(call -> core.generateContent(call.getArgument(0), LlmPurpose.GENERATION));
        var current = new ConfigurationAuthoringRequest(request().contractVersion(), request().sessionId(), request().turnId(),
            request().resourceKind(), request().task(), request().query(), request().currentConfiguration(), request().sources(),
            request().catalogue(), List.of(
                new ConfigurationAuthoringRequest.PriorTurn(ConfigurationAuthoringRequest.Role.USER,
                    "Only change displayName to Lookup Current record"),
                new ConfigurationAuthoringRequest.PriorTurn(ConfigurationAuthoringRequest.Role.ASSISTANT,
                    "Proposed the displayName edit")));
        assertThat(service(5_000).generate(current, identity()).getStatusCode().value()).isEqualTo(200);

        var original = ArgumentCaptor.forClass(AIGenerationRequest.class);
        verify(ai).generateContent(original.capture(), eq(LlmPurpose.GENERATION));
        var copied = ArgumentCaptor.forClass(AIGenerationRequest.class);
        verify(provider).generateContent(copied.capture());
        assertThat(copied.getValue()).isNotSameAs(original.getValue());
        assertThat(copied.getValue().getPrompt()).isEqualTo(current.query());
        assertThat(copied.getValue().getMessages()).isEqualTo(original.getValue().getMessages());
        assertThat(copied.getValue().getContext()).isNull();
        var captured = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captured.capture());
        var messages = captured.getValue().getInstructions();
        assertThat(messages).extracting(Message::getMessageType)
            .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER, MessageType.USER);
        assertThat(messages).extracting(Message::getText).containsExactly(
            AuthoringInstructions.system(current).trim(), current.priorTurns().get(0).content(),
            current.priorTurns().get(1).content(), "Authoring context data:\n" + new AuthoringJsonContract(MAPPER).input(current),
            current.query());
        assertThat(messages.getLast().getText()).isEqualTo(current.query());
        var context = MAPPER.readTree(new AuthoringJsonContract(MAPPER).input(current));
        assertThat(context.has("query")).isFalse();
        assertThat(context.has("priorTurns")).isFalse();
        assertThat(context.path("sources")).isEqualTo(MAPPER.valueToTree(current.sources()));
        assertThat(messages.getFirst().getText()).doesNotContain("Imported document", "Lookup Current record");
        assertThat(messages.getFirst().getText()).contains("Prior user requests are history", "final USER message is the current request");
        assertUnscoped();
    }

    @Test
    void releasedContractRejectsMessagesOnlyEvenWhenFinalMessageIsCurrentUserQuery() {
        var generation = AIGenerationRequest.builder().systemPrompt(AuthoringInstructions.system(request()))
            .messages(List.of(AIChatMessage.user("Earlier request"), AIChatMessage.user(request().query()))).build();
        assertThatThrownBy(() -> AIGenerationRequestContracts.validateStandardChatPrompting(generation))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("AIGenerationRequest.prompt or inputParts must provide the current user input when messages are present");
    }

    @Test
    void advisoryRecoveryIsVisiblePreservesChangesAndNeverCallsModelAgain() throws Exception {
        var raw = output();
        raw.remove("questions");
        raw.putObject("assumptions").put("original", "must remain visible");
        raw.withArray("sourceReferences").add("unverified-reference");
        when(ai.generateContent(any(), any())).thenReturn(generated(raw.toString()));
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("changes")).isEqualTo(raw.path("changes"));
        assertThat(response.getBody().path("diagnostics")).hasSize(3);
        assertThat(response.getBody().path("questions").isArray()).isTrue();
        assertThat(response.getBody().path("assumptions").isArray()).isTrue();
        assertThat(response.getBody().path("runtime").path("metadataRecoveryPolicy").asText()).isEqualTo("REPORT_AND_PRESERVE");
        verify(ai, times(1)).generateContent(any(), any());
        assertUnscoped();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prose %s", "%s {}", "```json\n%s\n```", "{\"changes\":[]}"})
    void strictWholeResponseGateRunsBeforeHelperAndNeverCallsAgain(String format) throws Exception {
        when(ai.generateContent(any(), any())).thenReturn(generated(format.contains("%s") ? format.formatted(output()) : format));
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("AUTHORING_INVALID_OUTPUT");
        assertThat(response.getBody().path("runtime").path("usage").path("promptTokens").asInt()).isEqualTo(123);
        verifyNoInteractions(structured);
        verify(ai, times(1)).generateContent(any(), any());
        assertUnscoped();
    }

    @Test
    void validJsonCannotHideAProviderError() throws Exception {
        var failed = generated(output().toString());
        failed.setStatus("ERROR");
        failed.setError("private provider detail");
        when(ai.generateContent(any(), any())).thenReturn(failed);
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody().toString()).doesNotContain("private provider detail").contains("AUTHORING_PROVIDER_ERROR");
        assertThat(response.getBody().path("runtime").path("providerError").asBoolean()).isTrue();
        verifyNoInteractions(structured);
    }

    @Test
    void accountingAdmissionDenialMakesNoProviderCall() {
        when(budgets.recordVerifiedAuthoringIngress(any())).thenThrow(new BudgetException(BudgetException.Code.LIMIT_EXCEEDED));
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody().toString()).contains("PROVIDER_BUDGET_LIMIT_EXCEEDED");
        assertThat(response.getBody().path("runtime").path("modelCalls").asInt()).isZero();
        verifyNoInteractions(ai);
        assertUnscoped();
    }

    @Test
    void uninitializedAccountingFailsExplicitly() {
        when(budgets.recordVerifiedAuthoringIngress(any())).thenThrow(new RuntimeBudgetBindingException(RuntimeBudgetBindingException.Code.NOT_INITIALIZED));
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getBody().toString()).contains("PROVIDER_BUDGET_NOT_INITIALIZED");
        verifyNoInteractions(ai);
    }

    @Test
    void providerDenialObserverWinsEvenWhenProviderReturnsValidOutput() throws Exception {
        when(ai.generateContent(any(), any())).thenAnswer(ignored -> {
            var invocation = turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION);
            invocation.observer().onDenied(new OpenAiBudgetInvocation.Denial(invocation.operationId(), UUID.randomUUID(), BudgetException.Code.LIMIT_EXCEEDED, null));
            return generated(output().toString());
        });
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody().toString()).contains("PROVIDER_BUDGET_INTERRUPTED");
        assertThat(response.getBody().has("changes")).isFalse();
        verifyNoInteractions(structured);
        assertUnscoped();
    }

    @Test
    void wrappedProviderTimeoutIsExplicitAndDoesNotRetry() {
        when(ai.generateContent(any(), any())).thenThrow(new IllegalStateException(new java.net.SocketTimeoutException()));
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(504);
        verify(ai, times(1)).generateContent(any(), any());
        assertUnscoped();
    }

    @Test
    void deadlineCancelsWorkerAndDoesNotLeakScopeOrFakeUsage() throws Exception {
        var exited = new CountDownLatch(1);
        when(ai.generateContent(any(), any())).thenAnswer(ignored -> {
            try { Thread.sleep(5_000); return generated(output().toString()); }
            finally { exited.countDown(); }
        });
        long started = System.nanoTime();
        var response = service(150).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(504);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2_000);
        assertThat(response.getBody().path("runtime").path("usage").isNull()).isTrue();
        assertThat(exited.await(2, TimeUnit.SECONDS)).isTrue();
        assertUnscoped();
    }

    @Test
    void overloadedQueueMakesNoProviderCall() {
        executor.shutdown();
        var response = service(5_000).generate(request(), identity());
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().toString()).contains("AUTHORING_BUSY");
        verifyNoInteractions(ai);
    }

    @Test
    void separateTurnsNeverReuseAnotherRequestsInputHistoryOrResponse() throws Exception {
        when(ai.generateContent(any(), any())).thenReturn(generated(output().toString()));
        var first = service(5_000).generate(request(), identity());
        var other = new ConfigurationAuthoringRequest(request().contractVersion(), "other-session", "other-turn",
            request().resourceKind(), ConfigurationAuthoringRequest.Task.CONNECTIONS, "Configure another connection",
            MAPPER.createObjectNode().put("other_config", true), java.util.List.of(), MAPPER.createObjectNode(), java.util.List.of());
        var otherOutput = output();
        otherOutput.withArray("sourceReferences").removeAll();
        when(ai.generateContent(any(), any())).thenReturn(generated(otherOutput.toString()));
        var second = service(5_000).generate(other, identity());
        assertThat(first.getBody().path("runtime").path("requestId")).isNotEqualTo(second.getBody().path("runtime").path("requestId"));
        var capture = ArgumentCaptor.forClass(AIGenerationRequest.class);
        verify(ai, times(2)).generateContent(capture.capture(), any());
        var next = capture.getAllValues().get(1);
        assertThat(next.getPrompt()).isEqualTo(other.query());
        assertThat(next.getMessages()).hasSize(1);
        assertThat(next.getMessages().getFirst().getContent()).contains("other_config")
            .doesNotContain("Imported document", "Search inventory", "\"query\":", "\"priorTurns\":");
        assertThat(next.getSystemPrompt()).contains("Task: CONNECTIONS").doesNotContain("Task: ACTIONS");
    }

    private void assertUnscoped() {
        assertThatThrownBy(() -> executor.submit(() -> turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION)).get(2, TimeUnit.SECONDS))
            .hasRootCauseInstanceOf(OpenAiBudgetException.class);
    }
    private ConfigurationAuthoringService service(long deadline) {
        return new ConfigurationAuthoringService(ai, structured, MAPPER, turns, budgets, executor, deadline, 4_096);
    }
    private AIGenerationResponse generated(String raw) {
        return AIGenerationResponse.builder().content(raw).status("SUCCESS").model("fixture-model")
            .tokensUsed(180).usage(Map.of("promptTokens", 123, "completionTokens", 57)).requestId("provider-request").build();
    }
}
