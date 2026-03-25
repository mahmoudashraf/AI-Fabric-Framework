package com.ai.infrastructure.connector.rest.template;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.service.RestActionExecutionService.ResolvedUpstream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, strict templating engine for routing config:
 * - Supports placeholders: {@code {{params.*}}}, {@code {{trace.*}}}, {@code {{body.*}}}, {@code {{status}}}.
 * - If the entire string is a single placeholder, the resolved value is returned as-is (type preserved).
 * - Otherwise placeholders are string-substituted.
 */
@Service
public class TemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_\\.\\[\\]-]+)\\s*}}");

    public Map<String, Object> contextFor(ActionExecuteRequestDto request, ResolvedUpstream upstream) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("actionId", request != null ? request.actionId() : null);
        ctx.put("idempotencyKey", request != null ? request.idempotencyKey() : null);
        ctx.put("params", request != null && request.params() != null ? request.params() : Map.of());
        ctx.put("trace", request != null && request.trace() != null ? toTraceMap(request) : Map.of());
        ctx.put("upstream", upstream != null ? Map.of("baseUrl", upstream.baseUrl(), "path", upstream.path()) : Map.of());
        return ctx;
    }

    private Map<String, Object> toTraceMap(ActionExecuteRequestDto request) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (request == null || request.trace() == null) {
            return trace;
        }
        if (StringUtils.hasText(request.trace().requestId())) {
            trace.put("requestId", request.trace().requestId());
        }
        if (StringUtils.hasText(request.trace().conversationId())) {
            trace.put("conversationId", request.trace().conversationId());
        }
        if (StringUtils.hasText(request.trace().userId())) {
            trace.put("userId", request.trace().userId());
        }
        if (StringUtils.hasText(request.trace().sessionId())) {
            trace.put("sessionId", request.trace().sessionId());
        }
        if (StringUtils.hasText(request.trace().tenantId())) {
            trace.put("tenantId", request.trace().tenantId());
        }
        return trace;
    }

    public Object resolve(Object template, Map<String, Object> context) {
        if (template == null) {
            return null;
        }
        if (template instanceof String s) {
            return resolveString(s, context);
        }
        if (template instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = entry.getKey().toString();
                Object value = resolve(entry.getValue(), context);
                out.put(key, value);
            }
            return out;
        }
        if (template instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                out.add(resolve(item, context));
            }
            return out;
        }
        return template;
    }

    private Object resolveString(String template, Map<String, Object> context) {
        if (!StringUtils.hasText(template)) {
            return template;
        }

        Matcher m = PLACEHOLDER.matcher(template);
        if (isSinglePlaceholder(template, m)) {
            String expr = m.group(1);
            return eval(expr, context);
        }

        m.reset();
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String expr = m.group(1);
            Object value = eval(expr, context);
            String replacement = value != null ? value.toString() : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private boolean isSinglePlaceholder(String template, Matcher matcher) {
        if (!matcher.find()) {
            return false;
        }
        if (matcher.start() != 0) {
            return false;
        }
        if (matcher.end() != template.length()) {
            return false;
        }
        return true;
    }

    private Object eval(String expr, Map<String, Object> context) {
        if (!StringUtils.hasText(expr)) {
            return null;
        }
        String e = expr.trim();

        // Root access shortcut (e.g. {{status}})
        if (!e.contains(".")) {
            return context != null ? context.get(e) : null;
        }

        String[] parts = e.split("\\.", 2);
        String root = parts[0].trim();
        String path = parts[1].trim();
        Object rootObj = context != null ? context.get(root) : null;
        return readPath(rootObj, path);
    }

    @SuppressWarnings("unchecked")
    private Object readPath(Object root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return null;
        }

        String remaining = path;
        Object current = root;

        while (StringUtils.hasText(remaining)) {
            int dot = remaining.indexOf('.');
            String segment = dot >= 0 ? remaining.substring(0, dot) : remaining;
            remaining = dot >= 0 ? remaining.substring(dot + 1) : null;

            segment = segment.trim();
            if (segment.isEmpty()) {
                continue;
            }

            // Handle optional [index] suffix (e.g. items[0])
            String key = segment;
            Integer index = null;
            int bracket = segment.indexOf('[');
            if (bracket >= 0 && segment.endsWith("]")) {
                key = segment.substring(0, bracket).trim();
                String rawIndex = segment.substring(bracket + 1, segment.length() - 1).trim();
                if (rawIndex.matches("^[0-9]+$")) {
                    index = Integer.parseInt(rawIndex);
                }
            }

            if (StringUtils.hasText(key)) {
                if (current instanceof Map<?, ?> map) {
                    current = map.get(key);
                } else {
                    return null;
                }
            }

            if (index != null) {
                if (current instanceof List<?> list) {
                    if (index < 0 || index >= list.size()) {
                        return null;
                    }
                    current = list.get(index);
                } else {
                    return null;
                }
            }

            if (remaining == null) {
                break;
            }
        }

        return current;
    }
}
