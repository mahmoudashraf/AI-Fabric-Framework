package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.AIAccessSubjectContext;
import com.ai.infrastructure.dto.AISecurityRequest;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.security.AISecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pipeline step that performs security analysis on incoming requests.
 * 
 * <p>This step is the first line of defense, analyzing requests for potential
 * security threats before any processing occurs. It uses the {@link AISecurityService}
 * to evaluate the request and can block suspicious activity.</p>
 * 
 * <p><strong>Order:</strong> 10 (first step in pipeline)</p>
 * 
 * <p><strong>Termination:</strong> If security analysis determines the request
 * should be blocked, this step terminates the pipeline with an error result.</p>
 * 
 * @see AISecurityService
 * @see PipelineStep
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAnalysisStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "SecurityAnalysis";
    private static final int STEP_ORDER = 10;
    
    // Operation types
    private static final String OPERATION_TYPE_INTENT_QUERY = "INTENT_QUERY";
    
    // Error messages
    private static final String ERROR_MSG_SECURITY_BLOCKED = "Request blocked by security controls.";
    
    // Metadata keys
    private static final String METADATA_KEY_AUTHENTICATED = "authenticated";
    private static final String METADATA_KEY_SESSION_ID = "sessionId";
    private static final String METADATA_KEY_IP_ADDRESS = "ipAddress";
    private static final String METADATA_KEY_USER_AGENT = "userAgent";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final AISecurityService securityService;
    
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
     * Perform security analysis on the request.
     * 
     * <p>Creates an {@link AISecurityRequest} from the pipeline context and
     * submits it to the security service for analysis. If the security service
     * indicates the request should be blocked, the pipeline is terminated.</p>
     * 
     * @param context the current pipeline context
     * @return updated context, or terminated context if blocked
     */
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Executing security analysis for request {}", context.getRequestId());
        
        OrchestrationContext orchContext = context.getOrchestrationContext();
        
        AISecurityRequest securityRequest = AISecurityRequest.builder()
            .requestId(context.getRequestId())
            .authContext(buildAuthContext(orchContext))
            .content(context.getOriginalQuery())
            .operationType(OPERATION_TYPE_INTENT_QUERY)
            .timestamp(context.getRequestTimestamp())
            .metadata(buildSecurityMetadata(orchContext))
            .ipAddress(orchContext.getIpAddress())
            .userAgent(orchContext.getUserAgent())
            .build();
        
        AISecurityResponse securityResponse = securityService.analyzeRequest(securityRequest);
        
        if (Boolean.TRUE.equals(securityResponse.getShouldBlock())) {
            log.warn("Security blocked request {} for user {}", 
                context.getRequestId(), context.getIdentifier());
            return context.terminate(OrchestrationResult.error(ERROR_MSG_SECURITY_BLOCKED));
        }
        
        log.debug("Security analysis passed for request {}", context.getRequestId());
        return context;
    }
    
    // =========================================================================
    // Private Helper Methods
    // =========================================================================
    
    /**
     * Build metadata map for security analysis.
     * 
     * @param context the orchestration context
     * @return metadata map with security-relevant information
     */
    private Map<String, Object> buildSecurityMetadata(OrchestrationContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_KEY_AUTHENTICATED, context.isAuthenticated());
        metadata.put(METADATA_KEY_SESSION_ID, context.getSessionId());
        
        if (context.getIpAddress() != null) {
            metadata.put(METADATA_KEY_IP_ADDRESS, context.getIpAddress());
        }
        if (context.getUserAgent() != null) {
            metadata.put(METADATA_KEY_USER_AGENT, context.getUserAgent());
        }
        if (context.getMetadata() != null && !context.getMetadata().isEmpty()) {
            metadata.putAll(context.getMetadata());
        }
        
        return metadata;
    }

    private AIAccessSubjectContext buildAuthContext(OrchestrationContext context) {
        Map<String, Object> metadata = context != null ? context.getMetadata() : null;
        return AIAccessSubjectContext.builder()
            .subjectId(resolveString(metadata, OrchestrationContextMetadataKeys.SUBJECT_ID, context != null ? context.getUserId() : null))
            .sessionId(context != null ? context.getSessionId() : null)
            .subjectType(resolveString(metadata, OrchestrationContextMetadataKeys.SUBJECT_TYPE, context != null && context.isAuthenticated() ? "USER" : "ANONYMOUS"))
            .authMode(resolveString(metadata, OrchestrationContextMetadataKeys.AUTH_MODE, null))
            .callerType(resolveString(metadata, OrchestrationContextMetadataKeys.CALLER_TYPE, null))
            .deploymentId(resolveString(metadata, OrchestrationContextMetadataKeys.DEPLOYMENT_ID, null))
            .customerId(resolveString(metadata, OrchestrationContextMetadataKeys.CUSTOMER_ID, null))
            .tenantId(resolveString(metadata, OrchestrationContextMetadataKeys.TENANT_ID, null))
            .issuer(resolveString(metadata, OrchestrationContextMetadataKeys.AUTH_ISSUER, null))
            .audiences(resolveStringList(metadata, OrchestrationContextMetadataKeys.AUTH_AUDIENCES))
            .grantedScopes(resolveStringList(metadata, OrchestrationContextMetadataKeys.GRANTED_SCOPES))
            .expiresAt(resolveString(metadata, OrchestrationContextMetadataKeys.AUTH_EXPIRES_AT, null))
            .build();
    }

    private String resolveString(Map<String, Object> metadata, String key, String fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object value = metadata.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private java.util.List<String> resolveStringList(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof java.util.List<?> list) {
            return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(text -> !text.isBlank())
                .toList();
        }
        return null;
    }
}
