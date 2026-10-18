package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.fabric.config.*;
import ai.fabric.core.AICoreService;
import ai.fabric.core.LlmPurpose;
import ai.fabric.dto.*;
import ai.fabric.intent.action.*;
import ai.fabric.intent.actiondraft.InMemoryActionDraftStore;
import ai.fabric.intent.orchestration.*;
import ai.fabric.intent.orchestration.pipeline.*;
import ai.fabric.intent.orchestration.pipeline.steps.IntentHandlingStep;
import ai.fabric.intent.orchestration.pipeline.steps.SmartSuggestionsStep;
import ai.fabric.intent.vectorspace.RankBasedMerger;
import ai.fabric.prompt.*;
import ai.fabric.spi.RAGProvider;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

/** Real framework steps and real native denial, with no provider transmission permitted. */
class RuntimeBudgetFrameworkPathsTest extends RuntimeBudgetFixture.TestScope {
    @Test
    void defaultAndCommercePostActionSummaryDenialsRetainExactReadAndWriteResults() throws Exception {
        for (String overlay : List.of("v1-default-optimized", "v1-commerce")) {
            for (ActionAccessMode mode : List.of(ActionAccessMode.READ, ActionAccessMode.READ_WRITE)) {
                try (var fixture = new Paths()) {
                    var receipt = fixture.registerAction(mode);
                    var intent = Intent.builder().type(IntentType.ACTION).action("fixture_action")
                        .requiresGeneration(true).generationInstructions("Summarize the completed action.").build();
                    try (var turn = fixture.turns.openChat(chatIdentity(), null)) {
                        var result = fixture.run(turn, List.of(intent), overlay, false);
                        fixture.assertInterrupted(result);
                        assertThat(result.getData().get("actionResult")).isSameAs(receipt);
                        assertThat(receipt.isSuccess()).isTrue();
                        assertThat(result.getData()).containsKey("actionExecutionId");
                        assertThat(fixture.effects).hasValue(1);
                        verify(fixture.handler, never()).handleError(any(), any());
                    }
                }
            }
        }
    }

    @Test
    void compoundWriteThenGenerationOnlyDenialRetainsTheAlreadyCompletedChild() throws Exception {
        try (var fixture = new Paths()) {
            var receipt = fixture.registerAction(ActionAccessMode.READ_WRITE);
            var action = Intent.builder().type(IntentType.ACTION).action("fixture_action").requiresGeneration(false).build();
            var information = Intent.builder().type(IntentType.INFORMATION).intent("Explain the result")
                .requiresRetrieval(false).requiresGeneration(true).build();
            try (var turn = fixture.turns.openChat(chatIdentity(), null)) {
                var result = fixture.run(turn, List.of(action, information), "v1-commerce", false);
                fixture.assertInterrupted(result);
                assertThat(result.getChildren()).hasSize(2);
                assertThat(result.getChildren().getFirst().getData().get("actionResult")).isSameAs(receipt);
                assertThat(result.getChildren().getFirst().isSuccess()).isTrue();
                assertThat(fixture.effects).hasValue(1);
            }
        }
    }

    @Test
    void realSmartSuggestionsStepDenialRetainsTheCompletedActionResult() throws Exception {
        try (var fixture = new Paths()) {
            var receipt = fixture.registerAction(ActionAccessMode.READ_WRITE);
            var action = Intent.builder().type(IntentType.ACTION).action("fixture_action").requiresGeneration(false)
                .nextStepRecommended(NextStepRecommendation.builder().intent("suggestion").query("Next options").confidence(0.9).build())
                .build();
            try (var turn = fixture.turns.openChat(chatIdentity(), null)) {
                var result = fixture.run(turn, List.of(action), "v1-commerce", true);
                fixture.assertInterrupted(result);
                assertThat(result.getData().get("actionResult")).isSameAs(receipt);
                assertThat(receipt.isSuccess()).isTrue();
                assertThat(fixture.effects).hasValue(1);
            }
        }
    }

    private static final class Paths implements AutoCloseable {
        private static final String KEY = "offline-native-denial-fixture-not-a-provider-key";
        final RuntimeBudgetFixture storage = new RuntimeBudgetFixture();
        final Scope scope = new Scope("customer", "deployment", "target", "staging",
            ReviewedOpenAiRateCard.credentialIdentity(KEY), ReviewedOpenAiRateCard.REFERENCE);
        final RuntimeBudgetBindingService budgets = storage.service(scope);
        final RuntimeBudgetTurnScopes turns = new RuntimeBudgetTurnScopes(budgets);
        final BudgetGuardedSpringAiModelResolver resolver;
        final AtomicInteger effects = new AtomicInteger();
        final AtomicInteger nativeCalls = new AtomicInteger();
        final AIActionRegistry registry = mock(AIActionRegistry.class);
        final AIActionHandler handler = mock(AIActionHandler.class);
        final AICoreService core = mock(AICoreService.class);

