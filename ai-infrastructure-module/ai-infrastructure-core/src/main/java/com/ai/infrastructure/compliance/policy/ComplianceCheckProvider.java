package com.ai.infrastructure.compliance.policy;

import com.ai.infrastructure.dto.AIComplianceRequest;

/**
 * Infrastructure hook that allows customers to enforce organisation specific compliance logic.
 */
@FunctionalInterface
public interface ComplianceCheckProvider {

    /**
     * Evaluate whether the supplied request is compliant with organisation policy.
     *
     * @param request contextual information about the compliance request
     * @return result describing compliance outcome and any violations
     */
    ComplianceCheckResult checkCompliance(AIComplianceRequest request);
}
