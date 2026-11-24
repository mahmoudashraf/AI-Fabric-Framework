package com.ai.fabric.runtime.documents;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DocumentPreparationTest {
    @Test void legacySourcesRetainTheExistingChunkDefaults() throws Exception {
        var profile=new ObjectMapper().readValue("{}",DocumentPreparation.class);
        assertThat(profile.processing().chunkTokens()).isEqualTo(800); assertThat(profile.processing().overlapTokens()).isZero();
        assertThat(profile.questions()).isEmpty();
    }
    @Test void rejectsUnboundedOrNonFiniteEvaluationAndOcrArguments() {
        assertThatThrownBy(() -> new DocumentPreparation.EvaluationOptions(21,5,8000,0,700)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentPreparation.EvaluationOptions(5,5,8000,Double.NaN,700)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentPreparation.Processing(400,300,100,5,ai.fabric.document.DocumentReadOptions.OcrMode.AUTO,"eng",150)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentPreparation.Processing(400,40,100,5,ai.fabric.document.DocumentReadOptions.OcrMode.AUTO,"eng;cmd",150)).isInstanceOf(IllegalArgumentException.class);
        var question=new DocumentPreparation.Question("q1","Question?","Reference",false);
        assertThatThrownBy(() -> new DocumentPreparation(null,null,List.of(question,question))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expectedEvidenceRetainsExactIdentityAndLegacyQuestionsNeedNoLabels() throws Exception {
        var mapper = new ObjectMapper();
        var question = mapper.readValue("{\"id\":\"q1\",\"question\":\"Policy?\",\"reviewed\":false}", DocumentPreparation.Question.class);
        assertThat(question.relevanceJudgments()).isEmpty();
        var snapshot = new DocumentKnowledgeService.PreparationSnapshot("source", 1, 1, 1L, DocumentPreparation.defaults(), List.of("eng"), true, false);
        assertThat(mapper.valueToTree(snapshot).path("relevanceLabelsSupported").asBoolean()).isTrue();
        var label = new DocumentPreparation.RelevanceJudgment("source", 1, "chunk-a", 3, true);
        assertThatThrownBy(() -> new DocumentPreparation.Question("q1", "Policy?", "", false, List.of(label, label))).isInstanceOf(IllegalArgumentException.class);
        for (int grade : List.of(-1, 4)) assertThatThrownBy(() -> new DocumentPreparation.RelevanceJudgment("source", 1, "chunk-a", grade, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentPreparation.RelevanceJudgment("source", 0, "chunk-a", 1, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DocumentPreparation.RelevanceJudgment("source", 1, "\nchunk", 1, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
