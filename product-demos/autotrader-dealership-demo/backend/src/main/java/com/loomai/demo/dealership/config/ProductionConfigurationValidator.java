package com.loomai.demo.dealership.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.loomai.demo.dealership.security.ReceiverBindingService;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.net.URI;

@Component
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final DealershipDemoProperties properties;
    private final ReceiverBindingService receivers;

    public ProductionConfigurationValidator(DealershipDemoProperties properties, ReceiverBindingService receivers) {
        this.properties = properties;
        this.receivers = receivers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isProductionGuardsEnabled()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        require(properties.getStaff().getUsername(), "APP_STAFF_USERNAME", missing);
        require(properties.getStaff().getPasswordHashBase64(), "APP_STAFF_PASSWORD_HASH_BASE64", missing);
        require(properties.getInternal().getApiKey(), "APP_INTERNAL_API_KEY", missing);
        require(properties.getPrivacy().getEncryptionKeyBase64(), "APP_PII_ENCRYPTION_KEY_BASE64", missing);
        require(properties.getPublicMediaBaseUrl(), "DEALERSHIP_PUBLIC_MEDIA_BASE_URL", missing);
        validateHttpsUrl(properties.getPublicMediaBaseUrl(), "DEALERSHIP_PUBLIC_MEDIA_BASE_URL", missing);
        if (properties.getAllowedOrigins().isEmpty()) {
            missing.add("CORS_ALLOWED_ORIGINS");
        }
        if (properties.getRuntime().isEnabled()) {
            var runtime = properties.getRuntime();
            var access = runtime.getPrivateAccess();
            require(runtime.getBaseUrl(), "LOOMAI_RUNTIME_BASE_URL", missing);
            require(runtime.getIntegrationSourceId(), "LOOMAI_RUNTIME_INTEGRATION_SOURCE_ID", missing);
            require(runtime.getIntegrationWebhookSourceId(), "LOOMAI_RUNTIME_INTEGRATION_WEBHOOK_SOURCE_ID", missing);
            require(access.getTrustedApiKey(), "LOOMAI_RUNTIME_TRUSTED_API_KEY", missing);
            require(access.getSigningKey(), "LOOMAI_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY", missing);
            require(access.getAudience(), "LOOMAI_RUNTIME_ASSERTION_AUDIENCE", missing);
            require(access.getDeploymentId(), "LOOMAI_RUNTIME_DEPLOYMENT_ID", missing);
            require(access.getCustomerId(), "LOOMAI_RUNTIME_CUSTOMER_ID", missing);
            require(access.getTenantId(), "LOOMAI_RUNTIME_TENANT_ID", missing);
        }
        if (properties.getProviderSimulator().isEnabled()) {
            var simulator = properties.getProviderSimulator();
            require(simulator.getBaseUrl(), "PROVIDER_SIMULATOR_BASE_URL", missing);
            require(simulator.getControlApiKey(), "PROVIDER_SIMULATOR_CONTROL_API_KEY", missing);
            require(simulator.getAdvertiserId(), "PROVIDER_SIMULATOR_ADVERTISER_ID", missing);
            require(simulator.getWebhookTargetUrl(), "PROVIDER_SIMULATOR_WEBHOOK_TARGET_URL", missing);
            validateHttpsUrl(simulator.getBaseUrl(), "PROVIDER_SIMULATOR_BASE_URL", missing);
            validateHttpsUrl(simulator.getWebhookTargetUrl(), "PROVIDER_SIMULATOR_WEBHOOK_TARGET_URL", missing);
        }
        if (properties.getHumanInbox().isEnabled()) {
            var inbox = properties.getHumanInbox();
            require(inbox.getBaseUrl(), "HUMAN_INBOX_BASE_URL", missing);
            require(inbox.getOperatorApiKey(), "HUMAN_INBOX_OPERATOR_API_KEY", missing);
            validateHttpsUrl(inbox.getBaseUrl(), "HUMAN_INBOX_BASE_URL", missing);
            if (inbox.getRequestTimeout() == null || inbox.getRequestTimeout().isZero()
                || inbox.getRequestTimeout().isNegative() || inbox.getRequestTimeout().compareTo(java.time.Duration.ofSeconds(30)) > 0) {
                missing.add("HUMAN_INBOX_REQUEST_TIMEOUT (must be between 1ms and 30s)");
            }
        }
        validateEncryptionKey(missing);
        validateStaffPasswordHash(missing);
        if (properties.getReceiver().isEnabled()) {
            try { receivers.requireActive(); }
            catch (org.springframework.web.server.ResponseStatusException invalid) {
                missing.add("DEALERSHIP_RECEIVER_* (complete scope, matching advertiser and dedicated credential required)");
            }
        }
        if (properties.getPrivacy().isLeadRetentionEnabled() && (properties.getPrivacy().getLeadRetention() == null
            || properties.getPrivacy().getLeadRetention().isNegative() || properties.getPrivacy().getLeadRetention().isZero()))
            missing.add("APP_LEAD_RETENTION (must be positive when enabled)");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Production configuration is incomplete: " + String.join(", ", missing));
        }
    }

    private void validateEncryptionKey(List<String> missing) {
        String configured = properties.getPrivacy().getEncryptionKeyBase64();
        if (!StringUtils.hasText(configured)) {
            return;
        }
        try {
            if (Base64.getDecoder().decode(configured.trim()).length != 32) {
                missing.add("APP_PII_ENCRYPTION_KEY_BASE64 (must decode to 32 bytes)");
            }
        } catch (IllegalArgumentException ex) {
            missing.add("APP_PII_ENCRYPTION_KEY_BASE64 (invalid Base64)");
        }
    }

    private void validateStaffPasswordHash(List<String> missing) {
        String configured = properties.getStaff().getPasswordHashBase64();
        if (!StringUtils.hasText(configured)) {
            return;
        }
        try {
            String hash = new String(Base64.getDecoder().decode(configured.trim()), java.nio.charset.StandardCharsets.UTF_8);
            if (hash.length() != 60 || !(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"))) {
                missing.add("APP_STAFF_PASSWORD_HASH_BASE64 (must contain one BCrypt hash)");
            }
        } catch (IllegalArgumentException ex) {
            missing.add("APP_STAFF_PASSWORD_HASH_BASE64 (invalid Base64)");
        }
    }

    private void require(String value, String name, List<String> missing) {
        if (!StringUtils.hasText(value)) {
            missing.add(name);
        }
    }

    private void validateHttpsUrl(String value, String name, List<String> missing) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                || uri.getUserInfo() != null || uri.getFragment() != null) {
                missing.add(name + " (must be an absolute HTTPS URL without user info or fragment)");
            }
        } catch (IllegalArgumentException ex) {
            missing.add(name + " (invalid URL)");
        }
    }
}
