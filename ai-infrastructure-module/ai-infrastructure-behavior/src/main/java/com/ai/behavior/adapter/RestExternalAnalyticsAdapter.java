package com.ai.behavior.adapter;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default REST-based adapter for external analytics providers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.behavior.providers.external", name = "enabled", havingValue = "true")
public class RestExternalAnalyticsAdapter implements ExternalAnalyticsAdapter {

    private final BehaviorModuleProperties properties;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;
    private RestTemplate cachedTemplate;

    @Override
    public List<BehaviorSignal> fetchEvents(BehaviorQuery query) {
        BehaviorModuleProperties.Providers.External config = properties.getProviders().getExternal();
        if (config.getBaseUrl() == null) {
            log.warn("External analytics base URL not configured, skipping fetch");
            return List.of();
        }

        try {
            ResponseEntity<String> response = restTemplate().exchange(
                config.getBaseUrl() + config.getQueryPath(),
                HttpMethod.POST,
                new HttpEntity<>(buildPayload(query), buildHeaders(config)),
                String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("External analytics provider responded with status {}", response.getStatusCode());
                return List.of();
            }

            return mapResponse(response.getBody());
        } catch (RestClientException ex) {
            log.warn("Failed to fetch behavior events from external provider {}", config.getBaseUrl(), ex);
            return List.of();
        }
    }

    @Override
    public String getProviderName() {
        return "external-rest";
    }

    private RestTemplate restTemplate() {
        if (cachedTemplate == null) {
            BehaviorModuleProperties.Providers.External config = properties.getProviders().getExternal();
            Duration timeout = config.getTimeout() != null ? config.getTimeout() : Duration.ofSeconds(5);
            cachedTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
        }
        return cachedTemplate;
    }

    private HttpHeaders buildHeaders(BehaviorModuleProperties.Providers.External config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getApiKey() != null) {
            headers.add("X-Api-Key", config.getApiKey());
        }
        return headers;
    }

    private Map<String, Object> buildPayload(BehaviorQuery query) {
        Map<String, Object> payload = new HashMap<>();
        if (query.getUserId() != null) {
            payload.put("userId", query.getUserId());
        }
        if (query.getSessionId() != null) {
            payload.put("sessionId", query.getSessionId());
        }
        if (query.getEventType() != null) {
            payload.put("eventType", query.getEventType().name());
        }
        if (query.getEntityType() != null) {
            payload.put("entityType", query.getEntityType());
        }
        if (query.getEntityId() != null) {
            payload.put("entityId", query.getEntityId());
        }
        if (query.getStartTime() != null) {
            payload.put("startTime", query.getStartTime());
        }
        if (query.getEndTime() != null) {
            payload.put("endTime", query.getEndTime());
        }
        payload.put("limit", query.getLimit());
        payload.put("ascending", query.isAscending());
        if (!query.safeMetadataEquals().isEmpty()) {
            payload.put("metadataEquals", query.safeMetadataEquals());
        }
        return payload;
    }

    private List<BehaviorSignal> mapResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode eventsNode = root.isArray() ? root : root.path("events");
            List<BehaviorSignal> events = new ArrayList<>();
            if (!eventsNode.isArray()) {
                return events;
            }
            for (JsonNode node : eventsNode) {
                events.add(objectMapper.treeToValue(node, BehaviorSignal.class));
            }
            return events;
        } catch (Exception ex) {
            log.warn("Failed to parse response from external analytics provider", ex);
            return List.of();
        }
    }
}
