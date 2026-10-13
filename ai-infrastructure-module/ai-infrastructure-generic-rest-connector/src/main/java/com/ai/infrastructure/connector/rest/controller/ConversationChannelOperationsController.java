package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.config.ConversationChannelConnectorProperties;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conversation-channel")
@ConditionalOnProperty(
    prefix = "rest-connector.conversation-channel",
    name = "enabled",
    havingValue = "true"
)
public class ConversationChannelOperationsController {

    private final ConversationChannelConnectorProperties properties;
    private final IntegrationStateRepository repository;

    public ConversationChannelOperationsController(
        ConversationChannelConnectorProperties properties,
        IntegrationStateRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> overview() {
        String sourceId = "conversation-channel:" + properties.getBindingId();
        List<IntegrationStateRepository.WebhookEvent> events = repository.recentEvents(sourceId, 100);
        Map<String, Long> counts = new LinkedHashMap<>();
        events.forEach(event -> counts.merge(event.status(), 1L, Long::sum));
        return Map.of(
            "enabled", true,
            "bindingId", properties.getBindingId(),
            "providerConfigured", properties.getProviderBaseUrl() != null && !properties.getProviderBaseUrl().isBlank(),
            "runtimeConfigured", properties.getRuntimeBaseUrl() != null && !properties.getRuntimeBaseUrl().isBlank(),
            "callbackRecoveryOwner", "PROVIDER",
            "manualCallbackReplaySupported", false,
            "rejectedCallbackCount", repository.eventRejectionCount(sourceId),
            "callbackCounts", Map.copyOf(counts),
            "recentCallbacks", events.stream().map(this::summary).toList()
        );
    }

    private Map<String, Object> summary(IntegrationStateRepository.WebhookEvent event) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("eventId", event.eventId());
        value.put("eventType", event.eventType());
        value.put("status", event.status());
        value.put("attemptCount", event.attemptCount());
        value.put("duplicateCount", event.duplicateCount());
        value.put("errorClass", event.errorClass());
        value.put("receivedAt", event.receivedAt());
        value.put("updatedAt", event.updatedAt());
        value.values().removeIf(java.util.Objects::isNull);
        return Map.copyOf(value);
    }
}
