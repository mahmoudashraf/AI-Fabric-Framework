package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceHealthSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceProbeSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSourcePreflightSupport;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedProductAdminService {

    private static final String TARGET_TYPE = "MANAGED_PRODUCT_SERVICE";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private final PlatformManagedProductServiceService serviceService;
    private final PlatformManagedProductServiceRepository serviceRepository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformCustomerRepository platformCustomerRepository;
    private final DeploymentRepository deploymentRepository;
    private final PlatformConsumerRepository platformConsumerRepository;
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedProductProvisioningService provisioningService;
    private final PlatformAuditService platformAuditService;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final ShopifyStoreSourcePreflightSupport sourcePreflightSupport;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PlatformManagedProductAdminService(PlatformManagedProductServiceService serviceService,
                                              PlatformManagedProductServiceRepository serviceRepository,
                                              ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                              PlatformCustomerRepository platformCustomerRepository,
                                              DeploymentRepository deploymentRepository,
                                              PlatformConsumerRepository platformConsumerRepository,
                                              PlatformSecretService platformSecretService,
                                              PlatformManagedProductProvisioningService provisioningService,
                                              PlatformAuditService platformAuditService,
                                              RailwayGraphqlClient railwayGraphqlClient,
                                              ShopifyStoreSourcePreflightSupport sourcePreflightSupport,
                                              ObjectMapper objectMapper) {
        this.serviceService = serviceService;
        this.serviceRepository = serviceRepository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.platformCustomerRepository = platformCustomerRepository;
        this.deploymentRepository = deploymentRepository;
        this.platformConsumerRepository = platformConsumerRepository;
        this.platformSecretService = platformSecretService;
        this.provisioningService = provisioningService;
        this.platformAuditService = platformAuditService;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.sourcePreflightSupport = sourcePreflightSupport;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    }

    public List<ShopifyStoreConnectionSummary> listDependents(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        return shopifyStoreConnectionRepository.findAllByProductServiceIdOrderByShopDomainAsc(service.getId()).stream()
            .map(connection -> toSummary(connection, service))
            .toList();
    }

    public List<PlatformAuditEventSummary> listActivity(String serviceRef) {
        serviceService.requireService(serviceRef);
        return platformAuditService.listRecentEventsForTarget(TARGET_TYPE, serviceRef, 100);
    }

    @Transactional
    public PlatformManagedProductServiceHealthSummary getHealth(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        boolean railwayManaged = hasText(service.getRailwayServiceId()) && hasText(service.getRailwayEnvironmentId());
        boolean secretConfigured = !hasText(service.getSecretName()) || platformSecretService.isSecretPresent(service.getSecretName());

        ProbeResult healthProbe = buildHealthProbe(service);
        DriftResult drift = buildDrift(service, railwayManaged, secretConfigured);
        String overallStatus = summarizeStatus(healthProbe.summary.status(), drift.status);
        String overallMessage = firstNonBlank(healthProbe.summary.message(), drift.message, "Managed product service health checked.");

        Instant now = Instant.now();
        ObjectNode details = mutableDetails(service);
        details.put("lastProbeAt", now.toString());
        details.put("lastProbeStatus", overallStatus);
        details.put("lastProbeMessage", overallMessage);
        details.put("driftStatus", drift.status);
        details.put("driftMessage", drift.message);
        if ("READY".equals(overallStatus)) {
            details.put("lastHealthyAt", now.toString());
            details.put("lastSuccessfulProbeAt", now.toString());
        } else if ("FAILED".equals(overallStatus) || "DEGRADED".equals(overallStatus) || "BLOCKED".equals(overallStatus)) {
            details.put("lastFailedProbeAt", now.toString());
        }
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(now);
        serviceRepository.save(service);

        platformAuditService.record(
            "MANAGED_PRODUCT_HEALTH_CHECKED",
            TARGET_TYPE,
            service.getServiceRef(),
            Map.of(
                "serviceRef", service.getServiceRef(),
                "status", overallStatus,
                "driftStatus", drift.status
            )
        );

        return new PlatformManagedProductServiceHealthSummary(
            service.getServiceRef(),
            overallStatus,
            railwayManaged,
            secretConfigured,
            drift.status,
            drift.message,
            text(details, "lastHealthyAt"),
            text(details, "lastProbeAt"),
            text(details, "lastSuccessfulProbeAt"),
            text(details, "lastFailedProbeAt"),
            text(details, "lastProbeStatus"),
            text(details, "lastProbeMessage"),
            healthProbe.summary
        );
    }

    @Transactional
    public PlatformManagedProductServiceSummary reconcile(String serviceRef) {
        return provisioningService.reconcile(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary scale(String serviceRef, Integer desiredReplicas) {
        return provisioningService.scale(serviceRef, desiredReplicas);
    }

    @Transactional
    public PlatformManagedProductServiceSummary rotateSecret(String serviceRef, String value) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        if (!hasText(service.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "Managed product service does not declare a secret to rotate: " + serviceRef);
        }
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "Secret value is required.");
        }
        if (platformSecretService.isManagedSecretName(service.getSecretName())) {
            platformSecretService.upsertManagedSecret(
                service.getSecretName(),
                value.trim(),
                Map.of("serviceRef", service.getServiceRef(), "purpose", "PRODUCT_SERVICE_SECRET")
            );
        } else if (platformSecretService.isSupportedSecretName(service.getSecretName())) {
            platformSecretService.updateSecret(service.getSecretName(), value.trim());
        } else {
            throw new ResponseStatusException(CONFLICT, "Unsupported secret type for managed product service: " + service.getSecretName());
        }
        ObjectNode details = mutableDetails(service);
        details.put("lastSecretRotatedAt", Instant.now().toString());
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        platformAuditService.record(
            "MANAGED_PRODUCT_SECRET_ROTATED",
            TARGET_TYPE,
            service.getServiceRef(),
            Map.of("serviceRef", service.getServiceRef(), "secretName", service.getSecretName())
        );
        return serviceService.getService(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary restart(String serviceRef) {
        return provisioningService.restart(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary forceRecreate(String serviceRef) {
        return provisioningService.forceRecreate(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary decommission(String serviceRef) {
        return provisioningService.decommission(serviceRef);
    }

    private ShopifyStoreConnectionSummary toSummary(ShopifyStoreConnectionEntity entity, PlatformManagedProductServiceEntity service) {
        PlatformCustomerEntity customer = hasText(entity.getCustomerId())
            ? platformCustomerRepository.findById(entity.getCustomerId()).orElse(null)
            : null;
        DeploymentEntity deployment = hasText(entity.getDeploymentId())
            ? deploymentRepository.findById(entity.getDeploymentId()).orElse(null)
            : null;
        PlatformConsumerEntity consumer = hasText(entity.getConsumerId())
            ? platformConsumerRepository.findByConsumerIdIgnoreCase(entity.getConsumerId()).orElse(null)
            : null;
        return new ShopifyStoreConnectionSummary(
            entity.getId(),
            entity.getShopDomain(),
            entity.getDisplayName(),
            service.getId(),
            service.getServiceRef(),
            service.getDisplayName(),
            entity.getCustomerId(),
            customer == null ? null : customer.getName(),
            entity.getDeploymentId(),
            deployment == null ? null : deployment.getName(),
            deployment == null ? null : deployment.getStatus(),
            entity.getConsumerId(),
            consumer == null ? null : consumer.getDisplayName(),
            entity.getInstallStatus(),
            entity.getSyncStatus(),
            entity.getSourceReadinessStatus(),
            entity.getWidgetStatus(),
            entity.getOnboardingStatus(),
            entity.isProductsEnabled(),
            entity.isCollectionsEnabled(),
            entity.isPagesEnabled(),
            entity.isPoliciesEnabled(),
            sourcePreflightSupport.summarizeCredentials(entity.getDetailsJson()),
            sourcePreflightSupport.summarize(entity.getDetailsJson()),
            sourcePreflightSupport.summarizeSync(entity.getDetailsJson()),
            sourcePreflightSupport.summarizeWidget(entity.getDetailsJson()),
            null,
            null,
            entity.getLastSourcePreflightAt(),
            entity.getLastSyncAt(),
            entity.getLastWebhookAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private ProbeResult buildHealthProbe(PlatformManagedProductServiceEntity service) {
        if (!hasText(service.getBaseUrl())) {
            return new ProbeResult(new PlatformManagedProductServiceProbeSummary(
                "BLOCKED",
                "GET",
                null,
                0,
                "Managed product service does not have a baseUrl yet.",
                Instant.now().toString()
            ));
        }
        String endpoint = joinUrl(service.getBaseUrl(), blankToFallback(service.getHealthPath(), "/actuator/health"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(HTTP_TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String status = response.statusCode() >= 200 && response.statusCode() < 300 ? "READY" : "FAILED";
            return new ProbeResult(new PlatformManagedProductServiceProbeSummary(
                status,
                "GET",
                endpoint,
                response.statusCode(),
                status.equals("READY") ? "Managed product service responded successfully." : "Managed product service returned HTTP " + response.statusCode() + ".",
                Instant.now().toString()
            ));
        } catch (Exception ex) {
            return new ProbeResult(new PlatformManagedProductServiceProbeSummary(
                "FAILED",
                "GET",
                endpoint,
                0,
                firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName()),
                Instant.now().toString()
            ));
        }
    }

    private DriftResult buildDrift(PlatformManagedProductServiceEntity service,
                                   boolean railwayManaged,
                                   boolean secretConfigured) {
        if (!secretConfigured) {
            return new DriftResult("SECRET_DRIFT", "Managed product service secret is missing.");
        }
        if ("SHARED_PLATFORM_SERVICE".equalsIgnoreCase(service.getDeploymentMode()) && !railwayManaged) {
            return new DriftResult("RAILWAY_LINKAGE_MISSING", "Railway linkage is missing for this managed product service.");
        }
        if (!hasText(service.getBaseUrl())) {
            return new DriftResult("BASE_URL_MISSING", "Managed product service base URL is not configured.");
        }
        return new DriftResult("NO_DRIFT", "No drift detected.");
    }

    private String summarizeStatus(String probeStatus, String driftStatus) {
        if ("FAILED".equalsIgnoreCase(probeStatus)) {
            return "FAILED";
        }
        if ("BLOCKED".equalsIgnoreCase(probeStatus)) {
            return "BLOCKED";
        }
        if ("NO_DRIFT".equalsIgnoreCase(driftStatus) && "READY".equalsIgnoreCase(probeStatus)) {
            return "READY";
        }
        return "DEGRADED";
    }

    private ObjectNode mutableDetails(PlatformManagedProductServiceEntity service) {
        try {
            JsonNode parsed = hasText(service.getDetailsJson())
                ? objectMapper.readTree(service.getDetailsJson())
                : objectMapper.createObjectNode();
            return parsed.isObject() ? (ObjectNode) parsed.deepCopy() : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return trimToNull(value.asText());
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private String blankToFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProbeResult(PlatformManagedProductServiceProbeSummary summary) {
    }

    private record DriftResult(String status, String message) {
    }
}
