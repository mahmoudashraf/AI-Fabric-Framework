package com.ai.fabric.runtime.documents;
import ai.fabric.core.*;
import ai.fabric.dto.*;
import ai.fabric.rag.evaluation.*;
import com.ai.fabric.runtime.auth.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentEvaluationModelsTest {
    private final AICoreService core=mock(AICoreService.class);
    private final DocumentEvaluationModels models=new DocumentEvaluationModels(core,new ObjectMapper());
    private final RuntimeResolvedIdentity identity=identity();
    @Test void oneStructuredJudgeCallUsesActualEvidenceAndMarksAbsentReferenceUnscored() {
        when(core.generateContent(any(AIGenerationRequest.class),eq(LlmPurpose.GENERATION))).thenReturn(AIGenerationResponse.builder().content("""
            {"faithfulness":{"score":0.9,"explanation":"The claim is supported by chunk 1."},
            "relevance":{"score":0.8,"explanation":"Addresses the question."},"referenceAgreement":{"score":0.9,"explanation":"No reference supplied."}}
            """).build());
        var usage=new ArrayList<AIGenerationResponse>();
        var result=models.judge(identity,usage::add).evaluate(new RagEvaluationSample("What is the return window?","30 days",List.of(new RagEvaluationSample.Evidence("chunk-1","source-a","Returns accepted within 30 days.")),""));
        assertThat(result.metrics().get("faithfulness").score()).isEqualTo(0.9);
        assertThat(result.metrics().get("referenceAgreement").status()).isEqualTo(RagEvaluationResult.Metric.Status.NOT_SCORED);
        var captured=org.mockito.ArgumentCaptor.forClass(AIGenerationRequest.class); verify(core).generateContent(captured.capture(),eq(LlmPurpose.GENERATION));
        assertThat(captured.getValue().getSystemPrompt()).contains("untrusted data"); assertThat(captured.getValue().getPrompt()).contains("chunk-1","Returns accepted");
        assertThat(usage).hasSize(1);
    }
    @Test void malformedJudgeDoesNotBecomeZeroOrTriggerAnotherGeneration() {
        when(core.generateContent(any(AIGenerationRequest.class),eq(LlmPurpose.GENERATION))).thenReturn(AIGenerationResponse.builder().content("YES, probably").build());
        assertThatThrownBy(() -> models.judge(identity,r -> {}).evaluate(new RagEvaluationSample("Question","Answer",List.of(),"")))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("structured metrics");
        verify(core,times(1)).generateContent(any(AIGenerationRequest.class),eq(LlmPurpose.GENERATION));
    }
    private static RuntimeResolvedIdentity identity() {
        RuntimeAuthContext auth=mock(RuntimeAuthContext.class);
        when(auth.getSubjectType()).thenReturn(RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER);
        when(auth.getAuthMode()).thenReturn(RuntimeAuthMode.PLATFORM_PROXY_SESSION);
        when(auth.getCallerType()).thenReturn(RuntimeAuthCallerType.PLATFORM_PROXY);
        when(auth.getExpiresAt()).thenReturn(java.time.Instant.now().plusSeconds(300));
        RuntimeResolvedIdentity identity=mock(RuntimeResolvedIdentity.class);when(identity.getAuthContext()).thenReturn(auth); return identity;
    }
}
