package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SearchAuthoringPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void existingHumanApprovalCanBeRetainedAndRevokedButNotCreatedOrStringCoerced() throws Exception {
        var approved = mapper.readTree("{\"providerConfig\":{\"knowledgeRetrieval\":{\"reranking\":{\"externalProcessingApproved\":true}}}}");
        var absent = mapper.createObjectNode();
        var string = mapper.readTree("{\"providerConfig\":{\"knowledgeRetrieval\":{\"reranking\":{\"externalProcessingApproved\":\"true\"}}}}");
        assertThatCode(() -> SearchAuthoringPolicy.requireExistingConsent(approved, approved)).doesNotThrowAnyException();
        assertThatCode(() -> SearchAuthoringPolicy.requireExistingConsent(approved, absent)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SearchAuthoringPolicy.requireExistingConsent(absent, approved)).hasMessageContaining("Approve external processing");
        assertThatThrownBy(() -> SearchAuthoringPolicy.requireExistingConsent(string, approved)).hasMessageContaining("Approve external processing");
    }
}
