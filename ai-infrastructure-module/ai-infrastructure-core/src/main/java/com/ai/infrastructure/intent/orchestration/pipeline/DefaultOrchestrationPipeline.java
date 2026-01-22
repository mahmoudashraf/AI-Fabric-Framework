package com.ai.infrastructure.intent.orchestration.pipeline;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default implementation of the orchestration {@link Pipeline}.
 * 
 * <p>This pipeline executes all registered {@link PipelineStep}s in order,
 * sorted by their {@link PipelineStep#getOrder()} value. Steps are auto-wired
 * by Spring and automatically included in the pipeline.</p>
 * 
 * <p><strong>Execution Process:</strong></p>
 * <ol>
 *   <li>Validate input parameters</li>
 *   <li>Create initial {@link PipelineContext}</li>
 *   <li>For each step (in order):
 *     <ul>
 *       <li>Check if step should be skipped</li>
 *       <li>Execute step and measure duration</li>
 *       <li>Check for early termination</li>
 *     </ul>
 *   </li>
 *   <li>Return the final result</li>
 * </ol>
 * 
 * <p><strong>Error Handling:</strong> If any step throws an exception, the
 * pipeline catches it and returns an error result. This prevents unhandled
 * exceptions from propagating up.</p>
 * 
 * <p><strong>Logging:</strong> The pipeline logs step execution at DEBUG level
 * and errors at ERROR level. Step timing is logged for performance analysis.</p>
 * 
 * @see Pipeline
 * @see PipelineStep
 * @see PipelineContext
 * @since 1.0
 */
@Slf4j
@Component
public class DefaultOrchestrationPipeline implements Pipeline {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String LOG_PREFIX = "[Pipeline]";
    private static final String ERROR_NULL_QUERY = "query must not be null";
    private static final String ERROR_BLANK_QUERY = "query must not be blank";
    private static final String ERROR_NULL_CONTEXT = "context must not be null";
    private static final String ERROR_STEP_FAILED_PREFIX = "Pipeline step failed: ";
    private static final String ERROR_NO_RESULT = "Pipeline completed but no result was produced";
    
    // =========================================================================
    // Fields
    // =========================================================================
    
    private final List<PipelineStep> steps;
    
    // =========================================================================
    // Constructor
    // =========================================================================
    
    /**
     * Create a new pipeline with the given steps.
     * 
     * <p>Steps are automatically sorted by their {@link PipelineStep#getOrder()}
     * value. Lower values execute earlier.</p>
     * 
     * @param steps the list of pipeline steps (injected by Spring)
     */
    public DefaultOrchestrationPipeline(List<PipelineStep> steps) {
        this.steps = steps.stream()
            .sorted(Comparator.comparingInt(PipelineStep::getOrder))
            .collect(Collectors.toList());
        
        String stepNames = this.steps.stream()
            .map(PipelineStep::getStepName)
            .collect(Collectors.joining(" → "));
        
        log.info("{} Initialized with {} steps: {}", LOG_PREFIX, this.steps.size(), stepNames);
    }
    
    // =========================================================================
    // Pipeline Interface Implementation
    // =========================================================================
    
    /**
     * {@inheritDoc}
     */
    @Override
    public OrchestrationResult execute(String query, OrchestrationContext context) {
        // Validate inputs
        Objects.requireNonNull(query, ERROR_NULL_QUERY);
        if (query.isBlank()) {
            throw new IllegalArgumentException(ERROR_BLANK_QUERY);
        }
        Objects.requireNonNull(context, ERROR_NULL_CONTEXT);
        context.validate();
        
        // Create initial context
        PipelineContext pipelineContext = PipelineContext.from(query, context);
        String requestId = pipelineContext.getRequestId();
        
        log.debug("{} Starting execution for request {} with {} steps", 
            LOG_PREFIX, requestId, steps.size());
        
        long pipelineStartTime = System.currentTimeMillis();
        boolean terminationLogged = false;
        
        // Execute each step
        for (PipelineStep step : steps) {
            // Check if step should be skipped
            if (step.shouldSkip(pipelineContext)) {
                log.debug("{} Skipping step {} for request {} (terminated={})", 
                    LOG_PREFIX, step.getStepName(), requestId, pipelineContext.isShouldTerminate());
                continue;
            }
            
            log.debug("{} Executing step {} for request {}", 
                LOG_PREFIX, step.getStepName(), requestId);
            
            long stepStartTime = System.currentTimeMillis();
            
            try {
                pipelineContext = step.process(pipelineContext);
                
                long stepDuration = System.currentTimeMillis() - stepStartTime;
                log.debug("{} Step {} completed in {}ms for request {}", 
                    LOG_PREFIX, step.getStepName(), stepDuration, requestId);
                
            } catch (Exception ex) {
                log.error("{} Step {} failed for request {}: {}", 
                    LOG_PREFIX, step.getStepName(), requestId, ex.getMessage(), ex);
                pipelineContext = pipelineContext.terminate(
                    OrchestrationResult.error(ERROR_STEP_FAILED_PREFIX + step.getStepName())
                );
            }
            
            // Check for early termination
            if (pipelineContext.isShouldTerminate() && !terminationLogged) {
                terminationLogged = true;
                log.debug("{} Pipeline terminated early at step {} for request {}",
                    LOG_PREFIX, step.getStepName(), requestId);
            }
        }
        
        long pipelineDuration = System.currentTimeMillis() - pipelineStartTime;
        log.debug("{} Pipeline execution completed in {}ms for request {}", 
            LOG_PREFIX, pipelineDuration, requestId);
        
        // Return the final result
        OrchestrationResult result = pipelineContext.getIntentResult();
        if (result == null) {
            log.error("{} {} for request {}", LOG_PREFIX, ERROR_NO_RESULT, requestId);
            return OrchestrationResult.error(ERROR_NO_RESULT);
        }
        
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PipelineStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
