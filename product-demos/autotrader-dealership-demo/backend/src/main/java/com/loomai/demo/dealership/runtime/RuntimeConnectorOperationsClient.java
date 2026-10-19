package com.loomai.demo.dealership.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class RuntimeConnectorOperationsClient {

    static final String READ_SCOPE = "runtime:connector:read";
    static final String WRITE_SCOPE = "runtime:connector:write";

    private final DealershipDemoProperties properties;
    private final RuntimePrivateAssertionSigner signer;
    private final RestClient restClient;

    @Value("${dealership.runtime.integration-connection-profile-ref:}")
    private String expectedConnectionProfileRef = "";
    @Value("${dealership.runtime.integration-resource-binding-ref:}")
    private String expectedResourceBindingRef = "";
    @Value("${dealership.runtime.integration-source-version:}")
    private String expectedSourceVersion = "";
    @Value("${dealership.runtime.integration-min-records:1}")
    private int minimumRecords = 1;
    @Value("${dealership.runtime.integration-max-age-seconds:600}")
    private long maxAgeSeconds = 600;

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
        return reconcileSource("dealership-reconcile-" + UUID.randomUUID());
    }

    public JsonNode reconcileSource(String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[!-~]{1,128}")) {
            throw new IllegalArgumentException("A bounded reconciliation idempotency key is required.");
        }
        return restClient.post()
            .uri(sourceUrl() + "/reconcile")
            .headers(headers -> applyPrivateHeaders(headers, WRITE_SCOPE))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .retrieve()
            .body(JsonNode.class);
    }

    public SourceEvidence initializeSource(String operationFingerprint) {
        requireReadinessConfiguration();
        if (operationFingerprint == null || !operationFingerprint.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("An initialization operation fingerprint is required.");
        }
        try {
            JsonNode completed = reconcileSource("receiver-init-" + operationFingerprint);
            Instant completedAt = verifyState(completed);
            SourceEvidence observed = verifySource(sourceStatus());
            if (observed.observedAt().isBefore(completedAt)) throw notReady();
            return observed;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Connector indexing evidence is unavailable.");
        }
    }

    public SourceEvidence currentSourceEvidence() {
        requireReadinessConfiguration();
        try {
            return verifySource(sourceStatus());
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Connector indexing evidence is unavailable.");
        }
    }

    private SourceEvidence verifySource(JsonNode source) {
        if (source == null || !configuredSourceId().equals(source.path("sourceId").asText())
            || !source.path("enabled").isBoolean() || !source.path("enabled").booleanValue()
            || !expectedConnectionProfileRef.equals(source.path("connectionProfileRef").asText())
            || !expectedResourceBindingRef.equals(source.path("protectedResourceBindingRef").asText())
            || !properties.getRuntime().getInventoryVectorSpace().equals(source.path("vectorSpace").asText())
            || !"READY".equals(source.path("preflightState").asText())
            || !"CURRENT".equals(source.path("freshnessState").asText())
            || !source.path("work").isArray()) throw notReady();
        JsonNode state = source.path("state");
        Instant successAt = verifyState(state);
        Instant startedAt = instant(state, "lastStartedAt");
        for (JsonNode work : source.path("work")) {
            if (!configuredSourceId().equals(work.path("sourceId").asText())) throw notReady();
            Instant updatedAt = instant(work, "updatedAt");
            String status = work.path("status").asText();
            // A new successful baseline supersedes old terminal failures, not pending work.
            boolean recoveredFailure = Set.of("FAILED", "FAILED_PERMANENT", "CANCELLED", "DEAD_LETTER").contains(status)
                && updatedAt.isBefore(startedAt);
            if (!recoveredFailure && (!"COMPLETED".equals(status) || hasError(work, "errorCode"))) throw notReady();
        }
        JsonNode counts = state.path("counts");
        return new SourceEvidence(configuredSourceId(), expectedConnectionProfileRef, expectedResourceBindingRef,
            expectedSourceVersion, successAt, count(counts, "indexedCount"), count(counts, "completedWorkCount"),
            digest(source.toString()));
    }

    private Instant verifyState(JsonNode state) {
        if (state == null || !configuredSourceId().equals(state.path("sourceId").asText())
            || !"COMPLETED".equals(state.path("status").asText())
            || !expectedSourceVersion.equals(state.path("sourceVersion").asText())
            || hasError(state, "errorClass") || hasError(state, "errorMessage")) throw notReady();
        JsonNode counts = state.path("counts");
        long sourceCount = count(counts, "sourceCount");
        long normalized = count(counts, "normalizedCount");
        long indexed = count(counts, "indexedCount");
        long deleted = count(counts, "deletedCount");
        long accepted = count(counts, "acceptedWorkCount");
        long completed = count(counts, "completedWorkCount");
        if (count(counts, "failedWorkCount") != 0 || sourceCount < minimumRecords || indexed < minimumRecords
            || normalized < minimumRecords || accepted < normalized || accepted != completed
            || indexed > completed || deleted != completed - indexed) throw notReady();
        Instant started = instant(state, "lastStartedAt");
        Instant success = instant(state, "lastSuccessAt");
        Instant updated = instant(state, "updatedAt");
        Instant now = Instant.now();
        if (success.isBefore(started) || updated.isBefore(success)
            || success.isBefore(now.minusSeconds(maxAgeSeconds)) || updated.isAfter(now.plusSeconds(5))) throw notReady();
        if (!state.path("lastErrorAt").isNull() && !state.path("lastErrorAt").isMissingNode()
            && instant(state, "lastErrorAt").isAfter(success)) throw notReady();
        return success;
    }

    private void requireReadinessConfiguration() {
        if (!StringUtils.hasText(expectedConnectionProfileRef) || !StringUtils.hasText(expectedResourceBindingRef)
            || !StringUtils.hasText(expectedSourceVersion) || minimumRecords < 1 || maxAgeSeconds < 1
            || maxAgeSeconds > 86400) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Scoped connector readiness requirements are not configured.");
        }
    }

    private long count(JsonNode counts, String field) {
        JsonNode value = counts.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw notReady();
        return value.longValue();
    }

    private Instant instant(JsonNode node, String field) {
        try {
            return Instant.parse(node.path(field).textValue());
        } catch (RuntimeException ex) {
            throw notReady();
        }
    }

    private boolean hasError(JsonNode node, String field) {
        JsonNode error = node.path(field);
        return !error.isMissingNode() && !error.isNull() && (!error.isTextual() || StringUtils.hasText(error.textValue()));
    }

    private ResponseStatusException notReady() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "CONNECTOR_SCOPED_INDEXING_NOT_READY");
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
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

    public record SourceEvidence(String sourceId, String connectionProfileRef, String resourceBindingRef,
                                 String sourceVersion, Instant observedAt, long indexedCount,
                                 long completedWorkCount, String evidenceHash) {
        public String reference() {
            return "connector:" + sourceId + ":profile:" + connectionProfileRef + ":binding:" + resourceBindingRef
                + ":indexed:" + indexedCount + ":completed:" + completedWorkCount + ":sha256:" + evidenceHash;
        }
    }
}
