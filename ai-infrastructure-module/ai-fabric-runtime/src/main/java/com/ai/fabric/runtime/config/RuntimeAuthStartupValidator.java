package com.ai.fabric.runtime.config;

import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class RuntimeAuthStartupValidator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(RuntimeAuthStartupValidator.class);

    private final RuntimeAuthProperties properties;

    public RuntimeAuthStartupValidator(RuntimeAuthProperties properties) {
        this.properties = properties != null ? properties : new RuntimeAuthProperties();
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> errors = validationErrors();
        validationWarnings().forEach(log::warn);
        if (!errors.isEmpty()) {
            errors.forEach(log::error);
            throw new IllegalStateException(String.join(" ", errors));
        }
    }

    public List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        RuntimeAuthProperties.Ingress ingress = properties.getIngress();
        RuntimeAuthProperties.PublicTokens publicTokens = properties.getPublicTokens();
        RuntimeAuthProperties.Bootstrap bootstrap = publicTokens.getBootstrap();

        if (ingress.getMode() == RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED
            && !StringUtils.hasText(ingress.getTrustedBackend().getApiKeyValue())) {
            errors.add(
                "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED but no trusted backend API key is configured. "
                    + "Set ai.fabric.runtime.auth.ingress.trusted-backend.api-key-value before starting the runtime."
            );
        }
        if (ingress.getMode() == RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED
            && !StringUtils.hasText(ingress.getPrivateAssertions().getSigningKey())) {
            errors.add(
                "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED but no private assertion signing key is configured. "
                    + "Set ai.fabric.runtime.auth.ingress.private-assertions.signing-key before starting the runtime."
            );
        }
        if (ingress.getMode() == RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED
            && isEmpty(ingress.getAcceptedIssuers())) {
            errors.add(
                "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED without ai.fabric.runtime.auth.ingress.accepted-issuers. "
                    + "Configure an explicit verified-issuer allowlist before starting the runtime."
            );
        }
        if (ingress.getMode() == RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED
            && isEmpty(ingress.getAcceptedAudiences())) {
            errors.add(
                "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED without ai.fabric.runtime.auth.ingress.accepted-audiences. "
                    + "Configure an explicit verified-audience allowlist before starting the runtime."
            );
        }

        RuntimeAuthProperties.IntegrationService integrationService = ingress.getIntegrationService();
        if (integrationService != null && integrationService.isEnabled()
            && (!StringUtils.hasText(integrationService.getApiKeyHeader())
                || !StringUtils.hasText(integrationService.getApiKeyValue())
                || !StringUtils.hasText(integrationService.getDeploymentId())
                || !StringUtils.hasText(integrationService.getTenantId()))) {
            errors.add(
                "Runtime integration-service ingress is enabled without a complete API-key and deployment identity. "
                    + "Configure ai.fabric.runtime.auth.ingress.integration-service before enabling deployment-local integration sync."
            );
        }

        RuntimeAuthProperties.CustomerIngestion customerIngestion = ingress.getCustomerIngestion();
        if (customerIngestion != null && customerIngestion.isEnabled()) {
            if (!StringUtils.hasText(customerIngestion.getDeploymentId())
                || !StringUtils.hasText(customerIngestion.getTenantId())) {
                errors.add(
                    "Runtime customer-ingestion ingress is enabled without deployment and tenant ownership. "
                        + "Configure ai.fabric.runtime.auth.ingress.customer-ingestion deployment-id and tenant-id."
                );
            }
            if (isEmpty(customerIngestion.getAllowedUpsertEntityTypes())
                && isEmpty(customerIngestion.getAllowedDeleteEntityTypes())
                && !customerIngestion.isWorkStatusEnabled()
                && !customerIngestion.isReadinessEnabled()) {
                errors.add(
                    "Runtime customer-ingestion ingress is enabled without any allowed operation or entity type."
                );
            }
            if (customerIngestion.isWorkStatusEnabled()
                && isEmpty(customerIngestion.getAllowedWorkStatusEntityTypes())) {
                errors.add(
                    "Runtime customer-ingestion indexing-work status is enabled without any allowed entity type."
                );
            }
            if (!StringUtils.hasText(ingress.getTrustedBackend().getApiKeyValue())
                || !StringUtils.hasText(ingress.getPrivateAssertions().getSigningKey())
                || isEmpty(ingress.getAcceptedIssuers())
                || isEmpty(ingress.getAcceptedAudiences())) {
                errors.add(
                    "Runtime customer-ingestion ingress requires trusted-backend authentication, private assertion signing, "
                        + "and explicit accepted issuer and audience policies."
                );
            }
        }

        boolean publicRuntimeConfigured = StringUtils.hasText(publicTokens.getSigningKey());
        if (publicRuntimeConfigured && isEmpty(publicTokens.getAcceptedIssuers())) {
            errors.add(
                "Runtime public bearer auth is configured without ai.fabric.runtime.auth.public-tokens.accepted-issuers. "
                    + "Configure an explicit public-token issuer allowlist before starting the runtime."
            );
        }
        if (publicRuntimeConfigured && isEmpty(publicTokens.getAcceptedAudiences())) {
            errors.add(
                "Runtime public bearer auth is configured without ai.fabric.runtime.auth.public-tokens.accepted-audiences. "
                    + "Configure an explicit public-token audience allowlist before starting the runtime."
            );
        }

        if (bootstrap.isEnabled() && !publicRuntimeConfigured) {
            errors.add(
                "Runtime public bootstrap is enabled but no public token signing key is configured. "
                    + "Set ai.fabric.runtime.auth.public-tokens.signing-key before enabling POST /api/public/chat/session."
            );
        }

        return List.copyOf(errors);
    }

    public List<String> validationWarnings() {
        List<String> warnings = new ArrayList<>();
        RuntimeAuthProperties.PublicTokens publicTokens = properties.getPublicTokens();
        RuntimeAuthProperties.Bootstrap bootstrap = publicTokens.getBootstrap();
        if (bootstrap.isEnabled() && isEmpty(bootstrap.getAllowedOrigins())) {
            warnings.add(
                "Runtime public bootstrap is enabled without any allowed origins. "
                    + "Cross-origin anonymous bootstrap requests will be denied unless allowed origins are configured."
            );
        }
        if (bootstrap.isEnabled() && bootstrap.isAllowMissingOrigin()) {
            warnings.add(
                "Runtime public bootstrap is enabled with allow-missing-origin=true. "
                    + "Anonymous public bootstrap requests without an Origin header will be accepted; use only when the embedding environment cannot provide origin headers."
            );
        }

        return List.copyOf(warnings);
    }

    private boolean isEmpty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        return values.stream().noneMatch(StringUtils::hasText);
    }
}
