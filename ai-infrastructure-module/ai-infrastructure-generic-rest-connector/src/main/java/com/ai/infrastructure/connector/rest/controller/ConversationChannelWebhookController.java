package com.ai.infrastructure.connector.rest.controller;

import ai.fabric.chat.channel.ConversationChannelEvent;
import com.ai.infrastructure.connector.rest.config.ConversationChannelConnectorProperties;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.service.ConversationChannelConnectorSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/conversation-channel")
@ConditionalOnProperty(
    prefix = "rest-connector.conversation-channel",
    name = "enabled",
    havingValue = "true"
)
public class ConversationChannelWebhookController {

    private final ConversationChannelConnectorProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final IntegrationStateRepository repository;

    public ConversationChannelWebhookController(
        ConversationChannelConnectorProperties properties,
        ObjectMapper objectMapper,
        IntegrationStateRepository repository
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
            .build();
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> event(
        @RequestHeader("X-LoomAI-Provider-Timestamp") String providerTimestamp,
        @RequestHeader("X-LoomAI-Provider-Nonce") String providerNonce,
        @RequestHeader("X-LoomAI-Provider-Signature") String providerSignature,
        @RequestBody String body
    ) throws Exception {
        String sourceId = sourceId();
        if (!ConversationChannelConnectorSecurity.timestampWithinSkew(
            providerTimestamp,
            Instant.now(),
            properties.getCallbackClockSkewSeconds()
        )) {
            repository.recordEventRejection(sourceId, "PROVIDER_TIMESTAMP_INVALID");
            return ResponseEntity.status(401).body(Map.of("accepted", false, "code", "PROVIDER_TIMESTAMP_INVALID"));
        }
        if (!ConversationChannelConnectorSecurity.verify(
            properties.getProviderWebhookSigningSecret(),
            providerTimestamp,
            providerNonce,
            body,
            providerSignature
        )) {
            repository.recordEventRejection(sourceId, "PROVIDER_SIGNATURE_INVALID");
            return ResponseEntity.status(401).body(Map.of("accepted", false, "code", "PROVIDER_SIGNATURE_INVALID"));
        }
        ConversationChannelEvent event = objectMapper.readValue(body, ConversationChannelEvent.class);
        if (!properties.getBindingId().equals(event.channelBindingId())) {
            repository.recordEventRejection(sourceId, "CHANNEL_BINDING_MISMATCH");
            return ResponseEntity.status(403).body(Map.of("accepted", false, "code", "CHANNEL_BINDING_MISMATCH"));
        }

        String bodyHash = ConversationChannelConnectorSecurity.sha256(body);
        String nonceEventId = "nonce:" + providerNonce;
        if (!repository.registerEvent(receipt(sourceId, nonceEventId, "NONCE", bodyHash, "RECEIVED"))) {
            repository.markEventDuplicate(sourceId, nonceEventId);
            return ResponseEntity.status(409).body(Map.of("accepted", false, "code", "PROVIDER_REPLAY_REJECTED"));
        }

        IntegrationStateRepository.WebhookEvent existing = repository.event(sourceId, event.eventId()).orElse(null);
        if (existing != null) {
            if (!MessageDigest.isEqual(
                existing.payloadSha256().getBytes(StandardCharsets.US_ASCII),
                bodyHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                repository.recordEventRejection(sourceId, "EVENT_ID_PAYLOAD_CONFLICT");
                return ResponseEntity.status(409).body(Map.of("accepted", false, "code", "EVENT_ID_PAYLOAD_CONFLICT"));
            }
            repository.markEventDuplicate(sourceId, event.eventId());
        } else {
            repository.registerEvent(receipt(sourceId, event.eventId(), event.type().name(), bodyHash, "RECEIVED"));
        }
        repository.beginEventAttempt(sourceId, event.eventId(), "FORWARDING");

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String signature = ConversationChannelConnectorSecurity.sign(
            properties.getRuntimeCallbackSigningSecret(), timestamp, nonce, body
        );
        HttpRequest request = HttpRequest.newBuilder(runtimeEventUri())
            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .header(properties.getRuntimeApiKeyHeader(), properties.getRuntimeApiKey())
            .header("X-LoomAI-Channel-Timestamp", timestamp)
            .header("X-LoomAI-Channel-Nonce", nonce)
            .header("X-LoomAI-Channel-Signature", signature)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            repository.updateEvent(sourceId, event.eventId(), "FAILED", "RUNTIME_CALLBACK_FAILED");
            return ResponseEntity.status(502).body(Map.of(
                "accepted", false,
                "code", "RUNTIME_CALLBACK_FAILED",
                "runtimeStatus", response.statusCode()
            ));
        }
        repository.updateEvent(sourceId, event.eventId(), "COMPLETED", null);
        Map<String, Object> acknowledgement = new LinkedHashMap<>();
        acknowledgement.put("accepted", true);
        acknowledgement.put("eventId", event.eventId());
        var runtimeResult = objectMapper.readTree(response.body());
        if (runtimeResult.path("controlVersion").canConvertToLong()) {
            acknowledgement.put("controlVersion", runtimeResult.path("controlVersion").asLong());
        }
        if (runtimeResult.path("controlState").isTextual()) {
            acknowledgement.put("controlState", runtimeResult.path("controlState").asText());
        }
        return ResponseEntity.accepted().body(Map.copyOf(acknowledgement));
    }

    private IntegrationStateRepository.WebhookEvent receipt(
        String sourceId,
        String eventId,
        String eventType,
        String payloadSha256,
        String status
    ) {
        Instant now = Instant.now();
        return new IntegrationStateRepository.WebhookEvent(
            sourceId,
            eventId,
            eventType,
            null,
            properties.getBindingId(),
            payloadSha256,
            status,
            0,
            0,
            0,
            null,
            now,
            now
        );
    }

    private String sourceId() {
        return "conversation-channel:" + properties.getBindingId();
    }

    private URI runtimeEventUri() {
        String base = properties.getRuntimeBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/api/private/conversation-channel-bindings/"
            + properties.getBindingId() + "/events");
    }
}
