package com.ai.fabric.runtime.documents;

import ai.fabric.rag.evaluation.RagEvaluationResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RetrievalRankingMetricsTest {
    private DocumentPreparation.RelevanceJudgment label(String id, int grade, boolean reviewed) {
        return new DocumentPreparation.RelevanceJudgment("source", 1, id, grade, reviewed);
    }
    private DocumentPreparation.Question question(DocumentPreparation.RelevanceJudgment... labels) {
        return new DocumentPreparation.Question("q1", "Policy?", "", false, List.of(labels));
    }
    private DocumentKnowledgeService.RetrievalEvidence evidence(String source, Long version, String id) {
        return new DocumentKnowledgeService.RetrievalEvidence(id, source, version, "Policy", id, 0, "Approved text", .1, Map.of());
    }
    @Test void calculatesRecallFirstRelevantRankAndGradedDiscountedGainWithoutModels() {
        var result = RetrievalRankingMetrics.calculate(question(label("a", 3, true), label("b", 2, true), label("c", 0, true)),
            "source", 1, List.of(evidence("source", 1L, "c"), evidence("source", 1L, "a")), 2);
        assertThat(result.get("recallAtK").score()).isEqualTo(.5);
        assertThat(result.get("reciprocalRank").score()).isEqualTo(.5);
        double discounted = 7 / (Math.log(3) / Math.log(2));
        assertThat(result.get("ndcgAtK").score()).isCloseTo(discounted / (7 + 3 / (Math.log(3) / Math.log(2))), within(1e-10));
        assertThat(result.get("recallAtK").explanation()).contains("k=2", "judged retrieved=2/2", "not a completeness guarantee");
    }
    @Test void emptyRetrievalIsZeroOnlyWhenReviewedKnownRelevantLabelsExist() {
        var result = RetrievalRankingMetrics.calculate(question(label("a", 2, true)), "source", 1, List.of(), 5);
        assertThat(result.values()).allSatisfy(metric -> {
            assertThat(metric.status()).isEqualTo(RagEvaluationResult.Metric.Status.SCORED);
            assertThat(metric.score()).isZero();
        });
    }
    @Test void missingUnreviewedStaleForeignAndUnanswerableLabelsAreNotScored() {
        for (var question : List.of(question(), question(label("a", 1, false)), question(label("a", 0, true)),
            question(new DocumentPreparation.RelevanceJudgment("source", 2, "a", 2, true)),
            question(new DocumentPreparation.RelevanceJudgment("other-source", 1, "a", 2, true)))) {
            var result = RetrievalRankingMetrics.calculate(question, "source", 1, List.of(evidence("source", 1L, "a")), 5);
            assertThat(result.values()).allSatisfy(metric -> {
                assertThat(metric.status()).isEqualTo(RagEvaluationResult.Metric.Status.NOT_SCORED);
                assertThat(metric.score()).isNull();
            });
        }
    }
    @Test void sameIdInAnotherSourceOrVersionAndDuplicatesCannotInflateRecall() {
        var result = RetrievalRankingMetrics.calculate(question(label("a", 3, true), label("b", 1, true)), "source", 1,
            List.of(evidence("foreign", 1L, "a"), evidence("source", 9L, "b"), evidence("source", null, "b"),
                evidence("source", 1L, "a"), evidence("source", 1L, "a")), 5);
        assertThat(result.get("recallAtK").score()).isEqualTo(.5);
        assertThat(result.get("reciprocalRank").score()).isEqualTo(1);
    }
    @Test void unjudgedCandidatesAreExplicitAndDoNotGainRelevanceCredit() {
        var result = RetrievalRankingMetrics.calculate(question(label("a", 3, true)), "source", 1,
            List.of(evidence("source", 1L, "unjudged"), evidence("source", 1L, "a")), 2);
        assertThat(result.get("reciprocalRank").score()).isEqualTo(.5);
        assertThat(result.get("ndcgAtK").explanation()).contains("judged retrieved=1/2", "unjudged results contribute no known relevance gain");
        assertThatThrownBy(() -> RetrievalRankingMetrics.calculate(question(), "source", 1, List.of(), 11)).isInstanceOf(IllegalArgumentException.class);
    }
}
