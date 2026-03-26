package com.ai.infrastructure.connector.rest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * Fail-fast validation for runtime proxy config.
 *
 * <p>This prevents confusing runtime errors like "URI is not absolute" by requiring an absolute
 * http(s) URL when the proxy feature is enabled.</p>
 */
@Slf4j
@Component
public class RestConnectorRuntimeProxyStartupValidator {

    public RestConnectorRuntimeProxyStartupValidator(RestConnectorRuntimeProxyProperties properties) {
        validate(properties);
    }

    private void validate(RestConnectorRuntimeProxyProperties properties) {
        if (properties == null || !properties.isEnabled()) {
            return;
        }

        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("rest-connector.runtime-proxy.enabled=true but rest-connector.runtime-proxy.base-url is missing.");
        }

        validateUrl(properties.getBaseUrl().trim(), "rest-connector.runtime-proxy.base-url");
    }

    private void validateUrl(String url, String key) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!StringUtils.hasText(scheme)) {
                throw new IllegalArgumentException("missing scheme");
            }
            String normalized = scheme.trim().toLowerCase(Locale.ROOT);
            if (!"http".equals(normalized) && !"https".equals(normalized)) {
                throw new IllegalArgumentException("unsupported scheme '" + scheme + "'");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException("missing host");
            }
        } catch (Exception ex) {
            throw new IllegalStateException(key + " must be a valid absolute http(s) URL (got '" + url + "'): " + ex.getMessage(), ex);
        }
    }
}

