package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;

import ai.fabric.config.AIProviderConfig;
import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver.OutputCapMode;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeBudgetResolverFactoryTest extends RuntimeBudgetFixture.TestScope {
    @Test void astraPurposeRequiresTheDurableAmendmentAndSupportedReasoningBeforeResolverCreation() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var original = scope();
        var extended = new Scope(original.customerId(), original.deploymentId(), original.targetProfileId(), original.environment(),
            original.credentialIdentity(), ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE);
        var budgets = fixture.service(extended);
        var approval = new com.ai.fabric.budget.ProviderBudgetLedger.Approval("astra-fixture", extended,
            new com.ai.fabric.budget.ProviderBudgetLedger.Limits(100, 2_000_000, 5_000_000_000L), NOW.plusSeconds(3600));
        activate(budgets, budgets.initialize(allocation(approval)));
        var config = providers(); config.getGeneration().setModel(ReviewedOpenAiRateCard.ASTRA_MODEL);
        var medium = com.ai.fabric.budget.openai.GuardedReasoningEffort.MEDIUM;
        assertThatThrownBy(() -> RuntimeBudgetResolverFactory.create(config, ObservationRegistry.NOOP, fixture.ledger,
            budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS, medium)).isInstanceOf(OpenAiBudgetException.class);
        var binding = budgets.readStore().binding();
        var before = budgets.readCurrent();
        budgets.amendPricing(new RuntimeBudgetPricingAmendment.Command(1, "astra", approval.id(), binding.storeUuid(), binding.runtimeFingerprint(),
            extended.rateCardReference(), ReviewedOpenAiRateCard.ASTRA_REFERENCE, "operator", "owner-review", "quality qualification"));
        var resolver = RuntimeBudgetResolverFactory.create(config, ObservationRegistry.NOOP, fixture.ledger,
            budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS, medium);
        try {
            var options = (org.springframework.ai.openai.OpenAiChatOptions) resolver.resolveChatOptions("openai",
                ai.fabric.dto.AIGenerationRequest.builder().model(ReviewedOpenAiRateCard.ASTRA_MODEL).maxTokens(8192).build());
            assertThat(options.getModel()).isEqualTo(ReviewedOpenAiRateCard.ASTRA_MODEL);
            assertThat(options.getReasoningEffort()).isEqualTo("medium"); assertThat(options.getTemperature()).isNull();
            assertThat(options.getMaxCompletionTokens()).isEqualTo(8192);
        } finally { resolver.destroy(); }
        assertThatThrownBy(() -> RuntimeBudgetResolverFactory.create(config, ObservationRegistry.NOOP, fixture.ledger,
            budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS)).isInstanceOf(OpenAiBudgetException.class);
        assertThat(budgets.readCurrent()).isEqualTo(before);
    }
    @Test void hostCanSelectBoundedReasoningWithoutChangingFundingOrTokenLimits() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var original = scope();
        var extended = new Scope(original.customerId(), original.deploymentId(), original.targetProfileId(), original.environment(),
            original.credentialIdentity(), ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE);
        var budgets = fixture.service(extended); var config = providers(); config.getOpenai().setModel(ReviewedOpenAiRateCard.GPT_54_MINI_MODEL);
        var resolver = RuntimeBudgetResolverFactory.create(config, ObservationRegistry.NOOP, fixture.ledger,
            budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS,
            com.ai.fabric.budget.openai.GuardedReasoningEffort.LOW);
        try {
            var options = (org.springframework.ai.openai.OpenAiChatOptions) resolver.resolveChatOptions("openai", null);
            assertThat(options.getReasoningEffort()).isEqualTo("low"); assertThat(options.getMaxCompletionTokens()).isEqualTo(300);
            assertThat(budgets.readStore().binding().state()).isEqualTo(RuntimeBudgetBindingService.State.UNINITIALIZED);
        } finally { resolver.destroy(); }
    }
    private static final String FIXTURE_KEY = "fixture-key-not-a-provider-credential";

    @Test
    void miniAliasAndSnapshotRequireTheVersionedCardForEveryPurpose() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var original = scope();
        var extended = new Scope(original.customerId(), original.deploymentId(), original.targetProfileId(), original.environment(),
            original.credentialIdentity(), ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE);
        var budgets = fixture.service(extended);
        for (String model : List.of(ReviewedOpenAiRateCard.GPT_54_MINI_MODEL, ReviewedOpenAiRateCard.GPT_54_MINI_SNAPSHOT)) {
            var config = providers(); config.getOpenai().setModel(model);
            var resolver = RuntimeBudgetResolverFactory.create(config, ObservationRegistry.NOOP, fixture.ledger,
                budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS);
            assertThat(resolver.resolveChatOptions("openai", null).getModel()).isEqualTo(model);
            assertThat(budgets.scope()).isEqualTo(extended);
            resolver.destroy();
            assertRejected(config);
        }
        var generationOnly = providers(); generationOnly.getGeneration().setModel(ReviewedOpenAiRateCard.GPT_54_MINI_MODEL);
        assertRejected(generationOnly);
        var unreviewed = providers(); unreviewed.getOpenai().setModel("gpt-5.4");
        assertThatThrownBy(() -> RuntimeBudgetResolverFactory.create(unreviewed, ObservationRegistry.NOOP, fixture.ledger,
            budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS)).isInstanceOf(OpenAiBudgetException.class);
        assertThat(budgets.readStore().binding().storeUuid()).isNull();
    }

    @Test
    void offlineGuardedResolverConstructionDoesNotNeedAnApprovalOrPerformModelCalls() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var budgets = fixture.service(scope());
        var resolver = RuntimeBudgetResolverFactory.create(providers(), ObservationRegistry.NOOP, fixture.ledger,
            budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS);
        assertThat(budgets.readStore().binding().state()).isEqualTo(RuntimeBudgetBindingService.State.UNINITIALIZED);
        assertThat(budgets.readiness().ready()).isFalse();
        resolver.destroy();
    }

    @Test
    void anyConfiguredFallbackChainIsRejectedBeforeItCanBypassTheGuard() throws Exception {
        for (int option = 0; option < 3; option++) {
            var config = providers();
            if (option == 0) config.setEnableFallback(true);
            if (option == 1) config.setGenerationFallbackProviders(List.of("custom-unmetered-provider"));
            if (option == 2) config.setEmbeddingFallbackProviders(List.of("custom-unmetered-provider"));
            assertRejected(config);
        }
    }

    @Test
    void unregisteredPurposeAndEmbeddingCredentialsAreRejectedOffline() throws Exception {
        var generation = providers();
        generation.getGeneration().setApiKey("other-fixture-key");
        assertRejected(generation);
        var embeddings = providers();
        embeddings.setEmbeddingApiKey("other-fixture-key");
        assertRejected(embeddings);
    }

    @Test
    void localProviderNameAloneAndUnsupportedEndpointAreNotProofOfGuardedCoverage() throws Exception {
        var onnx = providers();
        onnx.setEmbeddingProvider("onnx");
        assertRejected(onnx);
        var endpoint = providers();
        endpoint.getGeneration().setBaseUrl("https://unreviewed.invalid/v1");
        assertRejected(endpoint);
        var unsupported = providers();
        unsupported.getAzure().setEnabled(true);
        assertRejected(unsupported);
    }

    private static void assertRejected(AIProviderConfig providers) throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var budgets = fixture.service(scope());
        assertThatThrownBy(() -> RuntimeBudgetResolverFactory.create(providers, ObservationRegistry.NOOP, fixture.ledger,
            budgets, new RuntimeBudgetTurnScopes(budgets), null, OutputCapMode.MAX_COMPLETION_TOKENS)).isInstanceOf(OpenAiBudgetException.class);
        assertThat(budgets.readStore().binding().storeUuid()).isNull();
    }

    private static Scope scope() {
        return new Scope(SCOPE.customerId(), SCOPE.deploymentId(), SCOPE.targetProfileId(), SCOPE.environment(),
            ReviewedOpenAiRateCard.credentialIdentity(FIXTURE_KEY), SCOPE.rateCardReference());
    }

    private static AIProviderConfig providers() {
        var config = new AIProviderConfig();
        config.getOpenai().setApiKey(FIXTURE_KEY);
        config.getOpenai().setModel(ReviewedOpenAiRateCard.CHAT_MODEL);
        config.getOpenai().setMaxTokens(300);
        config.getOpenai().setEmbeddingModel(ReviewedOpenAiRateCard.EMBEDDING_MODEL);
        config.setGeneration(new AIProviderConfig.GenerationLlmConfig());
        config.setEmbeddingProvider("openai");
        return config;
    }
}
