package com.ai.fabric.platform.backend.deployment.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record DocumentKnowledgeVerificationExpectationOverrides(
    String deploymentId,
    String datasetId,
    String textObjectReference,
    String textRetrievalQuery,
    String jsonObjectReference,
    String jsonRetrievalQuery,
    String expectedConnectorType,
    boolean cleanupIndex,
    String targetProfileId,
    String releaseId
) {
    public Map<String, String> toEnvironmentOverrides() {
        Map<String, String> result = new LinkedHashMap<>();
        put(result, "DOCUMENT_DEPLOYMENT_ID", deploymentId);
        put(result, "DOCUMENT_TARGET_PROFILE_ID", targetProfileId);
        put(result, "DOCUMENT_RELEASE_ID", releaseId);
        put(result, "DOCUMENT_DATASET_ID", datasetId);
        put(result, "DOCUMENT_TEXT_OBJECT_REFERENCE", textObjectReference);
        put(result, "DOCUMENT_TEXT_RETRIEVAL_QUERY", textRetrievalQuery);
        put(result, "DOCUMENT_JSON_OBJECT_REFERENCE", jsonObjectReference);
        put(result, "DOCUMENT_JSON_RETRIEVAL_QUERY", jsonRetrievalQuery);
        put(result, "DOCUMENT_EXPECTED_CONNECTOR_TYPE", expectedConnectorType);
        result.put("DOCUMENT_CLEANUP_INDEX", Boolean.toString(cleanupIndex));
        return Map.copyOf(result);
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }
}
