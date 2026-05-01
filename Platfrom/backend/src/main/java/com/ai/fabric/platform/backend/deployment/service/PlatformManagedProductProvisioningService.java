package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
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
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedProductServiceRepository serviceRepository;
    private final ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;
    private final PlatformManagedProductServiceService serviceService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public PlatformManagedProductProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                                     PlatformProductProvisioningProperties productProvisioningProperties,
                                                     PlatformDeliveryProperties deliveryProperties,
                                                     RailwayGraphqlClient railwayGraphqlClient,
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
        this.platformSecretService = platformSecretService;
        this.serviceRepository = serviceRepository;
        this.shopifyStoreConnectionRepository = shopifyStoreConnectionRepository;
        this.serviceService = serviceService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlatformManagedProductServiceSummary reconcile(String serviceRef) {
        PlatformManagedProductServiceEntity service = serviceService.requireService(serviceRef);
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
        return "SHOPIFY_BRIDGE_SERVICE".equals(upper(service.getServiceKind()));
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
            default -> trimProjectName("product-" + normalizeToken(service.getServiceRef()));
        };
    }

    private String serviceRoot(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getServiceRoot())) {
            return service.getServiceRoot().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeServiceRoot();
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        };
    }

    private String dockerfilePath(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getDockerfilePath())) {
            return service.getDockerfilePath().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeDockerfilePath();
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        };
    }

    private String healthPath(PlatformManagedProductServiceEntity service) {
        if (hasText(service.getHealthPath())) {
            return service.getHealthPath().trim();
        }
        return switch (upper(service.getServiceKind())) {
            case "SHOPIFY_BRIDGE_SERVICE" -> productProvisioningProperties.shopifyBridgeHealthPath();
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
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed product service kind: " + service.getServiceKind());
        }
        return env;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String trimToNull(String value) {
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
}
