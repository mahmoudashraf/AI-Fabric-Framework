package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pipeline step that validates request compliance with policies and regulations.
 * 
 * <p>This step checks the request against configured compliance rules to ensure
 * it meets regulatory requirements (e.g., GDPR, HIPAA, content policies). It uses
 * the {@link AIComplianceService} for compliance evaluation.</p>
 * 
 * <p><strong>Order:</strong> 40 (after PII detection)</p>
 * 
 * <p><strong>Termination:</strong> If the request fails compliance validation,
 * the pipeline is terminated with an error result.</p>
 * 
 * @see AIComplianceService
 * @see PipelineStep
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceCheckStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "ComplianceCheck";
    private static final int STEP_ORDER = 40;
    
    // Error messages
    private static final String ERROR_MSG_COMPLIANCE_FAILED = "Request failed compliance validation.";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final AIComplianceService complianceService;
    
    // =========================================================================
    // PipelineStep Implementation
    // =========================================================================
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getStepName() {
        return STEP_NAME;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return STEP_ORDER;
    }
    
    /**
     * Validate request compliance.
     * 
     * <p>Creates an {@link AIComplianceRequest} with the processed query
     * (after PII redaction) and validates it against compliance policies.
     * If validation fails, the pipeline is terminated.</p>
     * 
     * @param context the current pipeline context
     * @return updated context, or terminated context if compliance fails
     */
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Checking compliance for request {}", context.getRequestId());
        
        AIComplianceRequest complianceRequest = AIComplianceRequest.builder()
            .requestId(context.getRequestId())
            .userId(context.getOrchestrationContext().getUserId())
            .content(context.getEffectiveQuery())
            .timestamp(context.getRequestTimestamp())
            .build();
        
        AIComplianceResponse complianceResponse = complianceService.checkCompliance(complianceRequest);
        
        if (Boolean.FALSE.equals(complianceResponse.getOverallCompliant())) {
            log.warn("Compliance check failed for request {} - user: {}", 
                context.getRequestId(), context.getIdentifier());
            return context.terminate(OrchestrationResult.error(ERROR_MSG_COMPLIANCE_FAILED));
        }
        
        log.debug("Compliance check passed for request {}", context.getRequestId());
        return context;
    }
}