        Paths() throws Exception {
            activate(budgets, budgets.initialize(allocation(new Approval(APPROVAL.id(), scope, APPROVAL.limits(), APPROVAL.expiresAt()))));
            var providers = new AIProviderConfig();
            providers.getOpenai().setApiKey(KEY);
            providers.getOpenai().setModel(ReviewedOpenAiRateCard.CHAT_MODEL);
            providers.getOpenai().setEmbeddingModel(ReviewedOpenAiRateCard.EMBEDDING_MODEL);
            providers.getOpenai().setMaxTokens(300);
            providers.setEmbeddingProvider("openai");
            resolver = RuntimeBudgetResolverFactory.create(providers, ObservationRegistry.NOOP, storage.ledger,
                budgets, turns, null, BudgetGuardedSpringAiModelResolver.OutputCapMode.MAX_COMPLETION_TOKENS);
            when(core.generateContent(any(AIGenerationRequest.class), any(LlmPurpose.class)))
                .thenAnswer(call -> { denyNative(call.getArgument(0)); return null; });
            when(core.generateTextResponse(anyString(), any(LlmPurpose.class)))
                .thenAnswer(call -> { denyNative(null); return null; });
        }

        ActionResult registerAction(ActionAccessMode mode) {
            when(registry.findHandler("fixture_action")).thenReturn(Optional.of(handler));
            when(registry.findMetadata("fixture_action")).thenReturn(Optional.of(AIActionMetaData.builder()
                .name("fixture_action").accessMode(mode).build()));
            when(handler.validateActionAllowed(any())).thenReturn(true);
            var receipt = ActionResult.builder().success(true).message("Completed")
                .data(ActionResultContracts.object(Map.of("receiptId", "effect-1"))).build();
            when(handler.executeAction(any(), any())).thenAnswer(call -> { effects.incrementAndGet(); return receipt; });
            when(handler.buildPostActionLlmFacts(eq(receipt), any())).thenReturn(Optional.of(Map.of("completed", true)));
            return receipt;
        }

        void denyNative(AIGenerationRequest request) {
            nativeCalls.incrementAndGet();
            storage.ledger.stop(APPROVAL.id(), scope, "OFFLINE_TEST_NO_TRANSMISSION");
            resolver.resolveChatModel("openai", request).call(new Prompt("offline fixture", resolver.resolveChatOptions("openai", request)));
            fail("A stopped ledger must deny before network transmission.");
        }

        OrchestrationResult run(RuntimeBudgetTurnScopes.Turn turn, List<Intent> intents, String overlay, boolean suggestions) {
            var bundle = new PromptBundleProperties();
            bundle.setOverlays(List.of(overlay));
            var prompts = new PromptTemplateResolver(new ClasspathPromptTemplateStore(new DefaultResourceLoader()), bundle);
            var postAction = new PostActionGenerationProperties(); postAction.setEnabled(true);
            var handling = new IntentHandlingStep(registry, provider(null), core, new AIServiceConfig(), provider(null),
                new VectorSpaceRoutingProperties(), new RankBasedMerger(), new RelationshipQueryPostActionGenerationProperties(),
                postAction, provider(new ObjectMapper()), new OrchestrationProperties(), provider(null), null,
                new InMemoryPendingActionStore(), new InMemoryActionDraftStore(), prompts, new PromptRenderer());
            var steps = new ArrayList<PipelineStep>();
            steps.add(new PipelineStep() {
                @Override public PipelineContext process(PipelineContext context) {
                    return context.toBuilder().intentResponse(MultiIntentResponse.builder().intents(intents).build()).build();
                }
                @Override public String getStepName() { return "fixture-intents"; }
                @Override public int getOrder() { return 1; }
            });
            steps.add(handling);
            if (suggestions) {
                var rag = mock(RAGProvider.class);
                when(rag.performRag(any())).thenAnswer(call -> { denyNative(null); return null; });
                steps.add(new SmartSuggestionsStep(new SmartSuggestionsProperties(), provider(rag)));
            }
            return new RAGOrchestrator(new DefaultOrchestrationPipeline(steps)).orchestrate("Complete the approved request",
                OrchestrationContext.builder().userId("owner").orchestrationTurnGuard(turn).build());
        }

        void assertInterrupted(OrchestrationResult result) {
            assertThat(nativeCalls.get()).isPositive();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo(RuntimeBudgetTurnScopes.INTERRUPTION_CODE);
            assertThat(result.getMetadata().get(RuntimeBudgetTurnScopes.INTERRUPTION_KEY))
                .isInstanceOf(RuntimeBudgetTurnScopes.BudgetInterruption.class);
            assertThat(budgets.readCurrent().accountedUsage().totalTokens()).isZero();
        }

        @Override public void close() { resolver.destroy(); }
    }

    private static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked") ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
