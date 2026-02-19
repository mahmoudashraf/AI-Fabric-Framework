package com.ai.infrastructure.connector.rest.templating;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateEngine {

    private static final Pattern FULL_PLACEHOLDER = Pattern.compile("^\\{\\{\\s*([^}]+?)\\s*}}$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");

    public Object apply(Object template, TemplateContext context) {
        if (template == null) {
            return null;
        }

        if (template instanceof Map<?, ?> raw) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                out.put(entry.getKey().toString(), apply(entry.getValue(), context));
            }
            return out;
        }

        if (template instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(apply(item, context));
            }
            return out;
        }

        if (template instanceof String str) {
            return applyString(str, context);
        }

        return template;
    }

    public String applyToString(String template, TemplateContext context) {
        if (!StringUtils.hasText(template)) {
            return template;
        }
        Object out = applyString(template, context);
        return out != null ? out.toString() : null;
    }

    private Object applyString(String template, TemplateContext context) {
        if (template == null) {
            return null;
        }

        String raw = template;
        Matcher full = FULL_PLACEHOLDER.matcher(raw.trim());
        if (full.matches()) {
            String expr = full.group(1);
            return resolveExpression(expr, context);
        }

        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while (m.find()) {
            sb.append(raw, idx, m.start());
            Object resolved = resolveExpression(m.group(1), context);
            sb.append(resolved != null ? resolved.toString() : "");
            idx = m.end();
        }
        sb.append(raw.substring(idx));
        return sb.toString();
    }

    private Object resolveExpression(String expression, TemplateContext context) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        String expr = expression.trim();

        if (!expr.contains(".")) {
            return resolveRoot(expr, context);
        }

        String[] parts = expr.split("\\.");
        if (parts.length == 0) {
            return null;
        }
        Object current = resolveRoot(parts[0], context);
        for (int i = 1; i < parts.length; i++) {
            current = step(current, parts[i]);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object resolveRoot(String root, TemplateContext context) {
        if (!StringUtils.hasText(root)) {
            return null;
        }

        String key = root.trim();
        return switch (key) {
            case "actionId" -> context != null ? context.actionId() : null;
            case "idempotencyKey" -> context != null ? context.idempotencyKey() : null;
            case "params" -> context != null ? context.params() : null;
            case "trace" -> context != null ? context.trace() : null;
            case "body" -> context != null ? context.body() : null;
            case "status" -> context != null ? context.status() : null;
            case "headers" -> context != null ? context.headers() : null;
            default -> throw new IllegalStateException("Unsupported template root '" + key + "'. Allowed roots: actionId, idempotencyKey, params, trace, body, status, headers.");
        };
    }

    private Object step(Object current, String token) {
        if (current == null || !StringUtils.hasText(token)) {
            return null;
        }
        String key = token.trim();

        if (current instanceof Map<?, ?> map) {
            return map.get(key);
        }

        if (current instanceof List<?> list) {
            try {
                int idx = Integer.parseInt(key);
                if (idx < 0 || idx >= list.size()) {
                    return null;
                }
                return list.get(idx);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }

        return null;
    }
}
