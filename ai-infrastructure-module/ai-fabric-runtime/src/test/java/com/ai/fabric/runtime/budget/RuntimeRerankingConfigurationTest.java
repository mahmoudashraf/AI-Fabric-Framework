package com.ai.fabric.runtime.budget;

import ai.fabric.rag.RetrievalProfile;
import ai.fabric.rag.config.RAGProperties;
import ai.fabric.spi.RerankingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

class RuntimeRerankingConfigurationTest extends RuntimeBudgetFixture.TestScope {
    private static final String KEY = "fixture-reranking-key";

    @Test
    void disabledRerankingCreatesNeitherProviderNorAccountingInfrastructure() {
        new ApplicationContextRunner().withUserConfiguration(RuntimeRerankingConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(RerankingProvider.class)
                    .doesNotHaveBean(RuntimeBudgetStorage.class).doesNotHaveBean(RuntimeBudgetBindingService.class);
            });
    }

    @Test
    void dedicatedCohereBudgetCanBootWithoutEnablingOpenAiOrApprovingSpend() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var properties = new DataSourceProperties();
        properties.setUrl(fixture.dataSource.getUrl()); properties.setUsername("sa"); properties.setPassword("");
        properties.setDriverClassName("org.h2.Driver");
        new ApplicationContextRunner().withUserConfiguration(RuntimeRerankingConfiguration.class)
            .withPropertyValues("ai.infrastructure.rag.reranking.enabled=true", "LOOMAI_RERANKING_API_KEY=" + KEY,
                "LOOMAI_RERANKING_BUDGET_CREDENTIAL_IDENTITY=" + ReviewedCohereRerankingRateCard.credentialIdentity(KEY),
                "LOOMAI_RERANKING_BUDGET_ENVIRONMENT=staging", "AI_FABRIC_RUNTIME_CUSTOMER_ID=customer",
                "AI_FABRIC_RUNTIME_DEPLOYMENT_ID=deployment", "AI_FABRIC_RUNTIME_TENANT_ID=tenant",
                "LOOMAI_RUNTIME_TARGET_PROFILE_ID=target")
            .withBean(DataSourceProperties.class, () -> properties)
            .withBean(DataSource.class, () -> fixture.dataSource)
            .withBean(RAGProperties.class, () -> {
                var rag = new RAGProperties(); rag.setRetrievalProfile(RetrievalProfile.dense());
                rag.getReranking().setEnabled(true); rag.getReranking().setProvider("cohere");
                rag.getReranking().setModel(ReviewedCohereRerankingRateCard.FAST); return rag;
            }).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(RerankingProvider.class);
                var binding = context.getBean("rerankingBudgetBinding", RuntimeBudgetBindingService.class);
                assertThat(binding.readiness().code()).isEqualTo("NOT_INITIALIZED");
                assertThat(context).doesNotHaveBean(RuntimeBudgetTurnScopes.class);
                assertThat(context.getBean(RerankingProvider.class).toString()).doesNotContain(KEY);
            });
    }
}
