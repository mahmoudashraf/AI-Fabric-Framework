package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.retirement.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceShopifyBillingConfigSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceShopifyBillingConfigSupport;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedProductProvisioningService {

    private static final String CUSTOMER_ACCOUNT_MCP_CLIENT_ID_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_ID";
    private static final String CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET";
    private static final String CUSTOMER_ACCOUNT_MCP_REDIRECT_URI_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_REDIRECT_URI";
    private static final String CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN";
    private static final String CUSTOMER_ACCOUNT_MCP_SCOPES_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES";
    private static final String CUSTOMER_ACCOUNT_MCP_PROTECTED_APPROVED_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED";
    private static final String CUSTOMER_ACCOUNT_MCP_STATE_TTL_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STATE_TTL";
    private static final String CUSTOMER_ACCOUNT_MCP_SESSION_TTL_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SESSION_TTL";
    private static final String CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT";
    private static final String CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT_SECRET = "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT";

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformProductProvisioningProperties productProvisioningProperties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final CoolifyTargetProfileResolver coolifyTargetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedProductServiceRepository serviceRepository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformManagedProductServiceService serviceService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final CustomerSelfServiceProperties customerSelfServiceProperties;
    private final CoolifyHostVolumeReclamationService hostVolumeReclamationService;

    @Autowired
    public PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                                     PlatformProductProvisioningProperties productProvisioningProperties,
                                                     PlatformDeliveryProperties deliveryProperties,
                                                     RailwayGraphqlClient railwayGraphqlClient,
                                                     CoolifyTargetProfileResolver coolifyTargetProfileResolver,
                                                     CoolifyApiClient coolifyApiClient,
                                                     DeploymentTargetProfileRepository targetProfileRepository,
                                                     PlatformSecretService platformSecretService,
                                                     PlatformManagedProductServiceRepository serviceRepository,
                                                     ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                                     PlatformManagedProductServiceService serviceService,
                                                     PlatformAuditService platformAuditService,
                                                     ObjectMapper objectMapper,
                                                     CustomerSelfServiceProperties customerSelfServiceProperties,
                                                     CoolifyHostVolumeReclamationService hostVolumeReclamationService) {
        this.provisioningProperties = provisioningProperties;
        this.productProvisioningProperties = productProvisioningProperties;
        this.deliveryProperties = deliveryProperties;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.coolifyTargetProfileResolver = coolifyTargetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.targetProfileRepository = targetProfileRepository;
        this.platformSecretService = platformSecretService;
        this.serviceRepository = serviceRepository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.serviceService = serviceService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.customerSelfServiceProperties = customerSelfServiceProperties;
        this.hostVolumeReclamationService = hostVolumeReclamationService;
    }

    PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                             PlatformProductProvisioningProperties productProvisioningProperties,
                                             PlatformDeliveryProperties deliveryProperties,
                                             RailwayGraphqlClient railwayGraphqlClient,
                                             CoolifyTargetProfileResolver coolifyTargetProfileResolver,
                                             CoolifyApiClient coolifyApiClient,
                                             DeploymentTargetProfileRepository targetProfileRepository,
                                             PlatformSecretService platformSecretService,
                                             PlatformManagedProductServiceRepository serviceRepository,
                                             ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                             PlatformManagedProductServiceService serviceService,
                                             PlatformAuditService platformAuditService, ObjectMapper objectMapper) {
        this(provisioningProperties, productProvisioningProperties, deliveryProperties, railwayGraphqlClient,
            coolifyTargetProfileResolver, coolifyApiClient, targetProfileRepository, platformSecretService,
            serviceRepository, shopifyStoreConnectionRepository, serviceService, platformAuditService, objectMapper,
            new CustomerSelfServiceProperties(Map.of(), 0),
            new CoolifyHostVolumeReclamationService((intent, serverUuid, command) -> {
                throw new IllegalStateException("Provider host verification is not configured for this test.");
            }));
    }

    PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                             PlatformProductProvisioningProperties productProvisioningProperties,
                                             PlatformDeliveryProperties deliveryProperties,
                                             RailwayGraphqlClient railwayGraphqlClient,
                                             CoolifyTargetProfileResolver coolifyTargetProfileResolver,
                                             CoolifyApiClient coolifyApiClient,
                                             DeploymentTargetProfileRepository targetProfileRepository,
                                             PlatformSecretService platformSecretService,
                                             PlatformManagedProductServiceRepository serviceRepository,
                                             ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                             PlatformManagedProductServiceService serviceService,
                                             PlatformAuditService platformAuditService, ObjectMapper objectMapper,
                                             CustomerSelfServiceProperties customerSelfServiceProperties) {
        this(provisioningProperties, productProvisioningProperties, deliveryProperties, railwayGraphqlClient,
            coolifyTargetProfileResolver, coolifyApiClient, targetProfileRepository, platformSecretService,
            serviceRepository, shopifyStoreConnectionRepository, serviceService, platformAuditService, objectMapper,
            customerSelfServiceProperties,
            new CoolifyHostVolumeReclamationService((intent, serverUuid, command) -> {
                throw new IllegalStateException("Provider host verification is not configured for this test.");
            }));
    }

    PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                             PlatformProductProvisioningProperties productProvisioningProperties,
                                             PlatformDeliveryProperties deliveryProperties,
                                             RailwayGraphqlClient railwayGraphqlClient,
                                             PlatformSecretService platformSecretService,
                                             PlatformManagedProductServiceRepository serviceRepository,
                                             ShopifyStoreConnectionRepository shopifyStoreConnectionRepository,
                                             PlatformManagedProductServiceService serviceService,
                                             PlatformAuditService platformAuditService,
                                             ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            productProvisioningProperties,
            deliveryProperties,
            railwayGraphqlClient,
            null,
            null,
            null,
            platformSecretService,
            serviceRepository,
            shopifyStoreConnectionRepository,
            serviceService,
            platformAuditService,
            objectMapper
        );
    }

    @Transactional
    public PlatformManagedProductServiceSummary reconcile(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        if (requiresCoolifyLifecycle(service)) {
            return reconcileCoolify(service);
        }
        if (!requiresRailwayLifecycle(service)) {
            if (hasText(service.getBaseUrl()) && !"ACTIVE".equalsIgnoreCase(service.getStatus())) {
                service.setStatus("ACTIVE");
                service.setUpdatedAt(Instant.now());
                serviceRepository.save(service);
            }
            return serviceService.getService(serviceRef);
        }

        service.setStatus("PROVISIONING");
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);

        try {
            ReconciledRailwayService reconciled = reconcileRailwayService(service);
            finalizeActiveService(
                service,
                reconciled.projectId(),
                reconciled.environmentId(),
                reconciled.serviceId(),
                reconciled.instance(),
                reconciled.publicBaseUrl(),
                reconciled.deploymentId(),
                "Railway product service reconciled successfully."
            );
            platformAuditService.record(
                "MANAGED_PRODUCT_RECONCILED",
                "MANAGED_PRODUCT_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "deploymentId", reconciled.deploymentId(),
                    "status", service.getStatus()
                )
            );
            return serviceService.getService(serviceRef);
        } catch (RuntimeException ex) {
            markLifecycleFailure(service, "FAILED", ex.getMessage(), "lastReconcileStatus", "lastReconcileMessage");
            platformAuditService.record(
                "MANAGED_PRODUCT_RECONCILE_FAILED",
                "MANAGED_PRODUCT_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "error", blankToFallback(ex.getMessage(), ex.getClass().getSimpleName())
                )
            );
            throw ex;
        }
    }

    @Transactional
    public PlatformManagedProductServiceSummary reconcile(String serviceRef, String targetProfileId) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        String normalizedTargetProfileId = trimToNull(targetProfileId);
        if (hasText(normalizedTargetProfileId)) {
            ObjectNode details = mutableDetails(service);
            alignCoolifyDomainWithTargetProfile(service, details, normalizedTargetProfileId);
            details.put("providerType", "COOLIFY");
            details.put("targetProfileId", normalizedTargetProfileId);
            service.setDetailsJson(details.toPrettyString());
            service.setUpdatedAt(Instant.now());
            serviceRepository.save(service);
        }
        return reconcile(serviceRef);
    }

    private void alignCoolifyDomainWithTargetProfile(PlatformManagedProductServiceEntity service,
                                                      ObjectNode details,
                                                      String targetProfileId) {
        if (targetProfileRepository == null || coolifyTargetProfileResolver == null) {
            return;
        }
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Coolify target profile not found: " + targetProfileId));
        CoolifyTargetProfileConfig config = coolifyTargetProfileResolver.requireConnection(profile).config();
        String previousTargetProfileId = trimToNull(details.path("targetProfileId").asText(null));
        boolean targetChanged = hasText(previousTargetProfileId)
            && !previousTargetProfileId.equals(targetProfileId);
        boolean domainOutsideTarget = hasText(service.getBaseUrl())
            && !coolifyDomainMatchesProfile(service.getBaseUrl(), config);
        if (!targetChanged && !domainOutsideTarget) {
            return;
        }
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        details.remove("coolifyFqdn");
    }

    private boolean coolifyDomainMatchesProfile(String domains, CoolifyTargetProfileConfig config) {
        String suffix = trimToNull(config.defaultPublicDomainSuffix());
        if (!hasText(suffix)) {
            return true;
        }
        String normalizedSuffix = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        String lowerSuffix = normalizedSuffix.toLowerCase(Locale.ROOT);
        for (String domain : domains.split(",")) {
            String host = normalizedUrlHost(domain);
            if (hasText(host)
                && (host.equalsIgnoreCase(normalizedSuffix)
                || host.toLowerCase(Locale.ROOT).endsWith("." + lowerSuffix))) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public PlatformManagedProductServiceSummary scale(String serviceRef, Integer desiredReplicas) {
        serviceService.updateDesiredReplicas(serviceRef, desiredReplicas);
        return reconcile(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary restart(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        if (requiresCoolifyLifecycle(service)) {
            return restartCoolify(service);
        }
        if (!requiresRailwayLifecycle(service)) {
            return serviceService.getService(serviceRef);
        }
        if (!hasText(service.getRailwayEnvironmentId()) || !hasText(service.getRailwayServiceId())) {
            return reconcile(serviceRef);
        }

        service.setStatus("PROVISIONING");
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);

        try {
            String deploymentId = railwayGraphqlClient.deployService(service.getRailwayServiceId(), service.getRailwayEnvironmentId());
            RailwayApiProvisioningProvider.awaitSuccessfulDeployment(
                deploymentId,
                sharedServiceName(service),
                provisioningProperties.deploymentTimeout(),
                productProvisioningProperties.pollInterval(),
                () -> railwayGraphqlClient.getDeployment(deploymentId),
                duration -> Thread.sleep(Math.max(duration.toMillis(), 0L)),
                ex -> {
                }
            );

            RailwayGraphqlClient.RailwayServiceInstanceSummary instance = railwayGraphqlClient.getServiceInstance(
                service.getRailwayEnvironmentId(),
                service.getRailwayServiceId()
            );
            String publicBaseUrl = ensureServiceDomain(
                service.getRailwayProjectId(),
                service.getRailwayEnvironmentId(),
                service.getRailwayServiceId()
            );
            finalizeActiveService(
                service,
                blankToFallback(service.getRailwayProjectId(), null),
                blankToFallback(service.getRailwayEnvironmentId(), null),
                blankToFallback(service.getRailwayServiceId(), null),
                instance,
                publicBaseUrl,
                deploymentId,
                "Managed product service restarted successfully."
            );
            ObjectNode details = mutableDetails(service);
            details.put("lastRestartedAt", Instant.now().toString());
            details.put("lastRestartStatus", "SUCCESS");
            details.put("lastRestartMessage", "Managed product service restarted successfully.");
            service.setDetailsJson(details.toPrettyString());
            serviceRepository.save(service);
            platformAuditService.record(
                "MANAGED_PRODUCT_RESTARTED",
                "MANAGED_PRODUCT_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "deploymentId", deploymentId
                )
            );
            return serviceService.getService(serviceRef);
        } catch (RuntimeException ex) {
            markLifecycleFailure(service, "FAILED", ex.getMessage(), "lastRestartStatus", "lastRestartMessage");
            platformAuditService.record(
                "MANAGED_PRODUCT_RESTART_FAILED",
                "MANAGED_PRODUCT_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "error", blankToFallback(ex.getMessage(), ex.getClass().getSimpleName())
                )
            );
            throw ex;
        }
    }

    @Transactional
    public PlatformManagedProductServiceEntity refreshRailwayBindingFromWorkspace(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        if (isCoolifyManaged(service)) {
            return service;
        }
        if (!requiresRailwayLifecycle(service)) {
            return service;
        }

        RailwayGraphqlClient.RailwayProjectSnapshot project = resolveCurrentProjectSnapshot(service);
        RailwayGraphqlClient.RailwayEnvironmentSummary environment = project == null
            ? null
            : project.environmentNamed(resolveEnvironmentName(service));
        RailwayGraphqlClient.RailwayServiceSummary railwayService = project == null
            ? null
            : project.serviceNamed(sharedServiceName(service));
        boolean needsRefresh = !hasText(service.getRailwayProjectId())
            || !hasText(service.getRailwayEnvironmentId())
            || !hasText(service.getRailwayServiceId())
            || project == null
            || environment == null
            || railwayService == null
            || !service.getRailwayProjectId().equals(project.id())
            || !service.getRailwayEnvironmentId().equals(environment.id())
            || !service.getRailwayServiceId().equals(railwayService.id());
        if (!needsRefresh) {
            return service;
        }

        RailwayGraphqlClient.RailwayProjectSnapshot discoveredProject = railwayGraphqlClient.findProjectByName(
            provisioningProperties.workspaceId(),
            sharedProjectName(service)
        );
        if (discoveredProject == null) {
            return service;
        }
        RailwayGraphqlClient.RailwayEnvironmentSummary discoveredEnvironment = discoveredProject.environmentNamed(resolveEnvironmentName(service));
        if (discoveredEnvironment == null) {
            return service;
        }
        RailwayGraphqlClient.RailwayServiceSummary discoveredService = discoveredProject.serviceNamed(sharedServiceName(service));
        if (discoveredService == null) {
            return service;
        }

        RailwayGraphqlClient.RailwayServiceInstanceSummary instance = railwayGraphqlClient.getServiceInstance(
            discoveredEnvironment.id(),
            discoveredService.id()
        );
        String publicBaseUrl = hasText(service.getBaseUrl())
            ? service.getBaseUrl().trim()
            : ensureServiceDomain(discoveredProject.id(), discoveredEnvironment.id(), discoveredService.id());
        String lastDeploymentId = trimToNull(mutableDetails(service).path("lastDeploymentId").asText(null));
        finalizeActiveService(
            service,
            discoveredProject.id(),
            discoveredEnvironment.id(),
            discoveredService.id(),
            instance,
            publicBaseUrl,
            lastDeploymentId,
            "Railway product service linkage refreshed from workspace inventory."
        );
        platformAuditService.record(
            "MANAGED_PRODUCT_RAILWAY_BINDING_REFRESHED",
            "MANAGED_PRODUCT_SERVICE",
            service.getServiceRef(),
            Map.of(
                "serviceRef", service.getServiceRef(),
                "railwayProjectId", discoveredProject.id(),
                "railwayEnvironmentId", discoveredEnvironment.id(),
                "railwayServiceId", discoveredService.id()
            )
        );
        return service;
    }

    @Transactional
    public PlatformManagedProductServiceSummary forceRecreate(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        if (requiresCoolifyLifecycle(service)) {
            return forceRecreateCoolify(service);
        }
        if (!requiresRailwayLifecycle(service)) {
            return serviceService.getService(serviceRef);
        }

        platformAuditService.record(
            "MANAGED_PRODUCT_FORCE_RECREATE_REQUESTED",
            "MANAGED_PRODUCT_SERVICE",
            service.getServiceRef(),
            Map.of("serviceRef", service.getServiceRef())
        );

        RailwayGraphqlClient.RailwayProjectSnapshot existingProject = null;
        if (hasText(service.getRailwayProjectId())) {
            try {
                existingProject = railwayGraphqlClient.getProject(service.getRailwayProjectId());
            } catch (RuntimeException ignored) {
                existingProject = null;
            }
        }
        if (existingProject == null) {
            existingProject = railwayGraphqlClient.findProjectByName(
                provisioningProperties.workspaceId(),
                sharedProjectName(service)
            );
        }
        if (existingProject != null && hasText(existingProject.id())) {
            railwayGraphqlClient.deleteProject(existingProject.id());
            awaitProjectDeletion(existingProject.id(), sharedProjectName(service));
        }

        clearRailwayBinding(service);
        return reconcile(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary decommission(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
        serviceService.requireNotOwnedDependency(service);
        long dependentStores = shopifyStoreConnectionRepository.countByProductServiceId(service.getId());
        if (dependentStores > 0) {
            throw new ResponseStatusException(
                CONFLICT,
                "Managed product service still has " + dependentStores + " dependent store mapping(s)."
            );
        }
        if (requiresCoolifyLifecycle(service)) {
            return decommissionCoolify(service);
        }

        if (hasText(service.getRailwayProjectId())) {
            railwayGraphqlClient.deleteProject(service.getRailwayProjectId());
            awaitProjectDeletion(service.getRailwayProjectId(), sharedProjectName(service));
        } else if (hasText(service.getRailwayServiceId())) {
            railwayGraphqlClient.deleteService(service.getRailwayServiceId());
        }

        if (hasText(service.getSecretName()) && platformSecretService.isManagedSecretName(service.getSecretName())) {
            platformSecretService.clearManagedSecret(
                service.getSecretName(),
                Map.of("serviceRef", service.getServiceRef(), "purpose", "PRODUCT_SERVICE_SECRET")
            );
        }

        service.setRailwayProjectId(null);
        service.setRailwayEnvironmentId(null);
        service.setRailwayServiceId(null);
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        service.setActualReplicas(0);
        service.setStatus("DECOMMISSIONED");
        ObjectNode details = mutableDetails(service);
        details.put("lastDecommissionedAt", Instant.now().toString());
        details.put("lastDecommissionStatus", "SUCCESS");
        details.put("lastDecommissionMessage", "Managed product service decommissioned successfully.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        platformAuditService.record(
            "MANAGED_PRODUCT_DECOMMISSIONED",
            "MANAGED_PRODUCT_SERVICE",
            service.getServiceRef(),
            Map.of("serviceRef", service.getServiceRef())
        );
        return serviceService.getService(serviceRef);
    }

    private PlatformManagedProductServiceSummary reconcileCoolify(PlatformManagedProductServiceEntity service) {
        ensureCoolifySupportAvailable();
        service.setStatus("PROVISIONING");
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        try {
            CoolifyBinding binding = resolveCoolifyBinding(service);
            CoolifyApplicationSummary application = reconcileCoolifyApplication(service, binding);
            ensureServiceSecret(service);
            String publicBaseUrl = publicBaseUrl(application);
            coolifyApiClient.updateEnvironmentVariables(
                binding.connection(),
                application.uuid(),
                buildCoolifyServiceEnv(service, publicBaseUrl)
            );
            CoolifyActionResponse deployResponse = coolifyApiClient.start(binding.connection(), application.uuid(), true, true);
            CoolifyApplicationSummary observed = awaitCoolifyDeployment(
                binding.connection(),
                application.uuid(),
                application,
                deployResponse
            );
            CoolifyApplicationCapacity.requireReadback(
                observed,
                coolifyManagedServiceRuntimeSettings(service),
                "MANAGED_PRODUCT_RUNTIME_CAPACITY_DRIFT"
            );
            finalizeActiveCoolifyService(
                service,
                binding.profile().getId(),
                binding.connection().config().projectUuid(),
                binding.connection().config().environmentUuid(),
                observed,
                deployResponse,
                publicBaseUrl(observed),
                "Coolify product service reconciled successfully."
            );
            platformAuditService.record(
                "MANAGED_PRODUCT_RECONCILED",
                "MANAGED_PRODUCT_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "providerType", "COOLIFY",
                    "coolifyApplicationUuid", observed.uuid()
                )
            );
            return serviceService.getService(service.getServiceRef());
        } catch (RuntimeException ex) {
            markLifecycleFailure(service, "FAILED", ex.getMessage(), "lastReconcileStatus", "lastReconcileMessage");
            platformAuditService.record(
                "MANAGED_PRODUCT_RECONCILE_FAILED",
                "MANAGED_PRODUCT_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "providerType", "COOLIFY",
                    "error", blankToFallback(ex.getMessage(), ex.getClass().getSimpleName())
                )
            );
            throw ex;
        }
    }

    private PlatformManagedProductServiceSummary restartCoolify(PlatformManagedProductServiceEntity service) {
        ensureCoolifySupportAvailable();
        ObjectNode details = mutableDetails(service);
        String applicationUuid = trimToNull(details.path("coolifyApplicationUuid").asText(null));
        if (!hasText(applicationUuid)) {
            return reconcile(service.getServiceRef());
        }
        CoolifyBinding binding = resolveCoolifyBinding(service);
        try {
            CoolifyActionResponse response = coolifyApiClient.restart(binding.connection(), applicationUuid);
            CoolifyApplicationSummary existing = coolifyApiClient.getApplication(binding.connection(), applicationUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Coolify application not found: " + applicationUuid));
            CoolifyApplicationSummary observed = awaitCoolifyDeployment(
                binding.connection(),
                applicationUuid,
                existing,
                response
            );
            finalizeActiveCoolifyService(
                service,
                binding.profile().getId(),
                binding.connection().config().projectUuid(),
                binding.connection().config().environmentUuid(),
                observed,
                response,
                publicBaseUrl(observed),
                "Coolify product service restarted successfully."
            );
            ObjectNode updatedDetails = mutableDetails(service);
            updatedDetails.put("lastRestartedAt", Instant.now().toString());
            updatedDetails.put("lastRestartStatus", "SUCCESS");
            updatedDetails.put("lastRestartMessage", "Coolify product service restarted successfully.");
            service.setDetailsJson(updatedDetails.toPrettyString());
            serviceRepository.save(service);
            platformAuditService.record(
                "MANAGED_PRODUCT_RESTARTED",
                "MANAGED_PRODUCT_SERVICE",
                service.getServiceRef(),
                Map.of("serviceRef", service.getServiceRef(), "providerType", "COOLIFY")
            );
            return serviceService.getService(service.getServiceRef());
        } catch (RuntimeException ex) {
            markLifecycleFailure(service, "FAILED", ex.getMessage(), "lastRestartStatus", "lastRestartMessage");
            throw ex;
        }
    }

    private PlatformManagedProductServiceSummary forceRecreateCoolify(PlatformManagedProductServiceEntity service) {
        ensureCoolifySupportAvailable();
        platformAuditService.record(
            "MANAGED_PRODUCT_FORCE_RECREATE_REQUESTED",
            "MANAGED_PRODUCT_SERVICE",
            service.getServiceRef(),
            Map.of("serviceRef", service.getServiceRef(), "providerType", "COOLIFY")
        );
        CoolifyBinding binding = resolveCoolifyBinding(service);
        ObjectNode details = mutableDetails(service);
        String applicationUuid = trimToNull(details.path("coolifyApplicationUuid").asText(null));
        CoolifyApplicationSummary discovered = findCoolifyApplication(
            binding,
            sharedServiceName(service),
            firstNonBlank(service.getBaseUrl(), coolifyDomain(service, binding.connection().config()))
        );
        String discoveredUuid = discovered == null ? null : trimToNull(discovered.uuid());
        boolean deleted = false;
        if (hasText(applicationUuid)) {
            deleted = deleteCoolifyApplicationIfPresent(binding.connection(), applicationUuid);
        }
        if (!deleted && hasText(discoveredUuid) && !discoveredUuid.equals(applicationUuid)) {
            deleteCoolifyApplicationIfPresent(binding.connection(), discoveredUuid);
        }
        clearCoolifyBinding(service);
        return reconcile(service.getServiceRef());
    }

    private PlatformManagedProductServiceSummary decommissionCoolify(PlatformManagedProductServiceEntity service) {
        ensureCoolifySupportAvailable();
        long dependentStores = shopifyStoreConnectionRepository.countByProductServiceId(service.getId());
        if (dependentStores > 0) {
            throw new ResponseStatusException(
                CONFLICT,
                "Managed product service still has " + dependentStores + " dependent store mapping(s)."
            );
        }
        ObjectNode details = mutableDetails(service);
        String applicationUuid = trimToNull(details.path("coolifyApplicationUuid").asText(null));
        if (hasText(applicationUuid)) {
            CoolifyBinding binding = resolveCoolifyBinding(service);
            coolifyApiClient.delete(binding.connection(), applicationUuid, true, false, true, true);
        }
        if (hasText(service.getSecretName()) && platformSecretService.isManagedSecretName(service.getSecretName())) {
            platformSecretService.clearManagedSecret(
                service.getSecretName(),
                Map.of("serviceRef", service.getServiceRef(), "purpose", "PRODUCT_SERVICE_SECRET")
            );
        }
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        service.setActualReplicas(0);
        service.setStatus("DECOMMISSIONED");
        details.remove(List.of("coolifyApplicationUuid", "coolifyProjectUuid", "coolifyEnvironmentUuid", "coolifyFqdn"));
        details.put("providerType", "COOLIFY");
        details.put("lastDecommissionedAt", Instant.now().toString());
        details.put("lastDecommissionStatus", "SUCCESS");
        details.put("lastDecommissionMessage", "Coolify product service decommissioned successfully.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        platformAuditService.record(
            "MANAGED_PRODUCT_DECOMMISSIONED",
            "MANAGED_PRODUCT_SERVICE",
            service.getServiceRef(),
            Map.of("serviceRef", service.getServiceRef(), "providerType", "COOLIFY")
        );
        return serviceService.getService(service.getServiceRef());
    }

    private CoolifyApplicationSummary reconcileCoolifyApplication(PlatformManagedProductServiceEntity service,
                                                                 CoolifyBinding binding) {
        if (PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(upper(service.getServiceKind()))) {
            return reconcileCoolifyDockerImageApplication(service, binding);
        }
        String appName = sharedServiceName(service);
        CoolifyCreatePublicApplicationRequest request = coolifyPublicApplicationRequest(service, binding, appName);
        ObjectNode details = mutableDetails(service);
        String existingUuid = trimToNull(details.path("coolifyApplicationUuid").asText(null));
        CoolifyApplicationSummary existing = null;
        if (hasText(existingUuid)) {
            existing = coolifyApiClient.getApplication(binding.connection(), existingUuid).orElse(null);
        }
        if (existing == null) {
            existing = findCoolifyApplication(
                binding,
                appName,
                firstNonBlank(service.getBaseUrl(), request.domains())
            );
        }
        if (existing != null && hasText(existing.uuid())) {
            request = request.withDomains(mergeCoolifyDomains(request.domains(), existing.fqdn()));
            coolifyApiClient.updatePublicApplication(binding.connection(), existing.uuid(), request);
            return coolifyApiClient.getApplication(binding.connection(), existing.uuid()).orElse(existing);
        }
        String uuid = coolifyApiClient.createPublicApplication(binding.connection(), request);
        return coolifyApiClient.getApplication(binding.connection(), uuid)
            .orElse(new CoolifyApplicationSummary(uuid, appName, request.domains(), "created", null, null, objectMapper.createObjectNode()));
    }

    private CoolifyApplicationSummary reconcileCoolifyDockerImageApplication(
        PlatformManagedProductServiceEntity service,
        CoolifyBinding binding
    ) {
        String appName = sharedServiceName(service);
        CoolifyCreateDockerImageApplicationRequest request = coolifyDockerImageApplicationRequest(service, binding, appName);
        ObjectNode details = mutableDetails(service);
        String existingUuid = trimToNull(details.path("coolifyApplicationUuid").asText(null));
        CoolifyApplicationSummary existing = null;
        if (hasText(existingUuid)) {
            existing = coolifyApiClient.getApplication(binding.connection(), existingUuid).orElse(null);
        }
        if (existing == null) {
            existing = findCoolifyApplication(
                binding,
                appName,
                firstNonBlank(service.getBaseUrl(), request.domains())
            );
        }
        if (existing != null && hasText(existing.uuid())) {
            request = request.withDomains(mergeCoolifyDomains(request.domains(), existing.fqdn()));
            if (!hasText(existing.imageRepository())) {
                coolifyApiClient.delete(binding.connection(), existing.uuid(), true, false, true, true);
                awaitCoolifyApplicationDeletion(binding.connection(), existing.uuid());
            } else {
                coolifyApiClient.updateDockerImageApplication(binding.connection(), existing.uuid(), request);
                return coolifyApiClient.getApplication(binding.connection(), existing.uuid()).orElse(existing);
            }
        }
        String uuid = coolifyApiClient.createDockerImageApplication(binding.connection(), request);
        return coolifyApiClient.getApplication(binding.connection(), uuid)
            .orElse(new CoolifyApplicationSummary(
                uuid,
                appName,
                request.domains(),
                "created",
                request.imageRepository(),
                request.imageTag(),
                objectMapper.createObjectNode()
            ));
    }

    private CoolifyCreateDockerImageApplicationRequest coolifyDockerImageApplicationRequest(
        PlatformManagedProductServiceEntity service,
        CoolifyBinding binding,
        String appName
    ) {
        CoolifyTargetProfileConfig config = binding.connection().config();
        ObjectNode details = mutableDetails(service);
        String imageRepository = requiredDetail(details, "imageRepository");
        String imageTag = requiredDetail(details, "imageTag");
        requiredDetail(details, "imageDigest");
        requiredDetail(details, "sourceCommit");
        String healthPath = firstNonBlank(healthPath(service), config.defaultHealthCheckPath());
        return new CoolifyCreateDockerImageApplicationRequest(
            config.projectUuid(),
            config.serverUuid(),
            config.environmentName(),
            config.environmentUuid(),
            imageRepository,
            imageTag,
            firstNonBlank(config.defaultPortsExposes(), "8080"),
            config.destinationUuid(),
            appName,
            "Managed product service " + service.getServiceRef(),
            coolifyDomain(service, config),
            true,
            healthPath,
            firstNonBlank(config.defaultHealthCheckPort(), "8080"),
            coolifyManagedServiceRuntimeSettings(service),
            false,
            config.forceHttps(),
            config.autogenerateDomain()
        );
    }

    private CoolifyApplicationRuntimeSettings coolifyManagedServiceRuntimeSettings(
        PlatformManagedProductServiceEntity service
    ) {
        if (!PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(
            upper(service.getServiceKind())
        )) {
            return null;
        }
        return new CoolifyApplicationRuntimeSettings(
            null,
            null,
            null,
            null,
            "256M",
            "256M",
            "64M",
            "0.250",
            null
        );
    }

    private String requiredDetail(JsonNode details, String field) {
        String value = trimToNull(details.path(field).asText(null));
        if (!hasText(value)) {
            throw new ResponseStatusException(
                CONFLICT,
                "Standalone AI Workspace release is missing " + field + "."
            );
        }
        return value;
    }

    private CoolifyCreatePublicApplicationRequest coolifyPublicApplicationRequest(PlatformManagedProductServiceEntity service,
                                                                                 CoolifyBinding binding,
                                                                                 String appName) {
        CoolifyTargetProfileConfig config = binding.connection().config();
        String healthPath = firstNonBlank(healthPath(service), config.defaultHealthCheckPath());
        return new CoolifyCreatePublicApplicationRequest(
            config.projectUuid(),
            config.serverUuid(),
            config.environmentName(),
            config.environmentUuid(),
            coolifyGitRepository(provisioningProperties.repository()),
            provisioningProperties.branch(),
            "dockerfile",
            "/",
            coolifyDockerfileLocation(dockerfilePath(service)),
            firstNonBlank(config.defaultPortsExposes(), "8080"),
            config.destinationUuid(),
            appName,
            "Managed product service " + service.getServiceRef(),
            coolifyDomain(service, config),
            true,
            healthPath,
            firstNonBlank(config.defaultHealthCheckPort(), "8080"),
            false,
            false,
            config.forceHttps(),
            config.autogenerateDomain()
        );
    }

    private CoolifyApplicationSummary findCoolifyApplication(CoolifyBinding binding,
                                                             String appName,
                                                             String baseUrl) {
        return coolifyApiClient.listApplications(binding.connection()).stream()
            .filter(app -> appName.equalsIgnoreCase(blankToFallback(app.name(), ""))
                || coolifyFqdnMatchesBaseUrl(app.fqdn(), baseUrl))
            .findFirst()
            .orElse(null);
    }

    private void awaitCoolifyApplicationDeletion(CoolifyConnection connection, String applicationUuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (coolifyApiClient.getApplication(connection, applicationUuid).isEmpty()) {
                return;
            }
            try {
                Thread.sleep(Math.max(productProvisioningProperties.pollInterval().toMillis(), 0L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Coolify application deletion.", ex);
            }
        }
        throw new ResponseStatusException(CONFLICT, "Timed out waiting for Coolify application deletion: " + applicationUuid);
    }

    private CoolifyApplicationSummary awaitCoolifyDeployment(CoolifyConnection connection,
                                                             String applicationUuid,
                                                             CoolifyApplicationSummary fallback,
                                                             CoolifyActionResponse deployResponse) {
        String deploymentUuid = deployResponse == null ? null : trimToNull(deployResponse.deploymentUuid());
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        Duration pollInterval = productProvisioningProperties.pollInterval();
        CoolifyApplicationSummary latest = fallback;
        CoolifyDeploymentSummary deployment = null;
        while (Instant.now().isBefore(deadline)) {
            if (hasText(deploymentUuid)) {
                deployment = coolifyApiClient.getDeployment(connection, deploymentUuid).orElse(null);
            }
            latest = coolifyApiClient.getApplication(connection, applicationUuid).orElse(latest);
            if (coolifyDeploymentFailed(deployment)) {
                throw new IllegalStateException(
                    "Coolify deployment failed for managed product service application " + applicationUuid
                        + " (deployment=" + deployment.deploymentUuid()
                        + ", status=" + deployment.status() + ")."
                );
            }
            boolean deploymentReady = !hasText(deploymentUuid) || coolifyDeploymentFinished(deployment);
            if (deploymentReady && coolifyApplicationReady(latest)) {
                return latest;
            }
            try {
                Thread.sleep(Math.max(pollInterval.toMillis(), 250L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Coolify deployment.", ex);
            }
        }
        throw new ResponseStatusException(CONFLICT, "Timed out waiting for Coolify deployment: " + applicationUuid);
    }

    private boolean coolifyApplicationReady(CoolifyApplicationSummary application) {
        if (application == null || !hasText(application.status())) {
            return false;
        }
        String normalized = upper(application.status());
        return normalized.startsWith("RUNNING") && !normalized.contains("UNHEALTHY");
    }

    private boolean coolifyDeploymentFinished(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = upper(deployment.status());
        return normalized.equals("FINISHED")
            || normalized.equals("SUCCESS")
            || normalized.equals("SUCCEEDED")
            || normalized.equals("COMPLETED")
            || hasText(deployment.finishedAt()) && !coolifyDeploymentFailed(deployment);
    }

    private boolean coolifyDeploymentFailed(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = upper(deployment.status());
        return normalized.contains("FAILED")
            || normalized.contains("ERROR")
            || normalized.contains("CANCELLED")
            || normalized.contains("CANCELED");
    }

    private boolean deleteCoolifyApplicationIfPresent(CoolifyConnection connection, String applicationUuid) {
        try {
            coolifyApiClient.delete(connection, applicationUuid, true, false, true, true);
            awaitCoolifyApplicationDeletion(connection, applicationUuid);
            return true;
        } catch (RuntimeException ex) {
            String message = blankToFallback(ex.getMessage(), "").toLowerCase(Locale.ROOT);
            if (message.contains("404") || message.contains("not found")) {
                return false;
            }
            throw ex;
        }
    }

    static String coolifyGitRepository(String repository) {
        String normalized = trimToNull(repository);
        if (!hasText(normalized)) {
            throw new RailwayProvisioningConfigurationException("Coolify git repository is required.");
        }
        String candidate = normalized;
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://github.com/") || lower.startsWith("http://github.com/")) {
            candidate = candidate.substring(candidate.indexOf("github.com/") + "github.com/".length());
        } else if (lower.startsWith("ssh://git@github.com/")) {
            candidate = candidate.substring("ssh://git@github.com/".length());
        } else if (lower.startsWith("git@github.com:")) {
            candidate = candidate.substring("git@github.com:".length());
            candidate = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
            if (candidate.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
                return "git@github.com:" + (candidate.endsWith(".git") ? candidate : candidate + ".git");
            }
            throw new RailwayProvisioningConfigurationException(
                "Coolify git repository must be a GitHub owner/repository slug or github.com URL."
            );
        } else if (lower.contains("://") || lower.startsWith("git@")) {
            throw new RailwayProvisioningConfigurationException(
                "Coolify git repository must be a GitHub owner/repository slug or github.com URL."
            );
        }
        String slug = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
        if (slug.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
            return "https://github.com/" + (slug.endsWith(".git") ? slug : slug + ".git");
        }
        throw new RailwayProvisioningConfigurationException(
            "Coolify git repository must be a GitHub owner/repository slug or github.com URL."
        );
    }

    static String coolifyDockerfileLocation(String dockerfilePath) {
        String normalized = trimToNull(dockerfilePath);
        if (!hasText(normalized)) {
            throw new RailwayProvisioningConfigurationException("Coolify dockerfile path is required.");
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private List<CoolifyEnvVar> buildCoolifyServiceEnv(PlatformManagedProductServiceEntity service,
                                                       String publicBaseUrl) {
        return buildServiceEnv(service, publicBaseUrl).stream()
            .map(env -> new CoolifyEnvVar(env.name(), env.value(), false, true, containsLineBreak(env.value()), false))
            .toList();
    }

    private boolean containsLineBreak(String value) {
        return value != null && (value.contains("\n") || value.contains("\r"));
    }

    private void finalizeActiveCoolifyService(PlatformManagedProductServiceEntity service,
                                              String targetProfileId,
                                              String projectUuid,
                                              String environmentUuid,
                                              CoolifyApplicationSummary application,
                                              CoolifyActionResponse deployResponse,
                                              String publicBaseUrl,
                                              String message) {
        service.setDesiredReplicas(desiredReplicas(service));
        service.setActualReplicas(desiredReplicas(service));
        service.setBaseUrl(publicBaseUrl);
        service.setPrivateNetworkUrl(null);
        service.setHealthPath(healthPath(service));
        service.setServiceRoot(serviceRoot(service));
        service.setDockerfilePath(dockerfilePath(service));
        service.setStatus("ACTIVE");
        ObjectNode details = buildServiceDetails(service, deployResponse == null ? null : deployResponse.deploymentUuid());
        details.put("providerType", "COOLIFY");
        details.put("targetProfileId", targetProfileId);
        details.put("coolifyApplicationUuid", application.uuid());
        details.put("coolifyProjectUuid", projectUuid);
        details.put("coolifyEnvironmentUuid", environmentUuid);
        details.put("coolifyFqdn", blankToFallback(application.fqdn(), ""));
        details.put("lastObservedStatus", blankToFallback(application.status(), ""));
        details.put("lastReconcileStatus", "SUCCESS");
        details.put("lastReconcileMessage", blankToFallback(message, "Coolify product service is active."));
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private CoolifyBinding resolveCoolifyBinding(PlatformManagedProductServiceEntity service) {
        ensureCoolifySupportAvailable();
        ObjectNode details = mutableDetails(service);
        String targetProfileId = trimToNull(details.path("targetProfileId").asText(null));
        DeploymentTargetProfileEntity profile;
        if (hasText(targetProfileId)) {
            profile = targetProfileRepository.findById(targetProfileId)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Coolify target profile not found: " + targetProfileId));
        } else {
            profile = resolveDefaultCoolifyPlatformServiceProfile();
            details.put("providerType", "COOLIFY");
            details.put("targetProfileId", profile.getId());
            service.setDetailsJson(details.toPrettyString());
            serviceRepository.save(service);
        }
        if (!profile.isActive() || !profile.isPlatformServicesAllowed()) {
            throw new ResponseStatusException(CONFLICT, "Coolify target profile is not active for platform services: " + profile.getId());
        }
        if (hasText(profile.getEnvironmentName())) {
            service.setEnvironmentScope(profile.getEnvironmentName().trim());
        }
        return new CoolifyBinding(profile, coolifyTargetProfileResolver.requireConnection(profile));
    }

    private DeploymentTargetProfileEntity resolveDefaultCoolifyPlatformServiceProfile() {
        List<DeploymentTargetProfileEntity> allowedProfiles = targetProfileRepository
            .findByProviderTypeOrderByEnvironmentNameAscUpdatedAtDesc(DeploymentProviderType.COOLIFY)
            .stream()
            .filter(DeploymentTargetProfileEntity::isActive)
            .filter(DeploymentTargetProfileEntity::isPlatformServicesAllowed)
            .toList();
        if (allowedProfiles.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "No active Coolify target profile allows platform services.");
        }
        return allowedProfiles.stream()
            .filter(DeploymentTargetProfileEntity::isDefaultForRestartableServices)
            .findFirst()
            .orElseGet(() -> {
                if (allowedProfiles.size() == 1) {
                    return allowedProfiles.get(0);
                }
                throw new ResponseStatusException(
                    CONFLICT,
                    "Multiple Coolify target profiles allow platform services. Set targetProfileId explicitly."
                );
            });
    }

    private String coolifyDomain(PlatformManagedProductServiceEntity service, CoolifyTargetProfileConfig config) {
        String existingBaseUrl = trimToNull(service.getBaseUrl());
        if (hasText(existingBaseUrl)) {
            return existingBaseUrl.split(",")[0].trim();
        }
        if (config.autogenerateDomain()) {
            return null;
        }
        String suffix = trimToNull(config.defaultPublicDomainSuffix());
        if (!hasText(suffix)) {
            return null;
        }
        String normalizedSuffix = suffix.startsWith(".") ? suffix.substring(1) : suffix;
        return "https://" + normalizeToken(service.getServiceRef()) + "." + normalizedSuffix;
    }

    private String publicBaseUrl(CoolifyApplicationSummary application) {
        if (application == null || !hasText(application.fqdn())) {
            return null;
        }
        String fqdn = application.fqdn().split(",")[0].trim();
        if (!hasText(fqdn)) {
            return null;
        }
        return fqdn.startsWith("http://") || fqdn.startsWith("https://") ? fqdn : "https://" + fqdn;
    }

    private String mergeCoolifyDomains(String desiredDomains, String observedDomains) {
        if (!hasText(desiredDomains)) {
            return trimToNull(observedDomains);
        }
        List<String> merged = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        appendCoolifyDomains(merged, seen, desiredDomains);
        if (coolifyDomainListsOverlap(desiredDomains, observedDomains)) {
            appendCoolifyDomains(merged, seen, observedDomains);
        }
        return merged.isEmpty() ? trimToNull(desiredDomains) : String.join(",", merged);
    }

    private void appendCoolifyDomains(List<String> merged, java.util.Set<String> seen, String domains) {
        if (!hasText(domains)) {
            return;
        }
        for (String candidate : domains.split(",")) {
            String normalized = trimToNull(candidate);
            String key = coolifyDomainKey(normalized);
            if (hasText(normalized) && hasText(key) && seen.add(key)) {
                merged.add(normalized);
            }
        }
    }

    private boolean coolifyDomainListsOverlap(String left, String right) {
        if (!hasText(left) || !hasText(right)) {
            return false;
        }
        java.util.Set<String> leftKeys = new java.util.LinkedHashSet<>();
        for (String candidate : left.split(",")) {
            String key = coolifyDomainKey(candidate);
            if (hasText(key)) {
                leftKeys.add(key);
            }
        }
        for (String candidate : right.split(",")) {
            if (leftKeys.contains(coolifyDomainKey(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String coolifyDomainKey(String domain) {
        String host = normalizedUrlHost(domain);
        if (hasText(host)) {
            return host.toLowerCase(Locale.ROOT);
        }
        String normalized = trimToNull(domain);
        return hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private boolean coolifyFqdnMatchesBaseUrl(String fqdn, String baseUrl) {
        String expected = normalizedUrlHost(baseUrl);
        if (!hasText(expected) || !hasText(fqdn)) {
            return false;
        }
        for (String candidate : fqdn.split(",")) {
            if (expected.equalsIgnoreCase(normalizedUrlHost(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizedUrlHost(String value) {
        String normalized = trimToNull(value);
        if (!hasText(normalized)) {
            return null;
        }
        normalized = normalized.replaceFirst("^https?://", "");
        int slash = normalized.indexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(0, slash);
        }
        return trimToNull(normalized);
    }

    private void clearCoolifyBinding(PlatformManagedProductServiceEntity service) {
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        service.setActualReplicas(0);
        service.setStatus("CREATED");
        ObjectNode details = mutableDetails(service);
        details.remove(List.of("coolifyApplicationUuid", "coolifyProjectUuid", "coolifyEnvironmentUuid", "coolifyFqdn"));
        details.put("providerType", "COOLIFY");
        details.put("lastForceRecreatedAt", Instant.now().toString());
        details.put("lastForceRecreateStatus", "SUCCESS");
        details.put("lastForceRecreateMessage", "Coolify product service linkage was cleared and will be recreated.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private void ensureCoolifySupportAvailable() {
        if (coolifyTargetProfileResolver == null || coolifyApiClient == null || targetProfileRepository == null) {
            throw new ResponseStatusException(CONFLICT, "Coolify product service lifecycle dependencies are not configured.");
        }
    }

    private void awaitProjectDeletion(String projectId, String projectName) {
        Instant deadline = Instant.now().plus(provisioningProperties.deploymentTimeout());
        while (Instant.now().isBefore(deadline)) {
            boolean projectExistsById;
            try {
                railwayGraphqlClient.getProject(projectId);
                projectExistsById = true;
            } catch (RuntimeException ignored) {
                projectExistsById = false;
            }
            if (!projectExistsById) {
                RailwayGraphqlClient.RailwayProjectSnapshot projectByName = null;
                try {
                    projectByName = railwayGraphqlClient.findProjectByName(provisioningProperties.workspaceId(), projectName);
                } catch (RuntimeException ignored) {
                    projectByName = null;
                }
                if (projectByName == null || !projectId.equalsIgnoreCase(blankToFallback(projectByName.id(), ""))) {
                    return;
                }
            }
            try {
                Thread.sleep(Math.max(productProvisioningProperties.pollInterval().toMillis(), 0L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException("Interrupted while waiting for Railway project deletion.", ex);
            }
        }
        throw new RailwayProvisioningException(
            "Timed out waiting for Railway project deletion to settle: " + projectName
        );
    }

    private boolean requiresRailwayLifecycle(PlatformManagedProductServiceEntity service) {
        return "SHOPIFY_BRIDGE_SERVICE".equals(upper(service.getServiceKind()))
            && !isCoolifyManaged(service);
    }

    private boolean requiresCoolifyLifecycle(PlatformManagedProductServiceEntity service) {
        return coolifyTargetProfileResolver != null
            && coolifyApiClient != null
            && targetProfileRepository != null
            && (isCoolifyManaged(service)
            || "SHOPIFY_BRIDGE_SERVICE".equals(upper(service.getServiceKind()))
            || "MCP_EXECUTION_GATEWAY_SERVICE".equals(upper(service.getServiceKind())));
    }

    private boolean isCoolifyManaged(PlatformManagedProductServiceEntity service) {
        ObjectNode details = mutableDetails(service);
        return "COOLIFY".equalsIgnoreCase(details.path("providerType").asText(null))
            || hasText(details.path("coolifyApplicationUuid").asText(null))
            || details.path("targetProfileId").asText("").startsWith("dtp-coolify-");
    }

    private RailwayGraphqlClient.RailwayProjectSnapshot resolveCurrentProjectSnapshot(PlatformManagedProductServiceEntity service) {
        if (!hasText(service.getRailwayProjectId())) {
            return null;
        }
        try {
            return railwayGraphqlClient.getProject(service.getRailwayProjectId());
        } catch (RuntimeException ex) {
            String message = blankToFallback(ex.getMessage(), "").toLowerCase(Locale.ROOT);
            if (message.contains("project not found")) {
                return null;
            }
            throw ex;
        }
    }

    private ReconciledRailwayService reconcileRailwayService(PlatformManagedProductServiceEntity service) {
        String environmentName = resolveEnvironmentName(service);
        RailwayGraphqlClient.RailwayProjectSnapshot project = railwayGraphqlClient.findProjectByName(
            provisioningProperties.workspaceId(),
            sharedProjectName(service)
        );
        if (project == null) {
            project = railwayGraphqlClient.createProject(
                provisioningProperties.workspaceId(),
                sharedProjectName(service),
                environmentName
            );
        }
        RailwayGraphqlClient.RailwayEnvironmentSummary environment = project.environmentNamed(environmentName);
        if (environment == null) {
            environment = railwayGraphqlClient.createEnvironment(project.id(), environmentName);
        }

        RailwayGraphqlClient.RailwayServiceSummary railwayService = project.serviceNamed(sharedServiceName(service));
        if (railwayService == null) {
            railwayService = railwayGraphqlClient.createServiceFromRepository(
                project.id(),
                sharedServiceName(service),
                provisioningProperties.repository(),
                provisioningProperties.branch()
            );
        }

        railwayGraphqlClient.connectServiceToRepository(
            railwayService.id(),
            provisioningProperties.repository(),
            provisioningProperties.branch()
        );
        railwayGraphqlClient.updateServiceInstance(
            railwayService.id(),
            environment.id(),
            serviceRoot(service),
            dockerfilePath(service),
            healthPath(service),
            desiredReplicas(service)
        );

        ensureServiceSecret(service);
        String publicBaseUrl = ensureServiceDomain(project.id(), environment.id(), railwayService.id());
        railwayGraphqlClient.upsertVariables(
            project.id(),
            environment.id(),
            railwayService.id(),
            buildServiceEnv(service, publicBaseUrl)
        );

        if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
            railwayGraphqlClient.commitStagedChanges(environment.id());
        }
        String deploymentId = railwayGraphqlClient.deployService(railwayService.id(), environment.id());
        RailwayApiProvisioningProvider.awaitSuccessfulDeployment(
            deploymentId,
            sharedServiceName(service),
            provisioningProperties.deploymentTimeout(),
            productProvisioningProperties.pollInterval(),
            () -> railwayGraphqlClient.getDeployment(deploymentId),
            duration -> Thread.sleep(Math.max(duration.toMillis(), 0L)),
            ex -> {
            }
        );

        RailwayGraphqlClient.RailwayServiceInstanceSummary instance = railwayGraphqlClient.getServiceInstance(
            environment.id(),
            railwayService.id()
        );
        return new ReconciledRailwayService(
            project.id(),
            environment.id(),
            railwayService.id(),
            instance,
            publicBaseUrl,
            deploymentId
        );
    }

    private void finalizeActiveService(PlatformManagedProductServiceEntity service,
                                       String projectId,
                                       String environmentId,
                                       String serviceId,
                                       RailwayGraphqlClient.RailwayServiceInstanceSummary instance,
                                       String publicBaseUrl,
                                       String deploymentId,
                                       String message) {
        service.setRailwayProjectId(projectId);
        service.setRailwayEnvironmentId(environmentId);
        service.setRailwayServiceId(serviceId);
        service.setDesiredReplicas(desiredReplicas(service));
        service.setActualReplicas(desiredReplicas(service));
        service.setBaseUrl(publicBaseUrl);
        service.setPrivateNetworkUrl(trimToNull(instance.upstreamUrl()));
        service.setHealthPath(healthPath(service));
        service.setServiceRoot(serviceRoot(service));
        service.setDockerfilePath(dockerfilePath(service));
        service.setStatus("ACTIVE");
        ObjectNode details = buildServiceDetails(service, deploymentId);
        details.put("lastReconcileStatus", "SUCCESS");
        details.put("lastReconcileMessage", blankToFallback(message, "Managed product service is active."));
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private String resolveEnvironmentName(PlatformManagedProductServiceEntity service) {
        return hasText(service.getEnvironmentScope()) ? service.getEnvironmentScope().trim() : provisioningProperties.environmentName();
    }

    private String sharedProjectName(PlatformManagedProductServiceEntity service) {
        return trimProjectName(
            productProvisioningProperties.sharedProjectNamePrefix()
                + "-" + normalizeToken(resolveEnvironmentName(service))
                + "-" + normalizeToken(service.getServiceRef())
        );
    }

    private String sharedServiceName(PlatformManagedProductServiceEntity service) {
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" ->
                trimProjectName(productProvisioningProperties.shopifyBridgeServiceNamePrefix() + "-" + normalizeToken(service.getServiceRef()));
            case "MCP_EXECUTION_GATEWAY_SERVICE" ->
                trimProjectName(productProvisioningProperties.mcpExecutionGatewayServiceNamePrefix() + "-" + normalizeToken(service.getServiceRef()));
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE ->
                trimProjectName("loomai-engage-" + normalizeToken(service.getServiceRef()));
            default -> trimProjectName("product-" + normalizeToken(service.getServiceRef()));
        };
    }

    private String serviceRoot(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getServiceRoot())) {
            return service.getServiceRoot().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeServiceRoot();
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> productProvisioningProperties.mcpExecutionGatewayServiceRoot();
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE ->
                "workspace-apps/standalone-ai-workspace";
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        };
    }

    private String dockerfilePath(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getDockerfilePath())) {
            return service.getDockerfilePath().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeDockerfilePath();
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> productProvisioningProperties.mcpExecutionGatewayDockerfilePath();
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE ->
                "workspace-apps/standalone-ai-workspace/Dockerfile";
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        };
    }

    private String healthPath(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getHealthPath())) {
            return service.getHealthPath().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeHealthPath();
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> productProvisioningProperties.mcpExecutionGatewayHealthPath();
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE -> "/health";
            default -> "/actuator/health";
        };
    }

    private int desiredReplicas(PlatformManagedProductServiceEntity service) {
        Integer desired = service.getDesiredReplicas();
        return desired != null && desired > 0 ? desired : 1;
    }

    private void ensureServiceSecret(PlatformManagedProductServiceEntity service) {
        if (PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE.equals(upper(service.getServiceKind()))) {
            return;
        }
        if (!hasText(service.getSecretName())) {
            service.setSecretName(defaultSecretName(service.getServiceRef()));
        }
        if (!platformSecretService.isSecretPresent(service.getSecretName())) {
            platformSecretService.upsertManagedSecret(
                service.getSecretName(),
                UUID.randomUUID().toString().replace("-", ""),
                Map.of("serviceRef", service.getServiceRef(), "purpose", "PRODUCT_SERVICE_SECRET")
            );
        }
    }

    private String defaultSecretName(String serviceRef) {
        return "MANAGED_PRODUCT_" + normalizeToken(serviceRef).replace('-', '_').toUpperCase(Locale.ROOT) + "_API_KEY";
    }

    private List<RailwayGraphqlClient.RailwayEnvVarInput> buildServiceEnv(PlatformManagedProductServiceEntity service,
                                                                          String publicBaseUrl) {
        List<RailwayGraphqlClient.RailwayEnvVarInput> env = new ArrayList<>();
        switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> {
                String sharedSecret = resolveSecret(service.getSecretName());
                String shopifyApiKey = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeShopifyApiKeySecretName());
                String shopifyApiSecret = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeShopifyApiSecretSecretName());
                String webhookSharedSecret = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeWebhookSharedSecretName());
                String customerAccountMcpClientId = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_CLIENT_ID_SECRET);
                String customerAccountMcpClientSecret = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET_SECRET);
                String customerAccountMcpRedirectUri = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_REDIRECT_URI_SECRET);
                String customerAccountMcpStorefrontDomain = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN_SECRET);
                String customerAccountMcpScopes = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_SCOPES_SECRET);
                String customerAccountMcpStateTtl = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_STATE_TTL_SECRET);
                String customerAccountMcpSessionTtl = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_SESSION_TTL_SECRET);
                String customerAccountMcpConnectTimeout = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT_SECRET);
                String customerAccountMcpReadTimeout = resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT_SECRET);
                boolean customerAccountMcpProtectedApproved = Boolean.parseBoolean(
                    blankToFallback(resolveOptionalSecret(CUSTOMER_ACCOUNT_MCP_PROTECTED_APPROVED_SECRET), "false")
                );
                boolean customerAccountMcpConfigured = customerAccountMcpProtectedApproved
                    && hasText(customerAccountMcpClientId)
                    && hasText(customerAccountMcpClientSecret)
                    && hasText(customerAccountMcpRedirectUri)
                    && hasText(customerAccountMcpScopes);
                boolean checkoutMcpConfigured = checkoutMcpCredentialsConfigured();
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_SHARED_SECRET", sharedSecret));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_SERVICE_REF", service.getServiceRef()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_APP_NAME", blankToFallback(service.getDisplayName(), "Shopify Bridge Service")));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_ENVIRONMENT_SCOPE", resolveEnvironmentName(service)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_ADMIN_API_VERSION", productProvisioningProperties.shopifyBridgeAdminApiVersion()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_PLATFORM_BASE_URL", deliveryProperties.publicBaseUrl()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_PLATFORM_ADMIN_API_KEY", sharedSecret));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_PUBLIC_BASE_URL", blankToFallback(publicBaseUrl, "")));
                addMcpGatewayEnv(env);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_SHOPIFY_API_KEY", shopifyApiKey);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_SHOPIFY_API_SECRET", shopifyApiSecret);
                addOptionalEnv(
                    env,
                    "SHOPIFY_BRIDGE_WEBHOOK_SHARED_SECRET",
                    hasText(webhookSharedSecret) ? webhookSharedSecret : shopifyApiSecret
                );
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_ENABLED", Boolean.toString(customerAccountMcpConfigured)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED", Boolean.toString(customerAccountMcpProtectedApproved)));
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_ID", customerAccountMcpClientId);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET", customerAccountMcpClientSecret);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_REDIRECT_URI", customerAccountMcpRedirectUri);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN", customerAccountMcpStorefrontDomain);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES", customerAccountMcpScopes);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STATE_TTL", customerAccountMcpStateTtl);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SESSION_TTL", customerAccountMcpSessionTtl);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT", customerAccountMcpConnectTimeout);
                addOptionalEnv(env, "SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT", customerAccountMcpReadTimeout);
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CHECKOUT_MCP_ENABLED", Boolean.toString(checkoutMcpConfigured)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED", "false"));
                PlatformManagedProductServiceShopifyBillingConfigSummary billingConfig =
                    PlatformManagedProductServiceShopifyBillingConfigSupport.summaryFromDetails(
                        objectMapper,
                        service.getDetailsJson(),
                        service.getServiceKind()
                    );
                PlatformManagedProductServiceShopifyBillingConfigSupport.railwayEnv(billingConfig)
                    .forEach((name, value) -> env.add(new RailwayGraphqlClient.RailwayEnvVarInput(name, value)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,info"));
            }
            case "MCP_EXECUTION_GATEWAY_SERVICE" -> {
                String sharedSecret = resolveSecret(service.getSecretName());
                String checkoutClientId = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientIdSecretName());
                String checkoutClientSecret = resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientSecretSecretName());
                boolean checkoutMcpConfigured = hasText(checkoutClientId) && hasText(checkoutClientSecret);
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_INTERNAL_API_KEY", sharedSecret));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_SERVICE_REF", service.getServiceRef()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SCOPE", resolveEnvironmentName(service)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_PROTOCOL_VERSION", "2025-11-25"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_API_KEY_HEADER_ALLOWLIST", "X-API-KEY,X-MCP-API-KEY,X-LOOM-MCP-KEY"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_PROFILE_REF_ALLOWLIST", "MCP_PROFILE_SHOPIFY_UCP_AGENT,SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_PROFILE_SHOPIFY_UCP_AGENT", defaultShopifyUcpAgentProfile()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE", defaultShopifyUcpAgentProfile()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SECRET_RESOLUTION_ENABLED", Boolean.toString(checkoutMcpConfigured)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SECRET_REF_PREFIX", "MCP_SECRET_"));
                if (checkoutMcpConfigured) {
                    env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID", checkoutClientId));
                    env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET", checkoutClientSecret));
                }
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,info"));
            }
            case PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE -> {
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput(
                    "PLATFORM_API_BASE_URL", deliveryProperties.publicBaseUrl()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("PORT", "8080"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("NODE_ENV", "production"));
            }
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        }
        return env;
    }

    private void addMcpGatewayEnv(List<RailwayGraphqlClient.RailwayEnvVarInput> env) {
        String gatewayRef = productProvisioningProperties.mcpExecutionGatewayServiceRef();
        PlatformManagedProductServiceEntity gateway = serviceRepository.findByServiceRefIgnoreCase(gatewayRef)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify Bridge requires managed MCP execution gateway service: " + gatewayRef
            ));
        if (!hasText(gateway.getBaseUrl())) {
            throw new ResponseStatusException(CONFLICT, "MCP execution gateway has no base URL: " + gatewayRef);
        }
        if (!hasText(gateway.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "MCP execution gateway has no managed API key secret: " + gatewayRef);
        }
        String gatewaySecret = resolveSecret(gateway.getSecretName());
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_BASE_URL", gateway.getBaseUrl()));
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY", gatewaySecret));
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY_HEADER", "X-MCP-GATEWAY-API-KEY"));
        env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_GATEWAY_EXECUTE_PATH", "/api/internal/mcp/actions/execute"));
    }

    private String defaultShopifyUcpAgentProfile() {
        return "https://shopify.dev/ucp/agent-profiles/examples/2026-08-25/valid-with-capabilities.json";
    }

    private boolean checkoutMcpCredentialsConfigured() {
        return hasText(resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientIdSecretName()))
            && hasText(resolveOptionalSecret(productProvisioningProperties.shopifyBridgeCheckoutMcpClientSecretSecretName()));
    }

    private String resolveSecret(String secretName) {
        String value = platformSecretService.resolveSecret(secretName);
        if (!hasText(value)) {
            throw new RailwayProvisioningConfigurationException(
                "Missing managed product service secret '" + secretName + "'."
            );
        }
        return value;
    }

    private String resolveOptionalSecret(String secretName) {
        String normalized = trimToNull(secretName);
        if (!hasText(normalized)) {
            return null;
        }
        String value = platformSecretService.resolveSecret(normalized);
        return hasText(value) ? value : null;
    }

    private void addOptionalEnv(List<RailwayGraphqlClient.RailwayEnvVarInput> env,
                                String name,
                                String value) {
        if (hasText(value)) {
            env.add(new RailwayGraphqlClient.RailwayEnvVarInput(name, value));
        }
    }

    private String ensureServiceDomain(String projectId,
                                       String environmentId,
                                       String serviceId) {
        List<RailwayGraphqlClient.RailwayServiceDomainSummary> existingDomains = railwayGraphqlClient.listServiceDomains(
            projectId,
            environmentId,
            serviceId
        );
        RailwayGraphqlClient.RailwayServiceDomainSummary domain = existingDomains.isEmpty()
            ? railwayGraphqlClient.createServiceDomain(serviceId, environmentId)
            : existingDomains.get(0);
        if (!hasText(domain.domain())) {
            throw new RailwayProvisioningException("Railway service domain was not created for managed product service " + serviceId + ".");
        }
        return "https://" + domain.domain();
    }

    private ObjectNode buildServiceDetails(PlatformManagedProductServiceEntity service,
                                           String deploymentId) {
        ObjectNode details = mutableDetails(service);
        details.put("serviceRef", service.getServiceRef());
        details.put("serviceKind", blankToFallback(service.getServiceKind(), ""));
        details.put("deploymentMode", blankToFallback(service.getDeploymentMode(), ""));
        details.put("deploymentId", deploymentId);
        details.put("reconciledAt", Instant.now().toString());
        details.put("lastDeploymentId", blankToFallback(deploymentId, ""));
        details.put("lastReconciledAt", Instant.now().toString());
        details.put("platformBaseUrl", deliveryProperties.publicBaseUrl());
        return details;
    }

    private void clearRailwayBinding(PlatformManagedProductServiceEntity service) {
        service.setRailwayProjectId(null);
        service.setRailwayEnvironmentId(null);
        service.setRailwayServiceId(null);
        service.setBaseUrl(null);
        service.setPrivateNetworkUrl(null);
        service.setActualReplicas(0);
        service.setStatus("CREATED");
        ObjectNode details = mutableDetails(service);
        details.put("lastForceRecreatedAt", Instant.now().toString());
        details.put("lastForceRecreateStatus", "SUCCESS");
        details.put("lastForceRecreateMessage", "Managed product service linkage was cleared and will be recreated.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private void markLifecycleFailure(PlatformManagedProductServiceEntity service,
                                      String status,
                                      String message,
                                      String statusField,
                                      String messageField) {
        service.setStatus(status);
        ObjectNode details = mutableDetails(service);
        details.put(statusField, "FAILED");
        details.put(messageField, blankToFallback(message, "Managed product service operation failed."));
        details.put("lastFailureAt", Instant.now().toString());
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
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

    private String trimProjectName(String value) {
        String normalized = normalizeToken(value);
        int max = Math.max(8, provisioningProperties.projectNameMaxLength());
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String normalizeToken(String value) {
        if (!hasText(value)) {
            return "default";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-|-$)", "");
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String firstNonBlank(String left, String right) {
        return hasText(left) ? left.trim() : hasText(right) ? right.trim() : null;
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    /** Generic immutable-service extension; checkpoints must commit before remote creation. */
    public interface OwnedDependencyCheckpoint {
        void guard();
        void creating(String kind);
        void created(String kind, String uuid);
        void volume(String mountPath, String uuid);
        void volumeReclamationPlanned(Map<String, String> volumeNamesByUuid);
        void volumesReclaimed(Set<String> volumeUuids);
    }

    public record OwnedDependencyResources(String applicationUuid, String databaseUuid,
                                          boolean applicationCreateIntent, boolean databaseCreateIntent,
                                          Map<String, String> volumes,
                                          Map<String, String> volumeReclamationPlan,
                                          Set<String> reclaimedVolumeUuids) {
        public OwnedDependencyResources {
            volumes = volumes == null ? Map.of() : Map.copyOf(volumes);
            volumeReclamationPlan = volumeReclamationPlan == null ? Map.of() : Map.copyOf(volumeReclamationPlan);
            reclaimedVolumeUuids = reclaimedVolumeUuids == null ? Set.of() : Set.copyOf(reclaimedVolumeUuids);
        }

        public OwnedDependencyResources(String applicationUuid, String databaseUuid,
                boolean applicationCreateIntent, boolean databaseCreateIntent,
                Map<String, String> volumes) {
            this(applicationUuid, databaseUuid, applicationCreateIntent, databaseCreateIntent,
                volumes, Map.of(), Set.of());
        }

        public OwnedDependencyResources(String applicationUuid, String databaseUuid,
                boolean applicationCreateIntent, boolean databaseCreateIntent,
                Map<String, String> volumes, Set<String> reclaimedVolumeUuids) {
            this(applicationUuid, databaseUuid, applicationCreateIntent, databaseCreateIntent,
                volumes, reclaimedVolumeUuids.stream().collect(java.util.stream.Collectors.toMap(
                    value -> value, value -> value)), reclaimedVolumeUuids);
        }
    }
    public record OwnedDependencyResult(String applicationUuid, String databaseUuid, String baseUrl) {}

    public CoolifyConnection ownedDependencyConnection(String targetProfileId, String environmentIntent) {
        ensureCoolifySupportAvailable();
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_TARGET_MISSING"));
        if (!profile.isActive() || profile.getProviderType() != com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY
            || !environmentIntent.equals(profile.getEnvironmentName())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TARGET_INVALID");
        }
        CoolifyConnection connection = coolifyTargetProfileResolver.requireConnection(profile);
        CoolifyTargetProfileConfig config = connection.config();
        TargetIntent intent = switch (environmentIntent) {
            case "staging" -> TargetIntent.CUSTOMER_STAGING;
            case "production" -> TargetIntent.CUSTOMER_PRODUCTION;
            default -> throw new ResponseStatusException(CONFLICT, "DEPENDENCY_ENVIRONMENT_UNSUPPORTED");
        };
        TargetIntent otherIntent = intent == TargetIntent.CUSTOMER_STAGING
            ? TargetIntent.CUSTOMER_PRODUCTION : TargetIntent.CUSTOMER_STAGING;
        var trusted = customerSelfServiceProperties.trustedServers().get(intent);
        var other = customerSelfServiceProperties.trustedServers().get(otherIntent);
        if (trusted == null || other == null || !hasText(trusted.uuid()) || !hasText(trusted.address())
            || !hasText(trusted.reportedAddress()) || !hasText(other.uuid()) || !hasText(other.address())
            || !hasText(other.reportedAddress())
            || trusted.uuid().equals(other.uuid()) || trusted.address().equals(other.address())
            || !trusted.uuid().equals(config.serverUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_SERVER_PLACEMENT_CONFLICT");
        }
        // Read actual placement. A profile name is not server evidence.
        JsonNode server = coolifyApiClient.getServer(connection, config.serverUuid());
        if (server == null || !trusted.reportedAddress().equals(server.path("ip").asText())
            || !trusted.uuid().equals(server.path("uuid").asText())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_SERVER_PLACEMENT_CONFLICT");
        }
        CoolifyDestinationPlacement placement = coolifyApiClient.resolveDestinationPlacement(
            connection, config.serverUuid(), config.destinationUuid())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DESTINATION_UNVERIFIED"));
        if (!config.serverUuid().equals(placement.serverUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DESTINATION_CONFLICT");
        }
        if (!hasText(config.projectUuid()) || !hasText(config.environmentUuid()) || !config.forceHttps()
            || !hasText(config.defaultPublicDomainSuffix()) || config.autogenerateDomain()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TRUSTED_TLS_DOMAIN_REQUIRED");
        }
        return connection;
    }

    public OwnedDependencyResult reconcileOwnedDependency(CoolifyConnection connection, String graphId,
            String serviceRef, com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, java.util.function.Function<String, List<CoolifyEnvVar>> environment,
            OwnedDependencyCheckpoint checkpoint) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        var config = connection.config();
        String marker = "ManagedDependency:" + graphId + ":" + spec.slot();
        String dbUuid = resources.databaseUuid();
        if (spec.database() != null) {
            CoolifyDatabaseRuntimeSettings databaseCapacity = CoolifyDatabaseCapacity.of(
                spec.database().capacity().milliCpu(),
                spec.database().capacity().memoryMiB()
            );
            String name = serviceRef + "-db";
            CoolifyDatabaseSummary db = null;
            if (hasText(dbUuid)) {
                db = coolifyApiClient.getDatabase(connection, dbUuid)
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_MISSING"));
            } else if (resources.databaseCreateIntent()) {
                var matches = coolifyApiClient.listDatabases(connection).stream()
                    .filter(value -> name.equals(value.name()) && marker.equals(value.raw().path("description").asText())).toList();
                if (matches.size() != 1) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_CREATE_UNCERTAIN");
                db = matches.get(0);
                dbUuid = db.uuid();
                checkpoint.created("DATABASE", dbUuid);
            }
            if (db == null) {
                checkpoint.guard();
                checkpoint.creating("DATABASE");
                List<CoolifyEnvVar> values = environment.apply(null);
                String password = values.stream().filter(v -> "__DATABASE_PASSWORD".equals(v.key()))
                    .map(CoolifyEnvVar::value).findFirst().orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_SECRET_MISSING"));
                dbUuid = coolifyApiClient.createPostgresDatabase(connection, new CoolifyCreatePostgresDatabaseRequest(
                    config.projectUuid(), config.serverUuid(), config.environmentName(), config.environmentUuid(),
                    config.destinationUuid(), name, marker, spec.database().image(), "dependency", password, "dependency",
                    false, false, databaseCapacity));
                checkpoint.created("DATABASE", dbUuid);
                db = coolifyApiClient.getDatabase(connection, dbUuid)
                    .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_READBACK_MISSING"));
            }
            requireOwnedDependencyResource(db.raw(), connection, name, marker);
            if (!spec.database().image().equals(db.raw().path("image").asText())
                || !"dependency".equals(db.postgresUser()) || !"dependency".equals(db.postgresDatabase())
                || db.raw().path("is_public").asBoolean(true)) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_SPEC_DRIFT");
            }
            CoolifyDatabaseCapacity.requireReadback(db, databaseCapacity, "DEPENDENCY_DATABASE_SPEC_DRIFT");
            checkpoint.guard();
            coolifyApiClient.startDatabase(connection, dbUuid);
            awaitOwnedDatabase(connection, dbUuid);
        }
        String suffix = config.defaultPublicDomainSuffix().replaceFirst("^\\.", "");
        String domain = "https://" + serviceRef + "." + suffix;
        var image = ownedDependencyImage(spec.image());
        var request = new CoolifyCreateDockerImageApplicationRequest(config.projectUuid(), config.serverUuid(),
            config.environmentName(), config.environmentUuid(), image.repository(), image.hashTag(), Integer.toString(spec.port()),
            config.destinationUuid(), serviceRef, marker, domain, true, spec.healthPath(), Integer.toString(spec.port()),
            new CoolifyApplicationRuntimeSettings(null, null, null, null,
                spec.capacity().memoryMiB() + "M", null, null,
                java.math.BigDecimal.valueOf(spec.capacity().milliCpu(), 3).toPlainString(), null),
            false, true, false);
        CoolifyApplicationSummary app = null;
        String appUuid = resources.applicationUuid();
        if (hasText(appUuid)) {
            app = coolifyApiClient.getApplication(connection, appUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING"));
        } else if (resources.applicationCreateIntent()) {
            var matches = coolifyApiClient.listApplications(connection).stream()
                .filter(value -> serviceRef.equals(value.name()) && marker.equals(value.raw().path("description").asText())).toList();
            if (matches.size() != 1) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_CREATE_UNCERTAIN");
            app = matches.get(0);
            appUuid = app.uuid();
            checkpoint.created("APPLICATION", appUuid);
        }
        if (app == null) {
            checkpoint.guard();
            checkpoint.creating("APPLICATION");
            appUuid = coolifyApiClient.createDockerImageApplication(connection, request);
            checkpoint.created("APPLICATION", appUuid);
            app = coolifyApiClient.getApplication(connection, appUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_READBACK_MISSING"));
        }
        requireOwnedDependencyResource(app.raw(), connection, serviceRef, marker);
        if (!image.repository().equals(app.imageRepository()) || !image.hashTag().equals(app.imageTag()) || !domain.equals(app.fqdn())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_SPEC_DRIFT");
        }
        for (var volume : spec.volumes()) {
            checkpoint.guard();
            var storage = coolifyApiClient.reconcilePersistentVolumeStorage(connection, appUuid,
                serviceRef + "-" + Integer.toHexString(volume.mountPath().hashCode()), volume.mountPath());
            if (!hasText(storage.uuid()) || !storage.name().startsWith(serviceRef + "-")) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
            }
            checkpoint.volume(volume.mountPath(), storage.uuid());
        }
        checkpoint.guard();
        coolifyApiClient.updateEnvironmentVariables(connection, appUuid, environment.apply(dbUuid).stream()
            .filter(v -> !"__DATABASE_PASSWORD".equals(v.key())).toList());
        checkpoint.guard();
        CoolifyActionResponse response = coolifyApiClient.start(connection, appUuid, true, true);
        if (response == null || !hasText(response.deploymentUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        }
        CoolifyApplicationSummary observed = awaitOwnedDependencyDeployment(connection, appUuid, response.deploymentUuid());
        requireOwnedDependencyResource(observed.raw(), connection, serviceRef, marker);
        if (!"running:healthy".equalsIgnoreCase(observed.status()) || !image.repository().equals(observed.imageRepository())
            || !image.hashTag().equals(observed.imageTag())
            || !domain.equals(observed.fqdn())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_HEALTH_READBACK_REQUIRED");
        }
        return new OwnedDependencyResult(appUuid, dbUuid, domain);
    }

    public record OwnedDependencyOperatorRestriction(String operatorId, String environmentKey,
            String previousValue, String restrictedValue, int remainingOperators) {
        @Override public String toString() {
            return "OwnedDependencyOperatorRestriction[operatorId=" + operatorId
                + ", remainingOperators=" + remainingOperators + ", configuration=***]";
        }
    }

    /** Reads a strict subset of existing credentials; it never resolves a desired enrollment or creates resources. */
    public OwnedDependencyOperatorRestriction planOwnedDependencyOperatorRemoval(CoolifyConnection connection,
            String graphId, String serviceRef,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, String bindingId, String operatorId) {
        requireExistingInboxResources(connection, graphId, serviceRef, spec, resources);
        var keys = spec.environment().entrySet().stream()
            .filter(entry -> entry.getValue().kind()
                == com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.ValueKind.OPERATORS)
            .map(Map.Entry::getKey).toList();
        if (keys.size() != 1 || !hasText(operatorId) || !hasText(bindingId)) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_REVOCATION_SCOPE_INVALID");
        }
        String current = coolifyApiClient.requireRuntimeEnvironmentValue(connection, resources.applicationUuid(), keys.getFirst());
        List<String> remaining = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (String row : current.split(";", -1)) {
            String[] fields = row.split("\\|", -1);
            if (fields.length != 4 || !hasText(fields[0]) || !hasText(fields[1]) || fields[2].length() < 32
                || !bindingId.equals(fields[3]) || !ids.add(fields[0])) {
                throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_CONFIGURATION_EVIDENCE_REQUIRED");
            }
            if (!operatorId.equals(fields[0])) remaining.add(row);
        }
        return new OwnedDependencyOperatorRestriction(operatorId, keys.getFirst(), current,
            String.join(";", remaining), remaining.size());
    }

    /** Changes only one existing inbox credential field, or stops that inbox after lifecycle disable. */
    public void applyOwnedDependencyOperatorRemoval(CoolifyConnection connection, String graphId, String serviceRef,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources, OwnedDependencyOperatorRestriction restriction,
            OwnedDependencyCheckpoint checkpoint) {
        requireExistingInboxResources(connection, graphId, serviceRef, spec, resources);
        var keys = spec.environment().entrySet().stream().filter(entry -> entry.getValue().kind()
            == com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.ValueKind.OPERATORS)
            .map(Map.Entry::getKey).toList();
        var remaining = java.util.Arrays.stream(restriction.previousValue().split(";", -1))
            .filter(row -> !restriction.operatorId().equals(row.split("\\|", -1)[0])).toList();
        if (keys.size() != 1 || !keys.getFirst().equals(restriction.environmentKey())
            || restriction.remainingOperators() != remaining.size()
            || !String.join(";", remaining).equals(restriction.restrictedValue())) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_REMOVAL_ONLY_REQUIRED");
        }
        String current = coolifyApiClient.requireRuntimeEnvironmentValue(connection, resources.applicationUuid(),
            restriction.environmentKey());
        if (!current.equals(restriction.previousValue()) && !current.equals(restriction.restrictedValue())) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_CONFIGURATION_CHANGED");
        }
        checkpoint.guard();
        if (restriction.remainingOperators() == 0) {
            disableOwnedDependency(connection, graphId, serviceRef, spec.slot(), resources.applicationUuid(), checkpoint);
            return;
        }
        coolifyApiClient.updateEnvironmentVariables(connection, resources.applicationUuid(), List.of(
            new CoolifyEnvVar(restriction.environmentKey(), restriction.restrictedValue(), false, true, false, true)));
        checkpoint.guard();
        var started = coolifyApiClient.start(connection, resources.applicationUuid(), true, true);
        if (started == null || !hasText(started.deploymentUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        }
        awaitOwnedDependencyDeployment(connection, resources.applicationUuid(), started.deploymentUuid());
        requireExistingInboxResources(connection, graphId, serviceRef, spec, resources);
        verifyOwnedDependencyReady(connection, graphId, serviceRef, spec.slot(), resources.applicationUuid(),
            resources.databaseUuid(), resources.volumes());
        if (!restriction.restrictedValue().equals(coolifyApiClient.requireRuntimeEnvironmentValue(
            connection, resources.applicationUuid(), restriction.environmentKey()))) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_CONFIGURATION_READBACK_FAILED");
        }
    }

    private void requireExistingInboxResources(CoolifyConnection connection, String graphId, String serviceRef,
            com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Service spec,
            OwnedDependencyResources resources) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        if (spec.contract() != com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1
            || resources == null || !hasText(resources.applicationUuid())
            || (spec.database() == null) != !hasText(resources.databaseUuid())) {
            throw new ResponseStatusException(CONFLICT, "INBOX_OPERATOR_EXISTING_RESOURCES_REQUIRED");
        }
        String marker = "ManagedDependency:" + graphId + ":" + spec.slot();
        var application = coolifyApiClient.getApplication(connection, resources.applicationUuid())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING"));
        requireOwnedDependencyResource(application.raw(), connection, serviceRef, marker);
        var image = ownedDependencyImage(spec.image());
        String domain = "https://" + serviceRef + "." + connection.config().defaultPublicDomainSuffix().replaceFirst("^\\.", "");
        if (!resources.applicationUuid().equals(application.uuid())
            || !image.repository().equals(application.imageRepository()) || !image.hashTag().equals(application.imageTag())
            || !domain.equals(application.fqdn())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_SPEC_DRIFT");
        }
        var storages = coolifyApiClient.listApplicationStorages(connection, resources.applicationUuid());
        Map<String, String> volumes = resources.volumes() == null ? Map.of() : resources.volumes();
        if (!spec.volumes().stream().map(com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.Volume::mountPath)
                .collect(java.util.stream.Collectors.toSet()).equals(volumes.keySet())
            || storages.size() != volumes.size() || storages.stream().anyMatch(storage ->
            !storage.uuid().equals(volumes.get(storage.mountPath())) || !"persistent".equals(storage.type())
                || hasText(storage.hostPath())
                || !storage.name().equals(serviceRef + "-" + Integer.toHexString(storage.mountPath().hashCode())))) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
        }
        if (hasText(resources.databaseUuid())) {
            var database = coolifyApiClient.getDatabase(connection, resources.databaseUuid())
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_MISSING"));
            requireOwnedDependencyResource(database.raw(), connection, serviceRef + "-db", marker);
            if (!resources.databaseUuid().equals(database.uuid()) || !spec.database().image().equals(database.raw().path("image").asText())
                || !"dependency".equals(database.postgresUser()) || !"dependency".equals(database.postgresDatabase())
                || database.raw().path("is_public").asBoolean(true)) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_SPEC_DRIFT");
            }
        }
    }

    private record OwnedDependencyImage(String repository, String hashTag) {}
    private OwnedDependencyImage ownedDependencyImage(String reference) {
        com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.immutableImage(reference);
        int boundary = reference.lastIndexOf("@sha256:");
        // Coolify's Docker Image API stores digests as sha256-<hex> in the image tag/hash field.
        return new OwnedDependencyImage(reference.substring(0, boundary), "sha256-" + reference.substring(boundary + 8));
    }

    public void retireOwnedDependency(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, OwnedDependencyResources resources, OwnedDependencyCheckpoint checkpoint) {
        String marker = "ManagedDependency:" + graphId + ":" + slot;
        Set<String> applicationVolumeUuids = Set.copyOf(resources.volumes().values());
        if (resources.applicationCreateIntent() && !hasText(resources.applicationUuid())
            || resources.databaseCreateIntent() && !hasText(resources.databaseUuid())
            || !hasText(resources.applicationUuid()) && !resources.volumes().isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_CREATE_UNCERTAIN_RECONCILE_REQUIRED");
        }
        if (applicationVolumeUuids.size() != resources.volumes().size()
            || resources.reclaimedVolumeUuids().stream().anyMatch(value -> !resources.volumeReclamationPlan().containsKey(value))
            || resources.volumeReclamationPlan().values().stream().anyMatch(value -> !hasText(value))
            || resources.volumeReclamationPlan().values().stream().distinct().count() != resources.volumeReclamationPlan().size()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
        }
        var application = hasText(resources.applicationUuid())
            ? coolifyApiClient.getApplication(connection, resources.applicationUuid()) : java.util.Optional.<CoolifyApplicationSummary>empty();
        var database = hasText(resources.databaseUuid())
            ? coolifyApiClient.getDatabase(connection, resources.databaseUuid()) : java.util.Optional.<CoolifyDatabaseSummary>empty();
        application.ifPresent(value -> requireOwnedDependencyResource(value.raw(), connection, serviceRef, marker));
        database.ifPresent(value -> requireOwnedDependencyResource(value.raw(), connection, serviceRef + "-db", marker));

        Map<String, String> volumePlan = resources.volumeReclamationPlan();
        if (volumePlan.isEmpty()) {
            if (hasText(resources.applicationUuid()) && application.isEmpty() && !applicationVolumeUuids.isEmpty()
                || hasText(resources.databaseUuid()) && database.isEmpty()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
            }
            var planned = new java.util.LinkedHashMap<String, String>();
            if (application.isPresent()) planned.putAll(requireOwnedApplicationVolumes(connection, serviceRef, resources));
            if (database.isPresent()) planned.putAll(requireOwnedDatabaseVolumes(connection, resources.databaseUuid()));
            if (planned.values().stream().distinct().count() != planned.size()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
            }
            volumePlan = Map.copyOf(planned);
            if (!volumePlan.isEmpty()) {
                checkpoint.guard();
                checkpoint.volumeReclamationPlanned(volumePlan);
            }
        } else {
            Map<String, String> existingPlan = volumePlan;
            if (!volumePlan.keySet().containsAll(applicationVolumeUuids)) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
            }
            if (application.isPresent()
                && !requireOwnedApplicationVolumes(connection, serviceRef, resources).entrySet()
                    .stream().allMatch(entry -> entry.getValue().equals(existingPlan.get(entry.getKey())))) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
            }
            if (database.isPresent()) {
                Map<String, String> observedDatabase = requireOwnedDatabaseVolumes(connection, resources.databaseUuid());
                Set<String> plannedDatabaseUuids = new java.util.HashSet<>(volumePlan.keySet());
                plannedDatabaseUuids.removeAll(applicationVolumeUuids);
                if (!plannedDatabaseUuids.equals(observedDatabase.keySet()) || observedDatabase.entrySet().stream()
                    .anyMatch(entry -> !entry.getValue().equals(existingPlan.get(entry.getKey())))) {
                    throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_EVIDENCE_INVALID");
                }
            }
        }
        Set<String> databaseVolumeUuids = new java.util.HashSet<>(volumePlan.keySet());
        databaseVolumeUuids.removeAll(applicationVolumeUuids);
        if (hasText(resources.databaseUuid()) && databaseVolumeUuids.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
        }
        Set<String> reclaimed = new java.util.HashSet<>(resources.reclaimedVolumeUuids());
        TargetIntent targetIntent = ownedDependencyTargetIntent(connection);

        if (hasText(resources.applicationUuid())) {
            if (application.isPresent()) {
                if (reclaimed.stream().anyMatch(applicationVolumeUuids::contains)) {
                    throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
                }
                checkpoint.guard();
                coolifyApiClient.delete(connection, resources.applicationUuid(), true, true, false, false);
                awaitCoolifyApplicationDeletion(connection, resources.applicationUuid());
            }
            hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                graphId + ":" + slot + ":application", "application", resources.applicationUuid());
            if (!reclaimed.containsAll(applicationVolumeUuids)) {
                checkpoint.guard();
                reclaimed.addAll(reclaimOwnedVolumes(targetIntent, connection, graphId + ":" + slot + ":application",
                    volumePlan, applicationVolumeUuids));
                checkpoint.volumesReclaimed(Set.copyOf(reclaimed));
            }
        }
        if (hasText(resources.databaseUuid())) {
            if (database.isPresent()) {
                if (reclaimed.stream().anyMatch(databaseVolumeUuids::contains)) {
                    throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
                }
                checkpoint.guard();
                coolifyApiClient.deleteDatabase(connection, resources.databaseUuid(), true, true, true, false);
                awaitCoolifyDatabaseDeletion(connection, resources.databaseUuid());
            }
            hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                graphId + ":" + slot + ":database", "database", resources.databaseUuid());
            if (!reclaimed.containsAll(databaseVolumeUuids)) {
                checkpoint.guard();
                reclaimed.addAll(reclaimOwnedVolumes(targetIntent, connection, graphId + ":" + slot + ":database",
                    volumePlan, databaseVolumeUuids));
                checkpoint.volumesReclaimed(Set.copyOf(reclaimed));
            }
        }
        if (!reclaimed.equals(volumePlan.keySet())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
        }
    }

    /** Exact provider readback used after idempotent service initialization. */
    public void verifyOwnedDependencyReady(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, String applicationUuid, String databaseUuid, Map<String, String> volumes) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        if (!hasText(applicationUuid)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_READBACK_REQUIRED");
        }
        String marker = "ManagedDependency:" + graphId + ":" + slot;
        var application = coolifyApiClient.getApplication(connection, applicationUuid)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_READBACK_MISSING"));
        requireOwnedDependencyResource(application.raw(), connection, serviceRef, marker);
        if (!"running:healthy".equalsIgnoreCase(application.status())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_HEALTH_READBACK_REQUIRED");
        }
        Map<String, String> expectedVolumes = volumes == null ? Map.of() : Map.copyOf(volumes);
        var storages = coolifyApiClient.listApplicationStorages(connection, applicationUuid);
        if (storages.size() != expectedVolumes.size() || storages.stream().anyMatch(storage ->
            !storage.uuid().equals(expectedVolumes.get(storage.mountPath())) || !"persistent".equals(storage.type())
                || hasText(storage.hostPath())
                || !storage.name().equals(serviceRef + "-" + Integer.toHexString(storage.mountPath().hashCode())))) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
        }
        if (hasText(databaseUuid)) {
            var database = coolifyApiClient.getDatabase(connection, databaseUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_MISSING"));
            requireOwnedDependencyResource(database.raw(), connection, serviceRef + "-db", marker);
            if (!"running:healthy".equalsIgnoreCase(database.status())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_NOT_READY");
            }
        }
    }

    /** Authoritative post-delete readback plus the durable pre-application-delete volume receipt. */
    public void verifyOwnedDependenciesReclaimed(CoolifyConnection connection, String graphId,
            List<com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyRetirementAdapter.Resource> resources) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Dependency provider work must run outside a database transaction.");
        }
        TargetIntent targetIntent = ownedDependencyTargetIntent(connection);
        for (var resource : resources) {
            if (resource == null || !hasText(resource.slot()) || !hasText(resource.serviceRef())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_RECLAMATION_EVIDENCE_REQUIRED");
            }
            if (hasText(resource.applicationUuid())
                && coolifyApiClient.getApplication(connection, resource.applicationUuid()).isPresent()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_DELETION_UNCONFIRMED");
            }
            if (hasText(resource.applicationUuid())) {
                hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                    graphId + ":" + resource.slot() + ":application", "application", resource.applicationUuid());
            }
            if (hasText(resource.databaseUuid())
                && coolifyApiClient.getDatabase(connection, resource.databaseUuid()).isPresent()) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_DELETION_UNCONFIRMED");
            }
            if (hasText(resource.databaseUuid())) {
                hostVolumeReclamationService.verifyContainersAbsent(targetIntent, connection.config().serverUuid(),
                    graphId + ":" + resource.slot() + ":database", "database", resource.databaseUuid());
            }
            Set<String> applicationVolumeUuids = resource.volumes() == null
                ? Set.of() : Set.copyOf(resource.volumes().values());
            if (applicationVolumeUuids.size() != resource.volumes().size()
                || !resource.volumeReclamationPlan().keySet().containsAll(applicationVolumeUuids)
                || !resource.volumeReclamationPlan().keySet().equals(resource.reclaimedVolumeUuids())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
            }
        }
    }

    private Map<String, String> requireOwnedApplicationVolumes(CoolifyConnection connection, String serviceRef,
            OwnedDependencyResources resources) {
        var storages = coolifyApiClient.listApplicationStorages(connection, resources.applicationUuid());
        if (storages.size() != resources.volumes().size() || storages.stream().anyMatch(storage ->
            !storage.uuid().equals(resources.volumes().get(storage.mountPath())) || !"persistent".equals(storage.type())
                || hasText(storage.hostPath())
                || !storage.name().equals(serviceRef + "-" + Integer.toHexString(storage.mountPath().hashCode())))) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
        }
        return storages.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            CoolifyApplicationStorageSummary::uuid, CoolifyApplicationStorageSummary::name));
    }

    private Map<String, String> requireOwnedDatabaseVolumes(CoolifyConnection connection, String databaseUuid) {
        var storages = coolifyApiClient.listDatabaseStorages(connection, databaseUuid);
        if (storages.isEmpty() || storages.stream().anyMatch(storage -> !"persistent".equals(storage.type())
            || hasText(storage.hostPath()) || !hasText(storage.uuid()) || !hasText(storage.name()))) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
        }
        try {
            return storages.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                CoolifyApplicationStorageSummary::uuid, CoolifyApplicationStorageSummary::name));
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_OWNERSHIP_CONFLICT");
        }
    }

    private Set<String> reclaimOwnedVolumes(TargetIntent intent, CoolifyConnection connection, String reference,
            Map<String, String> plan, Set<String> volumeUuids) {
        if (volumeUuids.isEmpty()) return Set.of();
        var receipt = hostVolumeReclamationService.reclaimAndVerify(intent, connection.config().serverUuid(), reference,
            volumeUuids.stream().sorted().map(uuid ->
                new CoolifyHostVolumeReclamationService.Volume(uuid, plan.get(uuid), false)).toList());
        if (!receipt.reclaimedUuids().equals(volumeUuids)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
        }
        return receipt.reclaimedUuids();
    }

    private void awaitCoolifyDatabaseDeletion(CoolifyConnection connection, String databaseUuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        while (Instant.now().isBefore(deadline)) {
            if (coolifyApiClient.getDatabase(connection, databaseUuid).isEmpty()) return;
            try {
                Thread.sleep(Math.max(productProvisioningProperties.pollInterval().toMillis(), 0L));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Coolify database deletion.", error);
            }
        }
        throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_DELETION_UNCONFIRMED");
    }

    private TargetIntent ownedDependencyTargetIntent(CoolifyConnection connection) {
        var matches = customerSelfServiceProperties.trustedServers().entrySet().stream()
            .filter(entry -> entry.getValue() != null
                && connection.config().serverUuid().equals(entry.getValue().uuid()))
            .map(Map.Entry::getKey).toList();
        if (matches.size() != 1) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_TARGET_AUTHORITY_UNAVAILABLE");
        }
        return matches.getFirst();
    }

    private void requireOwnedDependencyResource(JsonNode raw, CoolifyConnection connection, String name, String marker) {
        var config = connection.config();
        if (!name.equals(raw.path("name").asText()) || !marker.equals(raw.path("description").asText())
            || !config.serverUuid().equals(firstNonBlank(raw.path("server_uuid").asText(null), raw.at("/destination/server/uuid").asText(null)))
            || !config.environmentUuid().equals(firstNonBlank(raw.path("environment_uuid").asText(null), raw.at("/environment/uuid").asText(null)))
            || !config.projectUuid().equals(firstNonBlank(raw.path("project_uuid").asText(null), raw.at("/environment/project/uuid").asText(null)))
            || !config.destinationUuid().equals(firstNonBlank(raw.path("destination_uuid").asText(null), raw.at("/destination/uuid").asText(null)))) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_PROVIDER_OWNERSHIP_CONFLICT");
        }
    }

    private void awaitOwnedDatabase(CoolifyConnection connection, String uuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        do {
            var db = coolifyApiClient.getDatabase(connection, uuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_MISSING"));
            if ("running:healthy".equalsIgnoreCase(db.status())) return;
            try {
                Thread.sleep(Math.max(250, productProvisioningProperties.pollInterval().toMillis()));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_WAIT_INTERRUPTED");
            }
        } while (Instant.now().isBefore(deadline));
        throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_NOT_READY");
    }

    private CoolifyApplicationSummary awaitOwnedDependencyDeployment(CoolifyConnection connection, String uuid, String deploymentUuid) {
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        do {
            var application = coolifyApiClient.getApplication(connection, uuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_READBACK_MISSING"));
            var deployment = coolifyApiClient.getDeployment(connection, deploymentUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED"));
            if (!uuid.equals(deployment.applicationUuid()) || !deploymentUuid.equals(deployment.deploymentUuid())) {
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_OWNERSHIP_CONFLICT");
            }
            if (coolifyDeploymentFailed(deployment)) throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_FAILED");
            if (coolifyDeploymentFinished(deployment) && "running:healthy".equalsIgnoreCase(application.status())) return application;
            try {
                Thread.sleep(Math.max(250, productProvisioningProperties.pollInterval().toMillis()));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_WAIT_INTERRUPTED");
            }
        } while (Instant.now().isBefore(deadline));
        throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_NOT_READY");
    }

    public String ownedDependencyDatabaseJdbc(CoolifyConnection connection, String databaseUuid) {
        var database = coolifyApiClient.getDatabase(connection, databaseUuid)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_MISSING"));
        try {
            java.net.URI uri = java.net.URI.create(database.raw().path("internal_db_url").asText());
            if (!"postgresql".equals(uri.getScheme()) || !hasText(uri.getHost()) || uri.getPort() < 1
                || !"/dependency".equals(uri.getPath())) throw new IllegalArgumentException();
            return "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + "/dependency";
        } catch (RuntimeException error) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DATABASE_ADDRESS_UNVERIFIED");
        }
    }

    public void disableOwnedDependency(CoolifyConnection connection, String graphId, String serviceRef,
            String slot, String applicationUuid, OwnedDependencyCheckpoint checkpoint) {
        if (!hasText(applicationUuid)) return;
        var app = coolifyApiClient.getApplication(connection, applicationUuid)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING"));
        requireOwnedDependencyResource(app.raw(), connection, serviceRef, "ManagedDependency:" + graphId + ":" + slot);
        String currentState = upper(app.status()).split(":", 2)[0];
        if (java.util.Set.of("EXITED", "STOPPED").contains(currentState)) return;
        checkpoint.guard();
        coolifyApiClient.stop(connection, applicationUuid, false);
        var readback = coolifyApiClient.getApplication(connection, applicationUuid)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_DISABLE_UNCONFIRMED"));
        requireOwnedDependencyResource(readback.raw(), connection, serviceRef, "ManagedDependency:" + graphId + ":" + slot);
        if (!applicationUuid.equals(readback.uuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DISABLE_UNCONFIRMED");
        }
        String state = upper(readback.status()).split(":", 2)[0];
        if (!java.util.Set.of("EXITED", "STOPPED").contains(state)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DISABLE_UNCONFIRMED");
        }
    }

    public String quiesceOwnedDependencyForRuntimeGeneration(CoolifyConnection connection, String graphId,
            String serviceRef, String slot, String applicationUuid, OwnedDependencyCheckpoint checkpoint) {
        if (!hasText(applicationUuid)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING");
        }
        String marker = "ManagedDependency:" + graphId + ":" + slot;
        var app = coolifyApiClient.getApplication(connection, applicationUuid)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING"));
        requireOwnedDependencyResource(app.raw(), connection, serviceRef, marker);
        if (ownedDependencyStopped(app)) return ownedDependencyGenerationEvidence(graphId, slot, applicationUuid, "STOPPED");
        if (!ownedDependencyRunning(app)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_GENERATION_STATE_UNKNOWN");
        }
        checkpoint.guard();
        coolifyApiClient.stop(connection, applicationUuid, false);
        Instant deadline = Instant.now().plus(productProvisioningProperties.requestTimeout());
        do {
            checkpoint.guard();
            var observed = coolifyApiClient.getApplication(connection, applicationUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING"));
            requireOwnedDependencyResource(observed.raw(), connection, serviceRef, marker);
            if (ownedDependencyStopped(observed)) {
                return ownedDependencyGenerationEvidence(graphId, slot, applicationUuid, "STOPPED");
            }
            sleepOwnedDependencyPoll("DEPENDENCY_GENERATION_QUIESCE_INTERRUPTED");
        } while (Instant.now().isBefore(deadline));
        throw new ResponseStatusException(CONFLICT, "DEPENDENCY_GENERATION_QUIESCE_UNCONFIRMED");
    }

    public String resumeOwnedDependencyAfterRuntimeGeneration(CoolifyConnection connection, String graphId,
            String serviceRef, String slot, String applicationUuid, OwnedDependencyCheckpoint checkpoint) {
        if (!hasText(applicationUuid)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING");
        }
        String marker = "ManagedDependency:" + graphId + ":" + slot;
        var app = coolifyApiClient.getApplication(connection, applicationUuid)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "DEPENDENCY_APPLICATION_MISSING"));
        requireOwnedDependencyResource(app.raw(), connection, serviceRef, marker);
        if ("running:healthy".equalsIgnoreCase(app.status())) {
            return ownedDependencyGenerationEvidence(graphId, slot, applicationUuid, "RUNNING");
        }
        if (!ownedDependencyStopped(app)) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_GENERATION_STATE_UNKNOWN");
        }
        checkpoint.guard();
        CoolifyActionResponse response = coolifyApiClient.start(connection, applicationUuid, true, true);
        if (response == null || !hasText(response.deploymentUuid())) {
            throw new ResponseStatusException(CONFLICT, "DEPENDENCY_DEPLOYMENT_READBACK_REQUIRED");
        }
        CoolifyApplicationSummary observed = awaitOwnedDependencyDeployment(
            connection, applicationUuid, response.deploymentUuid()
        );
        requireOwnedDependencyResource(observed.raw(), connection, serviceRef, marker);
        return ownedDependencyGenerationEvidence(graphId, slot, applicationUuid, "RUNNING");
    }

    private boolean ownedDependencyRunning(CoolifyApplicationSummary application) {
        String state = upper(application == null ? null : application.status()).split(":", 2)[0];
        return java.util.Set.of("RUNNING", "STARTING", "RESTARTING", "DEPLOYING").contains(state);
    }

    private boolean ownedDependencyStopped(CoolifyApplicationSummary application) {
        String state = upper(application == null ? null : application.status()).split(":", 2)[0];
        return java.util.Set.of("EXITED", "STOPPED", "INACTIVE", "OFFLINE").contains(state);
    }

    private void sleepOwnedDependencyPoll(String errorCode) {
        try {
            Thread.sleep(Math.max(250, productProvisioningProperties.pollInterval().toMillis()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(CONFLICT, errorCode);
        }
    }

    private String ownedDependencyGenerationEvidence(String graphId, String slot, String applicationUuid, String state) {
        try {
            String material = String.join("\n", graphId, slot, applicationUuid, state);
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return "managed-dependency-generation:" + graphId + ":" + slot + ":" + state.toLowerCase(java.util.Locale.ROOT)
                + ":" + digest;
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("Dependency generation evidence could not be encoded.", error);
        }
    }

    private record ReconciledRailwayService(
        String projectId,
        String environmentId,
        String serviceId,
        RailwayGraphqlClient.RailwayServiceInstanceSummary instance,
        String publicBaseUrl,
        String deploymentId
    ) {
    }

    private record CoolifyBinding(
        DeploymentTargetProfileEntity profile,
        CoolifyConnection connection
    ) {
    }
}
