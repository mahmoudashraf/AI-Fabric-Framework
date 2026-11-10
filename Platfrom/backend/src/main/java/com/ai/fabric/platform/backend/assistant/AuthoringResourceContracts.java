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
            "/manifest/contributions/datasets", Map.of("type", "array", "identity", "datasetId"),
            "/manifest/contributions/knowledgeSources", Map.of("type", "array", "identity", "sourceKey"))));
        if (List.of("ACTIONS", "CONNECTIONS").contains(task)) {
            catalogue.put("actionArrayRules", "For an existing action field change always UPSERT /actionsConfig/actions by the exact action name. Do not SET paths traversing /actionsConfig/actions/0 or /actionsConfig/actions/<name>; actions is an array, not a map. To change a parameter, UPSERT the containing action with a params array containing only that named parameter; the server identity-merges it and preserves siblings. The value must include its action name and changed fields, not a full catalogue copy.");
            catalogue.set("actionFieldChangeExample", mapper.valueToTree(Map.of(
                "path", "/actionsConfig/actions", "operation", "UPSERT", "key", "<existing action name>",
                "value", Map.of("name", "<existing action name>", "displayName", "Requested label"))));
            catalogue.put("routingRules", "routingConfig.actions is an OBJECT MAP, not the actionsConfig.actions ARRAY. Use SET at each exact route entry, not UPSERT on the map. response.success-http-status is an integer ARRAY, e.g. [200] or [200,201], never a scalar. Use only documented accepted success codes. request.query/path/body/headers and response.result contain existing {{params.name}} / {{body.field}} mappings. Preserve upstream/auth bindings. Omit an unchanged route instead of emitting an unnecessary route change.");
            catalogue.set("routeChangeExample", mapper.valueToTree(Map.of(
                "path", "/routingConfig/actions/lookup_record", "operation", "SET", "value", Map.of(
                    "method", "GET", "path", "/v1/records", "request", Map.of("query", Map.of("id", "{{params.recordId}}")),
                    "response", Map.of("success-http-status", List.of(200), "result", Map.of("record", "{{body.record}}"), "message", "Record retrieved.")))));
            catalogue.put("parameterRules", "Map supported numeric constraints to min/max; nested ARRAY uses items; OBJECT uses properties and requiredProperties. Preserve unusual property keys literally. allowedValues is an actual allowed string-value list, not a schema union: never translate nullable into allowedValues:[\"null\"]. Never invent properties[\"*\"] for additionalProperties. If nullability, arbitrary-key constraints or another API schema restriction cannot be faithfully represented by this parameter contract, state that limitation explicitly and ask for the required decision; do not claim it is enforced or fabricate a replacement constraint. Existing unknown supported fields are preserved.");
        }
        if ("DATA_PLUGIN".equals(task)) {
            catalogue.put("privateDataArrayRules", "manifest.contributions.datasets and knowledgeSources are NAMED ARRAYS, never object maps. To edit a dataset's documentPolicy or syncConnector, UPSERT that dataset by its existing datasetId and provide only changed fields within value; SET cannot traverse array indices such as /datasets/0/documentPolicy. The server merges unmentioned fields and sibling datasets. Use sourceKey for knowledgeSources. Do not replace either whole named array.");
            catalogue.set("datasetPolicyChangeExample", mapper.valueToTree(Map.of(
                "path", "/manifest/contributions/datasets", "operation", "UPSERT", "key", "<existing datasetId>",
                "value", Map.of("datasetId", "<existing datasetId>", "documentPolicy", Map.of("maxSourceBytes", 2097152)))));
        }
        if ("ENGAGE".equals(task)) {
            catalogue.put("engageFieldRules", "Use the selected pack's existing schema, not invented branding keys. Generic/support assistant label is configuration.app.assistantLabel; dealership uses configuration.dealer.assistantLabel. Welcome text and starters are configuration.copy.welcomeMessage and copy.starterSuggestions (string array). theme uses primaryColor (#RRGGBB), logoUrl (HTTPS), stylePreset, fontFamily, borderRadius (CSS length string), darkMode (boolean or auto). Style presets: current, clarity, editorial, obsidian, botanical, atelier, kinetic, frost, workshop, terminal, pop, gallery, human. Preserve selected-pack identity, page context and installed references. Public app configuration never contains credentials or new runtime/adapter endpoints.");
            catalogue.put("engageToolRules", "toolGroups is an OBJECT with initialScope default|contextual and both default/contextual OBJECT groups, not an array. Each group needs label and 1..12 tools. Each tool needs label/query/position:search/mode:executor and optional icon. Tool icons: calendar, compare, details, documents, location, phone, search, shield, sparkles, tools. toolRail is an object {items,initiallyCollapsed}; at most6 items with stable id/label/action and optional icon/tone/scope/query/position/mode/requiresContext. action is open-tools|open-documents|prompt. Only prompt has query. Tone is primary|teal|violet|amber|neutral. These are presentation preferences, never capability installation or authority.");
            var tool = Map.of("label", "Explore options", "query", "Help me explore the available options.",
                "position", "search", "mode", "executor", "icon", "search");
            catalogue.set("engageToolGroupsExample", mapper.valueToTree(Map.of("initialScope", "default",
                "default", Map.of("label", "Explore", "icon", "search", "tools", List.of(tool)),
                "contextual", Map.of("label", "Current context", "icon", "details", "tools", List.of(tool)))));
            catalogue.set("engageToolRailExample", mapper.valueToTree(Map.of("initiallyCollapsed", false,
                "items", List.of(Map.of("id", "explore", "label", "Explore", "icon", "search",
                    "action", "open-tools", "scope", "default", "tone", "primary")))));
        }
        if ("SPECIALISTS".equals(task)) {
            catalogue.put("specialistConsistency", "To enable specialistAssistance propose BOTH enabled:true and a SET of behaviorConfig.executionExtensions that retains its existing entries and adds SPECIALIST_ASSISTANCE exactly once. To disable remove that extension explicitly as well. Never generate compiled/contentHash. Use only authorizedSpecialistCapabilities; an ordinary draft action is not proof of an installed specialist capability. Missing installed authority remains a question, not an invented worker/binding.");
        }
    }
}
