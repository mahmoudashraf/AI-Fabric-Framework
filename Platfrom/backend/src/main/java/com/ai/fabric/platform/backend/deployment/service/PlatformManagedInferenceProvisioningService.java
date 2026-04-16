package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformInferenceProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
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
    private final ObjectMapper objectMapper;

    public PlatformManagedInferenceProvisioningService(PlatformProvisioningProperties provisioningProperties,
                                                       PlatformInferenceProvisioningProperties inferenceProvisioningProperties,
                                                       RailwayGraphqlClient railwayGraphqlClient,
                                                       PlatformSecretService platformSecretService,
                                                       PlatformManagedInferenceServiceRepository serviceRepository,
                                                       PlatformManagedInferenceEndpointRepository endpointRepository,
                                                       PlatformManagedInferenceServiceService platformManagedInferenceServiceService,
                                                       ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.inferenceProvisioningProperties = inferenceProvisioningProperties;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.platformSecretService = platformSecretService;
        this.serviceRepository = serviceRepository;
        this.endpointRepository = endpointRepository;
        this.platformManagedInferenceServiceService = platformManagedInferenceServiceService;
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
            String privateNetworkUrl = trimToNull(instance.upstreamUrl());

            service.setRailwayProjectId(project.id());
            service.setRailwayEnvironmentId(environment.id());
            service.setRailwayServiceId(railwayService.id());
            service.setDesiredReplicas(desiredReplicas(service));
            service.setActualReplicas(desiredReplicas(service));
            service.setBaseUrl(publicBaseUrl);
            service.setPrivateNetworkUrl(privateNetworkUrl);
            service.setHealthPath(healthPath(service));
            service.setStatus("ACTIVE");
            service.setDetailsJson(buildServiceDetails(service, deploymentId).toPrettyString());
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
            return platformManagedInferenceServiceService.getService(serviceRef);
        } catch (RuntimeException ex) {
            service.setStatus("FAILED");
            service.setUpdatedAt(Instant.now());
            serviceRepository.save(service);
            throw ex;
        }
    }

    @Transactional
    public PlatformManagedInferenceServiceSummary scale(String serviceRef, Integer desiredReplicas) {
        PlatformManagedInferenceServiceSummary ignored = platformManagedInferenceServiceService.updateDesiredReplicas(serviceRef, desiredReplicas);
        return reconcile(serviceRef);
    }

    private boolean requiresRailwayLifecycle(PlatformManagedInferenceServiceEntity service) {
        String kind = upper(service.getServiceKind());
        return "SHARED_EMBEDDING_SERVICE".equals(kind) || "SHARED_OLLAMA_SERVICE".equals(kind);
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
        ObjectNode details = objectMapper.createObjectNode();
        details.put("serviceRef", service.getServiceRef());
        details.put("serviceKind", blankToFallback(service.getServiceKind(), ""));
        details.put("deploymentMode", blankToFallback(service.getDeploymentMode(), ""));
        details.put("deploymentId", deploymentId);
        details.put("reconciledAt", Instant.now().toString());
        return details;
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
}
