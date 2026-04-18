package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceHealthSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceBillingSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceInstallOverview;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceOverviewSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceProbeSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreBillingSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreOverview;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceWebhookSubscriptionOverview;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceWebhookSubscriptionSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceWebhookSubscriptionTopicSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceUsageEventSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceUsageSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreCapabilitySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreReadinessEvaluator;
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
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedProductAdminService {

    private static final String TARGET_TYPE = "MANAGED_PRODUCT_SERVICE";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final String BRIDGE_ADMIN_API_KEY_HEADER = "X-BRIDGE-API-KEY";

    private final PlatformManagedProductServiceService serviceService;
    private final PlatformManagedProductServiceRepository serviceRepository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformCustomerRepository platformCustomerRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final PlatformConsumerRepository platformConsumerRepository;
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedProductProvisioningService provisioningService;
    private final PlatformAuditService platformAuditService;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final ShopifyStoreSourcePreflightSupport sourcePreflightSupport;
    private final ShopifyStoreReadinessEvaluator readinessEvaluator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PlatformManagedProductAdminService(PlatformManagedProductServiceService serviceService,
                                              PlatformManagedProductServiceRepository serviceRepository,
                                              ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                              PlatformCustomerRepository platformCustomerRepository,
                                              DeploymentRepository deploymentRepository,
                                              DeploymentVersionRepository deploymentVersionRepository,
                                              DeploymentReleaseRepository deploymentReleaseRepository,
                                              PlatformConsumerRepository platformConsumerRepository,
                                              PlatformSecretService platformSecretService,
                                              PlatformManagedProductProvisioningService provisioningService,
                                              PlatformAuditService platformAuditService,
                                              RailwayGraphqlClient railwayGraphqlClient,
                                              ShopifyStoreSourcePreflightSupport sourcePreflightSupport,
                                              ShopifyStoreReadinessEvaluator readinessEvaluator,
                                              ObjectMapper objectMapper) {
        this.serviceService = serviceService;
        this.serviceRepository = serviceRepository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.platformCustomerRepository = platformCustomerRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.platformConsumerRepository = platformConsumerRepository;
        this.platformSecretService = platformSecretService;
        this.provisioningService = provisioningService;
        this.platformAuditService = platformAuditService;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.sourcePreflightSupport = sourcePreflightSupport;
        this.readinessEvaluator = readinessEvaluator;
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

    public PlatformManagedProductServiceOverviewSummary getOverview(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        if (!hasText(service.getBaseUrl())) {
            return degradedOverview(service, "Managed product service does not declare a base URL yet.");
        }
        if (!hasText(service.getSecretName())) {
            return degradedOverview(service, "Managed product service does not declare an admin secret for overview access.");
        }
        String apiKey = platformSecretService.resolveSecret(service.getSecretName());
        if (!hasText(apiKey)) {
            return degradedOverview(service, "Managed product service admin secret is missing.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(service.getBaseUrl(), "/api/admin/overview")))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header(BRIDGE_ADMIN_API_KEY_HEADER, apiKey)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return degradedOverview(service, "Managed product overview request failed with HTTP " + response.statusCode() + ".");
            }
            JsonNode body = objectMapper.readTree(response.body());
            return new PlatformManagedProductServiceOverviewSummary(
                service.getServiceRef(),
                text(body, "status", "DEGRADED"),
                summarizeOverviewMessage(body),
                text(body, "appName", null),
                text(body, "productFamily", service.getProductFamily()),
                text(body, "serviceKind", service.getServiceKind()),
                text(body, "environmentScope", service.getEnvironmentScope()),
                text(body, "platformBaseUrl", null),
                text(body, "publicBaseUrl", null),
                body.path("adminApiKeyConfigured").asBoolean(false),
                text(body, "serverStartedAt", null),
                parseInstallOverview(body.path("installs")),
                parseStoreOverview(body.path("stores")),
                parseWebhookSubscriptionOverview(body.path("webhookSubscriptions")),
                parseBillingOverview(body.path("billing")),
                parseUsageOverview(body.path("usage")),
                stringList(body.path("capabilities")),
                stringList(body.path("notYetImplemented"))
            );
        } catch (Exception ex) {
            return degradedOverview(service, firstNonBlank(ex.getMessage(), "Managed product overview request failed."));
        }
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

    public PlatformManagedProductServiceWebhookSubscriptionSummary getStoreWebhookSubscriptions(String serviceRef, String shopDomain) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        if (!hasText(service.getBaseUrl())) {
            throw new ResponseStatusException(CONFLICT, "Managed product service does not declare a base URL yet: " + serviceRef);
        }
        if (!hasText(service.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "Managed product service does not declare an admin secret: " + serviceRef);
        }
        shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), shopDomain)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify store " + shopDomain + " is not mapped to managed product service " + serviceRef + "."
            ));

        String apiKey = platformSecretService.resolveSecret(service.getSecretName());
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(CONFLICT, "Managed product service admin secret is missing.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(service.getBaseUrl(), "/api/admin/stores/" + encodePath(shopDomain) + "/webhook-subscriptions")))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header(BRIDGE_ADMIN_API_KEY_HEADER, apiKey)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(CONFLICT, "Managed product webhook subscription request failed with HTTP " + response.statusCode() + ".");
            }
            return parseWebhookSubscriptionSummary(objectMapper.readTree(response.body()));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, firstNonBlank(ex.getMessage(), "Managed product webhook subscription request failed."), ex);
        }
    }

    public PlatformManagedProductServiceStoreBillingSummary getStoreBillingSummary(String serviceRef, String shopDomain) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        if (!hasText(service.getBaseUrl())) {
            throw new ResponseStatusException(CONFLICT, "Managed product service does not declare a base URL yet: " + serviceRef);
        }
        if (!hasText(service.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "Managed product service does not declare an admin secret: " + serviceRef);
        }
        shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), shopDomain)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify store " + shopDomain + " is not mapped to managed product service " + serviceRef + "."
            ));

        String apiKey = platformSecretService.resolveSecret(service.getSecretName());
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(CONFLICT, "Managed product service admin secret is missing.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(service.getBaseUrl(), "/api/admin/stores/" + encodePath(shopDomain) + "/billing-summary")))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header(BRIDGE_ADMIN_API_KEY_HEADER, apiKey)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(CONFLICT, "Managed product billing summary request failed with HTTP " + response.statusCode() + ".");
            }
            return parseStoreBillingSummary(shopDomain, objectMapper.readTree(response.body()));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, firstNonBlank(ex.getMessage(), "Managed product billing summary request failed."), ex);
        }
    }

    @Transactional
    public PlatformManagedProductServiceSummary forceRecreate(String serviceRef) {
        return provisioningService.forceRecreate(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary decommission(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        long dependentStores = shopifyStoreConnectionRepository.countByProductServiceId(service.getId());
        if (dependentStores > 0) {
            throw new ResponseStatusException(
                CONFLICT,
                "Managed product service " + serviceRef + " still has " + dependentStores
                    + " dependent Shopify store mapping(s). Remove the mappings before decommissioning the service."
            );
        }
        return provisioningService.decommission(serviceRef);
    }

    private ShopifyStoreConnectionSummary toSummary(ShopifyStoreConnectionEntity entity, PlatformManagedProductServiceEntity service) {
        PlatformCustomerEntity customer = hasText(entity.getCustomerId())
            ? platformCustomerRepository.findById(entity.getCustomerId()).orElse(null)
            : null;
        DeploymentEntity deployment = hasText(entity.getDeploymentId())
            ? deploymentRepository.findById(entity.getDeploymentId()).orElse(null)
            : null;
        DeploymentVersionEntity latestVersion = deployment == null
            ? null
            : deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc(deployment.getId()).stream().findFirst().orElse(null);
        DeploymentReleaseEntity latestRelease = deployment == null
            ? null
            : deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).orElse(null);
        PlatformConsumerEntity consumer = hasText(entity.getConsumerId())
            ? platformConsumerRepository.findByConsumerIdIgnoreCase(entity.getConsumerId()).orElse(null)
            : null;
        var credentials = sourcePreflightSupport.summarizeCredentials(entity.getDetailsJson());
        var sourcePreflight = sourcePreflightSupport.summarize(entity.getDetailsJson());
        var syncDetail = sourcePreflightSupport.summarizeSync(entity.getDetailsJson());
        var webhookDetail = sourcePreflightSupport.summarizeWebhook(entity.getDetailsJson());
        var widgetDetail = sourcePreflightSupport.summarizeWidget(entity.getDetailsJson());
        var capabilities = summarizeCapabilities(latestVersion);
        var latestReleaseSummary = toReleaseSummary(latestRelease);
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
            credentials,
            sourcePreflight,
            syncDetail,
            webhookDetail,
            widgetDetail,
            capabilities,
            readinessEvaluator.evaluate(entity, credentials, sourcePreflight, syncDetail, widgetDetail, latestReleaseSummary),
            toVersionSummary(latestVersion),
            latestReleaseSummary,
            entity.getLastSourcePreflightAt(),
            entity.getLastSyncAt(),
            entity.getLastWebhookAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private ShopifyStoreCapabilitySummary summarizeCapabilities(DeploymentVersionEntity version) {
        if (version == null) {
            return null;
        }
        Set<String> actionNames = textSet(sourcePreflightSupport.readJsonNode(version.getActionsConfigJson()), "actions", "name");
        Set<String> knowledgeSourceIds = textSet(sourcePreflightSupport.readJsonNode(version.getKnowledgeSourceConfigJson()), "sources", "id");
        Set<String> shellModuleIds = textSet(sourcePreflightSupport.readJsonNode(version.getShellConfigJson()), "modules", "id");
        Set<String> marketplaceDatasetIds = textSet(sourcePreflightSupport.readJsonNode(version.getMarketplaceDatasetConfigJson()), "datasets", "datasetId");
        return new ShopifyStoreCapabilitySummary(
            actionNames.size(),
            knowledgeSourceIds.size(),
            shellModuleIds.size(),
            marketplaceDatasetIds.size(),
            List.copyOf(actionNames),
            List.copyOf(knowledgeSourceIds),
            List.copyOf(shellModuleIds),
            List.copyOf(marketplaceDatasetIds)
        );
    }

    private Set<String> textSet(JsonNode root, String arrayField, String valueField) {
        if (root == null || !root.path(arrayField).isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode node : root.path(arrayField)) {
            String value = node.path(valueField).asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private DeploymentVersionSummary toVersionSummary(DeploymentVersionEntity version) {
        if (version == null) {
            return null;
        }
        return new DeploymentVersionSummary(
            version.getId(),
            version.getDeploymentId(),
            version.getSourceDraftId(),
            version.getVersionLabel(),
            version.getStatus(),
            version.getConfigHash(),
            version.isReindexRequired(),
            version.getPublishedAt()
        );
    }

    private DeploymentReleaseSummary toReleaseSummary(DeploymentReleaseEntity release) {
        if (release == null) {
            return null;
        }
        return new DeploymentReleaseSummary(
            release.getId(),
            release.getDeploymentId(),
            release.getDeploymentVersionId(),
            release.getStatus(),
            release.getVerificationStatus(),
            release.getProvisioningStatus(),
            release.getProvisioningTarget(),
            release.getCurrentStepKey(),
            release.getCurrentStepDescription(),
            release.getErrorMessage(),
            release.getVerificationRunId(),
            sourcePreflightSupport.readJsonNode(release.getProvisioningDetailsJson()),
            release.getCreatedAt(),
            release.getAppliedAt(),
            release.getUpdatedAt()
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

    private PlatformManagedProductServiceOverviewSummary degradedOverview(PlatformManagedProductServiceEntity service, String message) {
        return new PlatformManagedProductServiceOverviewSummary(
            service.getServiceRef(),
            "DEGRADED",
            message,
            service.getDisplayName(),
            service.getProductFamily(),
            service.getServiceKind(),
            service.getEnvironmentScope(),
            null,
            service.getBaseUrl(),
            hasText(service.getSecretName()) && platformSecretService.isSecretPresent(service.getSecretName()),
            null,
            new PlatformManagedProductServiceInstallOverview(0, 0, 0, 0, null, null),
            new PlatformManagedProductServiceStoreOverview("FAILED", message, 0, 0, 0, 0, 0, null),
            new PlatformManagedProductServiceWebhookSubscriptionOverview("BLOCKED", message, null, 0, List.of()),
            null,
            null,
            List.of(),
            List.of()
        );
    }

    private PlatformManagedProductServiceInstallOverview parseInstallOverview(JsonNode node) {
        return new PlatformManagedProductServiceInstallOverview(
            node.path("totalCount").asInt(0),
            node.path("installedCount").asInt(0),
            node.path("uninstalledCount").asInt(0),
            node.path("credentialReadyCount").asInt(0),
            text(node, "lastAuthenticatedAt", null),
            text(node, "lastUninstalledAt", null)
        );
    }

    private PlatformManagedProductServiceStoreOverview parseStoreOverview(JsonNode node) {
        return new PlatformManagedProductServiceStoreOverview(
            text(node, "platformAccessStatus", "FAILED"),
            text(node, "platformAccessMessage", "Store overview unavailable."),
            node.path("totalCount").asInt(0),
            node.path("readyForGoLiveCount").asInt(0),
            node.path("storefrontReadyCount").asInt(0),
            node.path("liveCount").asInt(0),
            node.path("blockedCount").asInt(0),
            text(node, "lastWebhookAt", null)
        );
    }

    private PlatformManagedProductServiceWebhookSubscriptionOverview parseWebhookSubscriptionOverview(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return new PlatformManagedProductServiceWebhookSubscriptionOverview(
                "UNKNOWN",
                "Managed product service did not return webhook subscription overview data.",
                null,
                0,
                List.of()
            );
        }
        return new PlatformManagedProductServiceWebhookSubscriptionOverview(
            text(node, "status", "UNKNOWN"),
            text(node, "message", "Managed product service did not return webhook subscription overview data."),
            text(node, "webhookUri", null),
            node.path("expectedCount").asInt(0),
            stringList(node.path("expectedTopics"))
        );
    }

    private PlatformManagedProductServiceBillingSummary parseBillingOverview(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return null;
        }
        return new PlatformManagedProductServiceBillingSummary(
            text(node, "mode", null),
            text(node, "planName", null),
            text(node, "status", null),
            node.path("merchantApprovalRequired").asBoolean(false),
            node.path("launchBlocked").asBoolean(false),
            text(node, "message", null)
        );
    }

    private PlatformManagedProductServiceUsageSummary parseUsageOverview(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return null;
        }
        return new PlatformManagedProductServiceUsageSummary(
            text(node, "generatedAt", null),
            text(node, "lastActivityAt", null),
            node.path("activeShopsToday").asInt(0),
            node.path("activeShopsLast7Days").asInt(0),
            node.path("totalToday").asLong(0L),
            node.path("totalLast7Days").asLong(0L),
            parseUsageBreakdown(node.path("todayBreakdown")),
            parseUsageBreakdown(node.path("last7DayBreakdown"))
        );
    }

    private PlatformManagedProductServiceWebhookSubscriptionSummary parseWebhookSubscriptionSummary(JsonNode node) {
        List<PlatformManagedProductServiceWebhookSubscriptionTopicSummary> topics = new ArrayList<>();
        JsonNode topicNodes = node.path("topics");
        if (topicNodes.isArray()) {
            for (JsonNode topic : topicNodes) {
                topics.add(new PlatformManagedProductServiceWebhookSubscriptionTopicSummary(
                    text(topic, "topic", null),
                    text(topic, "expectedName", null),
                    text(topic, "status", "UNKNOWN"),
                    text(topic, "subscriptionId", null),
                    text(topic, "subscriptionName", null),
                    text(topic, "subscriptionUri", null),
                    text(topic, "message", null)
                ));
            }
        }
        return new PlatformManagedProductServiceWebhookSubscriptionSummary(
            text(node, "shopDomain", null),
            text(node, "status", "FAILED"),
            text(node, "message", "Managed product service did not return webhook subscription diagnostics."),
            text(node, "webhookUri", null),
            node.path("expectedCount").asInt(0),
            node.path("readyCount").asInt(0),
            node.path("missingCount").asInt(0),
            node.path("driftedCount").asInt(0),
            text(node, "checkedAt", null),
            List.copyOf(topics)
        );
    }

    private PlatformManagedProductServiceStoreBillingSummary parseStoreBillingSummary(String shopDomain, JsonNode node) {
        return new PlatformManagedProductServiceStoreBillingSummary(
            shopDomain,
            text(node, "mode", null),
            text(node, "planName", null),
            text(node, "status", "UNKNOWN"),
            node.path("merchantApprovalRequired").asBoolean(false),
            node.path("launchBlocked").asBoolean(false),
            text(node, "message", "Managed product service did not return store billing diagnostics.")
        );
    }

    private List<PlatformManagedProductServiceUsageEventSummary> parseUsageBreakdown(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<PlatformManagedProductServiceUsageEventSummary> values = new ArrayList<>();
        for (JsonNode item : node) {
            String eventType = text(item, "eventType", null);
            if (!hasText(eventType)) {
                continue;
            }
            values.add(new PlatformManagedProductServiceUsageEventSummary(eventType, item.path("count").asLong(0L)));
        }
        return List.copyOf(values);
    }

    private String summarizeOverviewMessage(JsonNode body) {
        String status = text(body, "status", "DEGRADED");
        String platformAccessMessage = text(body.path("stores"), "platformAccessMessage", null);
        if ("READY".equalsIgnoreCase(status)) {
            return firstNonBlank(platformAccessMessage, "Managed product overview resolved successfully.");
        }
        return firstNonBlank(platformAccessMessage, "Managed product overview is degraded.");
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .toList();
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        JsonNode child = node.path(fieldName);
        return child.isMissingNode() || child.isNull() ? fallback : child.asText(fallback);
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

    private String encodePath(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
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
