package com.ai.fabric.runtime.smartbrain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainDeliveryService {

    private static final Set<String> READY = Set.of("PENDING", "RETRY");
    private static final int MAX_ATTEMPTS = 8;

    private final SmartBrainDeliveryRepository deliveryRepository;
    private final SmartBrainOperationRepository operationRepository;
    private final SmartBrainConfigurationService configurationService;
    private final SmartBrainSecurity security;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final byte[] signingSecret;
    private final String runtimeBaseUrl;

    public SmartBrainDeliveryService(
        SmartBrainDeliveryRepository deliveryRepository,
        SmartBrainOperationRepository operationRepository,
        SmartBrainConfigurationService configurationService,
        SmartBrainSecurity security,
        ObjectMapper objectMapper,
        @Value("${loomai.smart-brain.delivery-signing-secret:}") String signingSecret,
        @Value("${loomai.smart-brain.accepted-runtime-url:}") String runtimeBaseUrl
    ) {
        this.deliveryRepository = deliveryRepository;
        this.operationRepository = operationRepository;
        this.configurationService = configurationService;
        this.security = security;
        this.objectMapper = objectMapper;
        this.runtimeBaseUrl = stripTrailingSlash(runtimeBaseUrl);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        if (configurationService.current().delivery().signedWebhook()) {
            if (signingSecret == null || signingSecret.length() < 32) {
                throw new IllegalStateException("Signed Smart Brain delivery requires a stable signing secret.");
            }
            this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            this.signingSecret = new byte[0];
        }
    }

    public void enqueue(SmartBrainOperationEntity operation) {
        SmartBrainRuntimeConfig.Delivery config = configurationService.current().delivery();
        if (!config.signedWebhook() || deliveryRepository.findByOperationId(operation.getOperationId()).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        SmartBrainDeliveryEntity delivery = new SmartBrainDeliveryEntity();
        delivery.setDeliveryId("sbd-" + UUID.randomUUID());
        delivery.setOperationId(operation.getOperationId());
        delivery.setTenantId(operation.getTenantId());
        delivery.setDeploymentId(operation.getDeploymentId());
        delivery.setCallbackUrl(config.callbackUrl());
        delivery.setStatus("PENDING");
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(now);
        delivery.setCreatedAt(now);
        delivery.setUpdatedAt(now);
        try {
            deliveryRepository.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException ignored) {
            // A concurrent terminal-state observer already enqueued this operation.
        }
    }

    public List<SmartBrainDeliveryEntity> ready() {
        return deliveryRepository
            .findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(READY, Instant.now());
    }

    public void deliver(SmartBrainDeliveryEntity delivery) {
        SmartBrainOperationEntity operation = operationRepository.findById(delivery.getOperationId()).orElse(null);
        if (operation == null || !Set.of("SUCCEEDED", "FAILED", "CANCELLED", "EXPIRED").contains(operation.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        try {
            String body = objectMapper.writeValueAsString(cloudEvent(delivery, operation, now));
            long timestamp = now.getEpochSecond();
            String signature = signature(timestamp + "." + body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(delivery.getCallbackUrl()))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/cloudevents+json")
                .header("Idempotency-Key", delivery.getDeliveryId())
                .header("X-LoomAI-Timestamp", Long.toString(timestamp))
                .header("X-LoomAI-Signature", "v1=" + signature)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setLastHttpStatus(response.statusCode());
            delivery.setUpdatedAt(Instant.now());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus("DELIVERED");
                delivery.setDeliveredAt(delivery.getUpdatedAt());
                delivery.setLastError(null);
            } else {
                retry(delivery, "Callback returned HTTP " + response.statusCode());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setUpdatedAt(Instant.now());
            retry(delivery, "Delivery was interrupted.");
        } catch (Exception exception) {
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setUpdatedAt(Instant.now());
            retry(delivery, bounded(exception.getMessage()));
        }
        deliveryRepository.save(delivery);
    }

    private ObjectNode cloudEvent(
        SmartBrainDeliveryEntity delivery,
        SmartBrainOperationEntity operation,
        Instant now
    ) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("specversion", "1.0");
        root.put("id", delivery.getDeliveryId());
        root.put("source", StringUtils.hasText(runtimeBaseUrl) ? runtimeBaseUrl : "urn:loomai:smart-brain");
        root.put("type", "com.loomai.smart-brain.operation.completed");
        root.put("subject", operation.getOperationId());
        root.put("time", now.toString());
        ObjectNode data = root.putObject("data");
        data.put("operationId", operation.getOperationId());
        data.put("triggerCode", operation.getTriggerCode());
        data.put("status", operation.getStatus());
        data.put("operationUrl", operationUrl(operation));
        if (StringUtils.hasText(operation.getProtectedResult())) {
            JsonNode result = objectMapper.readTree(security.decrypt(operation.getProtectedResult()));
            data.set("result", result);
        }
        if (StringUtils.hasText(operation.getFailureCode())) {
            ObjectNode failure = data.putObject("failure");
            failure.put("code", operation.getFailureCode());
            failure.put("message", operation.getFailureMessage());
        }
        return root;
    }

    private void retry(SmartBrainDeliveryEntity delivery, String error) {
        delivery.setLastError(bounded(error));
        if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            delivery.setStatus("DEAD_LETTER");
            delivery.setNextAttemptAt(delivery.getUpdatedAt());
            return;
        }
        delivery.setStatus("RETRY");
        long delaySeconds = Math.min(3600, 5L * (1L << Math.min(10, delivery.getAttemptCount() - 1)));
        delivery.setNextAttemptAt(delivery.getUpdatedAt().plusSeconds(delaySeconds));
    }

    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingSecret, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String operationUrl(SmartBrainOperationEntity operation) {
        String path = "/api/smart-brain/v1/operations/" + operation.getOperationId();
        return StringUtils.hasText(operation.getAcceptedRuntimeUrl())
            ? operation.getAcceptedRuntimeUrl() + path
            : path;
    }

    private String bounded(String value) {
        String resolved = StringUtils.hasText(value) ? value.trim() : "Webhook delivery failed.";
        return resolved.length() <= 500 ? resolved : resolved.substring(0, 500);
    }

    private String stripTrailingSlash(String value) {
        return StringUtils.hasText(value) ? value.trim().replaceAll("/+$", "") : "";
    }
}
