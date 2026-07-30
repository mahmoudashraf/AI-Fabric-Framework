package com.ai.fabric.platform.backend.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformCoreServicesProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyActionResponse;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.model.PlatformCoreServiceActionSummary;
import com.ai.fabric.platform.backend.model.PlatformCoreServiceSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PlatformCoreServiceOperationsService {

    private static final String TARGET_TYPE = "PLATFORM_CORE_SERVICE";

    private final PlatformCoreServicesProperties properties;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final CoolifyTargetProfileResolver coolifyTargetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public PlatformCoreServiceOperationsService(PlatformCoreServicesProperties properties,
                                                DeploymentTargetProfileRepository targetProfileRepository,
                                                CoolifyTargetProfileResolver coolifyTargetProfileResolver,
                                                CoolifyApiClient coolifyApiClient,
                                                PlatformAuditService platformAuditService,
                                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.targetProfileRepository = targetProfileRepository;
        this.coolifyTargetProfileResolver = coolifyTargetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public List<PlatformCoreServiceSummary> listServices() {
        return properties.services().stream()
            .map(this::inspect)
            .toList();
    }

    public PlatformCoreServiceSummary getService(String serviceRef) {
        return inspect(requireService(serviceRef));
    }

    public PlatformCoreServiceActionSummary deploy(String serviceRef) {
        PlatformCoreServicesProperties.CoreService service = requireService(serviceRef);
        requireEnabled();
        String providerResourceUuid = requireProviderResourceUuid(service);
        DeploymentTargetProfileEntity profile = requireTargetProfile();
        CoolifyConnection connection = coolifyTargetProfileResolver.requireConnection(profile);
        CoolifyActionResponse response = coolifyApiClient.start(connection, providerResourceUuid, true, true);
        platformAuditService.record(
            "PLATFORM_CORE_SERVICE_DEPLOY_REQUESTED",
            TARGET_TYPE,
            service.serviceRef(),
            Map.of(
                "serviceRef", service.serviceRef(),
                "targetProfileId", profile.getId(),
                "providerResourceUuid", providerResourceUuid,
                "deploymentUuid", blankToFallback(response.deploymentUuid(), "")
            )
        );
        return actionSummary(service, "DEPLOY", profile.getId(), response);
    }

    public PlatformCoreServiceActionSummary restart(String serviceRef) {
        PlatformCoreServicesProperties.CoreService service = requireService(serviceRef);
        requireEnabled();
        String providerResourceUuid = requireProviderResourceUuid(service);
        DeploymentTargetProfileEntity profile = requireTargetProfile();
        CoolifyConnection connection = coolifyTargetProfileResolver.requireConnection(profile);
        CoolifyActionResponse response = coolifyApiClient.restart(connection, providerResourceUuid);
        platformAuditService.record(
            "PLATFORM_CORE_SERVICE_RESTART_REQUESTED",
            TARGET_TYPE,
            service.serviceRef(),
            Map.of(
                "serviceRef", service.serviceRef(),
                "targetProfileId", profile.getId(),
                "providerResourceUuid", providerResourceUuid,
                "deploymentUuid", blankToFallback(response.deploymentUuid(), "")
            )
        );
        return actionSummary(service, "RESTART", profile.getId(), response);
    }

    private PlatformCoreServiceSummary inspect(PlatformCoreServicesProperties.CoreService service) {
        Instant observedAt = Instant.now();
        if (!properties.enabled()) {
            return summary(service, "DISABLED", null, "Platform core service operations are disabled.", observedAt, emptyDetails());
        }
        if (!StringUtils.hasText(properties.targetProfileId())) {
            return summary(service, "CONFIGURATION_ERROR", null, "Core service target profile is not configured.", observedAt, emptyDetails());
        }
        if (!StringUtils.hasText(service.providerResourceUuid())) {
            return summary(service, "CONFIGURATION_ERROR", null, "Core service provider resource UUID is not configured.", observedAt, emptyDetails());
        }

        try {
            DeploymentTargetProfileEntity profile = requireTargetProfile();
            CoolifyConnection connection = coolifyTargetProfileResolver.requireConnection(profile);
            return coolifyApiClient.getApplication(connection, service.providerResourceUuid())
                .map(application -> summary(
                    service,
                    normalizeObservedStatus(application.status()),
                    application.status(),
                    "Coolify application status fetched.",
                    observedAt,
                    applicationDetails(application)
                ))
                .orElseGet(() -> summary(
                    service,
                    "NOT_FOUND",
                    null,
                    "Coolify application was not found for the configured UUID.",
                    observedAt,
                    emptyDetails()
                ));
        } catch (RuntimeException ex) {
            return summary(
                service,
                "UNAVAILABLE",
                null,
                blankToFallback(ex.getMessage(), "Failed to inspect Coolify application."),
                observedAt,
                errorDetails(ex)
            );
        }
    }

    private PlatformCoreServiceSummary summary(PlatformCoreServicesProperties.CoreService service,
                                               String status,
                                               String observedStatus,
                                               String message,
                                               Instant observedAt,
                                               JsonNode details) {
        return new PlatformCoreServiceSummary(
            service.serviceRef(),
            blankToFallback(service.displayName(), service.serviceRef()),
            service.serviceKind(),
            "COOLIFY_CORE_SERVICE",
            properties.targetProfileId(),
            service.providerResourceUuid(),
            service.publicBaseUrl(),
            service.healthPath(),
            healthUrl(service),
            status,
            observedStatus,
            message,
            observedAt,
            details
        );
    }

    private PlatformCoreServiceActionSummary actionSummary(PlatformCoreServicesProperties.CoreService service,
                                                           String action,
                                                           String targetProfileId,
                                                           CoolifyActionResponse response) {
        return new PlatformCoreServiceActionSummary(
            service.serviceRef(),
            blankToFallback(service.displayName(), service.serviceRef()),
            action,
            "REQUESTED",
            blankToFallback(response.message(), action + " requested."),
            response.deploymentUuid(),
            targetProfileId,
            service.providerResourceUuid(),
            Instant.now(),
            actionDetails(response)
        );
    }

    private PlatformCoreServicesProperties.CoreService requireService(String serviceRef) {
        String normalized = normalizeRef(serviceRef);
        return properties.services().stream()
            .filter(service -> normalizeRef(service.serviceRef()).equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Platform core service not configured: " + serviceRef));
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Platform core service operations are disabled.");
        }
    }

    private DeploymentTargetProfileEntity requireTargetProfile() {
        String targetProfileId = properties.targetProfileId();
        if (!StringUtils.hasText(targetProfileId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Core service target profile is not configured.");
        }
        return targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Core service target profile was not found: " + targetProfileId
            ));
    }

    private String requireProviderResourceUuid(PlatformCoreServicesProperties.CoreService service) {
        if (!StringUtils.hasText(service.providerResourceUuid())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Core service provider resource UUID is not configured: " + service.serviceRef()
            );
        }
        return service.providerResourceUuid();
    }

    private ObjectNode applicationDetails(CoolifyApplicationSummary application) {
        ObjectNode details = objectMapper.createObjectNode();
        put(details, "uuid", application.uuid());
        put(details, "name", application.name());
        put(details, "fqdn", application.fqdn());
        put(details, "status", application.status());
        put(details, "imageRepository", application.imageRepository());
        put(details, "imageTag", application.imageTag());
        JsonNode raw = application.raw();
        put(details, "gitRepository", text(raw, "git_repository"));
        put(details, "gitBranch", text(raw, "git_branch"));
        put(details, "gitCommitSha", text(raw, "git_commit_sha"));
        put(details, "createdAt", firstText(raw, "created_at", "createdAt"));
        put(details, "updatedAt", firstText(raw, "updated_at", "updatedAt"));
        return details;
    }

    private ObjectNode actionDetails(CoolifyActionResponse response) {
        ObjectNode details = objectMapper.createObjectNode();
        put(details, "message", response.message());
        put(details, "deploymentUuid", response.deploymentUuid());
        return details;
    }

    private ObjectNode errorDetails(RuntimeException ex) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("errorType", ex.getClass().getSimpleName());
        put(details, "message", ex.getMessage());
        return details;
    }

    private ObjectNode emptyDetails() {
        return objectMapper.createObjectNode();
    }

    private String healthUrl(PlatformCoreServicesProperties.CoreService service) {
        if (!StringUtils.hasText(service.publicBaseUrl()) || !StringUtils.hasText(service.healthPath())) {
            return null;
        }
        return service.publicBaseUrl() + service.healthPath();
    }

    private String normalizeObservedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "UNKNOWN";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT).replace(':', '_').replace('-', '_');
        if (normalized.contains("HEALTHY")) {
            return "RUNNING_HEALTHY";
        }
        if (normalized.contains("RUNNING")) {
            return "RUNNING";
        }
        if (normalized.contains("STOPPED") || normalized.contains("EXITED")) {
            return "STOPPED";
        }
        if (normalized.contains("FAILED") || normalized.contains("ERROR")) {
            return "FAILED";
        }
        return normalized;
    }

    private String normalizeRef(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void put(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value);
        }
    }

    private String blankToFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
