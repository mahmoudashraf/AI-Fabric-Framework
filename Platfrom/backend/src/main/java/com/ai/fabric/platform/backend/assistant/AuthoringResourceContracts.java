package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/** Model guidance for existing editor/connector contracts, never a second validator or repair path. */
final class AuthoringResourceContracts {
    private AuthoringResourceContracts() { }

    static void populate(ObjectMapper mapper, ObjectNode catalogue, String task) {
        catalogue.put("contractVersion", "loomai-authoring-catalogue-v1");
        catalogue.put("maxChanges", 30);
        catalogue.set("collectionShapes", mapper.valueToTree(Map.of(
            "/actionsConfig/actions", Map.of("type", "array", "identity", "name"),
            "action.params", Map.of("type", "array", "identity", "name"),
            "/routingConfig/actions", Map.of("type", "object", "entryOperation", "SET /routingConfig/actions/{escapedActionName}; never UPSERT this map"),
            "/knowledgeSourceConfig/sources", Map.of("type", "array", "identity", "id"),
            "/marketplaceDatasetConfig/datasets", Map.of("type", "array", "identity", "datasetId"),
            "/manifest/contributions/knowledgeSources", Map.of("type", "array", "identity", "sourceKey"))));
        if (List.of("ACTIONS", "CONNECTIONS").contains(task)) {
            catalogue.put("routingRules", "routingConfig.actions is an OBJECT MAP, not the actionsConfig.actions ARRAY. Use SET at each exact route entry, not UPSERT on the map. response.success-http-status is an integer ARRAY, e.g. [200] or [200,201], never a scalar. Use only documented accepted success codes. request.query/path/body/headers and response.result contain existing {{params.name}} / {{body.field}} mappings. Preserve upstream/auth bindings. Omit an unchanged route instead of emitting an unnecessary route change.");
            catalogue.set("routeChangeExample", mapper.valueToTree(Map.of(
                "path", "/routingConfig/actions/lookup_record", "operation", "SET", "value", Map.of(
                    "method", "GET", "path", "/v1/records", "request", Map.of("query", Map.of("id", "{{params.recordId}}")),
                    "response", Map.of("success-http-status", List.of(200), "result", Map.of("record", "{{body.record}}"), "message", "Record retrieved.")))));
            catalogue.put("parameterRules", "Map supported numeric constraints to min/max; nested ARRAY uses items; OBJECT uses properties and requiredProperties. Preserve unusual property keys literally. allowedValues is an actual allowed string-value list, not a schema union: never translate nullable into allowedValues:[\"null\"]. Never invent properties[\"*\"] for additionalProperties. If nullability, arbitrary-key constraints or another API schema restriction cannot be faithfully represented by this parameter contract, state that limitation explicitly and ask for the required decision; do not claim it is enforced or fabricate a replacement constraint. Existing unknown supported fields are preserved.");
        }
        if ("SPECIALISTS".equals(task)) {
            catalogue.put("specialistConsistency", "To enable specialistAssistance propose BOTH enabled:true and a SET of behaviorConfig.executionExtensions that retains its existing entries and adds SPECIALIST_ASSISTANCE exactly once. To disable remove that extension explicitly as well. Never generate compiled/contentHash. Use only authorizedSpecialistCapabilities; an ordinary draft action is not proof of an installed specialist capability. Missing installed authority remains a question, not an invented worker/binding.");
        }
    }
}
