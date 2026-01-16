package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.extraction.ProgressiveIntentExtractionEngine;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Pipeline step that extracts user intent from the query using LLM analysis.
 * 
 * <p>This step uses the {@link IntentQueryExtractor} to analyze the processed
 * query and extract structured intents. The LLM determines intent type (ACTION,
 * INFORMATION, OUT_OF_SCOPE, COMPOUND), action names, and parameters.</p>
 * 
 * <p><strong>Order:</strong> 50 (after compliance check)</p>
 * 
 * <p><strong>LLM Decision Respect:</strong> Per framework philosophy, the LLM's
 * analysis of the specific query is respected. Configuration provides constraints,
 * not overrides.</p>
 * 
 * <p><strong>Termination:</strong> If no intents can be extracted from the query,
 * the pipeline is terminated with an error result.</p>
 * 
 * @see IntentQueryExtractor
 * @see MultiIntentResponse
 * @see PipelineStep
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentExtractionStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "IntentExtraction";
    private static final int STEP_ORDER = 50;
    
    // Error messages
    private static final String ERROR_MSG_NO_INTENT = "Unable to determine user intent.";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final IntentQueryExtractor intentQueryExtractor;
    private final ObjectProvider<ProgressiveIntentExtractionEngine> progressiveEngineProvider;
    
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
     * Extract user intent from the query using LLM analysis.
     * 
     * <p>This step:</p>
     * <ol>
     *   <li>Passes the processed query to the intent extractor</li>
     *   <li>The LLM analyzes the query and returns structured intents</li>
     *   <li>If no intents are extracted, terminates with error</li>
     *   <li>Otherwise, updates context with the intent response</li>
     * </ol>
     * 
     * <p>The LLM's intent analysis is respected as authoritative for this
     * specific query context.</p>
     * 
     * @param context the current pipeline context
     * @return updated context with intent response, or terminated if no intent
     */
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Extracting intent for request {}", context.getRequestId());
        
        String processedQuery = context.getEffectiveQuery();

        ProgressiveIntentExtractionEngine engine = progressiveEngineProvider != null
            ? progressiveEngineProvider.getIfAvailable()
            : null;

        MultiIntentResponse intentResponse;
        PipelineContext updatedContext = context;

        if (engine != null) {
            ProgressiveIntentExtractionEngine.ExtractionOutput output = engine.extract(
                processedQuery,
                context.getOrchestrationContext()
            );
            intentResponse = output != null ? output.response() : null;
            if (output != null && output.diagnostics() != null && !output.diagnostics().isEmpty()) {
                updatedContext = updatedContext.withMetadata("extractionDiagnostics", output.diagnostics());
            }
        } else {
            intentResponse = intentQueryExtractor.extract(
                processedQuery,
                context.getOrchestrationContext()
            );
        }
        
        if (!intentResponse.hasIntents()) {
            log.warn("No intents extracted for query '{}' in request {}", 
                processedQuery, context.getRequestId());
            return updatedContext.terminate(OrchestrationResult.error(ERROR_MSG_NO_INTENT));
        }
        
        int intentCount = intentResponse.getIntents().size();
        boolean isCompound = intentResponse.isCompound();
        
        log.debug("Extracted {} intent(s) for request {} (compound: {})", 
            intentCount, context.getRequestId(), isCompound);
        
        return updatedContext.toBuilder()
            .intentResponse(intentResponse)
            .build();
    }
}
