package com.loomai.verification.vehicleprovider.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.verification.vehicleprovider.config.SimulatorProperties;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventAttempt;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventDelivery;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventRequest;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.EventVariant;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.web.JsonProjection;
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
        String providerEventId = profile == Profile.AUTOTRADER
            ? stockId(request)
            : hasText(request.eventId()) ? request.eventId() : "evt-" + UUID.randomUUID();
        String resource = request.variant() == EventVariant.WRONG_RESOURCE ? accountId + "-other" : accountId;
        Instant occurredAt = request.variant() == EventVariant.DELAYED
            ? Instant.now().minusSeconds(600)
            : Instant.now();
        byte[] body = eventBody(profile, providerEventId, resource, eventSequence, occurredAt, request);
        int attempts = request.variant() == EventVariant.DUPLICATE ? 2 : 1;
        List<EventAttempt> results = new ArrayList<>();
        String connectorEventId = null;
        for (int index = 1; index <= attempts; index++) {
            SendResult sent = send(profile, target, body, request.variant(), index);
            results.add(sent.attempt());
            if (!hasText(connectorEventId) && hasText(sent.connectorEventId())) {
                connectorEventId = sent.connectorEventId();
            }
        }
        return new EventDelivery(
            hasText(connectorEventId) ? connectorEventId : providerEventId,
            providerEventId,
            request.variant(),
            profile.value(),
            accountId,
            target.getHost(),
            eventSequence,
            List.copyOf(results)
        );
    }

    private SendResult send(Profile profile, URI target, byte[] body, EventVariant variant, int attempt) {
        long timestamp = Instant.now().getEpochSecond();
        String signature = signature(simulator.webhookSecret(profile), timestamp, body);
        if (variant == EventVariant.WRONG_SIGNATURE) {
            signature = "invalid" + signature;
        }
        String signatureHeader = switch (profile) {
            case PROFILE_A -> "X-Simulator-A-Signature";
            case PROFILE_B -> "X-Simulator-B-Signature";
            case AUTOTRADER -> "AutoTrader-Signature";
        };
        String method = profile == Profile.PROFILE_A ? "POST" : "PUT";
        String signatureValue = "t=" + timestamp + ",v1=" + signature;
        HttpRequest.Builder outbound = HttpRequest.newBuilder(target)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header(signatureHeader, signatureValue)
            .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        if (profile != Profile.AUTOTRADER) {
            outbound.header("X-Simulator-Event", "true");
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(outbound.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new SendResult(
                new EventAttempt(attempt, response.statusCode(), responseClass(response.statusCode())),
                connectorEventId(response.body())
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new SendResult(new EventAttempt(attempt, 0, ex.getClass().getSimpleName()), null);
        } catch (Exception ex) {
            return new SendResult(new EventAttempt(attempt, 0, ex.getClass().getSimpleName()), null);
        }
    }

    private byte[] eventBody(Profile profile, String eventId, String resource, long sequence,
                             Instant occurredAt, EventRequest request) {
        if (request.variant() == EventVariant.MALFORMED) {
            return ("{\"eventId\":\"" + eventId + "\",\"eventType\":").getBytes(StandardCharsets.UTF_8);
        }
        if (profile == Profile.AUTOTRADER) {
            return autoTraderEventBody(resource, occurredAt, request);
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

    private byte[] autoTraderEventBody(String advertiserId, Instant occurredAt,
                                       EventRequest request) {
        String stockId = stockId(request);
        ObjectNode body = objectMapper.createObjectNode()
            .put("id", stockId)
            .put("time", occurredAt.toString())
            .put("type", "STOCK_UPDATE")
            .put("integrationId", simulator.autoTraderIntegrationId())
            .put("stockEventSource", "AT_CONNECT");
        ObjectNode data = simulator.findVehicle(Profile.AUTOTRADER, simulator.accountId(Profile.AUTOTRADER), stockId)
            .map(JsonProjection::autoTrader)
            .orElseGet(() -> {
                ObjectNode deleted = objectMapper.createObjectNode();
                deleted.set("advertiser", objectMapper.createObjectNode().put("advertiserId", advertiserId));
                deleted.set("metadata", objectMapper.createObjectNode()
                    .put("stockId", stockId)
                    .put("searchId", "")
                    .put("lifecycleState", "DELETED")
                    .put("lastUpdated", occurredAt.toString()));
                deleted.set("vehicle", objectMapper.createObjectNode());
                deleted.set("adverts", objectMapper.createObjectNode().set(
                    "retailAdverts",
                    objectMapper.createObjectNode().set(
                        "advertiserAdvert",
                        objectMapper.createObjectNode().put("status", "NOT_PUBLISHED")
                    )
                ));
                return deleted;
            });
        if (!advertiserId.equals(simulator.accountId(Profile.AUTOTRADER))) {
            data.with("advertiser").put("advertiserId", advertiserId);
        }
        body.set("data", data);
        var changedFields = body.putArray("changedFields");
        changedFields.addObject().put("path", "/time");
        changedFields.addObject().put("path", "/data/metadata/versionNumber");
        changedFields.addObject().put("path", "/data/metadata/lastUpdated");
        changedFields.addObject().put("path", "/data/metadata/lifecycleState");
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encode Auto Trader simulator event.", ex);
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

    private String connectorEventId(byte[] responseBody) {
        if (responseBody == null || responseBody.length == 0) {
            return null;
        }
        try {
            String value = objectMapper.readTree(responseBody).path("eventId").asText("").trim();
            return hasText(value) ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stockId(EventRequest request) {
        return hasText(request.vehicleId()) ? request.vehicleId().trim() : "unknown-stock";
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

    private record SendResult(EventAttempt attempt, String connectorEventId) {
    }
}
