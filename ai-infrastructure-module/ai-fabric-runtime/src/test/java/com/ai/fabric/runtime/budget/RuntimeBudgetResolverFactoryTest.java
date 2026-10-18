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
    private static final String FIXTURE_KEY = "fixture-key-not-a-provider-credential";

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
