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
        validationWarnings().forEach(log::warn);
    }

    List<String> validationWarnings() {
        List<String> warnings = new ArrayList<>();
        RuntimeAuthProperties.Ingress ingress = properties.getIngress();
        RuntimeAuthProperties.PublicTokens publicTokens = properties.getPublicTokens();
        RuntimeAuthProperties.Bootstrap bootstrap = publicTokens.getBootstrap();

        if (ingress.getMode() == RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED
            && !StringUtils.hasText(ingress.getTrustedBackend().getApiKeyValue())) {
            warnings.add(
                "Runtime auth ingress mode is VERIFIED_CONTEXT_REQUIRED but no trusted backend API key is configured. "
                    + "Verified auth-context headers will be rejected until ai.fabric.runtime.auth.ingress.trusted-backend.api-key-value is set."
            );
        }

        boolean publicRuntimeConfigured = StringUtils.hasText(publicTokens.getSigningKey());
        if (publicRuntimeConfigured && isEmpty(publicTokens.getAcceptedIssuers())) {
            warnings.add(
                "Runtime public bearer auth is configured without ai.fabric.runtime.auth.public-tokens.accepted-issuers. "
                    + "Public-runtime tokens will validate signatures, but issuer policy will remain open until an explicit allowlist is configured."
            );
        }
        if (publicRuntimeConfigured && isEmpty(publicTokens.getAcceptedAudiences())) {
            warnings.add(
                "Runtime public bearer auth is configured without ai.fabric.runtime.auth.public-tokens.accepted-audiences. "
                    + "Public-runtime tokens will validate signatures, but audience policy will remain open until an explicit allowlist is configured."
            );
        }

        if (bootstrap.isEnabled() && !publicRuntimeConfigured) {
            warnings.add(
                "Runtime public bootstrap is enabled but no public token signing key is configured. "
                    + "POST /api/public/chat/session will stay unavailable until ai.fabric.runtime.auth.public-tokens.signing-key is set."
            );
        }
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
