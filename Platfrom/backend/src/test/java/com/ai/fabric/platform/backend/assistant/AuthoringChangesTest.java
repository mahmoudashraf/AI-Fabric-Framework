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
    @Test void exactArrayIndicesCanSetAndRemoveNestedObjectFieldsAndUndoWithoutRebuildingItems() throws Exception {
        var current = mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"first","displayName":"Before","future":{"id":9007199254740993}},
              {"name":"untouched","description":"Keep"}]},
             "manifest":{"contributions":{"datasets":[{"datasetId":"documents","syncConnector":{"httpSource":{
               "mapping":{"metadataFields":{"tenantId":"unsafe-mapping","recordId":"id"}}}}},
               {"datasetId":"sibling","future":true}]}}}
            """);
        var result = changes.apply(current, mapper.readTree("""
            [{"path":"/actionsConfig/actions/0/displayName","operation":"SET","value":"After"},
             {"path":"/actionsConfig/actions/1/future","operation":"SET","value":{"added":true}},
             {"path":"/manifest/contributions/datasets/0/syncConnector/httpSource/mapping/metadataFields/tenantId","operation":"REMOVE"}]
            """), List.of("/actionsConfig", "/manifest"));
        assertThat(result.candidate().at("/actionsConfig/actions/0/displayName").asText()).isEqualTo("After");
        assertThat(result.candidate().at("/actionsConfig/actions/0/future")).isEqualTo(current.at("/actionsConfig/actions/0/future"));
        assertThat(result.candidate().at("/manifest/contributions/datasets/0/syncConnector/httpSource/mapping/metadataFields/tenantId").isMissingNode()).isTrue();
        assertThat(result.candidate().at("/manifest/contributions/datasets/0/syncConnector/httpSource/mapping/metadataFields/recordId").asText()).isEqualTo("id");
        assertThat(result.candidate().at("/manifest/contributions/datasets/1")).isEqualTo(current.at("/manifest/contributions/datasets/1"));
        assertThat(changes.undo(result.candidate(), result.diff(), List.of("/actionsConfig", "/manifest")).candidate()).isEqualTo(current);
    }
    @Test void indexedActionParentsStillAllowIdentityMergedParameterUpsertsAndExactUndo() throws Exception {
        var current = mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"lookup","params":[
              {"name":"q","type":"STRING","description":"Before","future":{"keep":true}},
              {"name":"limit","type":"INTEGER","defaultValue":10}],"future":true},
              {"name":"sibling","params":[]}]}}
            """);
        var result = changes.apply(current, mapper.readTree("""
            [{"path":"/actionsConfig/actions/0/params","operation":"UPSERT","key":"q",
              "value":{"name":"q","description":"After"}}]
            """), List.of("/actionsConfig"));
        assertThat(result.candidate().at("/actionsConfig/actions/0/params/0/description").asText()).isEqualTo("After");
        assertThat(result.candidate().at("/actionsConfig/actions/0/params/0/future")).isEqualTo(current.at("/actionsConfig/actions/0/params/0/future"));
        assertThat(result.candidate().at("/actionsConfig/actions/0/params/1")).isEqualTo(current.at("/actionsConfig/actions/0/params/1"));
        assertThat(result.candidate().at("/actionsConfig/actions/1")).isEqualTo(current.at("/actionsConfig/actions/1"));
        assertThat(result.diff().get(0).path("key").asText()).isEqualTo("q");
        assertThat(changes.undo(result.candidate(), result.diff(), List.of("/actionsConfig")).candidate()).isEqualTo(current);
    }
    @Test void arrayTraversalRejectsNonCanonicalMissingAndOutOfRangeIndicesWithoutInferringNames() throws Exception {
        var current = mapper.readTree("{\"actionsConfig\":{\"actions\":[{\"name\":\"lookup\"}]}}");
        for (String index : List.of("-", "-1", "+0", "00", "01", " 0", "0 ", "1.0", "1e0", "lookup", "1", "2147483648", "999999999999999999999999")) {
            var patch = mapper.createArrayNode();
            patch.addObject().put("path", "/actionsConfig/actions/" + index + "/displayName").put("operation", "SET").put("value", "Changed");
            assertThatThrownBy(() -> changes.apply(current, patch, List.of("/actionsConfig")))
                .as(index).hasMessageContaining("Array");
        }
        var scalar = mapper.readTree("{\"actionsConfig\":{\"actions\":[null,4]}}");
        for (int index : List.of(0, 1)) assertThatThrownBy(() -> changes.apply(scalar, mapper.readTree(
            "[{\"path\":\"/actionsConfig/actions/" + index + "/displayName\",\"operation\":\"SET\",\"value\":\"Changed\"}]"), List.of("/actionsConfig")))
            .hasMessageContaining("object");
        assertThat(current.at("/actionsConfig/actions/0/name").asText()).isEqualTo("lookup");
    }
    @Test void finalArrayParentsAndWholeNamedArrayReplacementRemainProtected() throws Exception {
        var current = mapper.readTree("{\"actionsConfig\":{\"actions\":[{\"name\":\"lookup\",\"params\":[]}]}}");
        for (String path : List.of("/actionsConfig/actions/0", "/actionsConfig/actions/-")) {
            for (String operation : List.of("SET", "REMOVE")) {
                var patch = mapper.createArrayNode();
                patch.addObject().put("path", path).put("operation", operation).putObject("value").put("name", "replacement");
                assertThatThrownBy(() -> changes.apply(current, patch, List.of("/actionsConfig"))).hasMessageContaining("UPSERT or REMOVE_ITEM");
            }
        }
        assertThatThrownBy(() -> changes.apply(current, mapper.readTree("""
            [{"path":"/actionsConfig/actions/0/params","operation":"SET","value":[]}]
            """), List.of("/actionsConfig"))).hasMessageContaining("UPSERT");
    }
    @Test void indexedEditsCannotBypassCredentialComparisonOrAllowedScope() throws Exception {
        var current = mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"login","params":[
              {"name":"password","defaultValue":"stored-password"},
              {"name":"opaque","sensitive":true,"defaultValue":"stored-sensitive"}]}]}}
            """);
        for (String patch : List.of(
            "[{\"path\":\"/actionsConfig/actions/0/params/0/defaultValue\",\"operation\":\"SET\",\"value\":\"changed\"}]",
            "[{\"path\":\"/actionsConfig/actions/0/params/0/defaultValue\",\"operation\":\"REMOVE\"}]",
            "[{\"path\":\"/actionsConfig/actions/0/params/1/sensitive\",\"operation\":\"SET\",\"value\":false}]",
            "[{\"path\":\"/actionsConfig/actions/0/params/0\",\"operation\":\"SET\",\"value\":{\"name\":\"password\"}}]")) {
            assertThatThrownBy(() -> changes.apply(current, mapper.readTree(patch), List.of("/actionsConfig")));
        }
        assertThatThrownBy(() -> changes.apply(current, mapper.readTree("""
            [{"path":"/actionsConfig/actions/0/displayName","operation":"SET","value":"Changed"}]
            """), List.of("/promptConfig"))).hasMessageContaining("outside");
        var result = changes.apply(current, mapper.readTree("""
            [{"path":"/actionsConfig/actions/0/displayName","operation":"SET","value":"Login"}]
            """), List.of("/actionsConfig"));
        assertThat(result.candidate().at("/actionsConfig/actions/0/params")).isEqualTo(current.at("/actionsConfig/actions/0/params"));
        assertThat(result.diff().toString()).doesNotContain("stored-password", "stored-sensitive");
    }
    @Test void unusedObjectOperationKeysDoNotEnterServerDiffsOrChangeUndoSemantics() throws Exception {
        var current = mapper.readTree("{\"promptConfig\":{\"group\":{\"before\":true},\"removeMe\":{\"keep\":7}}}");
        for (var unused : List.of(mapper.nullNode(), mapper.getNodeFactory().textNode("unused"))) {
            var patch = mapper.createArrayNode();
            patch.addObject().put("path", "/promptConfig/group").put("operation", "SET").set("value", mapper.readTree("{\"added\":true}"));
            patch.addObject().put("path", "/promptConfig/removeMe").put("operation", "REMOVE");
            patch.forEach(change -> ((com.fasterxml.jackson.databind.node.ObjectNode) change).set("key", unused));
            var originalPatch = patch.deepCopy();
            var result = changes.apply(current, patch, List.of("/promptConfig"));
            result.diff().forEach(diff -> assertThat(diff.has("key")).isFalse());
            assertThat(changes.undo(result.candidate(), result.diff(), List.of("/promptConfig")).candidate()).isEqualTo(current);
            assertThat(patch).isEqualTo(originalPatch);
            var legacyDiff = result.diff().deepCopy();
            legacyDiff.forEach(diff -> ((com.fasterxml.jackson.databind.node.ObjectNode) diff).set("key", unused));
            assertThat(changes.undo(result.candidate(), legacyDiff, List.of("/promptConfig")).candidate()).isEqualTo(current);
        }
    }
}
