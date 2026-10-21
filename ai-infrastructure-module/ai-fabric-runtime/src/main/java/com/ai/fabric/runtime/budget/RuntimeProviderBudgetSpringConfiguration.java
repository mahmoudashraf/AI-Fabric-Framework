package com.ai.fabric.runtime.budget;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.core.AIEmbeddingService;
import ai.fabric.indexing.api.AIIndexAnalysisHandler;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.service.VectorManagementService;
import ai.fabric.embedding.EmbeddingProvider;
import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver.OutputCapMode;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "loomai.provider-budget", name = "enabled", havingValue = "true")
public class RuntimeProviderBudgetSpringConfiguration {
    @Bean
    RuntimeProviderBudgetProperties runtimeProviderBudgetProperties(
        @Value("${loomai.provider-budget.environment:${LOOMAI_PROVIDER_BUDGET_ENVIRONMENT:}}") String environment,
        @Value("${loomai.provider-budget.credential-identity:${LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY:}}") String credential,
        @Value("${loomai.provider-budget.rate-card-reference:${LOOMAI_PROVIDER_BUDGET_RATE_CARD_REFERENCE:}}") String rateCard,
        @Value("${loomai.provider-budget.output-cap-mode:${LOOMAI_PROVIDER_BUDGET_OUTPUT_CAP_MODE:}}") String outputCapMode) {
        if (!OutputCapMode.MAX_COMPLETION_TOKENS.name().equals(outputCapMode)) {
            throw new IllegalArgumentException("RUNTIME_BUDGET_EXPLICIT_MAX_COMPLETION_TOKENS_REQUIRED");
        }
        return new RuntimeProviderBudgetProperties(environment, credential, rateCard, OutputCapMode.MAX_COMPLETION_TOKENS);
    }

    @Bean
    RuntimeBudgetConfiguration runtimeBudgetConfiguration(RuntimeProviderBudgetProperties properties,
        @Value("${AI_FABRIC_RUNTIME_CUSTOMER_ID:}") String customerId,
        @Value("${AI_FABRIC_RUNTIME_DEPLOYMENT_ID:}") String deploymentId,
        @Value("${AI_FABRIC_RUNTIME_TENANT_ID:}") String tenantId,
        @Value("${LOOMAI_RUNTIME_TARGET_PROFILE_ID:}") String targetProfileId) {
        return new RuntimeBudgetConfiguration(true, new ProviderBudgetLedger.Scope(customerId, deploymentId, targetProfileId,
            properties.environment(), properties.credentialIdentity(), properties.rateCardReference()), tenantId);
    }

    @Bean("runtimeProviderBudgetClock")
    Clock runtimeProviderBudgetClock() { return Clock.systemUTC(); }

    @Bean
    @DependsOnDatabaseInitialization
    RuntimeBudgetStorage runtimeBudgetStorage(DataSourceProperties properties) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            throw new IllegalStateException("Independent budget accounting requires the explicit runtime JDBC URL.");
        }
        var independent = new DriverManagerDataSource(properties.getUrl(), properties.determineUsername(), properties.determinePassword());
        if (properties.getDriverClassName() != null && !properties.getDriverClassName().isBlank()) {
            independent.setDriverClassName(properties.getDriverClassName());
        }
        return new RuntimeBudgetStorage(independent);
    }

    @Bean
    ProviderBudgetLedger runtimeProviderBudgetLedger(RuntimeBudgetStorage storage,
        @Qualifier("runtimeProviderBudgetClock") Clock clock) {
        return new JdbcProviderBudgetLedger(storage.independentDataSource(), clock);
    }

    @Bean
    RuntimeBudgetBindingService runtimeBudgetBindingService(RuntimeBudgetConfiguration configuration,
        ProviderBudgetLedger ledger, RuntimeBudgetStorage storage, @Qualifier("runtimeProviderBudgetClock") Clock clock) {
        return new RuntimeBudgetBindingService(configuration, ledger, storage.independentDataSource(), clock);
    }

    @Bean
    RuntimeBudgetTurnScopes runtimeBudgetTurnScopes(RuntimeBudgetBindingService budgets) { return new RuntimeBudgetTurnScopes(budgets); }

    @Bean("advancedRagSearchExecutor")
    @ConditionalOnProperty(prefix = "ai.infrastructure.rag.advanced", name = "enabled", havingValue = "true", matchIfMissing = true)
    Executor fundedAdvancedRagSearchExecutor() {
        // This guarded profile supports synchronous scopes; expanded searches retain that same scope.
        return Runnable::run;
    }

    @Bean
    @Primary
    BudgetGuardedSpringAiModelResolver runtimeBudgetSpringAiModelResolver(AIProviderConfig providers,
        ObjectProvider<ObservationRegistry> observations, ProviderBudgetLedger ledger, RuntimeBudgetBindingService budgets,
        RuntimeBudgetTurnScopes turns, @Qualifier("onnxEmbeddingProvider") ObjectProvider<EmbeddingProvider> localOnnx,
        RuntimeProviderBudgetProperties properties) {
        return RuntimeBudgetResolverFactory.create(providers, observations.getIfAvailable(() -> ObservationRegistry.NOOP), ledger,
            budgets, turns, "onnx".equals(providers.getEmbeddingProvider()) ? localOnnx.getIfAvailable() : null, properties.outputCapMode());
    }

    @Bean
    RuntimeBudgetAdminReadback runtimeBudgetAdminReadback(RuntimeBudgetBindingService budgets, RuntimeRequestAuthResolver auth) {
        return new RuntimeBudgetAdminReadback(budgets, auth);
    }

    @Bean("providerBudget")
    RuntimeProviderBudgetHealthIndicator runtimeProviderBudgetHealthIndicator(RuntimeBudgetBindingService budgets) {
        return new RuntimeProviderBudgetHealthIndicator(budgets);
    }

    @Bean
    RuntimeBudgetHealthGroups runtimeBudgetHealthGroups() { return new RuntimeBudgetHealthGroups(); }

    @Bean
    RuntimeBudgetIndexingOperationExecutor runtimeBudgetIndexingOperationExecutor(AIEmbeddingService embeddings,
        VectorManagementService vectors, VectorDatabaseService vectorDatabase, ObjectProvider<AIIndexAnalysisHandler> analysis,
        ObjectMapper mapper, RuntimeBudgetTurnScopes turns) {
        return new RuntimeBudgetIndexingOperationExecutor(embeddings, vectors, vectorDatabase, analysis, mapper, turns);
    }
}
