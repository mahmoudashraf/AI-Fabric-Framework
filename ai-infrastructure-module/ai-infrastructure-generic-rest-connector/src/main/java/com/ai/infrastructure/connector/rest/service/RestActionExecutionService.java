package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.api.ActionTargetRefDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.template.TemplateEngine;
import com.ai.infrastructure.connector.rest.util.UrlBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class RestActionExecutionService {

    private static final String ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    private static final String ERROR_ACTION_NOT_SUPPORTED = "ACTION_NOT_SUPPORTED";
    private static final String ERROR_TIMEOUT = "TIMEOUT";
    private static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private final RestRoutingConfig config;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;
    private final IdempotencyStore idempotencyStore;

    private final HttpClient httpClient;

    public RestActionExecutionService(RestRoutingConfig config,
                                     TemplateEngine templateEngine,
                                     ObjectMapper objectMapper,
                                     IdempotencyStore idempotencyStore) {
        this.config = config;
        this.templateEngine = templateEngine;
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;

        Duration connectTimeout = Duration.ofMillis(Math.max(100, config != null && config.getConnector() != null && config.getConnector().getHttp() != null
            ? config.getConnector().getHttp().getConnectTimeoutMs()
            : 2000));

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
    }

    public ActionResultDto execute(ActionExecuteRequestDto request) {
        long start = System.currentTimeMillis();

        String actionId = request != null ? request.actionId() : null;
        if (!StringUtils.hasText(actionId)) {
            return ActionResultDto.failure(ERROR_INVALID_REQUEST, "actionId is required.");
        }

        RestRoutingConfig.ActionRoute route = config != null && config.getActions() != null
            ? config.getActions().get(actionId.trim())
            : null;

        if (route == null) {
            return ActionResultDto.failure(ERROR_ACTION_NOT_SUPPORTED, "Action is not supported.");
        }

        if (config != null && config.getConnector() != null && config.getConnector().getIdempotency() != null
            && config.getConnector().getIdempotency().isEnabled()
            && StringUtils.hasText(request.idempotencyKey())) {
            return idempotencyStore.executeIdempotent(actionId.trim(), request.idempotencyKey().trim(), () -> executeOnce(route, request, start));
        }

        return executeOnce(route, request, start);
    }

    private ActionResultDto executeOnce(RestRoutingConfig.ActionRoute route, ActionExecuteRequestDto request, long startMs) {
        ResolvedUpstream resolved = resolveUpstream(route);

        Map<String, Object> ctx = templateEngine.contextFor(request, resolved);

        URI uri = buildUpstreamUri(resolved, route, ctx);
        String method = StringUtils.hasText(route.getMethod()) ? route.getMethod().trim().toUpperCase(Locale.ROOT) : "POST";

        int timeoutMs = route.getTimeoutMs() != null
            ? route.getTimeoutMs()
            : (config != null && config.getConnector() != null && config.getConnector().getHttp() != null ? config.getConnector().getHttp().getTimeoutMs() : 8000);
        Duration timeout = Duration.ofMillis(Math.max(100, timeoutMs));

        Map<String, String> headers = buildHeaders(route, request);
        Object requestBodyTemplate = route.getRequest() != null ? route.getRequest().getBody() : null;
        Object resolvedBody = requestBodyTemplate != null ? templateEngine.resolve(requestBodyTemplate, ctx) : null;
        String jsonBody = resolvedBody != null ? writeJson(resolvedBody) : "";

        HttpRequest httpRequest = buildHttpRequest(uri, method, headers, jsonBody, timeout);

        RestRoutingConfig.Retry retry = config != null && config.getConnector() != null && config.getConnector().getHttp() != null
            ? config.getConnector().getHttp().getRetry()
            : null;

        boolean canRetry = isSafeMethod(method) || StringUtils.hasText(request.idempotencyKey());
        int maxAttempts = retry != null && retry.isEnabled() && canRetry ? Math.max(1, retry.getMaxAttempts()) : 1;
        int backoffMs = retry != null ? Math.max(0, retry.getBackoffMs()) : 0;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                ActionResultDto out = normalizeResponse(route, request, response, ctx, startMs);

                if (attempt < maxAttempts && shouldRetryResponse(retry, response)) {
                    sleep(backoffMs, attempt);
                    continue;
                }

                return out;
            } catch (InterruptedException ie) {
                // Preserve the interrupt flag for cooperative cancellation; do not retry.
                Thread.currentThread().interrupt();
                return ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Upstream request interrupted.");
            } catch (Exception ex) {
                if (attempt < maxAttempts && shouldRetryException(retry, ex)) {
                    sleep(backoffMs, attempt);
                    continue;
                }
                String msg = isTimeout(ex) ? "Upstream request timed out." : "Upstream service unavailable.";
                return ActionResultDto.failure(isTimeout(ex) ? ERROR_TIMEOUT : ERROR_SERVICE_UNAVAILABLE, msg);
            }
        }

        return ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Upstream service unavailable.");
    }

    private boolean shouldRetryResponse(RestRoutingConfig.Retry retry, HttpResponse<String> response) {
        if (retry == null || !retry.isEnabled() || response == null) {
            return false;
        }
        int status = response.statusCode();
        List<Integer> retryOn = retry.getRetryOn() != null ? retry.getRetryOn() : List.of();
        return retryOn.contains(status);
    }

    private boolean shouldRetryException(RestRoutingConfig.Retry retry, Exception ex) {
        if (retry == null || !retry.isEnabled() || ex == null) {
            return false;
        }
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return false;
        }
        return isTimeout(ex);
    }

    private boolean isTimeout(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return false;
        }
        String msg = ex.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("timed out") || msg.contains("timeout");
    }

    private void sleep(int backoffMs, int attempt) {
        if (backoffMs <= 0) {
            return;
        }
        long multiplier = Math.max(1L, 1L << Math.min(8, Math.max(0, attempt - 1)));
        long delay = Math.min((long) backoffMs * multiplier, 5000L);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private ActionResultDto normalizeResponse(RestRoutingConfig.ActionRoute route,
                                              ActionExecuteRequestDto request,
                                              HttpResponse<String> response,
                                              Map<String, Object> ctx,
                                              long startMs) {
        int status = response != null ? response.statusCode() : 0;
        String rawBody = response != null ? response.body() : null;

        RestRoutingConfig.Response responseConfig = route.getResponse();
        boolean success = isSuccessStatus(responseConfig, status);

        Object parsedBody = parseJsonLenient(rawBody);
        Map<String, Object> bodyCtx = new LinkedHashMap<>(ctx);
        bodyCtx.put("status", status);
        bodyCtx.put("body", parsedBody);

        Map<String, Object> data;
        if (responseConfig != null && responseConfig.getResult() != null) {
            Object templated = templateEngine.resolve(responseConfig.getResult(), bodyCtx);
            data = normalizeToDataMap(templated);
        } else {
            data = normalizeToDataMap(parsedBody);
        }

        String message;
        if (responseConfig != null && StringUtils.hasText(responseConfig.getMessage())) {
            Object templated = templateEngine.resolve(responseConfig.getMessage(), bodyCtx);
            message = templated != null ? templated.toString() : null;
        } else {
            message = success ? "Action executed." : "Upstream call failed.";
        }

        List<ActionTargetRefDto> pinnedTargets = List.of();
        if (responseConfig != null && responseConfig.getPinnedTargets() != null) {
            Object templated = templateEngine.resolve(responseConfig.getPinnedTargets(), bodyCtx);
            pinnedTargets = parsePinnedTargets(templated);
        }

        String errorCode = null;
        if (!success) {
            errorCode = status > 0 ? ("UPSTREAM_HTTP_" + status) : ERROR_SERVICE_UNAVAILABLE;
        }

        long tookMs = System.currentTimeMillis() - startMs;
        log.info("Action '{}' -> {} {} (status={}, success={}, tookMs={})",
            request != null ? request.actionId() : "unknown",
            response != null ? "upstream" : "upstream",
            route.getMethod(),
            status,
            success,
            tookMs
        );

        return new ActionResultDto(success, message, data, pinnedTargets, errorCode);
    }

    private boolean isSuccessStatus(RestRoutingConfig.Response responseConfig, int status) {
        List<Integer> ok = responseConfig != null ? responseConfig.getSuccessHttpStatus() : null;
        if (ok != null && !ok.isEmpty()) {
            return ok.contains(status);
        }
        return status >= 200 && status < 300;
    }

    private List<ActionTargetRefDto> parsePinnedTargets(Object templated) {
        if (templated == null) {
            return List.of();
        }
        try {
            if (templated instanceof List<?> list) {
                List<ActionTargetRefDto> out = new ArrayList<>();
                for (Object item : list) {
                    if (item == null) {
                        continue;
                    }
                    ActionTargetRefDto ref = objectMapper.convertValue(item, ActionTargetRefDto.class);
                    if (ref != null) {
                        out.add(ref);
                    }
                }
                return Collections.unmodifiableList(out);
            }
            return List.of(objectMapper.convertValue(templated, ActionTargetRefDto.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Object parseJsonLenient(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody, Object.class);
        } catch (Exception ex) {
            return Map.of("raw", rawBody);
        }
    }

    private Map<String, Object> normalizeToDataMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k == null) {
                    return;
                }
                out.put(k.toString(), v);
            });
            return Collections.unmodifiableMap(out);
        }
        if (value instanceof List<?> list) {
            return Map.of("_items", list, "_count", list.size());
        }
        return Map.of("value", value);
    }

    private HttpRequest buildHttpRequest(URI uri,
                                         String method,
                                         Map<String, String> headers,
                                         String jsonBody,
                                         Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(timeout);

        String m = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "POST";
        if ("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m)) {
            builder.method(m, HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "", StandardCharsets.UTF_8));
        } else {
            builder.method(m, HttpRequest.BodyPublishers.noBody());
        }

        builder.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (!StringUtils.hasText(k) || v == null) {
                    return;
                }
                builder.header(k.trim(), v);
            });
        }
        return builder.build();
    }

    private URI buildUpstreamUri(ResolvedUpstream resolved, RestRoutingConfig.ActionRoute route, Map<String, Object> ctx) {
        String baseUrl = resolved.baseUrl;
        String path = resolved.path;

        String url = UrlBuilder.join(baseUrl, path);

        Map<String, Object> queryTemplate = route.getRequest() != null ? route.getRequest().getQuery() : null;
        if (queryTemplate == null || queryTemplate.isEmpty()) {
            return URI.create(url);
        }

        Object resolvedQueryObj = templateEngine.resolve(queryTemplate, ctx);
        Map<String, Object> resolvedQuery = resolvedQueryObj instanceof Map<?, ?> map ? toStringObjectMap(map) : Map.of();
        if (resolvedQuery.isEmpty()) {
            return URI.create(url);
        }

        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : resolvedQuery.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append("&");
            }
            first = false;
            sb.append(URLEncoder.encode(entry.getKey().trim(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        return URI.create(sb.toString());
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k == null) {
                return;
            }
            out.put(k.toString(), v);
        });
        return out;
    }

    private Map<String, String> buildHeaders(RestRoutingConfig.ActionRoute route, ActionExecuteRequestDto request) {
        Map<String, String> out = new LinkedHashMap<>();

        if (route.getHeaders() != null) {
            route.getHeaders().forEach((k, v) -> {
                if (!StringUtils.hasText(k) || v == null) {
                    return;
                }
                out.put(k.trim(), v);
            });
        }

        RestRoutingConfig.Connector connector = config != null ? config.getConnector() : null;
        RestRoutingConfig.Upstream upstream = connector != null ? connector.getUpstream() : null;
        RestRoutingConfig.UpstreamAuth auth = upstream != null ? upstream.getAuth() : null;
        if (auth != null && auth.getType() == RestRoutingConfig.UpstreamAuth.AuthType.API_KEY) {
            if (StringUtils.hasText(auth.getHeader()) && StringUtils.hasText(auth.getValue())) {
                out.put(auth.getHeader().trim(), auth.getValue().trim());
            }
        }

        if (request != null && request.trace() != null) {
            if (StringUtils.hasText(request.trace().requestId())) {
                out.put("X-AIFABRIC-REQUEST-ID", request.trace().requestId().trim());
            }
            if (StringUtils.hasText(request.trace().conversationId())) {
                out.put("X-AIFABRIC-CONVERSATION-ID", request.trace().conversationId().trim());
            }
            if (StringUtils.hasText(request.trace().userId())) {
                out.put("X-AIFABRIC-USER-ID", request.trace().userId().trim());
            }
            if (StringUtils.hasText(request.trace().sessionId())) {
                out.put("X-AIFABRIC-SESSION-ID", request.trace().sessionId().trim());
            }
        }

        return out;
    }

    private ResolvedUpstream resolveUpstream(RestRoutingConfig.ActionRoute route) {
        RestRoutingConfig.Connector connector = config != null ? config.getConnector() : null;
        RestRoutingConfig.Upstream upstream = connector != null ? connector.getUpstream() : null;

        if (StringUtils.hasText(route.getUrl())) {
            URI uri = URI.create(route.getUrl().trim());
            String base = uri.getScheme() + "://" + uri.getAuthority();
            String p = uri.getRawPath();
            String query = uri.getRawQuery();
            String fullPath = (p != null ? p : "") + (StringUtils.hasText(query) ? "?" + query : "");
            return new ResolvedUpstream(base, fullPath);
        }

        String baseUrl = upstream != null ? upstream.getBaseUrl() : null;
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("connector.upstream.base-url is required when using action route path.");
        }

        String path = route.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalStateException("Action route must set either url or path.");
        }

        return new ResolvedUpstream(baseUrl.trim(), path.trim());
    }

    private boolean isSafeMethod(String method) {
        String m = method != null ? method.trim().toUpperCase(Locale.ROOT) : "";
        return "GET".equals(m) || "HEAD".equals(m);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize upstream request JSON: " + ex.getMessage(), ex);
        }
    }

    public record ResolvedUpstream(String baseUrl, String path) {
    }
}
