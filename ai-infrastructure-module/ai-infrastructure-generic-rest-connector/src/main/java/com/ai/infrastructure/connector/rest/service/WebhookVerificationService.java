package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WebhookVerificationService {

    private final Clock clock;

    public WebhookVerificationService(Clock clock) {
        this.clock = clock;
    }

    public VerificationResult verify(RestRoutingConfig.WebhookVerification config, String header, byte[] rawBody) {
        if (config == null || config.getStrategy() == null || !StringUtils.hasText(config.getSecret())) {
            return VerificationResult.rejected("WEBHOOK_VERIFICATION_NOT_CONFIGURED");
        }
        if (!StringUtils.hasText(header)) {
            return VerificationResult.rejected("WEBHOOK_SIGNATURE_MISSING");
        }
        if (header.length() > 4096) {
            return VerificationResult.rejected("WEBHOOK_SIGNATURE_MALFORMED");
        }
        Map<String, String> components = components(header);
        String timestamp = components.get(config.getTimestampComponent());
        String signature = components.get(config.getSignatureComponent());
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            return VerificationResult.rejected("WEBHOOK_SIGNATURE_MALFORMED");
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return VerificationResult.rejected("WEBHOOK_TIMESTAMP_INVALID");
        }
        long now = clock.instant().getEpochSecond();
        long replayWindow = Math.max(0, config.getReplayWindowSeconds());
        if (epochSeconds < now - replayWindow || epochSeconds > now + replayWindow) {
            return VerificationResult.rejected("WEBHOOK_REPLAY_WINDOW_EXCEEDED");
        }
        String expected = hmacSha256Hex(
            config.getSecret(),
            timestamp.getBytes(StandardCharsets.UTF_8),
            new byte[]{'.'},
            rawBody
        );
        if (!constantTimeEquals(expected, signature.trim())) {
            return VerificationResult.rejected("WEBHOOK_SIGNATURE_INVALID");
        }
        return new VerificationResult(true, null, epochSeconds);
    }

    private Map<String, String> components(String header) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : header.split(",")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                continue;
            }
            out.put(part.substring(0, separator).trim(), part.substring(separator + 1).trim());
        }
        return out;
    }

    private String hmacSha256Hex(String secret, byte[]... parts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            for (byte[] part : parts) {
                mac.update(part);
            }
            byte[] digest = mac.doFinal();
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify webhook HMAC.", ex);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
            expected.toLowerCase().getBytes(StandardCharsets.UTF_8),
            actual.toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    public record VerificationResult(boolean accepted, String errorClass, Long timestamp) {
        private static VerificationResult rejected(String errorClass) {
            return new VerificationResult(false, errorClass, null);
        }
    }
}
