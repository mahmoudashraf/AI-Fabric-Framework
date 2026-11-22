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
}
