package com.ai.infrastructure.it.config;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.rag.SearchableEntityVectorDatabaseService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Decorates any VectorDatabaseService bean with the searchable-entity synchronizer so
 * that integration tests exercise the same persistence behavior used in production.
 */
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "test.searchable-vector-db.enabled", matchIfMissing = true)
public class SearchableVectorDatabaseTestConfiguration {

    @Bean
    public BeanPostProcessor vectorDatabaseDecorator(
        AISearchableEntityRepository searchableEntityRepository,
        AIEntityConfigurationLoader configurationLoader
    ) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof SearchableEntityVectorDatabaseService) {
                    return bean;
                }
                if (bean instanceof VectorDatabaseService service) {
                    return new SearchableEntityVectorDatabaseService(service, searchableEntityRepository, configurationLoader);
                }
                return bean;
            }
        };
    }
}
