package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionParamType;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_ACCESS_MODE = "accessMode";
    private static final String KEY_REQUIRES_CONFIRMATION = "requiresConfirmation";
    private static final String KEY_CONFIRMATION_MESSAGE = "confirmationMessage";
    private static final String KEY_PARAMS = "params";

    private static final String KEY_TYPE = "type";
    private static final String KEY_REQUIRED = "required";
    private static final String KEY_BATCH_TARGETS = "batchTargets";
    private static final String KEY_PATTERN = "pattern";
    private static final String KEY_ALLOWED_VALUES = "allowedValues";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_SENSITIVE = "sensitive";

    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

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
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        List<ConnectorActionDefinition> out = new ArrayList<>();
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

            Resource resource = resourceLoader.getResource(path.trim());
            if (resource == null || !resource.exists()) {
                throw new IllegalStateException("Action contract file not found: " + path);
            }

            out.addAll(loadFromResource(resource, path.trim()));
        }

        return List.copyOf(out);
    }

    private List<ConnectorActionDefinition> loadFromResource(Resource resource, String label) {
        Object root;
        try (InputStream in = resource.getInputStream()) {
            root = yaml.load(in);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read action contract YAML from " + label + ": " + ex.getMessage(), ex);
        }

        List<Map<String, Object>> actionMaps = extractActionMaps(root, label);
        if (actionMaps.isEmpty()) {
            log.info("No connector actions found in {}", label);
            return List.of();
        }

        List<ConnectorActionDefinition> actions = new ArrayList<>();
        for (Map<String, Object> map : actionMaps) {
            if (map == null || map.isEmpty()) {
                continue;
            }
            actions.add(parseAction(map, label));
        }
        return List.copyOf(actions);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractActionMaps(Object root, String label) {
        if (root == null) {
            return List.of();
        }
        if (root instanceof Map<?, ?> map) {
            Object actions = map.get(KEY_ACTIONS);
            if (actions == null) {
                return List.of();
            }
            if (!(actions instanceof List<?> list)) {
                throw new IllegalStateException("Invalid action contract in " + label + ": 'actions' must be a list.");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    throw new IllegalStateException("Invalid action contract in " + label + ": each action must be a map/object.");
                }
                Map<String, Object> casted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    casted.put(entry.getKey().toString(), entry.getValue());
                }
                out.add(casted);
            }
            return List.copyOf(out);
        }

        throw new IllegalStateException("Invalid action contract in " + label + ": expected a YAML object with an 'actions' list.");
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

        List<ConnectorActionParamDefinition> params = parseParams(raw.get(KEY_PARAMS), label, name);

        validateConfirmationTemplate(name, confirmationMessage, params, label);

        return new ConnectorActionDefinition(
            name.trim(),
            description,
            category,
            accessMode,
            requiresConfirmation,
            confirmationMessage,
            params
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
}

