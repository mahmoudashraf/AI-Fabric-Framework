package com.ai.infrastructure.relay.service;

import com.ai.infrastructure.relay.api.TraceContextDto;
import com.ai.infrastructure.relay.config.RelayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class AuditLogger {

    private final RelayProperties properties;

    public AuditLogger(RelayProperties properties) {
        this.properties = properties;
    }

    public void logAction(String actionId,
                          TraceContextDto trace,
                          boolean success,
                          String errorCode,
                          long latencyMs) {
        if (properties == null || properties.getAudit() == null || !properties.getAudit().isEnabled()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ts", Instant.now().toString());
        event.put("type", "action");
        event.put("actionId", actionId);
        event.put("requestId", trace != null ? trace.requestId() : null);
        event.put("conversationId", trace != null ? trace.conversationId() : null);
        event.put("userId", trace != null ? trace.userId() : null);
        event.put("sessionId", trace != null ? trace.sessionId() : null);
        event.put("success", success);
        if (StringUtils.hasText(errorCode)) {
            event.put("errorCode", errorCode);
        }
        event.put("latencyMs", latencyMs);
        log.info("relay_audit {}", event);
    }

    public void logRetrieval(String vectorSpace,
                             TraceContextDto trace,
                             boolean success,
                             String errorCode,
                             long latencyMs) {
        if (properties == null || properties.getAudit() == null || !properties.getAudit().isEnabled()) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("ts", Instant.now().toString());
        event.put("type", "retrieval");
        event.put("vectorSpace", vectorSpace);
        event.put("requestId", trace != null ? trace.requestId() : null);
        event.put("conversationId", trace != null ? trace.conversationId() : null);
        event.put("userId", trace != null ? trace.userId() : null);
        event.put("sessionId", trace != null ? trace.sessionId() : null);
        event.put("success", success);
        if (StringUtils.hasText(errorCode)) {
            event.put("errorCode", errorCode);
        }
        event.put("latencyMs", latencyMs);
        log.info("relay_audit {}", event);
    }
}

