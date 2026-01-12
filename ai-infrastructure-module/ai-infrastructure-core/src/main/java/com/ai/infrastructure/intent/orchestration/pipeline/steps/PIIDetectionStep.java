package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.PIIDetectionProperties;
import com.ai.infrastructure.config.PIIDetectionProperties.PIIDetectionDirection;
import com.ai.infrastructure.dto.PIIDetection;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pipeline step that detects and redacts Personally Identifiable Information (PII).
 * 
 * <p>This step analyzes the input query for PII such as social security numbers,
 * credit card numbers, email addresses, etc. When PII is detected, it is redacted
 * from the query before further processing.</p>
 * 
 * <p><strong>Order:</strong> 30 (after access control)</p>
 * 
 * <p><strong>Configuration:</strong> PII detection behavior is controlled by
 * {@link PIIDetectionProperties}:</p>
 * <ul>
 *   <li>{@code enabled} - Master switch for PII detection</li>
 *   <li>{@code detectionDirection} - INPUT, OUTPUT, or INPUT_OUTPUT</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This step only handles INPUT detection/redaction.
 * OUTPUT detection is handled by the {@link ResponseSanitizationStep}.</p>
 * 
 * @see PIIDetectionService
 * @see PIIDetectionProperties
 * @see PipelineStep
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PIIDetectionStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "PIIDetection";
    private static final int STEP_ORDER = 30;
    
    // Log messages
    private static final String LOG_PII_DETECTED = "PII detected in user query - types: {} (mode: INPUT_REDACTION)";
    private static final String LOG_PII_DISABLED = "PII INPUT detection is disabled (configuration: {})";
    private static final String LOG_QUERY_LENGTHS = "Original query length: {}, Redacted query length: {}";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final ObjectProvider<PIIDetectionService> piiDetectionService;
    private final PIIDetectionProperties piiDetectionProperties;
    
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
     * Detect and redact PII from the input query.
     * 
     * <p>If PII detection is enabled and configured for INPUT or INPUT_OUTPUT,
     * this step:</p>
     * <ol>
     *   <li>Analyzes the query for PII</li>
     *   <li>Extracts detected PII types</li>
     *   <li>Updates the context with the redacted query</li>
     *   <li>Records detected PII types for later use</li>
     * </ol>
     * 
     * @param context the current pipeline context
     * @return updated context with processed query and detected PII types
     */
    @Override
    public PipelineContext process(PipelineContext context) {
        PIIDetectionDirection detectionDirection = piiDetectionProperties.getDetectionDirection();
        
        boolean detectInput = piiDetectionProperties.isEnabled() &&
            (detectionDirection == PIIDetectionDirection.INPUT ||
             detectionDirection == PIIDetectionDirection.INPUT_OUTPUT);
        
        if (!detectInput) {
            log.debug(LOG_PII_DISABLED, detectionDirection);
            return context;
        }

        PIIDetectionService detectionService = piiDetectionService.getIfAvailable();
        if (detectionService == null) {
            log.debug("PII detection requested but no PIIDetectionService bean is available. Skipping.");
            return context;
        }
        
        log.debug("Executing PII detection for request {}", context.getRequestId());
        
        String originalQuery = context.getOriginalQuery();
        PIIDetectionResult piiAnalysis = detectionService.analyze(originalQuery);
        
        List<String> detectedPiiTypes = new ArrayList<>();
        String processedQuery = originalQuery;
        
        if (piiAnalysis.isPiiDetected()) {
            detectedPiiTypes = piiAnalysis.getDetections().stream()
                .map(PIIDetection::getType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
            
            log.info(LOG_PII_DETECTED, detectedPiiTypes);
            processedQuery = piiAnalysis.getProcessedQuery();
        }
        
        log.debug(LOG_QUERY_LENGTHS, originalQuery.length(), processedQuery.length());
        
        return context.toBuilder()
            .processedQuery(processedQuery)
            .detectedPiiTypes(detectedPiiTypes)
            .build();
    }
}
