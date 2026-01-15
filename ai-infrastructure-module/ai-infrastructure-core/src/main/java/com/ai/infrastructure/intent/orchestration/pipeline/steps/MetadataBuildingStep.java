package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pipeline step that builds and attaches metadata to the orchestration result.
 * 
 * <p>This step enriches the result with metadata about the request processing,
 * including request ID, session info, intent counts, and authentication status.</p>
 * 
 * <p><strong>Order:</strong> 70 (after intent handling)</p>
 * 
 * <p>The metadata provides context for downstream processing, logging,
 * and debugging. It is attached to the {@link OrchestrationResult}.</p>
 * 
 * @see OrchestrationResult
 * @see PipelineStep
 * @since 1.0
 */
@Slf4j
@Component
public class MetadataBuildingStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "MetadataBuilding";
    private static final int STEP_ORDER = 70;
    
    // Metadata keys
    private static final String METADATA_KEY_REQUEST_ID = "requestId";
    private static final String METADATA_KEY_SESSION_ID = "sessionId";
    private static final String METADATA_KEY_INTENTS_COUNT = "intentsCount";
    private static final String METADATA_KEY_COMPOUND = "compound";
    private static final String METADATA_KEY_AUTHENTICATED = "authenticated";
    private static final String METADATA_KEY_INTENT_METADATA = "intentMetadata";
    
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

    @Override
    public boolean shouldSkip(PipelineContext context) {
        return false;
    }
    
    /**
     * Build and attach metadata to the orchestration result.
     * 
     * <p>This step collects information from the pipeline context and
     * intent response to create comprehensive metadata for the result.</p>
     * 
     * @param context the current pipeline context
     * @return updated context (result metadata is set on the intent result)
     */
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Building metadata for request {}", context.getRequestId());
        
        OrchestrationResult result = context.getIntentResult();
        if (result == null) {
            log.warn("No intent result available for metadata building in request {}", 
                context.getRequestId());
            return context;
        }
        
        MultiIntentResponse intentResponse = context.getIntentResponse();
        
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result.getMetadata() != null && !result.getMetadata().isEmpty()) {
            metadata.putAll(result.getMetadata());
        }
        if (context.getMetadata() != null && !context.getMetadata().isEmpty()) {
            metadata.putAll(context.getMetadata());
        }
        metadata.put(METADATA_KEY_REQUEST_ID, context.getRequestId());
        metadata.put(METADATA_KEY_SESSION_ID, context.getOrchestrationContext().getSessionId());
        metadata.put(METADATA_KEY_INTENTS_COUNT, 
            intentResponse != null ? intentResponse.getIntents().size() : 0);
        metadata.put(METADATA_KEY_COMPOUND, 
            intentResponse != null && intentResponse.isCompound());
        metadata.put(METADATA_KEY_AUTHENTICATED, context.isAuthenticated());
        
        if (intentResponse != null && !CollectionUtils.isEmpty(intentResponse.getMetadata())) {
            metadata.put(METADATA_KEY_INTENT_METADATA, intentResponse.getMetadata());
        }
        
        result.setMetadata(Collections.unmodifiableMap(metadata));
        
        log.debug("Built metadata with {} entries for request {}", 
            metadata.size(), context.getRequestId());
        
        return context.toBuilder()
            .metadata(metadata)
            .build();
    }
}
