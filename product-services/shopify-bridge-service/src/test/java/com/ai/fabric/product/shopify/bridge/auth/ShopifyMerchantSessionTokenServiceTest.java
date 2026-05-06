package com.ai.fabric.product.shopify.bridge.auth;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopifyMerchantSessionTokenServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifyAcceptsValidSessionToken() {
        ShopifyMerchantSessionTokenService service = new ShopifyMerchantSessionTokenService(properties(), objectMapper);
        String token = sessionToken("shopify-api-key", "shopify-secret", Instant.now().plusSeconds(60));

        ShopifyMerchantSession session = service.verify("Bearer " + token);

        assertThat(session.shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(session.userId()).isEqualTo("gid://shopify/User/1");
    }

    @Test
    void verifyRejectsExpiredToken() {
        ShopifyMerchantSessionTokenService service = new ShopifyMerchantSessionTokenService(properties(), objectMapper);
        String token = sessionToken("shopify-api-key", "shopify-secret", Instant.now().minusSeconds(30));

        assertThatThrownBy(() -> service.verify("Bearer " + token))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void verifyRejectsWrongAudience() {
        ShopifyMerchantSessionTokenService service = new ShopifyMerchantSessionTokenService(properties(), objectMapper);
        String token = sessionToken("wrong-audience", "shopify-secret", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> service.verify("Bearer " + token))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("audience"));
    }

    @Test
    void verifyRejectsIssuerDestinationMismatch() {
        ShopifyMerchantSessionTokenService service = new ShopifyMerchantSessionTokenService(properties(), objectMapper);
        String token = sessionToken(
            "shopify-api-key",
            "shopify-secret",
            Instant.now().plusSeconds(60),
            "https://beta.myshopify.com/admin",
            "https://alpha.myshopify.com"
        );

        assertThatThrownBy(() -> service.verify("Bearer " + token))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("issuer"));
    }

    @Test
    void verifyRejectsNonShopifyDestination() {
        ShopifyMerchantSessionTokenService service = new ShopifyMerchantSessionTokenService(properties(), objectMapper);
        String token = sessionToken(
            "shopify-api-key",
            "shopify-secret",
            Instant.now().plusSeconds(60),
            "https://attacker.example.com/admin",
            "https://attacker.example.com"
        );

        assertThatThrownBy(() -> service.verify("Bearer " + token))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("destination"));
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-test",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "test",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
            "shopify-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }

    private String sessionToken(String audience, String secret, Instant expiresAt) {
        return sessionToken(
            audience,
            secret,
            expiresAt,
            "https://alpha.myshopify.com/admin",
            "https://alpha.myshopify.com"
        );
    }

    private String sessionToken(String audience, String secret, Instant expiresAt, String issuer, String destination) {
        try {
            String header = base64Url("""
                {"alg":"HS256","typ":"JWT"}
                """.trim());
            long now = Instant.now().getEpochSecond();
            String payload = base64Url("""
                {"iss":"%s","dest":"%s","aud":"%s","sub":"gid://shopify/User/1","nbf":%d,"exp":%d}
                """.formatted(issuer, destination, audience, now - 10, expiresAt.getEpochSecond()));
            String signingInput = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8))
            );
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
