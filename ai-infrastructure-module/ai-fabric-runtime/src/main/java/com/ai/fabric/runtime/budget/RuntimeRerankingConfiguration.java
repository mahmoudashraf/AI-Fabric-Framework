package com.ai.fabric.runtime.budget;

import ai.fabric.rag.config.RAGProperties;
import ai.fabric.reranking.cohere.CohereRerankingProvider;
import ai.fabric.spi.RerankingProvider;
import com.ai.fabric.budget.ProviderBudgetLedger;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai.infrastructure.rag.reranking", name = "enabled", havingValue = "true")
@Import(RuntimeProviderBudgetLedgerConfiguration.class)
public class RuntimeRerankingConfiguration {
    record CohereCredential(String value) {
        @Override public String toString() { return "CohereCredential[REDACTED]"; }
    }

    @Bean
    CohereCredential rerankingCredential(Environment environment) {
        String key = environment.getRequiredProperty("LOOMAI_RERANKING_API_KEY");
        if (!ReviewedCohereRerankingRateCard.credentialIdentity(key).equals(
            environment.getRequiredProperty("LOOMAI_RERANKING_BUDGET_CREDENTIAL_IDENTITY"))) {
            throw new IllegalArgumentException("RERANKING_CREDENTIAL_SCOPE_MISMATCH");
        }
        return new CohereCredential(key);
    }

    @Bean("rerankingBudgetBinding")
    RuntimeBudgetBindingService rerankingBudgetBinding(Environment environment, CohereCredential credential,
        ProviderBudgetLedger ledger, RuntimeBudgetStorage storage, @Qualifier("runtimeProviderBudgetClock") Clock clock) {
        var scope = new ProviderBudgetLedger.Scope(environment.getRequiredProperty("AI_FABRIC_RUNTIME_CUSTOMER_ID"),
            environment.getRequiredProperty("AI_FABRIC_RUNTIME_DEPLOYMENT_ID"),
            environment.getRequiredProperty("LOOMAI_RUNTIME_TARGET_PROFILE_ID"),
            environment.getRequiredProperty("LOOMAI_RERANKING_BUDGET_ENVIRONMENT"),
            ReviewedCohereRerankingRateCard.credentialIdentity(credential.value()), ReviewedCohereRerankingRateCard.REFERENCE);
        var configuration = new RuntimeBudgetConfiguration(true, scope,
            environment.getRequiredProperty("AI_FABRIC_RUNTIME_TENANT_ID"));
        return new RuntimeBudgetBindingService(configuration, ledger, storage.independentDataSource(), clock,
            RuntimeBudgetBindingService.Account.RERANKING);
    }

    @Bean
    RerankingProvider runtimeRerankingProvider(RAGProperties properties, CohereCredential credential,
        ProviderBudgetLedger ledger, @Qualifier("rerankingBudgetBinding") RuntimeBudgetBindingService budgets) {
        if (!"cohere".equals(properties.getReranking().getProvider()) || properties.getRetrievalProfile() == null) {
            throw new IllegalArgumentException("An explicit supported reranker and shared retrieval profile are required");
        }
        String model = properties.getReranking().getModel();
        var guard = new LedgerRerankingCallBudget(ledger, budgets::approval, credential::value, model);
        return new CohereRerankingProvider(credential::value, model, guard);
    }
}
