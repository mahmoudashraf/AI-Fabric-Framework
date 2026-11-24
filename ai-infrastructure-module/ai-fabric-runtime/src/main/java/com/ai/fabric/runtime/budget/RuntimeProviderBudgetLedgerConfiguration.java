package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Shared accounting infrastructure, without enabling either provider or registering an approval. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${loomai.provider-budget.enabled:false} || ${ai.infrastructure.rag.reranking.enabled:false}")
public class RuntimeProviderBudgetLedgerConfiguration {
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
}
