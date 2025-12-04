package com.ai.behavior.realapi;

import com.ai.behavior.config.BehaviorModuleConfiguration;
import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.policy.BehaviorAnalysisPolicy;
import com.ai.behavior.policy.DefaultBehaviorAnalysisPolicy;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.compliance.policy.ComplianceCheckResult;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(
    basePackages = {"com.ai.behavior", "com.ai.infrastructure"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.ai\\.behavior\\.integration\\..*"),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = com.ai.behavior.integration.TestBehaviorApplication.class)
    }
)
@EntityScan(basePackages = {"com.ai.behavior", "com.ai.infrastructure"})
@EnableConfigurationProperties(BehaviorModuleProperties.class)
@Import({
    BehaviorModuleConfiguration.class,
    AIInfrastructureAutoConfiguration.class
})
public class BehaviorRealApiTestApplication {

    @Configuration
    @EnableJpaRepositories(basePackages = {"com.ai.infrastructure.repository", "com.ai.behavior.repository", "com.ai.behavior.storage"})
    static class InfrastructureRepositoriesConfiguration {
    }

    @Configuration
    static class BehaviorPolicyConfiguration {
        @Bean
        @Primary
        BehaviorAnalysisPolicy behaviorAnalysisPolicy(BehaviorModuleProperties properties) {
            return new DefaultBehaviorAnalysisPolicy(properties);
        }

        @Bean
        EntityAccessPolicy testEntityAccessPolicy() {
            return (userId, entity) -> true;
        }

        @Bean
        ComplianceCheckProvider testComplianceCheckProvider() {
            return request -> ComplianceCheckResult.builder()
                .compliant(true)
                .details("real-api-test-policy")
                .build();
        }
    }
}
