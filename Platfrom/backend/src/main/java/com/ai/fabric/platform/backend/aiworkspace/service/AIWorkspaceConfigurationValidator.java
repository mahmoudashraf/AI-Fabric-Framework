package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AIWorkspaceConfigurationValidator {

    private static final int MAX_CONFIGURATION_CHARS = 32_000;
    private static final Set<String> FORBIDDEN_FIELD_FRAGMENTS = Set.of(
        "secret", "password", "apikey", "api_key", "authorization", "credential",
        "javascript", "scripturl", "runtimeurl", "brokerurl", "adapterurl"
    );
    private static final Set<String> TOOL_ICONS = Set.of(
        "calendar", "compare", "details", "documents", "location", "phone", "search", "shield", "sparkles", "tools");
    private static final Set<String> TOOL_RAIL_TONES = Set.of("primary", "teal", "violet", "amber", "neutral");

    private final PlatformAIWorkspaceProperties properties;

    public AIWorkspaceConfigurationValidator(PlatformAIWorkspaceProperties properties) {
        this.properties = properties;
    }

    public List<String> normalizeOrigins(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one exact website origin is required.");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : origins) {
            normalized.add(normalizeOrigin(value));
        }
        if (normalized.size() > 20) {
            throw new ResponseStatusException(BAD_REQUEST, "No more than 20 website origins are supported.");
        }
        return List.copyOf(normalized);
    }

    public void validateDealershipConfiguration(JsonNode configuration) {
        validatePublicConfiguration(configuration);
        if (configuration == null || !configuration.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Dealership experience configuration must be an object.");
        }
        rejectUnknown(configuration, Set.of(
            "dealer", "page", "knowledge", "capabilities", "copy", "toolGroups", "toolRail",
            "presentation", "requestContext", "theme"), "configuration");

        JsonNode dealer = requireObject(configuration, "dealer", "dealer");
        rejectUnknown(dealer, Set.of("id", "assistantLabel", "sourceMode"), "dealer");
        requireText(dealer, "id", "dealer.id", 128);
        requireText(dealer, "assistantLabel", "dealer.assistantLabel", 128);
        optionalText(dealer, "sourceMode", "dealer.sourceMode", 128);

        JsonNode page = requireObject(configuration, "page", "page");
        rejectUnknown(page, Set.of(
            "kind", "rootSelector", "contextLabel", "subjectLabel", "maxChars", "maxPages", "maxTotalChars"), "page");
        requireText(page, "rootSelector", "page.rootSelector", 256);
        requireText(page, "contextLabel", "page.contextLabel", 160);
        optionalText(page, "subjectLabel", "page.subjectLabel", 160);
        String pageKind = page.path("kind").asText("");
        if (!List.of("inventory", "vehicle-detail", "auto").contains(pageKind)) {
            throw new ResponseStatusException(BAD_REQUEST, "page.kind must be inventory, vehicle-detail, or auto.");
        }
        int maxChars = page.path("maxChars").asInt(1800);
        int maxPages = page.path("maxPages").asInt(3);
        int maxTotalChars = page.path("maxTotalChars").asInt(10000);
        if ((page.has("maxChars") && !page.path("maxChars").isIntegralNumber())
            || (page.has("maxPages") && !page.path("maxPages").isIntegralNumber())
            || (page.has("maxTotalChars") && !page.path("maxTotalChars").isIntegralNumber())
            || maxChars < 500 || maxChars > 10_000 || maxPages < 1 || maxPages > 10
            || maxTotalChars < maxChars || maxTotalChars > 50_000) {
            throw new ResponseStatusException(BAD_REQUEST, "Page attachment limits are outside the supported range.");
        }

        validateKnowledge(configuration.path("knowledge"));
        validateCapabilities(configuration.path("capabilities"), "capabilities");
        validateCopy(configuration.path("copy"));
        validateToolGroups(configuration.path("toolGroups"));
        validateToolRail(configuration.path("toolRail"));
        validatePresentation(configuration.path("presentation"));
        validateRequestContext(configuration.path("requestContext"));
        validateTheme(configuration.path("theme"));
    }

    public void validatePublicConfiguration(JsonNode configuration) {
        if (configuration == null || !configuration.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "AI Workspace configuration must be an object.");
        }
        if (configuration.toString().length() > MAX_CONFIGURATION_CHARS) {
            throw new ResponseStatusException(BAD_REQUEST, "AI Workspace configuration is too large.");
        }
        List<String> violations = new ArrayList<>();
        inspect(configuration, "$", violations);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsafe AI Workspace configuration field: " + violations.get(0));
        }
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            if (!("https".equals(scheme) || (properties.allowLoopbackHttp() && loopback && "http".equals(scheme)))) {
                throw new IllegalArgumentException();
            }
            if (host.isBlank() || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
            int port = uri.getPort();
            return scheme + "://" + host + (port < 0 ? "" : ":" + port);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Website origins must be exact HTTPS origins without paths.");
        }
    }

    private void inspect(JsonNode node, String path, List<String> violations) {
        if (!violations.isEmpty()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase(Locale.ROOT).replace("-", "");
                if (FORBIDDEN_FIELD_FRAGMENTS.stream().anyMatch(key::contains)) {
                    violations.add(path + "." + entry.getKey());
                    return;
                }
                inspect(entry.getValue(), path + "." + entry.getKey(), violations);
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) inspect(node.get(index), path + "[" + index + "]", violations);
        } else if (node.isTextual()) {
            String text = node.asText().trim().toLowerCase(Locale.ROOT);
            if (text.startsWith("javascript:") || text.startsWith("data:text/html") || text.contains("<script")) {
                violations.add(path);
            }
        }
    }

    private void validateKnowledge(JsonNode knowledge) {
        if (knowledge.isMissingNode() || knowledge.isNull()) return;
        requireObjectNode(knowledge, "knowledge");
        rejectUnknown(knowledge, Set.of("inventoryVectorSpace"), "knowledge");
        optionalIdentifier(knowledge, "inventoryVectorSpace", "knowledge.inventoryVectorSpace");
    }

    private void validateCapabilities(JsonNode capabilities, String label) {
        if (capabilities.isMissingNode() || capabilities.isNull()) return;
        requireObjectNode(capabilities, label);
        rejectUnknown(capabilities, Set.of("comparison", "testDrive", "callback"), label);
        for (String field : List.of("comparison", "testDrive", "callback")) optionalBoolean(capabilities, field, label + "." + field);
    }

    private void validateCopy(JsonNode copy) {
        if (copy.isMissingNode() || copy.isNull()) return;
        requireObjectNode(copy, "copy");
        rejectUnknown(copy, Set.of(
            "welcomeMessage", "placeholder", "emptyMessage", "companionModeLabel", "starterSuggestions"), "copy");
        for (String field : List.of("welcomeMessage", "placeholder", "emptyMessage", "companionModeLabel")) {
            optionalText(copy, field, "copy." + field, 1_000);
        }
        if (copy.has("starterSuggestions")) {
            boundedArray(copy.path("starterSuggestions"), "copy.starterSuggestions", 12)
                .forEach(value -> requireTextValue(value, "copy.starterSuggestions", 300));
        }
    }

    private void validateToolGroups(JsonNode groups) {
        if (groups.isMissingNode() || groups.isNull()) return;
        requireObjectNode(groups, "toolGroups");
        rejectUnknown(groups, Set.of("initialScope", "default", "contextual"), "toolGroups");
        String initialScope = requireText(groups, "initialScope", "toolGroups.initialScope", 32);
        if (!Set.of("default", "contextual").contains(initialScope)) throw badRequest("toolGroups.initialScope is invalid.");
        validateToolGroup(requireObject(groups, "default", "toolGroups.default"), "toolGroups.default");
        validateToolGroup(requireObject(groups, "contextual", "toolGroups.contextual"), "toolGroups.contextual");
    }

    private void validateToolGroup(JsonNode group, String label) {
        rejectUnknown(group, Set.of(
            "label", "icon", "tools", "contextLabel", "availableWithoutAttachments"), label);
        requireText(group, "label", label + ".label", 100);
        optionalEnum(group, "icon", label + ".icon", TOOL_ICONS);
        optionalText(group, "contextLabel", label + ".contextLabel", 160);
        optionalBoolean(group, "availableWithoutAttachments", label + ".availableWithoutAttachments");
        JsonNode tools = boundedArray(group.path("tools"), label + ".tools", 12);
        if (tools.isEmpty()) throw badRequest(label + ".tools cannot be empty.");
        for (int index = 0; index < tools.size(); index++) {
            JsonNode tool = tools.get(index);
            String toolLabel = label + ".tools[" + index + "]";
            requireObjectNode(tool, toolLabel);
            rejectUnknown(tool, Set.of("label", "query", "position", "mode", "icon"), toolLabel);
            requireText(tool, "label", toolLabel + ".label", 100);
            requireText(tool, "query", toolLabel + ".query", 1_000);
            if (!"search".equals(requireText(tool, "position", toolLabel + ".position", 32))) {
                throw badRequest(toolLabel + ".position must be search.");
            }
            if (!"executor".equals(requireText(tool, "mode", toolLabel + ".mode", 32))) {
                throw badRequest(toolLabel + ".mode must be executor.");
            }
            optionalEnum(tool, "icon", toolLabel + ".icon", TOOL_ICONS);
        }
    }

    private void validateToolRail(JsonNode rail) {
        if (rail.isMissingNode() || rail.isNull()) return;
        requireObjectNode(rail, "toolRail");
        rejectUnknown(rail, Set.of("items", "initiallyCollapsed"), "toolRail");
        optionalBoolean(rail, "initiallyCollapsed", "toolRail.initiallyCollapsed");
        JsonNode items = boundedArray(rail.path("items"), "toolRail.items", 6);
        Set<String> ids = new LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String label = "toolRail.items[" + index + "]";
            requireObjectNode(item, label);
            rejectUnknown(item, Set.of(
                "id", "label", "icon", "tone", "action", "scope", "query", "position", "mode", "requiresContext"), label);
            String id = requireText(item, "id", label + ".id", 64);
            if (!id.matches("[a-z0-9][a-z0-9-]*") || !ids.add(id)) throw badRequest(label + ".id must be unique and stable.");
            requireText(item, "label", label + ".label", 100);
            optionalEnum(item, "icon", label + ".icon", TOOL_ICONS);
            optionalEnum(item, "tone", label + ".tone", TOOL_RAIL_TONES);
            String action = requireText(item, "action", label + ".action", 32);
            if (!Set.of("open-tools", "open-documents", "prompt").contains(action)) throw badRequest(label + ".action is invalid.");
            optionalEnum(item, "scope", label + ".scope", Set.of("default", "contextual"));
            optionalEnum(item, "position", label + ".position", Set.of("landing", "catalog", "search", "cart"));
            optionalEnum(item, "mode", label + ".mode", Set.of(
                "conversational", "navigator", "navigator_deep", "thinker_deep", "cart_assistant", "executor"));
            optionalBoolean(item, "requiresContext", label + ".requiresContext");
            if ("prompt".equals(action)) requireText(item, "query", label + ".query", 1_000);
            else if (item.has("query")) throw badRequest(label + ".query is supported only for prompt actions.");
        }
    }

    private void validatePresentation(JsonNode presentation) {
        if (presentation.isMissingNode() || presentation.isNull()) return;
        requireObjectNode(presentation, "presentation");
        rejectUnknown(presentation, Set.of(
            "actionNames", "capabilities", "detailBasePath", "imageHostAllowlist", "imageFallbacks", "detailSlugs"),
            "presentation");
        optionalLocalPath(presentation, "detailBasePath", "presentation.detailBasePath");
        validateCapabilities(presentation.path("capabilities"), "presentation.capabilities");
        if (presentation.has("actionNames")) {
            JsonNode names = requireObject(presentation, "actionNames", "presentation.actionNames");
            rejectUnknown(names, Set.of(
                "searchInventory", "getVehicle", "compareVehicles", "requestTestDrive", "requestCallback"),
                "presentation.actionNames");
            names.fields().forEachRemaining(entry -> {
                String value = requireTextValue(entry.getValue(), "presentation.actionNames." + entry.getKey(), 128);
                if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*")) throw badRequest("presentation.actionNames contains an invalid action name.");
            });
        }
        if (presentation.has("imageHostAllowlist")) {
            boundedArray(presentation.path("imageHostAllowlist"), "presentation.imageHostAllowlist", 20).forEach(value -> {
                String host = requireTextValue(value, "presentation.imageHostAllowlist", 253).toLowerCase(Locale.ROOT);
                if (!host.matches("[a-z0-9.-]+") || host.contains("..")) {
                    throw badRequest("presentation.imageHostAllowlist contains an invalid host.");
                }
            });
        }
        validateStringMap(presentation.path("imageFallbacks"), "presentation.imageFallbacks", true);
        validateStringMap(presentation.path("detailSlugs"), "presentation.detailSlugs", false);
    }

    private void validateRequestContext(JsonNode context) {
        if (context.isMissingNode() || context.isNull()) return;
        requireObjectNode(context, "requestContext");
        if (context.size() > 40) throw badRequest("requestContext contains too many fields.");
        context.fields().forEachRemaining(entry -> {
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) throw badRequest("requestContext contains an invalid field name.");
            validateContextValue(entry.getValue(), "requestContext." + entry.getKey(), 0);
        });
    }

    private void validateTheme(JsonNode theme) {
        if (theme.isMissingNode() || theme.isNull()) return;
        requireObjectNode(theme, "theme");
        rejectUnknown(theme, Set.of("primaryColor", "borderRadius", "fontFamily", "darkMode"), "theme");
        if (theme.has("primaryColor") && !requireTextValue(theme.path("primaryColor"), "theme.primaryColor", 32).matches("#[0-9A-Fa-f]{6}")) {
            throw badRequest("theme.primaryColor must be a six-digit hex colour.");
        }
        if (theme.has("borderRadius") && !requireTextValue(theme.path("borderRadius"), "theme.borderRadius", 24)
            .matches("(?:0|[0-9]+(?:\\.[0-9]+)?(?:px|rem|em))")) {
            throw badRequest("theme.borderRadius is invalid.");
        }
        if (theme.has("fontFamily") && !requireTextValue(theme.path("fontFamily"), "theme.fontFamily", 200)
            .matches("[A-Za-z0-9 ,_'\"-]+")) {
            throw badRequest("theme.fontFamily is invalid.");
        }
        if (theme.has("darkMode")) {
            JsonNode darkMode = theme.path("darkMode");
            if (!darkMode.isBoolean() && !(darkMode.isTextual() && "auto".equals(darkMode.asText()))) {
                throw badRequest("theme.darkMode must be true, false, or auto.");
            }
        }
    }

    private void validateStringMap(JsonNode node, String label, boolean localPathValues) {
        if (node.isMissingNode() || node.isNull()) return;
        requireObjectNode(node, label);
        if (node.size() > 100) throw badRequest(label + " contains too many entries.");
        node.fields().forEachRemaining(entry -> {
            if (entry.getKey().isBlank() || entry.getKey().length() > 128) throw badRequest(label + " contains an invalid key.");
            String value = requireTextValue(entry.getValue(), label + "." + entry.getKey(), 512);
            if (localPathValues && !isSafeLocalPath(value)) throw badRequest(label + " values must be safe same-origin paths.");
            if (!localPathValues && !value.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) throw badRequest(label + " contains an invalid slug.");
        });
    }

    private void validateContextValue(JsonNode node, String label, int depth) {
        if (depth > 3) throw badRequest(label + " is too deeply nested.");
        if (node.isTextual()) requireTextValue(node, label, 1_000);
        else if (node.isNumber() || node.isBoolean() || node.isNull()) return;
        else if (node.isArray()) {
            if (node.size() > 30) throw badRequest(label + " contains too many values.");
            node.forEach(value -> validateContextValue(value, label, depth + 1));
        } else if (node.isObject()) {
            if (node.size() > 30) throw badRequest(label + " contains too many fields.");
            node.fields().forEachRemaining(entry -> validateContextValue(entry.getValue(), label + "." + entry.getKey(), depth + 1));
        } else throw badRequest(label + " contains an unsupported value.");
    }

    private JsonNode requireObject(JsonNode parent, String field, String label) {
        JsonNode node = parent == null ? null : parent.path(field);
        requireObjectNode(node, label);
        return node;
    }

    private void requireObjectNode(JsonNode node, String label) {
        if (node == null || !node.isObject()) throw badRequest(label + " must be an object.");
    }

    private JsonNode boundedArray(JsonNode node, String label, int maxItems) {
        if (node == null || !node.isArray() || node.size() > maxItems) throw badRequest(label + " must be a bounded array.");
        return node;
    }

    private void rejectUnknown(JsonNode object, Set<String> allowed, String label) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) throw badRequest("Unknown " + label + " field: " + name);
        }
    }

    private String requireText(JsonNode parent, String field, String label, int maxLength) {
        if (parent == null || !parent.isObject() || !parent.has(field)) {
            throw new ResponseStatusException(BAD_REQUEST, label + " is required.");
        }
        return requireTextValue(parent.path(field), label, maxLength);
    }

    private String requireTextValue(JsonNode node, String label, int maxLength) {
        if (node == null || !node.isTextual() || node.asText().trim().isEmpty() || node.asText().trim().length() > maxLength) {
            throw badRequest(label + " must be non-empty text no longer than " + maxLength + " characters.");
        }
        return node.asText().trim();
    }

    private void optionalText(JsonNode parent, String field, String label, int maxLength) {
        if (parent.has(field)) requireTextValue(parent.path(field), label, maxLength);
    }

    private void optionalIdentifier(JsonNode parent, String field, String label) {
        if (!parent.has(field)) return;
        String value = requireTextValue(parent.path(field), label, 128);
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*")) throw badRequest(label + " is invalid.");
    }

    private void validateIdentifierValue(JsonNode value, String label) {
        String text = requireTextValue(value, label, 128);
        if (!text.matches("[A-Za-z0-9][A-Za-z0-9_.:-]*")) throw badRequest(label + " contains an invalid identifier.");
    }

    private void optionalBoolean(JsonNode parent, String field, String label) {
        if (parent.has(field) && !parent.path(field).isBoolean()) throw badRequest(label + " must be a boolean.");
    }

    private void optionalEnum(JsonNode parent, String field, String label, Set<String> values) {
        if (!parent.has(field)) return;
        String value = requireTextValue(parent.path(field), label, 64);
        if (!values.contains(value)) throw badRequest(label + " is invalid.");
    }

    private void optionalLocalPath(JsonNode parent, String field, String label) {
        if (!parent.has(field)) return;
        String value = requireTextValue(parent.path(field), label, 512);
        if (!isSafeLocalPath(value)) throw badRequest(label + " must be a safe same-origin path.");
    }

    private boolean isSafeLocalPath(String value) {
        return value.startsWith("/") && !value.startsWith("//") && !value.contains("..")
            && !value.contains("\\") && !value.contains("?") && !value.contains("#");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(BAD_REQUEST, message);
    }
}
