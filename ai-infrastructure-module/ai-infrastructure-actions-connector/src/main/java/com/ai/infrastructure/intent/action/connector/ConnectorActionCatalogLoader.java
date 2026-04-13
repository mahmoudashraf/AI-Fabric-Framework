package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.intent.action.AIActionParamType;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.confirmation.ConfirmationInterceptorDecision;
import com.ai.infrastructure.intent.action.confirmation.ConfirmationInterceptorDecisionType;
import com.ai.infrastructure.intent.action.confirmation.ConfirmationInterceptorRule;
import com.ai.infrastructure.intent.action.confirmation.ConfirmationInterceptorStackPolicy;
import com.ai.infrastructure.intent.action.confirmation.ConfirmationInterceptorTrigger;
import lombok.extern.slf4j.Slf4j;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads connector-backed action contracts from configured sources (file-based in the initial release).
 *
 * <p>Greenfield: this loader is strict and fail-fast. Invalid schemas or missing files must
 * terminate startup rather than running with a partial catalog.</p>
 */
@Slf4j
@Service
public class ConnectorActionCatalogLoader {

    private static final String KEY_ACTIONS = "actions";
    private static final String KEY_CONFIRMATION_INTERCEPTORS = "confirmationInterceptors";
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_ACCESS_MODE = "accessMode";
    private static final String KEY_REQUIRES_CONFIRMATION = "requiresConfirmation";
    private static final String KEY_CONFIRMATION_MESSAGE = "confirmationMessage";
    private static final String KEY_PARAMS = "params";
    private static final String KEY_ANONYMOUS_ALLOWED = "anonymousAllowed";

    private static final String KEY_TYPE = "type";
    private static final String KEY_REQUIRED = "required";
    private static final String KEY_BATCH_TARGETS = "batchTargets";
    private static final String KEY_PATTERN = "pattern";
    private static final String KEY_ALLOWED_VALUES = "allowedValues";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_SENSITIVE = "sensitive";
    private static final String KEY_TRIGGER = "trigger";
    private static final String KEY_CONFIRMATION = "confirmation";
    private static final String KEY_ONCE_PARAM = "onceParam";
    private static final String KEY_DECISION = "decision";
    private static final String KEY_ACTION = "action";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_STACK = "stack";
    private static final String KEY_POP_CURRENT = "popCurrent";
    private static final String KEY_POP_PREVIOUS_IF_ACTION_IN = "popPreviousIfActionIn";

    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final Pattern INTERCEPTION_TEMPLATE_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private static final Pattern SAFE_ONCE_PARAM = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final ResourceLoader resourceLoader;
    private final Yaml yaml;

