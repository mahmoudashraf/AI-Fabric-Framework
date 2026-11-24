package com.ai.fabric.runtime.documents;

/** Per-run experiment selection; never edits the saved Brain or enables a provider. */
public enum EvaluationRetrievalVariant {
    CONFIGURED,
    DENSE_BASELINE,
    CONFIGURED_WITHOUT_RERANKING;

    public static EvaluationRetrievalVariant effective(EvaluationRetrievalVariant variant) {
        return variant == null ? CONFIGURED : variant;
    }
}
