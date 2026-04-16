package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformInferenceProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedInferenceProvisioningService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformInferenceProvisioningProperties inferenceProvisioningProperties;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedInferenceServiceRepository serviceRepository;
    private final PlatformManagedInferenceEndpointRepository endpointRepository;
    private final PlatformManagedInferenceServiceService platformManagedInferenceServiceService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public PlatformManagedInferenceProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                                       PlatformInferenceProvisioningProperties inferenceProvisioningProperties,
                                                       RailwayGraphqlClient railwayGraphqlClient,
                                                       PlatformSecretService platformSecretService,
                                                       PlatformManagedInferenceServiceRepository serviceRepository,
                                                       PlatformManagedInferenceEndpointRepository endpointRepository,
                                                       PlatformManagedInferenceServiceService platformManagedInferenceServiceService,
                                                       PlatformAuditService platformAuditService,
                                                       ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.inferenceProvisioningProperties = inferenceProvisioningProperties;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.platformSecretService = platformSecretService;
        this.serviceRepository = serviceRepository;
        this.endpointRepository = endpointRepository;
        this.platformManagedInferenceServiceService = platformManagedInferenceServiceService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlatformManagedInferenceServiceSummary reconcile(String serviceRef) {
        PlatformManagedInferenceServiceEntity service = platformManagedInferenceServiceService.requireService(serviceRef);
        if (!requiresRailwayLifecycle(service)) {
            if (hasText(service.getBaseUrl()) && !service.getStatus().equalsIgnoreCase("ACTIVE")) {
                service.setStatus("ACTIVE");
                service.setUpdatedAt(Instant.now());
                serviceRepository.save(service);
            }
            return platformManagedInferenceServiceService.getService(serviceRef);
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
                "Railway project and services reconciled successfully."
            );
            platformAuditService.record(
                "MANAGED_INFERENCE_RECONCILED",
                "MANAGED_INFERENCE_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "deploymentId", reconciled.deploymentId(),
                    "status", service.getStatus()
                )
            );
            return platformManagedInferenceServiceService.getService(serviceRef);
        } catch (RuntimeException ex) {
            markLifecycleFailure(service, "FAILED", ex.getMessage(), "lastReconcileStatus", "lastReconcileMessage");
            platformAuditService.record(
                "MANAGED_INFERENCE_RECONCILE_FAILED",
                "MANAGED_INFERENCE_SERVICE",
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
    public PlatformManagedInferenceServiceSummary scale(String serviceRef, Integer desiredReplicas) {
        PlatformManagedInferenceServiceSummary ignored = platformManagedInferenceServiceService.updateDesiredReplicas(serviceRef, desiredReplicas);
        return reconcile(serviceRef);
    }

    @Transactional
    public PlatformManagedInferenceServiceSummary restart(String serviceRef) {
        PlatformManagedInferenceServiceEntity service = platformManagedInferenceServiceService.requireService(serviceRef);
        if (!requiresRailwayLifecycle(service)) {
            return platformManagedInferenceServiceService.getService(serviceRef);
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
                inferenceProvisioningProperties.pollInterval(),
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
                "Managed inference service restarted successfully."
            );
            ObjectNode details = mutableDetails(service);
            details.put("lastRestartedAt", Instant.now().toString());
            details.put("lastRestartStatus", "SUCCESS");
            details.put("lastRestartMessage", "Managed inference service restarted successfully.");
            service.setDetailsJson(details.toPrettyString());
            serviceRepository.save(service);
            platformAuditService.record(
                "MANAGED_INFERENCE_RESTARTED",
                "MANAGED_INFERENCE_SERVICE",
                service.getServiceRef(),
                Map.of(
                    "serviceRef", service.getServiceRef(),
                    "deploymentId", deploymentId
                )
            );
            return platformManagedInferenceServiceService.getService(serviceRef);
        } catch (RuntimeException ex) {
            markLifecycleFailure(service, "FAILED", ex.getMessage(), "lastRestartStatus", "lastRestartMessage");
            platformAuditService.record(
                "MANAGED_INFERENCE_RESTART_FAILED",
                "MANAGED_INFERENCE_SERVICE",
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
    public PlatformManagedInferenceServiceSummary forceRecreate(String serviceRef) {
        PlatformManagedInferenceServiceEntity service = platformManagedInferenceServiceService.requireService(serviceRef);
        if (!requiresRailwayLifecycle(service)) {
            return platformManagedInferenceServiceService.getService(serviceRef);
        }

        platformAuditService.record(
            "MANAGED_INFERENCE_FORCE_RECREATE_REQUESTED",
            "MANAGED_INFERENCE_SERVICE",
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

    private void awaitProjectDeletion(String projectId, String projectName) {
        Instant deadline = Instant.now().plus(provisioningProperties.deploymentTimeout());
        while (Instant.now().isBefore(deadline)) {
            boolean projectExistsById = false;
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
                Thread.sleep(Math.max(inferenceProvisioningProperties.pollInterval().toMillis(), 0L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException("Interrupted while waiting for Railway project deletion.", ex);
            }
        }
        throw new RailwayProvisioningException(
            "Timed out waiting for Railway project deletion to settle: " + projectName
        );
    }

    private boolean requiresRailwayLifecycle(PlatformManagedInferenceServiceEntity service) {
        String kind = upper(service.getServiceKind());
        return "SHARED_EMBEDDING_SERVICE".equals(kind) || "SHARED_OLLAMA_SERVICE".equals(kind);
    }

    private ReconciledRailwayService reconcileRailwayService(PlatformManagedInferenceServiceEntity service) {
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
        railwayGraphqlClient.upsertVariables(
            project.id(),
            environment.id(),
            railwayService.id(),
            buildServiceEnv(service)
        );

        if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
            railwayGraphqlClient.commitStagedChanges(environment.id());
        }
        String deploymentId = railwayGraphqlClient.deployService(railwayService.id(), environment.id());
        RailwayApiProvisioningProvider.awaitSuccessfulDeployment(
            deploymentId,
            sharedServiceName(service),
            provisioningProperties.deploymentTimeout(),
            inferenceProvisioningProperties.pollInterval(),
            () -> railwayGraphqlClient.getDeployment(deploymentId),
            duration -> Thread.sleep(Math.max(duration.toMillis(), 0L)),
            ex -> {
            }
        );

        RailwayGraphqlClient.RailwayServiceInstanceSummary instance = railwayGraphqlClient.getServiceInstance(
            environment.id(),
            railwayService.id()
        );
        String publicBaseUrl = ensureServiceDomain(project.id(), environment.id(), railwayService.id());
        return new ReconciledRailwayService(
            project.id(),
            environment.id(),
            railwayService.id(),
            instance,
            publicBaseUrl,
            deploymentId
        );
    }

    private void finalizeActiveService(PlatformManagedInferenceServiceEntity service,
                                       String projectId,
                                       String environmentId,
                                       String serviceId,
                                       RailwayGraphqlClient.RailwayServiceInstanceSummary instance,
                                       String publicBaseUrl,
                                       String deploymentId,
                                       String message) {
        String privateNetworkUrl = trimToNull(instance.upstreamUrl());

        service.setRailwayProjectId(projectId);
        service.setRailwayEnvironmentId(environmentId);
        service.setRailwayServiceId(serviceId);
        service.setDesiredReplicas(desiredReplicas(service));
        service.setActualReplicas(desiredReplicas(service));
        service.setBaseUrl(publicBaseUrl);
        service.setPrivateNetworkUrl(privateNetworkUrl);
        service.setHealthPath(healthPath(service));
        service.setStatus("ACTIVE");
        ObjectNode details = buildServiceDetails(service, deploymentId);
        details.put("lastReconcileStatus", "SUCCESS");
        details.put("lastReconcileMessage", blankToFallback(message, "Managed inference service is active."));
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);

        List<PlatformManagedInferenceEndpointEntity> endpoints = endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId());
        for (PlatformManagedInferenceEndpointEntity endpoint : endpoints) {
            endpoint.setBaseUrl(endpointBaseUrl(service, publicBaseUrl));
            endpoint.setProtocolType(ManagedDeploymentProfileCatalog.INFERENCE_PROTOCOL_OPENAI_COMPATIBLE);
            endpoint.setSecretName(service.getSecretName());
            endpoint.setStatus("ACTIVE");
            endpoint.setUpdatedAt(Instant.now());
            endpointRepository.save(endpoint);
        }
    }

    private String resolveEnvironmentName(PlatformManagedInferenceServiceEntity service) {
        return hasText(service.getEnvironmentScope()) ? service.getEnvironmentScope().trim() : provisioningProperties.environmentName();
    }

    private String sharedProjectName(PlatformManagedInferenceServiceEntity service) {
        return trimProjectName(
            inferenceProvisioningProperties.sharedProjectNamePrefix() + "-" + normalizeToken(resolveEnvironmentName(service)) + "-" + normalizeToken(service.getServiceRef())
        );
    }

    private String sharedServiceName(PlatformManagedInferenceServiceEntity service) {
        return switch (upper(service.getServiceKind())) {
            case "SHARED_EMBEDDING_SERVICE" ->
                trimProjectName(inferenceProvisioningProperties.sharedEmbeddingServiceNamePrefix() + "-" + normalizeToken(service.getServiceRef()));
            case "SHARED_OLLAMA_SERVICE" ->
                trimProjectName(inferenceProvisioningProperties.sharedOllamaServiceNamePrefix() + "-" + normalizeToken(service.getServiceRef()));
            default -> trimProjectName("inference-" + normalizeToken(service.getServiceRef()));
        };
    }

    private String serviceRoot(PlatformManagedInferenceServiceEntity service) {
        return switch (upper(service.getServiceKind())) {
            case "SHARED_EMBEDDING_SERVICE" -> inferenceProvisioningProperties.sharedEmbeddingServiceRoot();
            case "SHARED_OLLAMA_SERVICE" -> inferenceProvisioningProperties.sharedOllamaServiceRoot();
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed inference service kind: " + service.getServiceKind());
        };
    }

    private String dockerfilePath(PlatformManagedInferenceServiceEntity service) {
        return switch (upper(service.getServiceKind())) {
            case "SHARED_EMBEDDING_SERVICE" -> inferenceProvisioningProperties.sharedEmbeddingDockerfilePath();
            case "SHARED_OLLAMA_SERVICE" -> inferenceProvisioningProperties.sharedOllamaDockerfilePath();
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed inference service kind: " + service.getServiceKind());
        };
    }

    private String healthPath(PlatformManagedInferenceServiceEntity service) {
        return switch (upper(service.getServiceKind())) {
            case "SHARED_EMBEDDING_SERVICE" -> inferenceProvisioningProperties.sharedEmbeddingHealthPath();
            case "SHARED_OLLAMA_SERVICE" -> inferenceProvisioningProperties.sharedOllamaHealthPath();
            default -> "/actuator/health";
        };
    }

    private int desiredReplicas(PlatformManagedInferenceServiceEntity service) {
        Integer desired = service.getDesiredReplicas();
        return desired != null && desired > 0 ? desired : 1;
    }

    private void ensureServiceSecret(PlatformManagedInferenceServiceEntity service) {
        if (!hasText(service.getSecretName())) {
            service.setSecretName(defaultSecretName(service.getServiceRef()));
        }
        if (!platformSecretService.isSecretPresent(service.getSecretName())) {
            platformSecretService.upsertManagedSecret(
                service.getSecretName(),
                UUID.randomUUID().toString().replace("-", ""),
                Map.of("serviceRef", service.getServiceRef(), "purpose", "INFERENCE_SERVICE_API_KEY")
            );
        }
    }

    private String defaultSecretName(String serviceRef) {
        return "MANAGED_INFERENCE_" + normalizeToken(serviceRef).replace('-', '_').toUpperCase(Locale.ROOT) + "_API_KEY";
    }

    private List<RailwayGraphqlClient.RailwayEnvVarInput> buildServiceEnv(PlatformManagedInferenceServiceEntity service) {
        List<RailwayGraphqlClient.RailwayEnvVarInput> env = new ArrayList<>();
        switch (upper(service.getServiceKind())) {
            case "SHARED_EMBEDDING_SERVICE" -> {
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_SERVICE_FEATURES_ENABLE_GENERATION", "false"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS", "true"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_PROVIDERS_EMBEDDING_PROVIDER", ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_PROVIDERS_ENABLE_FALLBACK", "false"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_PROVIDERS_ONNX_ENABLED", "true"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_PROVIDERS_ONNX_MODEL_ALIAS", blankToFallback(service.getModelId(), "bge-small-en-v1.5")));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_PROVIDERS_ONNX_MAX_SEQUENCE_LENGTH", "512"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_PROVIDERS_ONNX_USE_GPU", "false"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("AI_FABRIC_EMBEDDING_WORKER_API_KEY", resolveSecret(service.getSecretName())));
            }
            case "SHARED_OLLAMA_SERVICE" -> {
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("OLLAMA_KEEP_ALIVE", "24h"));
                env.add(new RailwayGraphqlClient.RailwayEnvVarInput("OLLAMA_ORIGINS", "*"));
                if (hasText(service.getModelId())) {
                    env.add(new RailwayGraphqlClient.RailwayEnvVarInput("OLLAMA_BOOTSTRAP_MODELS", service.getModelId().trim()));
                }
            }
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported managed inference service kind: " + service.getServiceKind());
        }
        return env;
    }

    private String resolveSecret(String secretName) {
        String value = platformSecretService.resolveSecret(secretName);
        if (!hasText(value)) {
            throw new RailwayProvisioningConfigurationException(
                "Missing managed inference secret '" + secretName + "'."
            );
        }
        return value;
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
            throw new RailwayProvisioningException("Railway service domain was not created for managed inference service " + serviceId + ".");
        }
        return "https://" + domain.domain();
    }

    private String endpointBaseUrl(PlatformManagedInferenceServiceEntity service, String publicBaseUrl) {
        return switch (upper(service.getProtocolType())) {
            case ManagedDeploymentProfileCatalog.INFERENCE_PROTOCOL_OPENAI_COMPATIBLE -> publicBaseUrl + "/v1";
            default -> publicBaseUrl;
        };
    }

    private ObjectNode buildServiceDetails(PlatformManagedInferenceServiceEntity service,
                                           String deploymentId) {
        ObjectNode details = mutableDetails(service);
        details.put("serviceRef", service.getServiceRef());
        details.put("serviceKind", blankToFallback(service.getServiceKind(), ""));
        details.put("deploymentMode", blankToFallback(service.getDeploymentMode(), ""));
        details.put("deploymentId", deploymentId);
        details.put("reconciledAt", Instant.now().toString());
        details.put("lastDeploymentId", blankToFallback(deploymentId, ""));
        details.put("lastReconciledAt", Instant.now().toString());
        return details;
    }

    private void clearRailwayBinding(PlatformManagedInferenceServiceEntity service) {
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
        details.put("lastForceRecreateMessage", "Managed inference service linkage was cleared and will be recreated.");
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);

        List<PlatformManagedInferenceEndpointEntity> endpoints = endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId());
        for (PlatformManagedInferenceEndpointEntity endpoint : endpoints) {
            endpoint.setBaseUrl(null);
            endpoint.setStatus("CREATED");
            endpoint.setUpdatedAt(Instant.now());
            endpointRepository.save(endpoint);
        }
    }

    private void markLifecycleFailure(PlatformManagedInferenceServiceEntity service,
                                      String status,
                                      String message,
                                      String statusField,
                                      String messageField) {
        service.setStatus(status);
        ObjectNode details = mutableDetails(service);
        details.put(statusField, "FAILED");
        details.put(messageField, blankToFallback(message, "Managed inference service operation failed."));
        details.put("lastFailureAt", Instant.now().toString());
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
    }

    private ObjectNode mutableDetails(PlatformManagedInferenceServiceEntity service) {
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
