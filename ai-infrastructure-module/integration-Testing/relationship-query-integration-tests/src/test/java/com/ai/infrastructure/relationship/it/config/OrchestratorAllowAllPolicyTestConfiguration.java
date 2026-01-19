package com.ai.infrastructure.relationship.it.config;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.compliance.policy.ComplianceCheckResult;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Test-only policies for the orchestrator pipeline.
 *
 * <p>Access control and compliance are fail-closed by design, so integration tests must
 * provide explicit allow policies to avoid terminating the pipeline early.</p>
 */
@TestConfiguration
public class OrchestratorAllowAllPolicyTestConfiguration {

    @Bean
    public EntityAccessPolicy allowAllEntityAccessPolicy() {
        return (userId, entity) -> true;
    }

    @Bean
    public ComplianceCheckProvider allowAllComplianceCheckProvider() {
        return request -> ComplianceCheckResult.builder()
            .compliant(true)
            .build();
    }
}

