package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

@ConfigurationProperties(prefix = "platform.core-services")
public record PlatformCoreServicesProperties(
    boolean enabled,
    String targetProfileId,
    List<CoreService> services
) {

    public PlatformCoreServicesProperties {
        targetProfileId = normalizeText(targetProfileId);
        services = services == null
            ? List.of()
            : services.stream()
                .filter(service -> service != null && StringUtils.hasText(service.serviceRef()))
                .map(CoreService::normalized)
                .toList();
    }

    public record CoreService(
        String serviceRef,
        String displayName,
        String serviceKind,
        String providerResourceUuid,
        String publicBaseUrl,
        String healthPath
    ) {

        public CoreService {
            serviceRef = normalizeText(serviceRef);
            displayName = normalizeText(displayName);
            serviceKind = normalizeText(serviceKind);
            providerResourceUuid = normalizeText(providerResourceUuid);
            publicBaseUrl = stripTrailingSlash(normalizeText(publicBaseUrl));
            healthPath = normalizeHealthPath(healthPath);
        }

        CoreService normalized() {
            return new CoreService(serviceRef, displayName, serviceKind, providerResourceUuid, publicBaseUrl, healthPath);
        }
    }

    private static String normalizeHealthPath(String value) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
