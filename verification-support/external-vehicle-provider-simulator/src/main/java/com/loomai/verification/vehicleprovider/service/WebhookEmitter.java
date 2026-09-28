package com.loomai.verification.vehicleprovider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.verification.vehicleprovider.config.SimulatorProperties;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventAttempt;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventDelivery;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventRequest;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventVariant;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WebhookEmitter {

    private final SimulatorProperties properties;
    private final SimulatorService simulator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebhookEmitter(SimulatorProperties properties,
                          SimulatorService simulator,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.simulator = simulator;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    public EventDelivery emit(Profile profile, String accountId, EventRequest request) {
        simulator.requireAccount(profile, accountId, accountId);
        URI target = validateTarget(request.targetUrl());
        long sequence = simulator.nextEventSequence(profile, accountId);
        long eventSequence = request.variant() == EventVariant.OUT_OF_ORDER
            ? Math.max(0, sequence - 2)
            : sequence;
        String eventId = hasText(request.eventId()) ? request.eventId() : "evt-" + UUID.randomUUID();
        String resource = request.variant() == EventVariant.WRONG_RESOURCE ? accountId + "-other" : accountId;
        Instant occurredAt = request.variant() == EventVariant.DELAYED
            ? Instant.now().minusSeconds(600)
            : Instant.now();
        byte[] body = eventBody(profile, eventId, resource, eventSequence, occurredAt, request);
        int attempts = request.variant() == EventVariant.DUPLICATE ? 2 : 1;
        List<EventAttempt> results = new ArrayList<>();
        for (int index = 1; index <= attempts; index++) {
            results.add(send(profile, target, body, request.variant(), index));
        }
        return new EventDelivery(
            eventId,
            request.variant(),
            profile.value(),
            accountId,
            target.getHost(),
            eventSequence,
            List.copyOf(results)
        );
    }

    private EventAttempt send(Profile profile, URI target, byte[] body, EventVariant variant, int attempt) {
        long timestamp = Instant.now().getEpochSecond();
        String signature = signature(simulator.webhookSecret(profile), timestamp, body);
        if (variant == EventVariant.WRONG_SIGNATURE) {
            signature = "invalid" + signature;
        }
        String signatureHeader = profile == Profile.PROFILE_A
            ? "X-Simulator-A-Signature"
            : "X-Simulator-B-Signature";
        String method = profile == Profile.PROFILE_A ? "POST" : "PUT";
        HttpRequest outbound = HttpRequest.newBuilder(target)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header(signatureHeader, "t=" + timestamp + ",v1=" + signature)
            .header("X-Simulator-Event", "true")
            .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(outbound, HttpResponse.BodyHandlers.ofByteArray());
            return new EventAttempt(attempt, response.statusCode(), responseClass(response.statusCode()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new EventAttempt(attempt, 0, ex.getClass().getSimpleName());
        } catch (Exception ex) {
            return new EventAttempt(attempt, 0, ex.getClass().getSimpleName());
        }
    }

    private byte[] eventBody(Profile profile, String eventId, String resource, long sequence,
                             Instant occurredAt, EventRequest request) {
        if (request.variant() == EventVariant.MALFORMED) {
            return ("{\"eventId\":\"" + eventId + "\",\"eventType\":").getBytes(StandardCharsets.UTF_8);
        }
        ObjectNode body = objectMapper.createObjectNode()
            .put("eventId", eventId)
            .put("eventType", "vehicle.changed")
            .put("sequence", sequence)
            .put("occurredAt", occurredAt.toString())
            .put("sourceVersion", simulator.sourceVersion(profile, simulator.accountId(profile)));
        if (profile == Profile.PROFILE_A) {
            body.put("accountId", resource);
        } else {
            body.put("ownerRef", resource);
        }
        if (hasText(request.vehicleId())) {
            body.put("vehicleId", request.vehicleId());
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encode simulator event.", ex);
        }
    }

    private URI validateTarget(String value) {
        URI target;
        try {
            target = URI.create(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Webhook target URL is invalid.");
        }
        String host = target.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (host == null || target.getUserInfo() != null || target.getFragment() != null
            || !("https".equalsIgnoreCase(target.getScheme()) || (loopback && "http".equalsIgnoreCase(target.getScheme())))
            || !allowedHost(host)) {
            throw new IllegalArgumentException("Webhook target is outside the configured HTTPS host allowlist.");
        }
        return target;
    }

    private boolean allowedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return properties.allowedWebhookHosts().stream().anyMatch(pattern -> {
            String normalizedPattern = pattern.trim().toLowerCase(Locale.ROOT);
            if (normalizedPattern.startsWith("*.")) {
                String suffix = normalizedPattern.substring(1);
                return normalizedHost.endsWith(suffix) && normalizedHost.length() > suffix.length();
            }
            return normalizedHost.equals(normalizedPattern);
        });
    }

    private static String signature(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign simulator webhook.", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String responseClass(int status) {
        if (status >= 200 && status < 300) {
            return "ACCEPTED";
        }
        if (status >= 400 && status < 500) {
            return "REJECTED";
        }
        if (status >= 500) {
            return "UPSTREAM_FAILURE";
        }
        return "UNEXPECTED";
    }
}