    public ConnectorActionCatalogLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;

        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        this.yaml = new Yaml(new SafeConstructor(options));
    }

    /**
     * Load all connector action definitions from configured sources.
     *
     * @param sources configured catalog sources (may be empty)
     * @return list of parsed action definitions (never null)
     */
    public List<ConnectorActionDefinition> loadActions(List<AIActionCatalogProperties.ActionSourceProperties> sources) {
        return loadCatalog(sources).actions();
    }

    public ConnectorActionCatalog loadCatalog(List<AIActionCatalogProperties.ActionSourceProperties> sources) {
        if (sources == null || sources.isEmpty()) {
            return new ConnectorActionCatalog(List.of(), List.of(), List.of());
        }

        List<ConnectorActionDefinition> actions = new ArrayList<>();
        List<ConfirmationInterceptorRule> confirmationInterceptors = new ArrayList<>();
        List<String> sourceLocations = new ArrayList<>();
        for (AIActionCatalogProperties.ActionSourceProperties source : sources) {
            if (source == null) {
                continue;
            }
            if (source.getType() == AIActionCatalogProperties.ActionSourceType.DB) {
                throw new IllegalStateException("Action source type DB is not supported yet (configure FILE sources only).");
            }

            String path = source.getPath();
            if (!StringUtils.hasText(path)) {
                throw new IllegalStateException("Action source path is required for FILE sources.");
            }

            String label = path.trim();
            Resource resource = resourceLoader.getResource(label);
            if (resource == null || !resource.exists()) {
                if (source.isOptional()) {
                    log.info("Optional action contract file not found: {} (skipping)", label);
                    continue;
                }
                throw new IllegalStateException("Action contract file not found: " + label);
            }

            CatalogPart part = loadFromResource(resource, label);
            actions.addAll(part.actions());
            confirmationInterceptors.addAll(part.confirmationInterceptors());
            sourceLocations.add(label);
        }

        validateCatalog(actions, confirmationInterceptors);
        return new ConnectorActionCatalog(actions, confirmationInterceptors, sourceLocations);
    }

    private CatalogPart loadFromResource(Resource resource, String label) {
        Object root;
        try (InputStream in = resource.getInputStream()) {
            root = yaml.load(in);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read action contract YAML from " + label + ": " + ex.getMessage(), ex);
        }

        Map<String, Object> rootMap = extractRootMap(root, label);
        List<Map<String, Object>> actionMaps = extractSectionMaps(rootMap.get(KEY_ACTIONS), label, KEY_ACTIONS, "action");
        List<Map<String, Object>> interceptorMaps = extractSectionMaps(
            rootMap.get(KEY_CONFIRMATION_INTERCEPTORS),
            label,
            KEY_CONFIRMATION_INTERCEPTORS,
            "confirmation interceptor"
        );
        if (actionMaps.isEmpty()) {
            log.info("No connector actions found in {}", label);
        }

        List<ConnectorActionDefinition> actions = new ArrayList<>();
        for (Map<String, Object> map : actionMaps) {
            if (map == null || map.isEmpty()) {
                continue;
            }
            actions.add(parseAction(map, label));
        }

        List<ConfirmationInterceptorRule> confirmationInterceptors = new ArrayList<>();
        for (Map<String, Object> map : interceptorMaps) {
            if (map == null || map.isEmpty()) {
                continue;
            }
            confirmationInterceptors.add(parseConfirmationInterceptor(map, label));
        }
        return new CatalogPart(actions, confirmationInterceptors);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractRootMap(Object root, String label) {
        if (root == null) {
            return Map.of();
        }
        if (root instanceof Map<?, ?> map) {
            return toStringKeyedMap(map);
        }

        throw new IllegalStateException("Invalid action contract in " + label + ": expected a YAML object.");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSectionMaps(Object rawSection, String label, String sectionName, String entryLabel) {
        if (rawSection == null) {
            return List.of();
        }
        if (!(rawSection instanceof List<?> list)) {
            throw new IllegalStateException("Invalid action contract in " + label + ": '" + sectionName + "' must be a list.");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalStateException("Invalid action contract in " + label + ": each " + entryLabel + " must be a map/object.");
            }
            out.add(toStringKeyedMap(raw));
        }
        return List.copyOf(out);
    }

    private ConnectorActionDefinition parseAction(Map<String, Object> raw, String label) {
        String name = readString(raw, KEY_NAME);
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("Invalid action contract in " + label + ": action.name is required.");
        }

        String description = readString(raw, KEY_DESCRIPTION);
        String category = readString(raw, KEY_CATEGORY);

        String accessModeRaw = readString(raw, KEY_ACCESS_MODE);
        ActionAccessMode accessMode = parseAccessMode(accessModeRaw, label, name);

        boolean requiresConfirmation = readBoolean(raw, KEY_REQUIRES_CONFIRMATION, false);
        String confirmationMessage = readString(raw, KEY_CONFIRMATION_MESSAGE);
        boolean anonymousAllowed = readBoolean(raw, KEY_ANONYMOUS_ALLOWED, false);

        List<ConnectorActionParamDefinition> params = parseParams(raw.get(KEY_PARAMS), label, name);

        validateConfirmationTemplate(name, confirmationMessage, params, label);

        return new ConnectorActionDefinition(
            name.trim(),
            description,
            category,
            accessMode,
            requiresConfirmation,
            confirmationMessage,
            params,
            anonymousAllowed
        );
    }

    private ConfirmationInterceptorRule parseConfirmationInterceptor(Map<String, Object> raw, String label) {
        String name = readString(raw, KEY_NAME);
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("Invalid action contract in " + label + ": confirmationInterceptors[].name is required.");
        }

        Map<String, Object> triggerRaw = readObjectMap(raw.get(KEY_TRIGGER), label, "confirmationInterceptors[" + name + "].trigger");
        List<String> pendingActions = readStringList(triggerRaw.get("pendingActions"));
        if (pendingActions.isEmpty()) {
            throw new IllegalStateException("Invalid action contract in " + label
                + " for confirmation interceptor '" + name + "': trigger.pendingActions must list one or more actions.");
        }

        String confirmationRaw = readString(triggerRaw, KEY_CONFIRMATION);
        IntentType confirmation = parseConfirmationIntentType(confirmationRaw, label, name);
        String onceParam = readString(triggerRaw, KEY_ONCE_PARAM);

        Map<String, Object> decisionRaw = readObjectMap(raw.get(KEY_DECISION), label, "confirmationInterceptors[" + name + "].decision");
        ConfirmationInterceptorDecisionType decisionType = parseDecisionType(readString(decisionRaw, KEY_TYPE), label, name);
        String action = readString(decisionRaw, KEY_ACTION);
        Map<String, Object> params = decisionRaw.containsKey(KEY_PARAMS)
            ? readOptionalObjectMap(decisionRaw.get(KEY_PARAMS), label, "confirmationInterceptors[" + name + "].decision.params")
            : Map.of();
        String message = readString(decisionRaw, KEY_MESSAGE);

        Map<String, Object> stackRaw = raw.containsKey(KEY_STACK)
            ? readOptionalObjectMap(raw.get(KEY_STACK), label, "confirmationInterceptors[" + name + "].stack")
            : Map.of();
        boolean popCurrent = readBoolean(stackRaw, KEY_POP_CURRENT, false);
        List<String> popPreviousIfActionIn = readStringList(stackRaw.get(KEY_POP_PREVIOUS_IF_ACTION_IN));

        return new ConfirmationInterceptorRule(
            name.trim(),
            new ConfirmationInterceptorTrigger(pendingActions, confirmation, onceParam),
            new ConfirmationInterceptorDecision(decisionType, action, params, message),
            new ConfirmationInterceptorStackPolicy(popCurrent, popPreviousIfActionIn)
        );
    }

    private List<ConnectorActionParamDefinition> parseParams(Object rawParams, String label, String actionName) {
        if (rawParams == null) {
            return List.of();
        }
        if (!(rawParams instanceof List<?> list)) {
            throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName + "': params must be a list.");
        }

        List<ConnectorActionParamDefinition> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName + "': each param must be a map/object.");
            }
            Map<String, Object> casted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                casted.put(entry.getKey().toString(), entry.getValue());
            }
            out.add(parseParam(casted, label, actionName));
        }

        return List.copyOf(out);
    }

    private ConnectorActionParamDefinition parseParam(Map<String, Object> raw, String label, String actionName) {
        String name = readString(raw, KEY_NAME);
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName + "': param.name is required.");
        }

        String description = readString(raw, KEY_DESCRIPTION);
        String typeRaw = readString(raw, KEY_TYPE);
        if (!StringUtils.hasText(typeRaw)) {
            throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName + "', param '" + name + "': type is required.");
        }

        AIActionParamType type = parseParamType(typeRaw, label, actionName, name);

        boolean required = readBoolean(raw, KEY_REQUIRED, false);
        boolean batchTargets = readBoolean(raw, KEY_BATCH_TARGETS, false);
        String pattern = readString(raw, KEY_PATTERN);
        List<String> allowedValues = readStringList(raw.get(KEY_ALLOWED_VALUES));
        Long min = readLong(raw.get(KEY_MIN), label, actionName, name, KEY_MIN);
        Long max = readLong(raw.get(KEY_MAX), label, actionName, name, KEY_MAX);
        boolean sensitive = readBoolean(raw, KEY_SENSITIVE, false);

        return new ConnectorActionParamDefinition(
            name.trim(),
            description,
            type,
            required,
            batchTargets,
            pattern,
            allowedValues,
            min,
            max,
            sensitive
        );
    }

    private void validateConfirmationTemplate(String actionName,
                                              String confirmationMessage,
                                              List<ConnectorActionParamDefinition> params,
                                              String label) {
        if (!StringUtils.hasText(confirmationMessage)) {
            return;
        }

        Set<String> paramNames = new LinkedHashSet<>();
        if (params != null) {
            for (ConnectorActionParamDefinition param : params) {
                if (param != null && StringUtils.hasText(param.name())) {
                    paramNames.add(param.name());
                }
            }
        }

        for (String placeholder : extractPlaceholders(confirmationMessage)) {
            if (!paramNames.contains(placeholder)) {
                throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName
                    + "': confirmationMessage placeholder '{{" + placeholder + "}}' does not match any declared param.");
            }
        }
    }

    private Set<String> extractPlaceholders(String template) {
        if (!StringUtils.hasText(template)) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (StringUtils.hasText(name)) {
                out.add(name.trim());
            }
        }
        return Set.copyOf(out);
    }

    private void validateCatalog(List<ConnectorActionDefinition> actions,
                                 List<ConfirmationInterceptorRule> confirmationInterceptors) {
        Map<String, ConnectorActionDefinition> actionsByName = new LinkedHashMap<>();
        for (ConnectorActionDefinition action : actions) {
            if (action == null || !StringUtils.hasText(action.name())) {
                continue;
            }
            String normalized = action.name().trim().toLowerCase(Locale.ROOT);
            if (actionsByName.put(normalized, action) != null) {
                throw new IllegalStateException("Duplicate action name in connector catalog: " + action.name());
            }
        }

        Set<String> ruleNames = new LinkedHashSet<>();
        for (ConfirmationInterceptorRule rule : confirmationInterceptors) {
            if (rule == null) {
                continue;
            }
            if (!StringUtils.hasText(rule.name())) {
                throw new IllegalStateException("Confirmation interceptor name is required.");
            }
            String normalizedRuleName = rule.name().trim().toLowerCase(Locale.ROOT);
            if (!ruleNames.add(normalizedRuleName)) {
                throw new IllegalStateException("Duplicate confirmation interceptor name: " + rule.name());
            }
            validateConfirmationInterceptor(rule, actionsByName);
        }
    }

    private void validateConfirmationInterceptor(ConfirmationInterceptorRule rule,
                                                 Map<String, ConnectorActionDefinition> actionsByName) {
        ConfirmationInterceptorTrigger trigger = rule.trigger();
        ConfirmationInterceptorDecision decision = rule.decision();
        ConfirmationInterceptorStackPolicy stackPolicy = rule.stackPolicy();

        if (trigger == null || decision == null) {
            throw new IllegalStateException("Confirmation interceptor '" + rule.name() + "' is incomplete.");
        }
        for (String pendingAction : trigger.pendingActions()) {
            requireKnownAction(actionsByName, pendingAction, "trigger.pendingActions", rule.name());
        }
        if (StringUtils.hasText(trigger.onceParam()) && !SAFE_ONCE_PARAM.matcher(trigger.onceParam().trim()).matches()) {
            throw new IllegalStateException("Confirmation interceptor '" + rule.name() + "' has an unsafe trigger.onceParam value.");
        }
        if (decision.type() == ConfirmationInterceptorDecisionType.PROMPT_ACTION
            || decision.type() == ConfirmationInterceptorDecisionType.EXECUTE_ACTION) {
            requireKnownAction(actionsByName, decision.action(), "decision.action", rule.name());
        }
        if (decision.type() == ConfirmationInterceptorDecisionType.PROMPT_ACTION) {
            ConnectorActionDefinition action = actionsByName.get(normalizeName(decision.action()));
            if (action != null && !action.requiresConfirmation()) {
                throw new IllegalStateException("Confirmation interceptor '" + rule.name()
                    + "' uses PROMPT_ACTION with non-confirmable action '" + decision.action() + "'.");
            }
        }
        if (decision.type() == ConfirmationInterceptorDecisionType.REPLY && !StringUtils.hasText(decision.message())) {
            throw new IllegalStateException("Confirmation interceptor '" + rule.name() + "' requires decision.message for REPLY.");
        }
        if (stackPolicy != null) {
            for (String actionName : stackPolicy.popPreviousIfActionIn()) {
                requireKnownAction(actionsByName, actionName, "stack.popPreviousIfActionIn", rule.name());
            }
        }
        validateInterceptorTemplates(decision.params(), "decision.params", rule.name());
        validateInterceptorTemplates(decision.message(), "decision.message", rule.name());
    }

    private void validateInterceptorTemplates(Object raw, String field, String ruleName) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                validateInterceptorTemplates(value, field, ruleName);
            }
            return;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                validateInterceptorTemplates(item, field, ruleName);
            }
            return;
        }
        if (!(raw instanceof String text) || !StringUtils.hasText(text)) {
            return;
        }
        Matcher matcher = INTERCEPTION_TEMPLATE_PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            validateInterceptorTemplateExpression(matcher.group(1), field, ruleName);
        }
    }

    private void validateInterceptorTemplateExpression(String expression, String field, String ruleName) {
        if (!StringUtils.hasText(expression)) {
            throw new IllegalStateException("Confirmation interceptor '" + ruleName + "' has an empty template placeholder in " + field + ".");
        }
        String normalized = expression.trim();
        int fallbackSeparator = normalized.indexOf('|');
        String path = fallbackSeparator >= 0 ? normalized.substring(0, fallbackSeparator).trim() : normalized;
        if (path.startsWith("pending.actionParams.")) {
            if (!StringUtils.hasText(path.substring("pending.actionParams.".length()))) {
                throw new IllegalStateException("Confirmation interceptor '" + ruleName + "' has an invalid pending placeholder in " + field + ".");
            }
            return;
        }
        if (path.startsWith("stack.previous.actionParams.")) {
            if (!StringUtils.hasText(path.substring("stack.previous.actionParams.".length()))) {
                throw new IllegalStateException("Confirmation interceptor '" + ruleName + "' has an invalid stack.previous placeholder in " + field + ".");
            }
            return;
        }
        throw new IllegalStateException("Confirmation interceptor '" + ruleName
            + "' uses unsupported template placeholder '{{" + normalized + "}}' in " + field + ".");
    }

    private void requireKnownAction(Map<String, ConnectorActionDefinition> actionsByName,
                                    String actionName,
                                    String field,
                                    String ruleName) {
        if (!StringUtils.hasText(actionName)) {
            throw new IllegalStateException("Confirmation interceptor '" + ruleName + "' requires " + field + ".");
        }
        if (!actionsByName.containsKey(normalizeName(actionName))) {
            throw new IllegalStateException("Confirmation interceptor '" + ruleName
                + "' references unknown action '" + actionName + "' in " + field + ".");
        }
    }

    private IntentType parseConfirmationIntentType(String raw, String label, String ruleName) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("Invalid action contract in " + label
                + " for confirmation interceptor '" + ruleName + "': trigger.confirmation is required.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            IntentType intentType = IntentType.valueOf(normalized);
            if (intentType != IntentType.CONFIRMATION_POSITIVE && intentType != IntentType.CONFIRMATION_NEGATIVE) {
                throw new IllegalStateException("Invalid action contract in " + label
                    + " for confirmation interceptor '" + ruleName
                    + "': trigger.confirmation must be CONFIRMATION_POSITIVE or CONFIRMATION_NEGATIVE.");
            }
            return intentType;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid action contract in " + label
                + " for confirmation interceptor '" + ruleName
                + "': trigger.confirmation must be CONFIRMATION_POSITIVE or CONFIRMATION_NEGATIVE.");
        }
    }

    private ConfirmationInterceptorDecisionType parseDecisionType(String raw, String label, String ruleName) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("Invalid action contract in " + label
                + " for confirmation interceptor '" + ruleName + "': decision.type is required.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return ConfirmationInterceptorDecisionType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid action contract in " + label
                + " for confirmation interceptor '" + ruleName
                + "': decision.type must be PROMPT_ACTION, EXECUTE_ACTION, or REPLY.");
        }
    }

    private ActionAccessMode parseAccessMode(String raw, String label, String actionName) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName + "': accessMode is required.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return ActionAccessMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName
                + "': accessMode must be one of READ, READ_WRITE, WRITE_ONLY (got '" + raw + "').");
        }
    }

    private AIActionParamType parseParamType(String raw, String label, String actionName, String paramName) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return AIActionParamType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName
                + "', param '" + paramName + "': unsupported type '" + raw + "'.");
        }
    }

    private String readString(Map<String, Object> map, String key) {
        if (map == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String s = value.toString();
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private boolean readBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null || !StringUtils.hasText(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = value.toString();
        if (!StringUtils.hasText(s)) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s.trim());
    }

    private Map<String, Object> readObjectMap(Object raw, String label, String field) {
        Map<String, Object> map = readOptionalObjectMap(raw, label, field);
        if (map.isEmpty()) {
            throw new IllegalStateException("Invalid action contract in " + label + ": " + field + " must be an object.");
        }
        return map;
    }

    private Map<String, Object> readOptionalObjectMap(Object raw, String label, String field) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Invalid action contract in " + label + ": " + field + " must be an object.");
        }
        return Collections.unmodifiableMap(toStringKeyedMap(map));
    }

    private List<String> readStringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String s = item.toString();
                if (StringUtils.hasText(s)) {
                    out.add(s.trim());
                }
            }
            return List.copyOf(out);
        }
        String s = raw.toString();
        if (!StringUtils.hasText(s)) {
            return List.of();
        }
        return List.of(s.trim());
    }

    private Long readLong(Object raw,
                          String label,
                          String actionName,
                          String paramName,
                          String field) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            double d = number.doubleValue();
            if (!Double.isFinite(d) || d != Math.rint(d)) {
                throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName
                    + "', param '" + paramName + "': " + field + " must be an integer.");
            }
            return number.longValue();
        }
        String s = raw.toString();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid action contract in " + label + " for action '" + actionName
                + "', param '" + paramName + "': " + field + " must be an integer.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toStringKeyedMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return toStringKeyedMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(normalizeValue(item));
            }
            return List.copyOf(out);
        }
        return value;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record CatalogPart(
        List<ConnectorActionDefinition> actions,
        List<ConfirmationInterceptorRule> confirmationInterceptors
    ) {
    }
}
