package com.ai.fabric.runtime.webhook;

import com.ai.infrastructure.http.OutboundHttpExecutionRequest;
import com.ai.infrastructure.http.OutboundHttpExecutionResponse;
import com.ai.infrastructure.http.OutboundHttpExecutor;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class RuntimeActionWebhookDeliveryWorker {

    private static final int CLAIM_BATCH_SIZE = 10;
    private static final Duration CLAIM_STALE_AFTER = Duration.ofMinutes(2);
    private static final Duration MAX_RETRY_BACKOFF = Duration.ofMinutes(5);
    private static final List<String> READY_STATUSES = List.of(
        ActionWebhookDeliveryEntity.STATUS_PENDING,
        ActionWebhookDeliveryEntity.STATUS_RETRY_PENDING
    );

    private final ActionWebhookDeliveryRepository deliveryRepository;
    private final OutboundHttpExecutor outboundHttpExecutor;
    private final RuntimeWebhookSecretResolver secretResolver;
    private final Clock clock;

    public RuntimeActionWebhookDeliveryWorker(ActionWebhookDeliveryRepository deliveryRepository,
                                              OutboundHttpExecutor outboundHttpExecutor,
                                              RuntimeWebhookSecretResolver secretResolver,
                                              Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.outboundHttpExecutor = outboundHttpExecutor;
        this.secretResolver = secretResolver;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${ai.webhooks.dispatch.fixed-delay-ms:5000}")
    public void dispatchReadyDeliveries() {
        List<ActionWebhookDeliveryEntity> claimed = claimReadyDeliveries();
        for (ActionWebhookDeliveryEntity delivery : claimed) {
            dispatchSingleDelivery(delivery);
        }
    }

    @Transactional
    protected List<ActionWebhookDeliveryEntity> claimReadyDeliveries() {
        Instant now = Instant.now(clock);
        Instant staleBefore = now.minus(CLAIM_STALE_AFTER);
        List<ActionWebhookDeliveryEntity> deliveries = deliveryRepository.findDispatchableForUpdate(
            READY_STATUSES,
            ActionWebhookDeliveryEntity.STATUS_IN_PROGRESS,
            now,
            staleBefore,
            PageRequest.of(0, CLAIM_BATCH_SIZE)
        );
        for (ActionWebhookDeliveryEntity delivery : deliveries) {
            delivery.setStatus(ActionWebhookDeliveryEntity.STATUS_IN_PROGRESS);
            delivery.setClaimedAt(now);
            delivery.setUpdatedAt(now);
        }
        return deliveries.stream().map(this::snapshot).toList();
    }

    private void dispatchSingleDelivery(ActionWebhookDeliveryEntity delivery) {
        try {
            String targetUrl = validateTargetUrl(secretResolver.resolveRequired(delivery.getUrlSecretRef()));
            OutboundHttpExecutionResponse response = outboundHttpExecutor.execute(
                new OutboundHttpExecutionRequest(
                    targetUrl,
                    HttpMethod.POST,
                    buildHeaders(delivery),
                    delivery.getPayloadJson(),
                    Duration.ofMillis(resolvePositive(delivery.getTimeoutMs(), 3_000)),
                    Duration.ofMillis(resolvePositive(delivery.getTimeoutMs(), 3_000))
                )
            );
            int statusCode = response != null ? response.statusCode() : 0;
            if (statusCode >= 200 && statusCode < 300) {
                markDelivered(delivery.getId(), statusCode);
                return;
            }
            if (isRetryableStatus(statusCode)) {
                markRetryPending(delivery.getId(), statusCode, "Webhook delivery returned HTTP " + statusCode + ".");
                return;
            }
            markFailed(delivery.getId(), statusCode, "Webhook delivery returned non-retryable HTTP " + statusCode + ".");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            markFailed(delivery.getId(), null, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Webhook delivery '{}' failed: {}", delivery.getId(), ex.getMessage());
            markRetryPending(delivery.getId(), null, "Webhook delivery failed: " + blankToFallback(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    @Transactional
    protected void markDelivered(String deliveryId, Integer statusCode) {
        deliveryRepository.findById(deliveryId).ifPresent(delivery -> {
            Instant now = Instant.now(clock);
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setStatus(ActionWebhookDeliveryEntity.STATUS_DELIVERED);
            delivery.setLastStatusCode(statusCode);
            delivery.setLastError(null);
            delivery.setDeliveredAt(now);
            delivery.setClaimedAt(null);
            delivery.setUpdatedAt(now);
        });
    }

    @Transactional
    protected void markRetryPending(String deliveryId, Integer statusCode, String message) {
        deliveryRepository.findById(deliveryId).ifPresent(delivery -> {
            Instant now = Instant.now(clock);
            int attempts = delivery.getAttemptCount() + 1;
            delivery.setAttemptCount(attempts);
            delivery.setLastStatusCode(statusCode);
            delivery.setLastError(truncate(message));
            delivery.setClaimedAt(null);
            delivery.setUpdatedAt(now);
            if (attempts >= resolvePositive(delivery.getMaxAttempts(), 5)) {
                delivery.setStatus(ActionWebhookDeliveryEntity.STATUS_FAILED);
                delivery.setNextAttemptAt(now);
                return;
            }
            delivery.setStatus(ActionWebhookDeliveryEntity.STATUS_RETRY_PENDING);
            delivery.setNextAttemptAt(now.plus(backoffForAttempt(attempts)));
        });
    }

    @Transactional
    protected void markFailed(String deliveryId, Integer statusCode, String message) {
        deliveryRepository.findById(deliveryId).ifPresent(delivery -> {
            Instant now = Instant.now(clock);
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setStatus(ActionWebhookDeliveryEntity.STATUS_FAILED);
            delivery.setLastStatusCode(statusCode);
            delivery.setLastError(truncate(message));
            delivery.setClaimedAt(null);
            delivery.setUpdatedAt(now);
            delivery.setNextAttemptAt(now);
        });
    }

    private Map<String, String> buildHeaders(ActionWebhookDeliveryEntity delivery) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "ai-fabric-runtime/webhook");
        headers.put("X-AI-Fabric-Delivery-Id", delivery.getId());
        headers.put("X-AI-Fabric-Event-Type", delivery.getEventType());
        String timestamp = Instant.now(clock).toString();
        headers.put("X-AI-Fabric-Delivery-Timestamp", timestamp);
        if (StringUtils.hasText(delivery.getSigningSecretRef())) {
            String secret = secretResolver.resolveRequired(delivery.getSigningSecretRef());
            headers.put("X-AI-Fabric-Signature", sign(secret, timestamp, delivery.getPayloadJson()));
        }
        return headers;
    }

    private String sign(String secret, String timestamp, String body) {
        try {
            String message = timestamp + "\n" + (body != null ? body : "");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute webhook HMAC signature: " + ex.getMessage(), ex);
        }
    }

    private String validateTargetUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalArgumentException("Webhook target URL is required.");
        }
        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Webhook target URL must start with http:// or https://.");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalArgumentException("Webhook target URL host is required.");
        }
        if (StringUtils.hasText(uri.getUserInfo())) {
            throw new IllegalArgumentException("Webhook target URL must not include user info.");
        }
        if (StringUtils.hasText(uri.getFragment())) {
            throw new IllegalArgumentException("Webhook target URL must not include a fragment.");
        }
        return uri.toString();
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
            || statusCode == 425
            || statusCode == 429
            || statusCode == 0
            || statusCode >= 500;
    }

    private Duration backoffForAttempt(int attempts) {
        long seconds = Math.min(MAX_RETRY_BACKOFF.getSeconds(), (long) Math.pow(2, Math.max(0, attempts - 1)) * 10L);
        return Duration.ofSeconds(Math.max(10L, seconds));
    }

    private int resolvePositive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private String truncate(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.length() > 4000 ? trimmed.substring(0, 4000) : trimmed;
    }

    private String blankToFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private ActionWebhookDeliveryEntity snapshot(ActionWebhookDeliveryEntity source) {
        ActionWebhookDeliveryEntity copy = new ActionWebhookDeliveryEntity();
        copy.setId(source.getId());
        copy.setActionName(source.getActionName());
        copy.setEventType(source.getEventType());
        copy.setTargetRef(source.getTargetRef());
        copy.setUrlSecretRef(source.getUrlSecretRef());
        copy.setSigningSecretRef(source.getSigningSecretRef());
        copy.setTimeoutMs(source.getTimeoutMs());
        copy.setMaxAttempts(source.getMaxAttempts());
        copy.setAttemptCount(source.getAttemptCount());
        copy.setStatus(source.getStatus());
        copy.setDeploymentId(source.getDeploymentId());
        copy.setConversationId(source.getConversationId());
        copy.setRequestId(source.getRequestId());
        copy.setPayloadJson(source.getPayloadJson());
        copy.setNextAttemptAt(source.getNextAttemptAt());
        copy.setClaimedAt(source.getClaimedAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
