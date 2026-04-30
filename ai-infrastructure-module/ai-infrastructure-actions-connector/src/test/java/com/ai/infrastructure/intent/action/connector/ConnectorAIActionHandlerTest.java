package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorAIActionHandlerTest {

    @Test
    void shouldBuildCompactLlmFactsFromConfiguredProjection() {
        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("Action executed.")
            .data(ActionPayload.object(Map.of(
                "success", true,
                "data", Map.of(
                    "query", "Find ready candidates under 10",
                    "documents", List.of(
                        candidateRecord("Beta Candidate", "11.5", true),
                        candidateRecord("Alpha Candidate", "8.5", true),
                        candidateRecord("Gamma Candidate", "7.0", false),
                        candidateRecord("Delta Candidate", "6.5", true)
                    ),
                    "totalResults", 4,
                    "returnedResults", 4
                )
            )))
            .build();
        ConnectorAIActionHandler handler = new ConnectorAIActionHandler(
            metadata(),
            false,
            null,
            Set.of(),
            null,
            configuredFacts()
        );

        Optional<Map<String, Object>> facts = handler.buildPostActionLlmFacts(actionResult, null);

        assertThat(facts).isPresent();
        assertThat(facts.get())
            .containsEntry("action", "search_records")
            .containsEntry("query", "Find ready candidates under 10")
            .containsEntry("totalResults", 4)
            .containsEntry("returnedResults", 4);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) facts.get().get("records");
        assertThat(records)
            .extracting(record -> record.get("name"))
            .containsExactly("Delta Candidate", "Alpha Candidate", "Gamma Candidate", "Beta Candidate");
        assertThat(records.getFirst())
            .containsEntry("scoreValue", "6.5")
            .containsEntry("isReady", true)
            .doesNotContainKey("internalPayload");
    }

    @Test
    void shouldEmitConfiguredConstraintMatchesAndNumericSummaryWithoutDomainRules() {
        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("Action executed.")
            .data(ActionPayload.object(Map.of(
                "data", Map.of(
                    "query", "Find ready candidates with score <= 10",
                    "documents", List.of(
                        candidateRecord("Beta Candidate", "11.5", true),
                        candidateRecord("Alpha Candidate", "8.5", true),
                        candidateRecord("Gamma Candidate", "7.0", false),
                        candidateRecord("Delta Candidate", "6.5", true)
                    ),
                    "totalResults", 4,
                    "returnedResults", 4
                )
            )))
            .build();
        ConnectorAIActionHandler handler = new ConnectorAIActionHandler(
            metadata(),
            false,
            null,
            Set.of(),
            null,
            configuredFacts()
        );

        Optional<Map<String, Object>> facts = handler.buildPostActionLlmFacts(actionResult, null);

        assertThat(facts).isPresent();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> constraintMatches = (List<Map<String, Object>>) facts.get().get("recordsConstraintMatches");
        assertThat(constraintMatches)
            .extracting(record -> record.get("name"))
            .containsExactly("Delta Candidate", "Alpha Candidate");
        assertThat(facts.get()).containsEntry("recordsConstraintMatchCount", 2);

        @SuppressWarnings("unchecked")
        Map<String, Object> allSummary = (Map<String, Object>) facts.get().get("recordsScoreSummary");
        assertThat(allSummary)
            .containsEntry("scoredRecords", 4)
            .containsEntry("lowestScoreName", "Delta Candidate")
            .containsEntry("lowestScore", 6.5)
            .containsEntry("highestScoreName", "Beta Candidate")
            .containsEntry("highestScore", 11.5);

        @SuppressWarnings("unchecked")
        Map<String, Object> matchedSummary = (Map<String, Object>) facts.get().get("recordsMatchedScoreSummary");
        assertThat(matchedSummary)
            .containsEntry("scoredRecords", 2)
            .containsEntry("lowestScoreName", "Delta Candidate")
            .containsEntry("lowestScore", 6.5)
            .containsEntry("highestScoreName", "Alpha Candidate")
            .containsEntry("highestScore", 8.5)
            .containsEntry("highestReady", true);
    }

    @Test
    void shouldUseGenericFallbackProjectionWhenNoCatalogProjectionIsConfigured() {
        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("Action executed.")
            .data(ActionPayload.object(Map.of(
                "data", Map.of(
                    "query", "alpha",
                    "documents", List.of(Map.of(
                        "id", "record-1",
                        "name", "Alpha Record",
                        "metadata", Map.of(
                            "status", "ready",
                            "rank", 3,
                            "internalPayload", "bounded scalar evidence"
                        ),
                        "nested", Map.of("ignoredNestedList", List.of("a", "b"))
                    )),
                    "totalResults", 1
                )
            )))
            .build();
        ConnectorAIActionHandler handler = new ConnectorAIActionHandler(
            metadata(),
            false,
            null,
            Set.of(),
            null
        );

        Optional<Map<String, Object>> facts = handler.buildPostActionLlmFacts(actionResult, null);

        assertThat(facts).isPresent();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = (List<Map<String, Object>>) facts.get().get("documents");
        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst())
            .containsEntry("name", "Alpha Record")
            .containsEntry("status", "ready")
            .containsEntry("rank", 3)
            .doesNotContainKey("ignoredNestedList");
    }

    private AIActionMetaData metadata() {
        return AIActionMetaData.builder()
            .name("search_records")
            .category("generic")
            .accessMode(ActionAccessMode.READ)
            .build();
    }

    private Map<String, Object> candidateRecord(String name, String score, boolean ready) {
        return Map.of(
            "name", name,
            "kind", "candidate",
            "metadata", Map.of(
                "scoreValue", score,
                "isReady", ready,
                "internalPayload", "large internal payload"
            )
        );
    }

    private ConnectorActionLlmFactsDefinition configuredFacts() {
        ConnectorActionLlmFactsRuleDefinition numericUpperBound = new ConnectorActionLlmFactsRuleDefinition(
            "QUERY_NUMERIC_UPPER_BOUND",
            "scoreValue",
            List.of(
                "\\b(?:under|below|less\\s+than|no\\s+more\\s+than|at\\s+most|up\\s+to)\\s*(?<amount>\\d+(?:\\.\\d+)?)\\b",
                "\\bscore\\b\\s*(?<operator><=|<)\\s*(?<amount>\\d+(?:\\.\\d+)?)\\b"
            ),
            List.of(),
            0,
            300,
            -300,
            -300,
            true
        );
        ConnectorActionLlmFactsRuleDefinition readyRule = new ConnectorActionLlmFactsRuleDefinition(
            "QUERY_TERMS_BOOLEAN_TRUE",
            "isReady",
            List.of(),
            List.of("ready"),
            0,
            150,
            0,
            -150,
            false
        );
        ConnectorActionLlmFactsRuleDefinition nameRule = new ConnectorActionLlmFactsRuleDefinition(
            "QUERY_CONTAINS_FIELD_VALUE",
            "name",
            List.of(),
            List.of(),
            1_000,
            0,
            0,
            0,
            false
        );
        ConnectorActionLlmFactsConstraintDefinition constraints = new ConnectorActionLlmFactsConstraintDefinition(
            "recordsConstraintMatches",
            "recordsConstraintMatchCount",
            List.of("name", "scoreValue", "isReady"),
            List.of(numericUpperBound, readyRule)
        );
        ConnectorActionLlmFactsSummaryExtraFieldDefinition readyExtra = new ConnectorActionLlmFactsSummaryExtraFieldDefinition(
            "isReady",
            "lowestReady",
            "highestReady"
        );
        ConnectorActionLlmFactsSummaryDefinition allSummary = new ConnectorActionLlmFactsSummaryDefinition(
            "recordsScoreSummary",
            "ALL",
            "scoreValue",
            "scoredRecords",
            "lowestScore",
            "highestScore",
            "name",
            "lowestScoreName",
            "highestScoreName",
            List.of(readyExtra)
        );
        ConnectorActionLlmFactsSummaryDefinition matchedSummary = new ConnectorActionLlmFactsSummaryDefinition(
            "recordsMatchedScoreSummary",
            "CONSTRAINT_MATCHES",
            "scoreValue",
            "scoredRecords",
            "lowestScore",
            "highestScore",
            "name",
            "lowestScoreName",
            "highestScoreName",
            List.of(readyExtra)
        );
        ConnectorActionLlmFactsListDefinition listDefinition = new ConnectorActionLlmFactsListDefinition(
            "documents",
            "records",
            5,
            List.of("name", "kind", "metadata.scoreValue", "metadata.isReady"),
            null,
            300,
            List.of(nameRule, numericUpperBound, readyRule),
            constraints,
            List.of(allSummary, matchedSummary)
        );
        return new ConnectorActionLlmFactsDefinition(
            "data",
            List.of("query", "totalResults", "returnedResults"),
            List.of(listDefinition),
            List.of()
        );
    }
}
