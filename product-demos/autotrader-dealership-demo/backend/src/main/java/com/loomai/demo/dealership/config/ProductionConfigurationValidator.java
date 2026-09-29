package com.loomai.demo.dealership.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final DealershipDemoProperties properties;

    public ProductionConfigurationValidator(DealershipDemoProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isProductionGuardsEnabled()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        require(properties.getStaff().getUsername(), "APP_STAFF_USERNAME", missing);
        require(properties.getStaff().getPasswordHash(), "APP_STAFF_PASSWORD_HASH", missing);
        require(properties.getInternal().getApiKey(), "APP_INTERNAL_API_KEY", missing);
        require(properties.getPrivacy().getEncryptionKeyBase64(), "APP_PII_ENCRYPTION_KEY_BASE64", missing);
        if (properties.getAllowedOrigins().isEmpty()) {
            missing.add("CORS_ALLOWED_ORIGINS");
        }
        if (properties.getRuntime().isEnabled()) {
            var runtime = properties.getRuntime();
            var access = runtime.getPrivateAccess();
            require(runtime.getBaseUrl(), "LOOMAI_RUNTIME_BASE_URL", missing);
            require(access.getTrustedApiKey(), "LOOMAI_RUNTIME_TRUSTED_API_KEY", missing);
            require(access.getSigningKey(), "LOOMAI_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY", missing);
            require(access.getAudience(), "LOOMAI_RUNTIME_ASSERTION_AUDIENCE", missing);
            require(access.getDeploymentId(), "LOOMAI_RUNTIME_DEPLOYMENT_ID", missing);
            require(access.getCustomerId(), "LOOMAI_RUNTIME_CUSTOMER_ID", missing);
            require(access.getTenantId(), "LOOMAI_RUNTIME_TENANT_ID", missing);
        }
        validateEncryptionKey(missing);
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

    private void require(String value, String name, List<String> missing) {
        if (!StringUtils.hasText(value)) {
            missing.add(name);
        }
    }
}
