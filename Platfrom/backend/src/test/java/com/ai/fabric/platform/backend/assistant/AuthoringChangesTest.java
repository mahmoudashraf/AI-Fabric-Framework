package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthoringChangesTest {
    final ObjectMapper mapper = new ObjectMapper();
    final AuthoringChanges changes = new AuthoringChanges(mapper);
    @Test void requestedAiValuesReplaceManualValuesWithoutLosingOtherSettings() throws Exception {
        var current = mapper.readTree("{\"promptConfig\":{\"systemPrompt\":\"Manual wording\",\"customSetting\":123},\"actionsConfig\":{\"actions\":[]}}");
        var patch = mapper.readTree("[{\"path\":\"/promptConfig/systemPrompt\",\"operation\":\"SET\",\"value\":\"Requested AI wording\"}]");
        var result = changes.apply(current, patch, List.of("/promptConfig"));
        assertThat(result.candidate().at("/promptConfig/systemPrompt").asText()).isEqualTo("Requested AI wording");
        assertThat(result.candidate().at("/promptConfig/customSetting").asInt()).isEqualTo(123);
        assertThat(result.candidate().path("actionsConfig")).isEqualTo(current.path("actionsConfig"));
        assertThat(current.at("/promptConfig/systemPrompt").asText()).isEqualTo("Manual wording");
    }
    @Test void upsertsSeveralActionsPreservingUnknownFieldsAndOtherActions() throws Exception {
        var current = mapper.readTree("{\"actionsConfig\":{\"actions\":[{\"name\":\"keep\",\"extension\":{\"x\":1}},{\"name\":\"edit\",\"description\":\"Manual\",\"futureField\":true}]}}");
        var patch = mapper.readTree("[{\"path\":\"/actionsConfig/actions\",\"operation\":\"UPSERT\",\"key\":\"edit\",\"value\":{\"name\":\"edit\",\"description\":\"AI\"}},{\"path\":\"/actionsConfig/actions\",\"operation\":\"UPSERT\",\"key\":\"new\",\"value\":{\"name\":\"new\",\"accessMode\":\"READ\"}}]");
        var result = changes.apply(current, patch, List.of("/actionsConfig"));
        assertThat(result.candidate().at("/actionsConfig/actions")).hasSize(3);
        assertThat(result.candidate().at("/actionsConfig/actions/0")).isEqualTo(current.at("/actionsConfig/actions/0"));
        assertThat(result.candidate().at("/actionsConfig/actions/1/futureField").asBoolean()).isTrue();
        assertThat(result.diff().get(1).path("existed").asBoolean()).isFalse();
        assertThat(changes.apply(result.candidate(), changes.inverse(result.diff()), List.of("/actionsConfig")).candidate()).isEqualTo(current);
    }
    @Test void removesAnExplicitNamedActionAndUndoRestoresIt() throws Exception {
        var current = mapper.readTree("{\"actionsConfig\":{\"actions\":[{\"name\":\"a\"},{\"name\":\"b\"}]}}");
        var patch = mapper.readTree("[{\"path\":\"/actionsConfig/actions\",\"operation\":\"REMOVE_ITEM\",\"key\":\"b\"}]");
        var result = changes.apply(current, patch, List.of("/actionsConfig"));
        assertThat(result.candidate().at("/actionsConfig/actions")).hasSize(1);
        assertThat(changes.apply(result.candidate(), changes.inverse(result.diff()), List.of("/actionsConfig")).candidate()).isEqualTo(current);
    }
    @Test void scopeAndCredentialFieldsRemainHardBoundaries() throws Exception {
        var current = mapper.readTree("{\"providerConfig\":{\"apiKey\":\"secret-value\"},\"promptConfig\":{}}");
        for (String patch : List.of(
            "[{\"path\":\"/providerConfig/apiKey\",\"operation\":\"SET\",\"value\":\"new-secret\"}]",
            "[{\"path\":\"/providerConfig\",\"operation\":\"REMOVE\"}]")) {
            assertThatThrownBy(() -> changes.apply(current, mapper.readTree(patch), List.of("/providerConfig")))
                .satisfies(failure -> assertThat(failure.getMessage()).containsIgnoringCase("credential"));
        }
        assertThatThrownBy(() -> changes.apply(current, mapper.readTree("[{\"path\":\"/providerConfig\",\"operation\":\"SET\",\"value\":{}}]"), List.of("/promptConfig")))
            .hasMessageContaining("outside");
        assertThat(changes.project(current).toString()).doesNotContain("secret-value").contains("[REDACTED]");
    }
    @Test void redactedFieldsKeepTheirStoredValuesButCannotBeInvented() throws Exception {
        var current = mapper.readTree("{\"providerConfig\":{\"apiKey\":\"secret\",\"model\":\"old\"}}");
        var result = changes.apply(current, mapper.readTree("[{\"path\":\"/providerConfig\",\"operation\":\"SET\",\"value\":{\"apiKey\":\"[REDACTED]\",\"model\":\"new\"}}]"), List.of("/providerConfig"));
        assertThat(result.candidate().at("/providerConfig/apiKey").asText()).isEqualTo("secret");
        assertThat(result.diff().toString()).doesNotContain("secret");
        assertThatThrownBy(() -> changes.apply(mapper.readTree("{\"providerConfig\":{}}"), mapper.readTree("[{\"path\":\"/providerConfig\",\"operation\":\"SET\",\"value\":{\"apiKey\":\"[REDACTED]\"}}]"), List.of("/providerConfig")))
            .hasMessageContaining("credential");
    }
    @Test void bulkArrayReplacementAndUnstructuredOperationsAreNotSilentCatalogueDeletion() throws Exception {
        var current = mapper.readTree("{\"actionsConfig\":{\"actions\":[{\"name\":\"kept\"}]}}");
        assertThatThrownBy(() -> changes.apply(current, mapper.readTree("[{\"path\":\"/actionsConfig/actions\",\"operation\":\"SET\",\"value\":[]}]"), List.of("/actionsConfig")))
            .hasMessageContaining("UPSERT");
        assertThatThrownBy(() -> changes.apply(current, mapper.readTree("[{\"path\":\"/actionsConfig/actions\",\"operation\":\"UPSERT\",\"key\":\"a\",\"value\":{\"name\":\"b\"}}]"), List.of("/actionsConfig")))
            .hasMessageContaining("identity");
    }
    @Test void preservesLargeNumericIdsAndFormalEscapedPaths() throws Exception {
        var current = mapper.readTree("{\"routingConfig\":{\"actions\":{}}}");
        var result = changes.apply(current, mapper.readTree("[{\"path\":\"/routingConfig/actions/a~1b\",\"operation\":\"SET\",\"value\":{\"id\":123456789012345678901234567890}}]"), List.of("/routingConfig"));
        assertThat(result.candidate().at("/routingConfig/actions/a~1b/id").asText()).isEqualTo("123456789012345678901234567890");
    }
    @Test void undoRemovesFieldsIntroducedInsideObjectsAndExistingNamedItems() throws Exception {
        var current = mapper.readTree("{\"promptConfig\":{\"group\":{\"old\":true}},\"actionsConfig\":{\"actions\":[{\"name\":\"a\",\"description\":\"Before\"}]}}");
        var result = changes.apply(current, mapper.readTree("""
            [{"path":"/promptConfig/group","operation":"SET","value":{"new":true}},
             {"path":"/actionsConfig/actions","operation":"UPSERT","key":"a","value":{"name":"a","description":"After","newField":true}}]
            """), List.of("/promptConfig", "/actionsConfig"));
        assertThat(changes.undo(result.candidate(), result.diff(), List.of("/promptConfig", "/actionsConfig")).candidate()).isEqualTo(current);
    }
    @Test void routeAuthValuesAndCustomHeaderValuesNeverReachTheProviderOrDiff() throws Exception {
        var current = mapper.readTree("""
            {"routingConfig":{"connector":{"upstream":{"auth":{"type":"API_KEY","header":"X-Custom","value":"stored-auth"}}},
             "actions":{"read":{"headers":{"X-Customer-Secret":"stored-header","Accept":"application/json"}}},
             "notes":"Do not share stored-auth or stored-header in another field."}}
            """);
        var projection = changes.project(current).toString();
        assertThat(projection).doesNotContain("stored-auth", "stored-header").contains("application/json", "API_KEY");
        assertThatThrownBy(() -> changes.apply(current, mapper.readTree("""
            [{"path":"/routingConfig/connector/upstream/auth/value","operation":"SET","value":"replacement"}]
            """), List.of("/routingConfig"))).hasMessageContaining("credentials");
        var result = changes.apply(current, mapper.readTree("""
            [{"path":"/routingConfig/connector/upstream/auth","operation":"SET","value":{"type":"BEARER","value":"[REDACTED]"}}]
            """), List.of("/routingConfig"));
        assertThat(result.diff().toString()).doesNotContain("stored-auth", "stored-header");
        assertThat(result.candidate().at("/routingConfig/connector/upstream/auth/value").asText()).isEqualTo("stored-auth");
    }
    @Test void usesActualDatasetAndKnowledgeSourceIdentities() throws Exception {
        var current = mapper.readTree("{\"knowledgeSourceConfig\":{\"sources\":[]},\"marketplaceDatasetConfig\":{\"datasets\":[]}}");
        var result = changes.apply(current, mapper.readTree("""
            [{"path":"/knowledgeSourceConfig/sources","operation":"UPSERT","key":"source-1","value":{"id":"source-1"}},
             {"path":"/marketplaceDatasetConfig/datasets","operation":"UPSERT","key":"dataset-1","value":{"datasetId":"dataset-1"}}]
            """), List.of("/knowledgeSourceConfig", "/marketplaceDatasetConfig"));
        assertThat(result.candidate().at("/marketplaceDatasetConfig/datasets/0/datasetId").asText()).isEqualTo("dataset-1");
    }
    @Test void credentialDeclarationsRemainEditableButTheirExampleValuesArePrivate() throws Exception {
        var current = mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"login","params":[
             {"name":"password","type":"STRING","defaultValue":"real-password"},
             {"name":"body","type":"OBJECT","properties":{"secret":{"type":"STRING","default":"real-secret"}}}]}]}}
            """);
        var projected = changes.project(current);
        assertThat(projected.at("/actionsConfig/actions/0/params/1/properties/secret/type").asText()).isEqualTo("STRING");
        assertThat(projected.toString()).doesNotContain("real-password", "real-secret");
        var saved = changes.apply(current, mapper.readTree("""
            [{"path":"/actionsConfig/actions","operation":"UPSERT","key":"login","value":{"name":"login","params":[
             {"name":"password","description":"Customer's password"},
             {"name":"body","properties":{"secret":{"type":"STRING","description":"Required API credential"}}} ]}}]
            """), List.of("/actionsConfig"));
        assertThat(saved.candidate().at("/actionsConfig/actions/0/params/0/defaultValue").asText()).isEqualTo("real-password");
        assertThat(saved.diff().toString()).doesNotContain("real-password", "real-secret");
    }
}
