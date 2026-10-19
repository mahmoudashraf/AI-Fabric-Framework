package com.loomai.demo.dealership.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** One explicit inbound business account; unrelated to the outbound runtime/Inbox authority. */
@Component
public class ReceiverBindingService {
    public static final String API_KEY_HEADER = "X-DEALERSHIP-INTERNAL-KEY";
    private final DealershipDemoProperties properties;
    private final ProviderStockClient stock;

    public ReceiverBindingService(DealershipDemoProperties properties, ProviderStockClient stock) {
        this.properties = properties;
        this.stock = stock;
    }

    public Scope requireActive() {
        var receiver = properties.getReceiver();
        if (!receiver.isEnabled() || !identifier(receiver.getBindingId(), 80)
            || !identifier(receiver.getCustomerId(), 64) || !identifier(receiver.getTenantId(), 64)
            || !identifier(receiver.getDeploymentId(), 64) || !identifier(receiver.getAdvertiserId(), 160)
            || !credential(receiver.getApiKey())
            || sameKey(receiver.getApiKey(), properties.getInternal().getApiKey())
            || sameKey(receiver.getApiKey(), properties.getRuntime().getPrivateAccess().getTrustedApiKey())
            || sameKey(receiver.getApiKey(), properties.getRuntime().getPrivateAccess().getSigningKey())
            || sameKey(receiver.getApiKey(), properties.getHumanInbox().getOperatorApiKey())) throw unavailable();
        if (!receiver.getAdvertiserId().equals(stock.configuredAdvertiserId())) throw unavailable();
        return new Scope(receiver.getBindingId(), receiver.getCustomerId(), receiver.getTenantId(),
            receiver.getDeploymentId(), receiver.getAdvertiserId());
    }

    public void authenticate(String supplied) {
        requireActive();
        if (!sameKey(properties.getReceiver().getApiKey(), supplied))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal authentication is required.");
    }

    public boolean permits(Scope scope, String subject, String session, String customer, String tenant, String deployment) {
        return bounded(subject, 160) && bounded(session, 160) && scope.customerId().equals(customer)
            && scope.tenantId().equals(tenant) && scope.deploymentId().equals(deployment);
    }

    private boolean identifier(String value, int max) {
        return bounded(value, max) && value.matches("[A-Za-z0-9._-]+");
    }

    private boolean bounded(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.equals(value.trim())
            && value.chars().noneMatch(Character::isISOControl);
    }

    private boolean credential(String value) { return bounded(value, 4096) && value.length() >= 32; }

    private boolean sameKey(String expected, String supplied) {
        return expected != null && supplied != null && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The receiver binding is unavailable.");
    }

    public record Scope(String bindingId, String customerId, String tenantId, String deploymentId, String advertiserId) {
        public String fingerprint() {
            return hash(Map.of("contract", "dealership-receiver-binding/v1", "bindingId", bindingId,
                "customerId", customerId, "tenantId", tenantId, "deploymentId", deploymentId, "advertiserId", advertiserId));
        }

        public String storageKey(String requestKey) {
            return "receiver-v1-" + hash(Map.of("bindingFingerprint", fingerprint(), "requestKey", requestKey));
        }

        private static String hash(Map<String, String> fields) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(new ObjectMapper().writeValueAsBytes(new TreeMap<>(fields))));
            } catch (Exception failure) { throw new IllegalStateException("Receiver identity could not be fingerprinted."); }
        }
    }
}
