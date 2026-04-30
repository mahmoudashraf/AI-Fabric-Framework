package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link AIActionHandler} implementation for connector-backed actions loaded from a file-based catalog.
 */
@Slf4j
public final class ConnectorAIActionHandler implements AIActionHandler {

    private static final String REDACTED = "[REDACTED]";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final AIActionMetaData metadata;
    private final boolean requiresConfirmation;
    private final String confirmationTemplate;
    private final Set<String> sensitiveParams;
    private final ActionConnectorExecutor executor;

    public ConnectorAIActionHandler(AIActionMetaData metadata,
                                    boolean requiresConfirmation,
                                    String confirmationTemplate,
                                    Set<String> sensitiveParams,
                                    ActionConnectorExecutor executor) {
        this.metadata = metadata;
        this.requiresConfirmation = requiresConfirmation;
        this.confirmationTemplate = StringUtils.hasText(confirmationTemplate) ? confirmationTemplate.trim() : null;
        this.sensitiveParams = sensitiveParams != null ? Set.copyOf(sensitiveParams) : Set.of();
        this.executor = executor;
    }

    @Override
    public AIActionMetaData getActionMetadata() {
        return metadata;
    }

    @Override
    public boolean requiresConfirmation() {
        return requiresConfirmation;
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params, ActionContext context) {
        if (!StringUtils.hasText(confirmationTemplate)) {
            return defaultConfirmationMessage(params);
        }
        try {
            return renderTemplate(confirmationTemplate, params != null ? params : Map.of());
        } catch (Exception ex) {
            log.debug("Failed to render confirmation template for action {}: {}", metadata != null ? metadata.getName() : "unknown", ex.getMessage());
            return defaultConfirmationMessage(params);
        }
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, ActionContext context) {
        if (executor == null) {
            return ActionResult.builder()
                .success(false)
                .errorCode("SERVICE_UNAVAILABLE")
                .message("Connector executor is not configured.")
                .build();
        }
        String actionId = metadata != null ? metadata.getName() : null;
        if (!StringUtils.hasText(actionId)) {
            return ActionResult.builder()
                .success(false)
                .errorCode("ACTION_EXECUTION_FAILED")
                .message("Action metadata is missing action name.")
                .build();
        }
        return executor.execute(actionId, metadata.getAccessMode(), params != null ? params : Map.of(), context);
    }

    @Override
    public Optional<Map<String, Object>> buildPostActionLlmFacts(ActionResult actionResult, ActionContext context) {
        if (actionResult == null) {
            return Optional.empty();
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        if (metadata != null && StringUtils.hasText(metadata.getName())) {
            facts.put("action", metadata.getName());
        }
        if (metadata != null && StringUtils.hasText(metadata.getCategory())) {
            facts.put("category", metadata.getCategory());
        }
        facts.put("success", actionResult.isSuccess());
        if (StringUtils.hasText(actionResult.getMessage())) {
            facts.put("message", actionResult.getMessage());
        }
        if (StringUtils.hasText(actionResult.getErrorCode())) {
            facts.put("errorCode", actionResult.getErrorCode());
        }

        Map<String, Object> payload = actionResult.getData() != null
            ? actionResult.getData().toMap()
            : Map.of();
        Map<String, Object> businessPayload = mapValue(payload, "data");
        if (businessPayload == null) {
            businessPayload = payload;
        }
        copyCommonFields(payload, facts);
        if (businessPayload != payload) {
            copyCommonFields(businessPayload, facts);
        }
        putCompactedList(facts, "documents", businessPayload.get("documents"));
        putCompactedList(facts, "items", businessPayload.get("items"));
        putCompactedList(facts, "policies", businessPayload.get("policies"));
        Object product = businessPayload.get("product");
        if (product instanceof Map<?, ?> productMap) {
            Map<String, Object> compactProduct = compactRecord(productMap);
            if (!compactProduct.isEmpty()) {
                facts.put("product", compactProduct);
            }
        }
        return Optional.of(Collections.unmodifiableMap(facts));
    }

    private String renderTemplate(String template, Map<String, Object> params) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = renderValue(name, params);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String renderValue(String name, Map<String, Object> params) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        String key = name.trim();
        if (sensitiveParams.contains(key)) {
            return REDACTED;
        }
        Object raw = params != null ? params.get(key) : null;
        if (raw == null) {
            return "";
        }
        String s = raw.toString();
        if (!StringUtils.hasText(s)) {
            return "";
        }
        return escapeHtml(s.trim());
    }

