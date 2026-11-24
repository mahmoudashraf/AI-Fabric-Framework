package com.ai.fabric.runtime.documents;

import ai.fabric.rag.evaluation.RagEvaluationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Arithmetic over explicitly reviewed identities, not a model or a claim of complete relevance labels. */
public final class RetrievalRankingMetrics {
    private RetrievalRankingMetrics() { }

    public static Map<String, RagEvaluationResult.Metric> calculate(DocumentPreparation.Question question,
        String sourceId, long sourceVersion, List<DocumentKnowledgeService.RetrievalEvidence> evidence, int k) {
        if (k < 1 || k > 10) throw new IllegalArgumentException("Ranking evaluation requires a bounded top-k.");
        var judgments = question.relevanceJudgments();
        if (judgments.isEmpty()) return notScored("No reviewed expected evidence identities were supplied.");
        if (judgments.stream().anyMatch(label -> !label.reviewed())) return notScored("Expected evidence includes unreviewed labels.");
        if (judgments.stream().anyMatch(label -> !sourceId.equals(label.sourceId()) || sourceVersion != label.sourceVersion()))
            return notScored("Expected evidence belongs to another source version. Review labels against the active index.");
        Map<String, Integer> grades = new LinkedHashMap<>();
        judgments.forEach(label -> grades.put(label.evidenceId(), label.grade()));
        long relevant = grades.values().stream().filter(grade -> grade > 0).count();
        if (relevant == 0) return notScored("No known relevant identities. Unanswerable questions need separate abstention review.");
        var ranked = evidence.stream().filter(item -> sourceId.equals(item.sourceId()) && Long.valueOf(sourceVersion).equals(item.sourceVersion()))
            .map(DocumentKnowledgeService.RetrievalEvidence::chunkId).distinct().limit(k).toList();
        int hits = 0, judged = 0; double reciprocal = 0, dcg = 0;
        for (int index = 0; index < ranked.size(); index++) {
            int grade = grades.getOrDefault(ranked.get(index), 0);
            if (grades.containsKey(ranked.get(index))) judged++;
            if (grade > 0) { hits++; if (reciprocal == 0) reciprocal = 1.0 / (index + 1); }
            dcg += gain(grade, index);
        }
        var ideal = grades.values().stream().sorted(java.util.Comparator.reverseOrder()).limit(k).toList();
        double idealDcg = 0;
        for (int index = 0; index < ideal.size(); index++) idealDcg += gain(ideal.get(index), index);
        String qualification = " Exact-ID ranking v1, k=" + k + ", source version=" + sourceVersion
            + ", judged retrieved=" + judged + "/" + ranked.size()
            + ". Uses the supplied reviewed pool, not a completeness guarantee; unjudged results contribute no known relevance gain.";
        return Map.of("recallAtK", metric((double)hits / relevant, "Known relevant identities retrieved / known relevant identities." + qualification),
            "reciprocalRank", metric(reciprocal, "Reciprocal position of the first known relevant identity." + qualification),
            "ndcgAtK", metric(dcg / idealDcg, "Normalized discounted gain, grades 0..3, gain=2^grade-1." + qualification));
    }

    private static double gain(int grade, int zeroBasedRank) { return (Math.pow(2, grade) - 1) / (Math.log(zeroBasedRank + 2) / Math.log(2)); }
    private static RagEvaluationResult.Metric metric(double score, String explanation) {
        return new RagEvaluationResult.Metric(RagEvaluationResult.Metric.Status.SCORED, score, explanation);
    }
    private static Map<String, RagEvaluationResult.Metric> notScored(String reason) {
        var metric = new RagEvaluationResult.Metric(RagEvaluationResult.Metric.Status.NOT_SCORED, null, reason);
        return Map.of("recallAtK", metric, "reciprocalRank", metric, "ndcgAtK", metric);
    }
}
