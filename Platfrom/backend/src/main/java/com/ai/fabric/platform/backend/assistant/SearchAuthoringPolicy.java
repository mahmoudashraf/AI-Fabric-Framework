package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** External data-processing consent is a customer decision, not an AI-generated attestation. */
final class SearchAuthoringPolicy {
    private static final String CONSENT = "/providerConfig/knowledgeRetrieval/reranking/externalProcessingApproved";

    private SearchAuthoringPolicy() { }

    static void requireExistingConsent(JsonNode current, JsonNode candidate) {
        if (candidate.at(CONSENT).asBoolean(false)
            && !(current.at(CONSENT).isBoolean() && current.at(CONSENT).booleanValue())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Approve external processing in Search settings before asking AI to enable Cohere reranking.");
        }
    }
}
