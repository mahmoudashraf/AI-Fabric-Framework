package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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

