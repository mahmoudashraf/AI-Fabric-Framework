package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.ConversationChannelConnectorProperties;
import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(
    prefix = "rest-connector.conversation-channel",
    name = "enabled",
    havingValue = "true"
)
public class ConversationChannelDispatchWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationChannelDispatchWorker.class);

    private final ConversationChannelConnectorProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public ConversationChannelDispatchWorker(
        ConversationChannelConnectorProperties properties,
        RestConnectorServiceProperties serviceProperties,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        validate(serviceProperties);
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
            .build();
    }

    @Scheduled(fixedDelayString = "${REST_CONNECTOR_CONVERSATION_CHANNEL_POLL_MS:1000}")
    public void deliverNext() {
        try {
            JsonNode claim = claim();
            if (claim == null) {
                return;
            }
            String dispatchId = claim.path("dispatchId").asText();
            int attempt = claim.path("attempt").asInt(1);
            JsonNode dispatch = claim.path("dispatch");
            Map<String, Object> outcome = deliver(dispatch);
            acknowledge(dispatchId, attempt, outcome);
        } catch (Exception exception) {
            LOGGER.warn("Conversation channel dispatch tick failed: {}", exception.getMessage());
        }
    }

    private JsonNode claim() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "workerId", properties.getWorkerId(),
            "leaseSeconds", properties.getLeaseSeconds()
        ));
        HttpResponse<String> response = send(
            runtimeUrl("/api/private/conversation-channel-dispatches/claim"),
            body,
            properties.getRuntimeApiKeyHeader(),
            properties.getRuntimeApiKey()
        );
        if (response.statusCode() == 204) {
            return null;
        }
        require2xx(response, "claim conversation channel dispatch");
        return objectMapper.readTree(response.body());
    }

    private Map<String, Object> deliver(JsonNode dispatch) {
        try {
            HttpResponse<String> response = send(
                providerUrl("/api/conversation-channel/v1/dispatches"),
                objectMapper.writeValueAsString(dispatch),
                properties.getProviderApiKeyHeader(),
                properties.getProviderApiKey()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429
                    || properties.getAdditionalRetryableProviderHttpStatuses().contains(response.statusCode());
                return failure(
                    "PROVIDER_HTTP_" + response.statusCode(),
                    "Conversation channel provider rejected the dispatch.",
                    retryable
                );
            }
            JsonNode result = objectMapper.readTree(response.body());
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("accepted", result.path("accepted").asBoolean(true));
            outcome.put("externalConversationRef", text(result, "externalConversationRef"));
            outcome.put("providerMessageRef", text(result, "providerMessageRef"));
            outcome.put("code", text(result, "code"));
            outcome.put("message", text(result, "message"));
            outcome.put("retryable", result.path("retryable").asBoolean(false));
            return outcome;
        } catch (Exception exception) {
            return failure("PROVIDER_UNAVAILABLE", exception.getMessage(), true);
        }
    }

    private void acknowledge(String dispatchId, int attempt, Map<String, Object> outcome) throws Exception {
        HttpResponse<String> response = send(
            runtimeUrl("/api/private/conversation-channel-dispatches/" + dispatchId + "/outcomes/" + attempt),
            objectMapper.writeValueAsString(outcome),
            properties.getRuntimeApiKeyHeader(),
            properties.getRuntimeApiKey()
        );
        require2xx(response, "acknowledge conversation channel dispatch");
    }

    private HttpResponse<String> send(URI uri, String body, String header, String value) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header(header, value)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void require2xx(HttpResponse<String> response, String operation) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Failed to " + operation + "; HTTP " + response.statusCode() + ".");
        }
    }

    private Map<String, Object> failure(String code, String message, boolean retryable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", false);
        result.put("code", code);
        result.put("message", message == null ? "Conversation channel delivery failed." : message);
        result.put("retryable", retryable);
        return result;
    }

    private URI runtimeUrl(String path) { return URI.create(trimBase(properties.getRuntimeBaseUrl()) + path); }
    private URI providerUrl(String path) { return URI.create(trimBase(properties.getProviderBaseUrl()) + path); }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private String trimBase(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private void validate(RestConnectorServiceProperties serviceProperties) {
        require(properties.getBindingId(), "binding ID");
        require(properties.getRuntimeBaseUrl(), "runtime base URL");
        require(properties.getRuntimeApiKey(), "runtime API key");
        require(properties.getProviderBaseUrl(), "provider base URL");
        require(properties.getProviderApiKey(), "provider API key");
        requireSecret(properties.getRuntimeCallbackSigningSecret(), "runtime callback signing secret");
        requireSecret(properties.getProviderWebhookSigningSecret(), "provider webhook signing secret");
        for (Integer status : properties.getAdditionalRetryableProviderHttpStatuses()) {
            if (status == null || status < 400 || status > 499 || status == 401 || status == 403) {
                throw new IllegalStateException(
                    "Additional provider retry statuses must be 4xx failures other than authentication or authorization denials."
                );
            }
        }
        if (!serviceProperties.getPersistence().isEnabled()) {
            throw new IllegalStateException(
                "Conversation channel delivery requires rest-connector.persistence.enabled=true."
            );
        }
        if (properties.getCallbackClockSkewSeconds() < 30 || properties.getCallbackClockSkewSeconds() > 900) {
            throw new IllegalStateException(
                "Conversation channel callback clock skew must be between 30 and 900 seconds."
            );
        }
    }

    private void require(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Conversation channel " + name + " is required.");
        }
    }

    private void requireSecret(String value, String name) {
        if (value == null || value.length() < 32) {
            throw new IllegalStateException("Conversation channel " + name + " must contain at least 32 characters.");
        }
    }
}
