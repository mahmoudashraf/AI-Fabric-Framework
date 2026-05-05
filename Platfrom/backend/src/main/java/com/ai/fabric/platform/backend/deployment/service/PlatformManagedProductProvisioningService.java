package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedProductProvisioningService {

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
                                                     ObjectMapper objectMapper) {
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
    public PlatformManagedProductServiceSummary scale(String serviceRef, Integer desiredReplicas) {
        serviceService.updateDesiredReplicas(serviceRef, desiredReplicas);
        return reconcile(serviceRef);
    }

    @Transactional
    public PlatformManagedProductServiceSummary restart(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
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
            CoolifyApplicationSummary observed = coolifyApiClient.getApplication(binding.connection(), application.uuid())
                .orElse(application);
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
            CoolifyApplicationSummary observed = coolifyApiClient.getApplication(binding.connection(), applicationUuid)
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Coolify application not found: " + applicationUuid));
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
        ObjectNode details = mutableDetails(service);
        String applicationUuid = trimToNull(details.path("coolifyApplicationUuid").asText(null));
        if (hasText(applicationUuid)) {
            CoolifyBinding binding = resolveCoolifyBinding(service);
            try {
                coolifyApiClient.delete(binding.connection(), applicationUuid, true, false, true, true);
            } catch (RuntimeException ex) {
                String message = blankToFallback(ex.getMessage(), "").toLowerCase(Locale.ROOT);
                if (!message.contains("404") && !message.contains("not found")) {
                    throw ex;
                }
            }
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
        String appName = sharedServiceName(service);
        ObjectNode details = mutableDetails(service);
        String existingUuid = trimToNull(details.path("coolifyApplicationUuid").asText(null));
        CoolifyApplicationSummary existing = null;
        if (hasText(existingUuid)) {
            existing = coolifyApiClient.getApplication(binding.connection(), existingUuid).orElse(null);
        }
        if (existing == null) {
            existing = coolifyApiClient.listApplications(binding.connection()).stream()
                .filter(app -> appName.equalsIgnoreCase(blankToFallback(app.name(), "")))
                .findFirst()
                .orElse(null);
        }
        if (existing == null && hasText(service.getBaseUrl())) {
            existing = coolifyApiClient.listApplications(binding.connection()).stream()
                .filter(app -> coolifyFqdnMatchesBaseUrl(app.fqdn(), service.getBaseUrl()))
                .findFirst()
                .orElse(null);
        }
        CoolifyCreatePublicApplicationRequest request = coolifyPublicApplicationRequest(service, binding, appName);
        if (existing != null && hasText(existing.uuid())) {
            coolifyApiClient.updatePublicApplication(binding.connection(), existing.uuid(), request);
            return coolifyApiClient.getApplication(binding.connection(), existing.uuid()).orElse(existing);
        }
        String uuid = coolifyApiClient.createPublicApplication(binding.connection(), request);
        return coolifyApiClient.getApplication(binding.connection(), uuid)
            .orElse(new CoolifyApplicationSummary(uuid, appName, request.domains(), "created", null, null, objectMapper.createObjectNode()));
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
            profile = targetProfileRepository.findByProviderTypeOrderByEnvironmentNameAscUpdatedAtDesc(DeploymentProviderType.COOLIFY).stream()
                .filter(DeploymentTargetProfileEntity::isActive)
                .filter(DeploymentTargetProfileEntity::isPlatformServicesAllowed)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "No active Coolify target profile allows platform services."));
            details.put("providerType", "COOLIFY");
            details.put("targetProfileId", profile.getId());
            service.setDetailsJson(details.toPrettyString());
            serviceRepository.save(service);
        }
        if (!profile.isActive() || !profile.isPlatformServicesAllowed()) {
            throw new ResponseStatusException(CONFLICT, "Coolify target profile is not active for platform services: " + profile.getId());
        }
        return new CoolifyBinding(profile, coolifyTargetProfileResolver.requireConnection(profile));
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
            default -> "/actuator/health";
        };
    }

    private int desiredReplicas(PlatformManagedProductServiceEntity service) {
        Integer desired = service.getDesiredReplicas();
        return desired != null && desired > 0 ? desired : 1;
    }

    private void ensureServiceSecret(PlatformManagedProductServiceEntity service) {
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
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_INTERNAL_API_KEY", sharedSecret));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_SERVICE_REF", service.getServiceRef()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SCOPE", resolveEnvironmentName(service)));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_PROTOCOL_VERSION", "2025-11-25"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_API_KEY_HEADER_ALLOWLIST", "X-API-KEY,X-MCP-API-KEY,X-LOOM-MCP-KEY"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_PROFILE_REF_ALLOWLIST", "MCP_PROFILE_SHOPIFY_UCP_AGENT,SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_PROFILE_SHOPIFY_UCP_AGENT", defaultShopifyUcpAgentProfile()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("SHOPIFY_BRIDGE_MCP_UCP_AGENT_PROFILE", defaultShopifyUcpAgentProfile()));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SECRET_RESOLUTION_ENABLED", "false"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MCP_GATEWAY_ENVIRONMENT_SECRET_REF_PREFIX", "MCP_SECRET_"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,info"));
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
        return "https://shopify.dev/ucp/agent-profiles/examples/2026-04-08/valid-with-capabilities.json";
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
