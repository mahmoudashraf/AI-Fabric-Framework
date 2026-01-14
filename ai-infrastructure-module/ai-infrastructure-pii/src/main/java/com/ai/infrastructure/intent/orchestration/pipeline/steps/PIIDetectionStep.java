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
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Pipeline step that (optionally) detects and redacts Personally Identifiable Information (PII)
 * in the user query before downstream processing.
 *
 * <p><strong>Order:</strong> 30 (after access control)</p>
 */
@Slf4j
@RequiredArgsConstructor
public class PIIDetectionStep implements PipelineStep {

    private static final String STEP_NAME = "PIIDetection";
    private static final int STEP_ORDER = 30;

    private final PIIDetectionService piiDetectionService;
    private final PIIDetectionProperties piiDetectionProperties;

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public PipelineContext process(PipelineContext context) {
        if (piiDetectionService == null || piiDetectionProperties == null) {
            return context;
        }

        PIIDetectionDirection detectionDirection = piiDetectionProperties.getDetectionDirection();
        boolean detectInput = piiDetectionProperties.isEnabled() &&
            (detectionDirection == PIIDetectionDirection.INPUT ||
                detectionDirection == PIIDetectionDirection.INPUT_OUTPUT);

        if (!detectInput) {
            return context;
        }

        String originalQuery = context.getOriginalQuery();
        if (!StringUtils.hasText(originalQuery)) {
            return context;
        }

        PIIDetectionResult piiResult = piiDetectionService.detectAndProcess(originalQuery);
        if (piiResult == null) {
            return context;
        }

        String processedQuery = StringUtils.hasText(piiResult.getProcessedQuery())
            ? piiResult.getProcessedQuery()
            : originalQuery;

        List<String> detectedTypes = piiResult.getDetections().stream()
            .map(PIIDetection::getType)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

        if (piiResult.isPiiDetected()) {
            log.info("PII detected in user query - types: {}", detectedTypes);
        }

        return context.toBuilder()
            .processedQuery(processedQuery)
            .detectedPiiTypes(detectedTypes)
            .build();
    }
}

