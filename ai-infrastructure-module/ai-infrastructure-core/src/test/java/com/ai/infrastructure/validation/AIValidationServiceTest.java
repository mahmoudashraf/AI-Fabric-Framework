package com.ai.infrastructure.validation;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.validation.AIValidationService.AccuracyAnalysis;
import com.ai.infrastructure.validation.AIValidationService.CompletenessAnalysis;
import com.ai.infrastructure.validation.AIValidationService.ConsistencyAnalysis;
import com.ai.infrastructure.validation.AIValidationService.DataQualityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIValidationServiceTest {

    @Mock
    private AICoreService aiCoreService;

    @Mock
    private RAGService ragService;

    private AIValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new AIValidationService(aiCoreService, ragService);
        when(aiCoreService.generateContent(ArgumentMatchers.any()))
            .thenReturn(AIGenerationResponse.builder().content("analysis").build());
    }

    @Test
    void validateDataQualityComputesDeterministicScores() {
        Map<String, Object> recordOne = new HashMap<>();
        recordOne.put("id", "1");
        recordOne.put("price", 100);
        recordOne.put("status", "ACTIVE");

        Map<String, Object> recordTwo = new HashMap<>();
        recordTwo.put("id", "2");
        recordTwo.put("price", null);
        recordTwo.put("status", "unknown");
        recordTwo.put("extra", "value");

        List<Map<String, Object>> data = List.of(recordOne, recordTwo);

        DataQualityResult result = validationService.validateDataQuality(data, "product");

        CompletenessAnalysis completeness = result.getCompleteness();
        assertEquals(0.75, completeness.getCompletenessScore(), 1e-6);
        assertEquals(2, completeness.getMissingFields());

        ConsistencyAnalysis consistency = result.getConsistency();
        assertEquals(1.0, consistency.getConsistencyScore(), 1e-6);
        assertEquals(0, consistency.getInconsistencies());

        AccuracyAnalysis accuracy = result.getAccuracy();
        assertEquals(1, accuracy.getErrors());
        assertEquals(5.0 / 6.0, accuracy.getAccuracyScore(), 1e-6);

        assertEquals((0.75 + 1.0 + (5.0 / 6.0)) / 3.0, result.getOverallScore(), 1e-6);
    }
}
