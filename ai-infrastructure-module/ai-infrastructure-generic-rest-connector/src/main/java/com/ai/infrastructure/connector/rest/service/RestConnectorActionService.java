package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.api.ActionTargetRefDto;
import com.ai.infrastructure.connector.rest.api.TraceContextDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.templating.TemplateContext;
import com.ai.infrastructure.connector.rest.templating.TemplateEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class RestConnectorActionService {

    private static final String ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    private static final String ERROR_ACTION_NOT_SUPPORTED = "ACTION_NOT_SUPPORTED";
    private static final String ERROR_MAPPING_ERROR = "MAPPING_ERROR";
    private static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    private static final String ERROR_TIMEOUT = "TIMEOUT";
    private static final String ERROR_RATE_LIMITED = "RATE_LIMITED";

    private static final String LIST_ITEMS = "_items";
    private static final String LIST_COUNT = "_count";

    private final RestRoutingConfig config;
    private final ObjectMapper objectMapper;
    private final TemplateEngine templateEngine;
    private final UpstreamClient upstreamClient;
    private final InMemoryIdempotencyStore idempotencyStore;

    public RestConnectorActionService(RestRoutingConfig config,
                                     ObjectMapper objectMapper,
                                     TemplateEngine templateEngine,
                                     UpstreamClient upstreamClient,
                                     InMemoryIdempotencyStore idempotencyStore) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.templateEngine = templateEngine;
        this.upstreamClient = upstreamClient;
        this.idempotencyStore = idempotencyStore;
    }

    public ActionResultDto execute(ActionExecuteRequestDto request) {
        long start = System.currentTimeMillis();

        String actionId = request != null ? request.actionId() : null;
        if (!StringUtils.hasText(actionId)) {
            return ActionResultDto.failure(ERROR_INVALID_REQUEST, "actionId is required.");
        }

        RestRoutingConfig.ActionRoute route = resolveRoute(actionId.trim());
        if (route == null) {
            return ActionResultDto.failure(ERROR_ACTION_NOT_SUPPORTED, "Action is not supported.");
        }

        String requestId = request != null && request.trace() != null ? request.trace().requestId() : null;

        try {
            ActionResultDto result = idempotencyStore.executeIdempotent(request, () -> executeOnce(actionId.trim(), route, request));
            log.info("Action '{}' executed (requestId={}, success={}, errorCode={}, durationMs={})",
                actionId.trim(),
                safe(requestId),
                result != null && result.success(),
                result != null ? safe(result.errorCode()) : ERROR_SERVICE_UNAVAILABLE,
                System.currentTimeMillis() - start
            );
            return result != null ? result : ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Connector returned null result.");
        } catch (Exception ex) {
            log.warn("Action '{}' failed (requestId={}): {}", actionId.trim(), safe(requestId), ex.getMessage());
            return ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Connector service is unavailable.");
        }
    }

    private ActionResultDto executeOnce(String actionId, RestRoutingConfig.ActionRoute route, ActionExecuteRequestDto request) {
        Map<String, Object> params = request != null && request.params() != null ? new LinkedHashMap<>(request.params()) : Map.of();
        Map<String, Object> trace = traceMap(request != null ? request.trace() : null);
        String idempotencyKey = request != null && StringUtils.hasText(request.idempotencyKey()) ? request.idempotencyKey().trim() : null;

        RestRoutingConfig.Connector connector = config != null ? config.getConnector() : null;
        RestRoutingConfig.Upstream upstream = connector != null ? connector.getUpstream() : null;
        RestRoutingConfig.UpstreamAuth upstreamAuth = upstream != null ? upstream.getAuth() : null;
        RestRoutingConfig.Http http = connector != null ? connector.getHttp() : null;
        RestRoutingConfig.Retry retry = http != null ? http.getRetry() : null;

        int timeoutMs = route.getTimeoutMs() != null ? route.getTimeoutMs() : (http != null ? http.getTimeoutMs() : 8000);
        Duration timeout = Duration.ofMillis(Math.max(100, timeoutMs));

        boolean retriesEnabled = retry != null && retry.isEnabled() && retry.getMaxAttempts() > 1;
        int maxAttempts = retriesEnabled ? retry.getMaxAttempts() : 1;
        int backoffMs = retry != null ? retry.getBackoffMs() : 200;
        List<Integer> retryOn = retry != null && retry.getRetryOn() != null ? retry.getRetryOn() : List.of(429, 502, 503, 504);

        boolean safeToRetry = idempotencyKey == null || (config != null && config.getConnector() != null && config.getConnector().getIdempotency() != null && config.getConnector().getIdempotency().isEnabled());

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                TemplateContext ctx = new TemplateContext(actionId, params, trace, idempotencyKey, null, null, Map.of());

                URI uri = buildUpstreamUri(route, upstream != null ? upstream.getBaseUrl() : null, ctx);
                String method = normalizeMethod(route.getMethod());

                Map<String, String> headers = buildUpstreamHeaders(route, upstreamAuth, ctx);
                Object bodyTemplate = route.getRequest() != null && route.getRequest().getBody() != null
                    ? route.getRequest().getBody()
                    : params;
                Object body = templateEngine.apply(bodyTemplate, ctx);
                String jsonBody = requiresBody(method) ? writeJson(body) : null;

                UpstreamClient.UpstreamResponse upstreamResponse = upstreamClient.execute(uri, method, jsonBody, headers, timeout);

                if (shouldRetryStatus(upstreamResponse.statusCode(), retryOn) && attempt < maxAttempts && safeToRetry) {
                    sleep(backoffForAttempt(backoffMs, attempt));
                    continue;
                }

                return normalizeUpstreamResponse(actionId, route, request, upstreamResponse);
            } catch (Exception ex) {
                if (attempt < maxAttempts && safeToRetry && isRetryableException(ex)) {
                    sleep(backoffForAttempt(backoffMs, attempt));
                    continue;
                }
                return failureFromException(ex);
            }
        }

        return ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Upstream service unavailable.");
    }

    private URI buildUpstreamUri(RestRoutingConfig.ActionRoute route, String baseUrl, TemplateContext ctx) {
        String resolvedUrl;
        if (StringUtils.hasText(route.getUrl())) {
            resolvedUrl = templateEngine.applyToString(route.getUrl().trim(), ctx);
        } else {
            if (!StringUtils.hasText(baseUrl)) {
                throw new IllegalStateException("connector.upstream.base-url is required when using action path routing.");
            }
            String base = baseUrl.trim();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String path = StringUtils.hasText(route.getPath()) ? templateEngine.applyToString(route.getPath().trim(), ctx) : null;
            if (!StringUtils.hasText(path)) {
                throw new IllegalStateException("Action route path is required.");
            }
            String p = path.trim();
            if (!p.startsWith("/")) {
                p = "/" + p;
            }
            resolvedUrl = base + p;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(resolvedUrl);
        Map<String, Object> query = route.getRequest() != null && route.getRequest().getQuery() != null ? route.getRequest().getQuery() : Map.of();
        if (!query.isEmpty()) {
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (!StringUtils.hasText(entry.getKey())) {
                    continue;
                }
                Object rendered = templateEngine.apply(entry.getValue(), ctx);
                if (rendered == null) {
                    continue;
                }
                if (rendered instanceof List<?> list) {
                    for (Object item : list) {
                        if (item == null) {
                            continue;
                        }
                        builder.queryParam(entry.getKey().trim(), item.toString());
                    }
                } else {
                    builder.queryParam(entry.getKey().trim(), rendered.toString());
                }
            }
        }
        return builder.build(true).toUri();
    }

    private Map<String, String> buildUpstreamHeaders(RestRoutingConfig.ActionRoute route,
                                                     RestRoutingConfig.UpstreamAuth upstreamAuth,
                                                     TemplateContext ctx) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");

        // trace propagation
        if (ctx != null && ctx.trace() != null) {
            putIfText(headers, "X-AIFABRIC-REQUEST-ID", string(ctx.trace().get("requestId")));
            putIfText(headers, "X-AIFABRIC-CONVERSATION-ID", string(ctx.trace().get("conversationId")));
            putIfText(headers, "X-AIFABRIC-USER-ID", string(ctx.trace().get("userId")));
            putIfText(headers, "X-AIFABRIC-SESSION-ID", string(ctx.trace().get("sessionId")));
            putIfText(headers, "X-AIFABRIC-TENANT-ID", string(ctx.trace().get("tenantId")));
        }
        if (ctx != null && StringUtils.hasText(ctx.idempotencyKey())) {
            headers.put("X-AIFABRIC-IDEMPOTENCY-KEY", ctx.idempotencyKey().trim());
        }

        if (upstreamAuth != null && upstreamAuth.getType() == RestRoutingConfig.UpstreamAuth.AuthType.API_KEY) {
            putIfText(headers, upstreamAuth.getHeader(), upstreamAuth.getValue());
        }

        Map<String, String> routeHeaders = route != null && route.getHeaders() != null ? route.getHeaders() : Map.of();
        for (Map.Entry<String, String> entry : routeHeaders.entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            String value = entry.getValue();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String rendered = templateEngine.applyToString(value, ctx);
            if (StringUtils.hasText(rendered)) {
                headers.put(entry.getKey().trim(), rendered.trim());
            }
        }

        return headers;
    }

    private ActionResultDto normalizeUpstreamResponse(String actionId,
                                                      RestRoutingConfig.ActionRoute route,
                                                      ActionExecuteRequestDto request,
                                                      UpstreamClient.UpstreamResponse response) {
        int status = response != null ? response.statusCode() : 0;

        Object parsedBody = parseJsonBody(response != null ? response.body() : null);
        Map<String, Object> headers = response != null ? response.headers() : Map.of();

        Map<String, Object> params = request != null && request.params() != null ? request.params() : Map.of();
        Map<String, Object> trace = traceMap(request != null ? request.trace() : null);
        String idempotencyKey = request != null && StringUtils.hasText(request.idempotencyKey()) ? request.idempotencyKey().trim() : null;

        TemplateContext ctx = new TemplateContext(actionId, params, trace, idempotencyKey, parsedBody, status, headers);

        boolean success = isSuccessStatus(route, status);
        if (!success) {
            String code = errorCodeForStatus(status);
            String message = StringUtils.hasText(route.getResponse() != null ? route.getResponse().getMessage() : null)
                ? templateEngine.applyToString(route.getResponse().getMessage(), ctx)
                : "Upstream API returned HTTP " + status + ".";
            return ActionResultDto.failure(code, message, Map.of("status", status));
        }

        Object resultTemplate = route.getResponse() != null ? route.getResponse().getResult() : null;
        Object resultRaw = resultTemplate != null ? templateEngine.apply(resultTemplate, ctx) : parsedBody;

        Map<String, Object> data = normalizeToDataMap(resultRaw);
        String message = StringUtils.hasText(route.getResponse() != null ? route.getResponse().getMessage() : null)
            ? templateEngine.applyToString(route.getResponse().getMessage(), ctx)
            : "OK";

        List<ActionTargetRefDto> pinnedTargets = parsePinnedTargets(route, ctx);
        return new ActionResultDto(true, message, data, pinnedTargets != null && !pinnedTargets.isEmpty() ? pinnedTargets : null, null);
    }

    private List<ActionTargetRefDto> parsePinnedTargets(RestRoutingConfig.ActionRoute route, TemplateContext ctx) {
        Object template = route != null && route.getResponse() != null ? route.getResponse().getPinnedTargets() : null;
        if (template == null) {
            return List.of();
        }

        Object raw = templateEngine.apply(template, ctx);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }

        List<ActionTargetRefDto> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            try {
                ActionTargetRefDto ref = objectMapper.convertValue(item, ActionTargetRefDto.class);
                if (ref != null && StringUtils.hasText(ref.id())) {
                    out.add(ref);
                }
            } catch (Exception ignore) {
                // ignore invalid pinned target items (best-effort)
            }
        }
        return List.copyOf(out);
    }

    private Object parseJsonBody(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception ex) {
            return body;
        }
    }

    private boolean isSuccessStatus(RestRoutingConfig.ActionRoute route, int status) {
        RestRoutingConfig.Response response = route != null ? route.getResponse() : null;
        List<Integer> allowed = response != null ? response.getSuccessHttpStatus() : null;
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(status);
        }
        return status >= 200 && status < 300;
    }

    private Map<String, Object> normalizeToDataMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }

        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                out.put(entry.getKey().toString(), entry.getValue());
            }

            if (out.containsKey(LIST_ITEMS)) {
                return normalizeListPayload(out);
            }

            if (containsReservedListKeys(out)) {
                return Map.of("body", out);
            }

            return Map.copyOf(out);
        }

        if (raw instanceof List<?> list) {
            return Map.of(
                LIST_ITEMS, list,
                LIST_COUNT, list.size()
            );
        }

        return Map.of("value", raw);
    }

    private Map<String, Object> normalizeListPayload(Map<String, Object> map) {
        Object itemsRaw = map.get(LIST_ITEMS);
        Object countRaw = map.get(LIST_COUNT);
        if (!(itemsRaw instanceof List<?> items) || !(countRaw instanceof Number number)) {
            return Map.of("body", map);
        }
        int count = number.intValue();
        if (count != items.size()) {
            return Map.of("body", map);
        }
        return Map.copyOf(map);
    }

    private boolean containsReservedListKeys(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (String key : map.keySet()) {
            if (key == null) {
                continue;
            }
            String k = key.trim();
            if (LIST_ITEMS.equals(k) || LIST_COUNT.equals(k) || "_totalCount".equals(k) || "_cursor".equals(k)) {
                return true;
            }
        }
        return false;
    }

    private RestRoutingConfig.ActionRoute resolveRoute(String actionId) {
        if (config == null || config.getActions() == null || !StringUtils.hasText(actionId)) {
            return null;
        }
        return config.getActions().get(actionId.trim());
    }

    private Map<String, Object> traceMap(TraceContextDto trace) {
        if (trace == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        putIfText(out, "requestId", trace.requestId());
        putIfText(out, "conversationId", trace.conversationId());
        putIfText(out, "userId", trace.userId());
        putIfText(out, "sessionId", trace.sessionId());
        putIfText(out, "tenantId", trace.tenantId());
        return Map.copyOf(out);
    }

    private void putIfText(Map<String, Object> out, String key, String value) {
        if (out == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        out.put(key.trim(), value.trim());
    }

    private void putIfText(Map<String, String> out, String key, String value) {
        if (out == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        out.put(key.trim(), value.trim());
    }

    private String normalizeMethod(String raw) {
        String m = StringUtils.hasText(raw) ? raw.trim().toUpperCase(Locale.ROOT) : "POST";
        return switch (m) {
            case "GET", "POST", "PUT", "PATCH", "DELETE" -> m;
            default -> throw new IllegalStateException("Unsupported HTTP method: " + m);
        };
    }

    private boolean requiresBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : Map.of());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize upstream JSON body: " + ex.getMessage(), ex);
        }
    }

    private boolean shouldRetryStatus(int statusCode, List<Integer> retryOn) {
        if (statusCode <= 0) {
            return true;
        }
        if (retryOn == null || retryOn.isEmpty()) {
            return false;
        }
        return retryOn.contains(statusCode);
    }

    private boolean isRetryableException(Exception ex) {
        if (ex == null) {
            return false;
        }
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return false;
        }
        if (ex instanceof HttpTimeoutException) {
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && msg.toLowerCase(Locale.ROOT).contains("timeout");
    }

    private Duration backoffForAttempt(int initialBackoffMs, int attempt) {
        long base = Math.max(0, initialBackoffMs);
        long multiplier = Math.max(1, 1L << Math.min(8, Math.max(0, attempt - 1)));
        long ms = base * multiplier;
        return Duration.ofMillis(Math.min(ms, 2000));
    }

    private void sleep(Duration d) {
        if (d == null || d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private ActionResultDto failureFromException(Exception ex) {
        if (ex instanceof IllegalStateException || ex instanceof IllegalArgumentException) {
            return ActionResultDto.failure(ERROR_MAPPING_ERROR, "Invalid action mapping.");
        }
        if (ex instanceof HttpTimeoutException || ex instanceof java.net.SocketTimeoutException) {
            return ActionResultDto.failure(ERROR_TIMEOUT, "Upstream API timed out.");
        }
        String msg = ex != null && ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        if (msg.contains("timeout")) {
            return ActionResultDto.failure(ERROR_TIMEOUT, "Upstream API timed out.");
        }
        return ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Upstream service unavailable.");
    }

    private String errorCodeForStatus(int status) {
        if (status == 429) {
            return ERROR_RATE_LIMITED;
        }
        if (status == 408 || status == 504) {
            return ERROR_TIMEOUT;
        }
        if (status >= 500) {
            return ERROR_SERVICE_UNAVAILABLE;
        }
        return "UPSTREAM_ERROR";
    }

    private String safe(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }

    private String string(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString();
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
