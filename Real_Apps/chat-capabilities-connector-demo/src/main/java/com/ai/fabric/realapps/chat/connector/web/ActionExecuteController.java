package com.ai.fabric.realapps.chat.connector.web;

import com.ai.fabric.realapps.chat.connector.config.ConnectorAuthProperties;
import com.ai.fabric.realapps.chat.connector.service.ConnectorActionDispatcher;
import com.ai.fabric.realapps.chat.connector.service.InMemoryIdempotencyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/actions")
@RequiredArgsConstructor
public class ActionExecuteController {

    private final ConnectorAuthProperties authProperties;
    private final InMemoryIdempotencyStore idempotencyStore;
    private final ConnectorActionDispatcher dispatcher;

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody(required = false) Map<String, Object> body,
                                                      @RequestHeader Map<String, String> headers) {
        if (authProperties != null && authProperties.enabled()) {
            String header = authProperties.apiKeyHeader();
            String actual = header != null ? headerValue(headers, header) : null;
            if (!StringUtils.hasText(actual) || !authProperties.apiKey().trim().equals(actual.trim())) {
                return ResponseEntity.ok(failure("AUTH_FAILED", "Authentication failed"));
            }
        }

        if (body == null || body.isEmpty()) {
            return ResponseEntity.ok(failure("INVALID_REQUEST", "Request body is required"));
        }

        String actionId = readString(body.get("actionId"));
        if (!StringUtils.hasText(actionId)) {
            return ResponseEntity.ok(failure("INVALID_REQUEST", "actionId is required"));
        }

        Map<String, Object> params = readMap(body.get("params"));
        String idempotencyKey = readString(body.get("idempotencyKey"));
        ConnectorActionDispatcher.Trace trace = readTrace(body.get("trace"));

        if (StringUtils.hasText(idempotencyKey)) {
            Map<String, Object> cached = idempotencyStore.get(actionId, idempotencyKey);
            if (cached != null && !cached.isEmpty()) {
                return ResponseEntity.ok(cached);
            }
        }

        Map<String, Object> result = dispatcher.dispatch(actionId, params, trace);

        if (StringUtils.hasText(idempotencyKey) && result != null && !result.isEmpty()) {
            idempotencyStore.putIfAbsent(actionId, idempotencyKey, result);
        }

        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                out.put(entry.getKey().toString(), entry.getValue());
            }
            return out;
        }
        return Map.of();
    }

    private ConnectorActionDispatcher.Trace readTrace(Object raw) {
        Map<String, Object> map = readMap(raw);
        return new ConnectorActionDispatcher.Trace(
            readString(map.get("requestId")),
            readString(map.get("conversationId")),
            readString(map.get("userId")),
            readString(map.get("sessionId"))
        );
    }

    private String readString(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.toString();
        return StringUtils.hasText(s) ? s : null;
    }

    private String headerValue(Map<String, String> headers, String headerName) {
        if (headers == null || headers.isEmpty() || !StringUtils.hasText(headerName)) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (headerName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> failure(String errorCode, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", false);
        if (StringUtils.hasText(message)) {
            out.put("message", message);
        }
        if (StringUtils.hasText(errorCode)) {
            out.put("errorCode", errorCode);
        }
        return out;
    }
}
