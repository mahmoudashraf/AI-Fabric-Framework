package com.loomai.demo.dealership.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class RuntimeConnectorOperationsClient {

    static final String READ_SCOPE = "runtime:connector:read";
    static final String WRITE_SCOPE = "runtime:connector:write";

    private final DealershipDemoProperties properties;
    private final RuntimePrivateAssertionSigner signer;
    private final RestClient restClient;

    public RuntimeConnectorOperationsClient(DealershipDemoProperties properties,
                                            RuntimePrivateAssertionSigner signer,
                                            RestClient.Builder builder) {
        this.properties = properties;
        this.signer = signer;
        this.restClient = builder.build();
    }

    public JsonNode sourceStatus() {
        return restClient.get()
            .uri(sourceUrl())
            .headers(headers -> applyPrivateHeaders(headers, READ_SCOPE))
            .retrieve()
            .body(JsonNode.class);
    }

    public JsonNode reconcileSource() {
        return restClient.post()
            .uri(sourceUrl() + "/reconcile")
            .headers(headers -> applyPrivateHeaders(headers, WRITE_SCOPE))
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .retrieve()
            .body(JsonNode.class);
    }

    public WebhookEventEvidence awaitWebhookEvent(String eventId, Duration timeout) {
        if (!StringUtils.hasText(eventId) || !eventId.trim().matches("[A-Za-z0-9._:-]{1,240}")) {
            throw new IllegalArgumentException("Provider event id is invalid.");
        }
        Duration boundedTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
            ? Duration.ofSeconds(10)
            : timeout.compareTo(Duration.ofSeconds(30)) > 0 ? Duration.ofSeconds(30) : timeout;
        long deadline = System.nanoTime() + boundedTimeout.toNanos();
        WebhookEventEvidence latest = new WebhookEventEvidence("PENDING", 0, null);
        do {
            JsonNode events = restClient.get()
                .uri(webhookEventsUrl())
                .headers(headers -> applyPrivateHeaders(headers, READ_SCOPE))
                .retrieve()
                .body(JsonNode.class);
            if (events != null && events.isArray()) {
                for (JsonNode event : events) {
                    if (eventId.trim().equals(event.path("eventId").asText())) {
                        latest = new WebhookEventEvidence(
                            event.path("status").asText("PENDING"),
                            event.path("attemptCount").asInt(0),
                            event.path("errorClass").isTextual() ? event.path("errorClass").asText() : null
                        );
                        if (Set.of("COMPLETED", "DEAD_LETTER").contains(latest.status())) {
                            return latest;
                        }
                    }
                }
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for provider event reconciliation.", ex);
            }
        } while (true);
        return latest;
    }

    public String configuredSourceId() {
        String sourceId = properties.getRuntime().getIntegrationSourceId();
        if (!StringUtils.hasText(sourceId) || !sourceId.trim().matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalStateException("LoomAI runtime integration source is not configured.");
        }
        return sourceId.trim();
    }

    public String configuredWebhookSourceId() {
        String sourceId = properties.getRuntime().getIntegrationWebhookSourceId();
        if (!StringUtils.hasText(sourceId) || !sourceId.trim().matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalStateException("LoomAI runtime integration webhook source is not configured.");
        }
        return sourceId.trim();
    }

    private String sourceUrl() {
        requireEnabled();
        return baseUrl() + "/api/admin/connector/integrations/sources/"
            + UriUtils.encodePathSegment(configuredSourceId(), StandardCharsets.UTF_8);
    }

    private String webhookEventsUrl() {
        requireEnabled();
        return baseUrl() + "/api/admin/connector/integrations/webhooks/"
            + UriUtils.encodePathSegment(configuredWebhookSourceId(), StandardCharsets.UTF_8)
            + "/events";
    }

    private void applyPrivateHeaders(HttpHeaders headers, String scope) {
        DealershipDemoProperties.PrivateAccess access = properties.getRuntime().getPrivateAccess();
        if (!StringUtils.hasText(access.getTrustedApiKeyHeader()) || !StringUtils.hasText(access.getTrustedApiKey())) {
            throw new IllegalStateException("Runtime trusted-backend authentication is not configured.");
        }
        if (!StringUtils.hasText(access.getAuthorizationHeader())) {
            throw new IllegalStateException("Runtime private assertion header is not configured.");
        }
        headers.set(access.getTrustedApiKeyHeader().trim(), access.getTrustedApiKey().trim());
        headers.set(access.getAuthorizationHeader().trim(), signer.authorizationValue(List.of(scope)));
        headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    private String baseUrl() {
        String value = properties.getRuntime().getBaseUrl().trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void requireEnabled() {
        if (!properties.getRuntime().isEnabled() || !StringUtils.hasText(properties.getRuntime().getBaseUrl())) {
            throw new IllegalStateException("LoomAI runtime integration is not configured.");
        }
    }

    public record WebhookEventEvidence(String status, int attemptCount, String errorClass) {
    }
}