    private String defaultConfirmationMessage(Map<String, Object> params) {
        String actionName = metadata != null && StringUtils.hasText(metadata.getName()) ? metadata.getName() : "action";
        if (params == null || params.isEmpty()) {
            return "Confirm " + actionName + "?";
        }

        Map<String, Object> safe = new LinkedHashMap<>(params);
        String joined = safe.entrySet().stream()
            .filter(e -> e.getKey() != null && StringUtils.hasText(e.getKey()))
            .limit(6)
            .map(e -> {
                String k = e.getKey();
                String v = sensitiveParams.contains(k) ? REDACTED : (e.getValue() == null ? "null" : String.valueOf(e.getValue()));
                return k + "=" + v;
            })
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
        return "Confirm " + actionName + " (" + joined + ")?";
    }

    private void copyCommonFields(Map<String, Object> source, Map<String, Object> target) {
        if (source == null || source.isEmpty()) {
            return;
        }
        copyIfPresent(source, target, "query");
        copyIfPresent(source, target, "count");
        copyIfPresent(source, target, "totalResults");
        copyIfPresent(source, target, "returnedResults");
        copyIfPresent(source, target, "lookup");
        copyIfPresent(source, target, "lookupMethod");
        copyIfPresent(source, target, "productTitle");
        copyIfPresent(source, target, "productHandle");
        copyIfPresent(source, target, "variantTitle");
        copyIfPresent(source, target, "available");
        copyIfPresent(source, target, "inventoryQuantity");
        copyIfPresent(source, target, "storefrontUrl");
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            putIfPresent(target, key, value);
        }
    }

    private void putCompactedList(Map<String, Object> facts, String key, Object value) {
        List<Map<String, Object>> compacted = compactRecords(value);
        if (!compacted.isEmpty()) {
            facts.put(key, compacted);
            facts.put(key + "Count", compacted.size());
        }
    }

    private List<Map<String, Object>> compactRecords(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (out.size() >= 5) {
                break;
            }
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> compact = compactRecord(map);
                if (!compact.isEmpty()) {
                    out.add(Collections.unmodifiableMap(compact));
                }
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private Map<String, Object> compactRecord(Map<?, ?> record) {
        Map<String, Object> compact = new LinkedHashMap<>();
        putIfPresent(compact, "title", firstNonBlank(asString(mapObject(record, "title")), asString(mapObject(record, "name"))));
        putIfPresent(compact, "entityType", firstNonBlank(asString(mapObject(record, "entityType")), asString(mapObject(record, "type"))));

        Object metadata = mapObject(record, "metadata");
        if (metadata instanceof Map<?, ?> metadataMap) {
            putSelectedMetadata(compact, metadataMap);
        }
        putSelectedMetadata(compact, record);

        if (!hasCommerceFacts(compact)) {
            putIfPresent(compact, "content", truncate(asString(mapObject(record, "content")), 300));
        }
        return compact;
    }

    private boolean hasCommerceFacts(Map<String, Object> compact) {
        if (compact == null || compact.isEmpty()) {
            return false;
        }
        return compact.containsKey("price")
            || compact.containsKey("available")
            || compact.containsKey("inventoryQuantity")
            || compact.containsKey("reviewSignalsPresent")
            || compact.containsKey("reviewAverage")
            || compact.containsKey("reviewCount");
    }

    private void putSelectedMetadata(Map<String, Object> target, Map<?, ?> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        putIfPresent(target, "vendor", mapObject(source, "vendor"));
        putIfPresent(target, "productType", mapObject(source, "productType"));
        putIfPresent(target, "available", mapObject(source, "available"));
        putIfPresent(target, "price", mapObject(source, "price"));
        putIfPresent(target, "inventoryQuantity", mapObject(source, "inventoryQuantity"));
        putIfPresent(target, "primarySku", mapObject(source, "primarySku"));
        putIfPresent(target, "sku", mapObject(source, "sku"));
        putIfPresent(target, "reviewSignalsPresent", mapObject(source, "reviewSignalsPresent"));
        putIfPresent(target, "reviewAverage", mapObject(source, "reviewAverage"));
        putIfPresent(target, "reviewCount", mapObject(source, "reviewCount"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> source, String key) {
        Object value = source != null ? source.get(key) : null;
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private Object mapObject(Map<?, ?> source, String key) {
        if (source == null || key == null) {
            return null;
        }
        Object direct = source.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry != null && entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (!trimmed.isEmpty()) {
                target.put(key, trimmed);
            }
            return;
        }
        target.put(key, value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    private String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private String escapeHtml(String input) {
        if (!StringUtils.hasText(input)) {
            return "";
        }
        String s = input;
        s = s.replace("&", "&amp;");
        s = s.replace("<", "&lt;");
        s = s.replace(">", "&gt;");
        s = s.replace("\"", "&quot;");
        s = s.replace("'", "&#39;");
        // Normalize newlines to avoid UI injection tricks
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        // Keep confirmation messages readable and bounded
        int max = 200;
        if (s.length() > max) {
            return s.substring(0, max - 3) + "...";
        }
        return s;
    }
}
