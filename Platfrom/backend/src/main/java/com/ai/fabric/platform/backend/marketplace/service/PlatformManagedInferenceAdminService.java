package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedInferenceProvisioningService;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceDependentDeploymentSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceEndpointSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceHealthSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceProbeSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class PlatformManagedInferenceAdminService {

    private static final String TARGET_TYPE = "MANAGED_INFERENCE_SERVICE";
    private static final String EMBEDDING_SMOKE_TEXT = "platform managed inference health check";
    private static final String CHAT_SMOKE_TEXT = "Reply with ok";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private final PlatformManagedInferenceServiceService serviceService;
    private final PlatformManagedInferenceProvisioningService provisioningService;
    private final PlatformManagedInferenceServiceRepository serviceRepository;
    private final PlatformManagedInferenceEndpointRepository endpointRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository deploymentDraftRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PlatformManagedInferenceAdminService(PlatformManagedInferenceServiceService serviceService,
                                                PlatformManagedInferenceProvisioningService provisioningService,
                                                PlatformManagedInferenceServiceRepository serviceRepository,
                                                PlatformManagedInferenceEndpointRepository endpointRepository,
                                                DeploymentRepository deploymentRepository,
                                                DeploymentDraftRepository deploymentDraftRepository,
                                                DeploymentVersionRepository deploymentVersionRepository,
                                                PlatformSecretService platformSecretService,
                                                PlatformAuditService platformAuditService,
                                                RailwayGraphqlClient railwayGraphqlClient,
                                                ObjectMapper objectMapper) {
        this.serviceService = serviceService;
        this.provisioningService = provisioningService;
        this.serviceRepository = serviceRepository;
        this.endpointRepository = endpointRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentDraftRepository = deploymentDraftRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    }

    public List<PlatformManagedInferenceDependentDeploymentSummary> listDependents(String serviceRef) {
        PlatformManagedInferenceServiceEntity service = serviceService.requireService(serviceRef);
        Set<String> endpointRefs = endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId()).stream()
            .map(PlatformManagedInferenceEndpointEntity::getProfileRef)
            .filter(this::hasText)
            .map(String::trim)
            .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<PlatformManagedInferenceDependentDeploymentSummary> summaries = new ArrayList<>();
        for (DeploymentEntity deployment : deploymentRepository.findAllByOrderByCreatedAtDesc()) {
            DeploymentDraftEntity draft = resolveDraft(deployment);
            DeploymentVersionEntity version = resolveVersion(deployment);
            Set<String> usages = new LinkedHashSet<>();
            usages.addAll(providerConfigUsages(draft == null ? null : draft.getProviderConfigJson(), service.getServiceRef(), endpointRefs, "draft"));
            usages.addAll(providerConfigUsages(version == null ? null : version.getProviderConfigJson(), service.getServiceRef(), endpointRefs, "active-version"));
            if (!usages.isEmpty()) {
                summaries.add(new PlatformManagedInferenceDependentDeploymentSummary(
                    deployment.getId(),
                    deployment.getName(),
                    deployment.getEnvironmentName(),
                    deployment.getStatus(),
                    deployment.getActiveVersionId(),
                    usages.stream().anyMatch(item -> item.startsWith("draft:")),
                    usages.stream().anyMatch(item -> item.startsWith("active-version:")),
                    isRuntimeActive(deployment),
                    List.copyOf(usages)
                ));
            }
        }
        return summaries;
    }

    public List<PlatformAuditEventSummary> listActivity(String serviceRef) {
        serviceService.requireService(serviceRef);
        return platformAuditService.listRecentEventsForTarget(TARGET_TYPE, serviceRef, 100);
    }

    @Transactional
    public PlatformManagedInferenceHealthSummary getHealth(String serviceRef) {
        PlatformManagedInferenceServiceEntity service = serviceService.requireService(serviceRef);
        List<PlatformManagedInferenceEndpointEntity> endpoints = endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId());
        boolean railwayManaged = requiresRailwayLifecycle(service);
        boolean secretConfigured = !hasText(service.getSecretName()) || platformSecretService.isSecretPresent(service.getSecretName());

        ProbeResult healthProbe = buildHealthProbe(service, railwayManaged);
        ProbeResult inferenceProbe = buildInferenceProbe(service, endpoints, secretConfigured);
        DriftResult drift = buildDrift(service, endpoints, secretConfigured, railwayManaged);

        String overallStatus = summarizeOverallStatus(healthProbe.summary().status(), inferenceProbe.summary().status(), drift.status());
        Instant now = Instant.now();
        ObjectNode details = mutableDetails(service);
        details.put("lastProbeAt", now.toString());
        details.put("lastProbeStatus", overallStatus);
        details.put("lastProbeMessage", firstNonBlank(inferenceProbe.summary().message(), healthProbe.summary().message(), drift.message(), ""));
        details.put("driftStatus", drift.status());
        details.put("driftMessage", drift.message());
        if ("READY".equals(overallStatus)) {
            details.put("lastHealthyAt", now.toString());
            details.put("lastSuccessfulProbeAt", now.toString());
        } else if ("FAILED".equals(overallStatus) || "BLOCKED".equals(overallStatus) || "DEGRADED".equals(overallStatus)) {
            details.put("lastFailedProbeAt", now.toString());
        }
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(now);
        serviceRepository.save(service);

        platformAuditService.record(
            "MANAGED_INFERENCE_HEALTH_CHECKED",
            TARGET_TYPE,
            service.getServiceRef(),
            Map.of(
                "serviceRef", service.getServiceRef(),
                "status", overallStatus,
                "driftStatus", drift.status(),
                "secretConfigured", secretConfigured
            )
        );

        return new PlatformManagedInferenceHealthSummary(
            service.getServiceRef(),
            overallStatus,
            railwayManaged,
            secretConfigured,
            drift.status(),
            drift.message(),
            text(details, "lastHealthyAt"),
            text(details, "lastProbeAt"),
            text(details, "lastSuccessfulProbeAt"),
            text(details, "lastFailedProbeAt"),
            text(details, "lastProbeStatus"),
            text(details, "lastProbeMessage"),
            healthProbe.summary(),
            inferenceProbe.summary()
        );
    }

    public PlatformManagedInferenceServiceSummary reconcile(String serviceRef) {
        provisioningService.reconcile(serviceRef);
        return verifyAfterMutation(serviceRef, "RECONCILE");
    }

    public PlatformManagedInferenceServiceSummary scale(String serviceRef, Integer desiredReplicas) {
        provisioningService.scale(serviceRef, desiredReplicas);
        return verifyAfterMutation(serviceRef, "SCALE");
    }

    @Transactional
    public PlatformManagedInferenceServiceSummary rotateSecret(String serviceRef, String value) {
        PlatformManagedInferenceServiceEntity service = serviceService.requireService(serviceRef);
        if (!hasText(service.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "Managed inference service does not declare a secret to rotate: " + serviceRef);
        }
        if (!hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "Secret value is required.");
        }
        Map<String, Object> auditDetails = Map.of(
            "serviceRef", service.getServiceRef(),
            "deploymentId", service.getDeploymentId() == null ? "" : service.getDeploymentId()
        );
        if (platformSecretService.isManagedSecretName(service.getSecretName())) {
            platformSecretService.upsertManagedSecret(service.getSecretName(), value.trim(), auditDetails);
        } else if (platformSecretService.isSupportedSecretName(service.getSecretName())) {
            platformSecretService.updateSecret(service.getSecretName(), value.trim());
        } else {
            throw new ResponseStatusException(CONFLICT, "Unsupported secret type for managed inference service: " + service.getSecretName());
        }
        ObjectNode details = mutableDetails(service);
        details.put("lastSecretRotatedAt", Instant.now().toString());
        service.setDetailsJson(details.toPrettyString());
        serviceRepository.save(service);
        platformAuditService.record(
            "MANAGED_INFERENCE_SECRET_ROTATED",
            TARGET_TYPE,
            service.getServiceRef(),
            Map.of("serviceRef", service.getServiceRef(), "secretName", service.getSecretName())
        );
        provisioningService.reconcile(serviceRef);
        return verifyAfterMutation(serviceRef, "ROTATE_SECRET");
    }

    public PlatformManagedInferenceServiceSummary restart(String serviceRef) {
        provisioningService.restart(serviceRef);
        return verifyAfterMutation(serviceRef, "RESTART");
    }

    public PlatformManagedInferenceServiceSummary forceRecreate(String serviceRef) {
        provisioningService.forceRecreate(serviceRef);
        platformAuditService.record(
            "MANAGED_INFERENCE_FORCE_RECREATED",
            TARGET_TYPE,
            serviceRef,
            Map.of("serviceRef", serviceRef)
        );
        return verifyAfterMutation(serviceRef, "FORCE_RECREATE");
    }

    @Transactional
    PlatformManagedInferenceServiceSummary verifyAfterMutation(String serviceRef, String action) {
        PlatformManagedInferenceHealthSummary health = getHealth(serviceRef);
        PlatformManagedInferenceServiceEntity service = serviceService.requireService(serviceRef);
        Instant now = Instant.now();
        ObjectNode details = mutableDetails(service);
        details.put("lastVerifiedOperation", action);
        details.put("lastVerifiedAt", now.toString());
        details.put("lastVerifiedStatus", health.status());
        details.put(
            "lastVerifiedMessage",
            firstNonBlank(
                health.lastProbeMessage(),
                health.inferenceProbe().message(),
                health.healthProbe().message(),
                health.driftMessage()
            )
        );
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(now);
        serviceRepository.save(service);
        platformAuditService.record(
            "MANAGED_INFERENCE_OPERATION_VERIFIED",
            TARGET_TYPE,
            serviceRef,
            Map.of(
                "serviceRef", serviceRef,
                "action", action,
                "status", health.status(),
                "message", firstNonBlank(health.lastProbeMessage(), health.driftMessage(), "")
            )
        );
        return serviceService.getService(serviceRef);
    }

    private ProbeResult buildHealthProbe(PlatformManagedInferenceServiceEntity service, boolean railwayManaged) {
        if (!railwayManaged) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                "service_health",
                "Service health endpoint",
                "SKIPPED",
                blankToNull(service.getBaseUrl()),
                "GET",
                "This managed inference service does not use Railway lifecycle health checks.",
                Instant.now().toString()
            ));
        }
        if (!hasText(service.getBaseUrl()) || !hasText(service.getHealthPath())) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                "service_health",
                "Service health endpoint",
                "BLOCKED",
                blankToNull(service.getBaseUrl()),
                "GET",
                "Managed inference service does not have a usable base URL and health path.",
                Instant.now().toString()
            ));
        }
        String endpoint = joinUrl(service.getBaseUrl(), service.getHealthPath());
        return sendGetProbe("service_health", "Service health endpoint", endpoint);
    }

    private ProbeResult buildInferenceProbe(PlatformManagedInferenceServiceEntity service,
                                            List<PlatformManagedInferenceEndpointEntity> endpoints,
                                            boolean secretConfigured) {
        if (ManagedDeploymentProfileCatalog.INFERENCE_SERVICE_MODE_BUNDLED_RUNTIME.equals(normalize(service.getDeploymentMode()))
            || ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX.equals(normalize(service.getProviderType()))) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                "inference_probe",
                "Authenticated inference probe",
                "SKIPPED",
                blankToNull(service.getBaseUrl()),
                "POST",
                "Bundled ONNX does not expose an external authenticated inference endpoint.",
                Instant.now().toString()
            ));
        }

        PlatformManagedInferenceEndpointEntity endpoint = preferredEndpoint(endpoints);
        if (endpoint == null || !hasText(endpoint.getBaseUrl())) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                "inference_probe",
                "Authenticated inference probe",
                "BLOCKED",
                null,
                "POST",
                "Managed inference service does not currently expose an active endpoint base URL.",
                Instant.now().toString()
            ));
        }
        if (hasText(service.getSecretName()) && !secretConfigured) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                "inference_probe",
                "Authenticated inference probe",
                "BLOCKED",
                endpoint.getBaseUrl(),
                "POST",
                "Managed inference secret is missing from the platform secret store.",
                Instant.now().toString()
            ));
        }

        String provider = blankToFallback(endpoint.getProviderType(), service.getProviderType());
        String purpose = normalize(endpoint.getEndpointPurpose());
        if (!isOpenAiCompatibleProvider(provider)) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                "inference_probe",
                "Authenticated inference probe",
                "SKIPPED",
                endpoint.getBaseUrl(),
                "POST",
                "Provider '" + provider + "' does not yet expose a managed-service admin probe in this slice.",
                Instant.now().toString()
            ));
        }

        String secret = hasText(service.getSecretName()) ? platformSecretService.resolveSecret(service.getSecretName()) : null;
        if (hasText(service.getSecretName()) && !hasText(secret)) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                "inference_probe",
                "Authenticated inference probe",
                "BLOCKED",
                endpoint.getBaseUrl(),
                "POST",
                "Managed inference secret could not be resolved.",
                Instant.now().toString()
            ));
        }

        return "EMBEDDING".equals(purpose)
            ? sendJsonPostProbe(
                "inference_probe",
                "Authenticated inference probe",
                openAiEmbeddingsEndpoint(endpoint.getBaseUrl()),
                objectNode("""
                    {
                      "model": "%s",
                      "input": "%s"
                    }
                    """.formatted(
                    firstNonBlank(service.getModelId(), ManagedDeploymentProfileCatalog.defaultEmbeddingModel(provider)),
                    EMBEDDING_SMOKE_TEXT
                )),
                secret,
                this::isOpenAiEmbeddingResponse
            )
            : sendJsonPostProbe(
                "inference_probe",
                "Authenticated inference probe",
                openAiChatEndpoint(endpoint.getBaseUrl()),
                objectNode("""
                    {
                      "model": "%s",
                      "messages": [
                        {
                          "role": "user",
                          "content": "%s"
                        }
                      ],
                      "max_tokens": 4
                    }
                    """.formatted(
                    firstNonBlank(service.getModelId(), ManagedDeploymentProfileCatalog.defaultLlmModel(provider)),
                    CHAT_SMOKE_TEXT
                )),
                secret,
                this::isOpenAiChatResponse
            );
    }

    private DriftResult buildDrift(PlatformManagedInferenceServiceEntity service,
                                   List<PlatformManagedInferenceEndpointEntity> endpoints,
                                   boolean secretConfigured,
                                   boolean railwayManaged) {
        if (hasText(service.getSecretName()) && !secretConfigured) {
            return new DriftResult("SECRET_DRIFT", "Managed inference secret is missing from the platform secret store.");
        }
        if (railwayManaged) {
            if (!hasText(service.getRailwayProjectId())
                || !hasText(service.getRailwayEnvironmentId())
                || !hasText(service.getRailwayServiceId())) {
                return new DriftResult("RAILWAY_RESOURCE_DRIFT", "Railway linkage is missing for this managed service.");
            }
            try {
                RailwayGraphqlClient.RailwayProjectSnapshot project = railwayGraphqlClient.getProject(service.getRailwayProjectId());
                if (project.environmentNamed(service.getRailwayEnvironmentId()) == null
                    && project.environmentNamed(blankToNull(service.getEnvironmentScope())) == null) {
                    return new DriftResult("RAILWAY_RESOURCE_DRIFT", "Stored Railway environment linkage no longer exists.");
                }
                if (project.serviceNamed(service.getRailwayServiceId()) == null
                    && project.serviceNamed(normalizeRailwayServiceName(service)) == null) {
                    return new DriftResult("RAILWAY_RESOURCE_DRIFT", "Stored Railway service linkage no longer exists.");
                }
                if (service.getDesiredReplicas() != null
                    && service.getActualReplicas() != null
                    && !service.getDesiredReplicas().equals(service.getActualReplicas())) {
                    return new DriftResult("SCALE_DRIFT", "Desired replicas do not match the currently recorded actual replicas.");
                }
                if (hasText(service.getBaseUrl())) {
                    String expectedDomain = URI.create(service.getBaseUrl()).getHost();
                    boolean domainPresent = railwayGraphqlClient
                        .listServiceDomains(service.getRailwayProjectId(), service.getRailwayEnvironmentId(), service.getRailwayServiceId())
                        .stream()
                        .anyMatch(domain -> expectedDomain.equalsIgnoreCase(domain.domain()));
                    if (!domainPresent) {
                        return new DriftResult("ENDPOINT_DRIFT", "Stored public base URL no longer matches Railway domains.");
                    }
                }
            } catch (RuntimeException ex) {
                return new DriftResult("RAILWAY_RESOURCE_DRIFT", "Railway lookup failed: " + blankToFallback(ex.getMessage(), ex.getClass().getSimpleName()));
            }
        }
        for (PlatformManagedInferenceEndpointEntity endpoint : endpoints) {
            if ("ACTIVE".equalsIgnoreCase(endpoint.getStatus()) && !hasText(endpoint.getBaseUrl())) {
                return new DriftResult("ENDPOINT_DRIFT", "Active endpoint '" + endpoint.getProfileRef() + "' is missing a resolved base URL.");
            }
        }
        return new DriftResult("NO_DRIFT", "Platform and service linkage are aligned.");
    }

    private String summarizeOverallStatus(String healthStatus, String inferenceStatus, String driftStatus) {
        if ("FAILED".equals(healthStatus) || "FAILED".equals(inferenceStatus)) {
            return "FAILED";
        }
        if ("BLOCKED".equals(healthStatus) || "BLOCKED".equals(inferenceStatus)) {
            return "BLOCKED";
        }
        if (!"NO_DRIFT".equals(driftStatus)) {
            return "DEGRADED";
        }
        if ("READY".equals(healthStatus) || "READY".equals(inferenceStatus)) {
            return "READY";
        }
        return "SKIPPED";
    }

    private ProbeResult sendGetProbe(String key, String label, String endpoint) {
        Instant checkedAt = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                    key, label, "READY", endpoint, "GET", label + " responded with HTTP " + response.statusCode() + ".", checkedAt.toString()
                ));
            }
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                key, label, "FAILED", endpoint, "GET", label + " responded with HTTP " + response.statusCode() + ".", checkedAt.toString()
            ));
        } catch (Exception ex) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                key, label, "FAILED", endpoint, "GET", label + " probe failed: " + ex.getMessage(), checkedAt.toString()
            ));
        }
    }

    private ProbeResult sendJsonPostProbe(String key,
                                          String label,
                                          String endpoint,
                                          JsonNode requestBody,
                                          String bearerToken,
                                          ResponseValidator validator) {
        Instant checkedAt = Instant.now();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
            if (hasText(bearerToken)) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                    key, label, "FAILED", endpoint, "POST", label + " responded with HTTP " + response.statusCode() + ".", checkedAt.toString()
                ));
            }
            JsonNode responseBody = objectMapper.readTree(blankToEmpty(response.body()));
            if (!validator.isValid(responseBody)) {
                return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                    key, label, "FAILED", endpoint, "POST", label + " returned an invalid response payload.", checkedAt.toString()
                ));
            }
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                key, label, "READY", endpoint, "POST", label + " accepted an authenticated probe.", checkedAt.toString()
            ));
        } catch (Exception ex) {
            return new ProbeResult(new PlatformManagedInferenceProbeSummary(
                key, label, "FAILED", endpoint, "POST", label + " probe failed: " + ex.getMessage(), checkedAt.toString()
            ));
        }
    }

    private PlatformManagedInferenceEndpointEntity preferredEndpoint(List<PlatformManagedInferenceEndpointEntity> endpoints) {
        return endpoints.stream()
            .filter(endpoint -> "ACTIVE".equalsIgnoreCase(endpoint.getStatus()))
            .sorted((left, right) -> Integer.compare(priority(left.getEndpointPurpose()), priority(right.getEndpointPurpose())))
            .findFirst()
            .orElse(null);
    }

    private int priority(String purpose) {
        return switch (normalize(purpose)) {
            case "EMBEDDING" -> 0;
            case "ORCHESTRATION" -> 1;
            case "GENERATION" -> 2;
            default -> 10;
        };
    }

    private Set<String> providerConfigUsages(String providerConfigJson,
                                             String serviceRef,
                                             Set<String> endpointRefs,
                                             String scopeLabel) {
        Set<String> usages = new LinkedHashSet<>();
        if (!hasText(providerConfigJson)) {
            return usages;
        }
        try {
            JsonNode providerConfig = objectMapper.readTree(providerConfigJson);
            addUsageIfMatches(usages, scopeLabel, "orchestration", matchesValue(providerConfig, "orchestrationManagedServiceRef", serviceRef));
            addUsageIfMatches(usages, scopeLabel, "generation", matchesValue(providerConfig, "generationManagedServiceRef", serviceRef));
            addUsageIfMatches(usages, scopeLabel, "embedding", matchesValue(providerConfig, "embeddingManagedServiceRef", serviceRef));
            addUsageIfMatches(usages, scopeLabel, "marketplace-managed", matchesValue(providerConfig.path("marketplaceInference"), "managedServiceRefs", serviceRef));
            for (String endpointRef : endpointRefs) {
                addUsageIfMatches(usages, scopeLabel, "orchestration", matchesValue(providerConfig, "orchestrationEndpointProfile", endpointRef));
                addUsageIfMatches(usages, scopeLabel, "generation", matchesValue(providerConfig, "generationEndpointProfile", endpointRef));
                addUsageIfMatches(usages, scopeLabel, "embedding", matchesValue(providerConfig, "embeddingEndpointProfile", endpointRef));
                addUsageIfMatches(usages, scopeLabel, "marketplace-endpoint", matchesValue(providerConfig.path("marketplaceInference"), "endpointProfileRefs", endpointRef));
            }
        } catch (Exception ignored) {
            return usages;
        }
        return usages;
    }

    private void addUsageIfMatches(Set<String> usages, String scopeLabel, String usage, boolean matches) {
        if (matches) {
            usages.add(scopeLabel + ":" + usage);
        }
    }

    private boolean matchesValue(JsonNode node, String fieldName, String expected) {
        if (node == null || node.isMissingNode() || node.isNull() || !hasText(expected)) {
            return false;
        }
        JsonNode value = node.path(fieldName);
        if (value.isTextual()) {
            return expected.equalsIgnoreCase(value.asText(""));
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (item.isTextual() && expected.equalsIgnoreCase(item.asText(""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private DeploymentDraftEntity resolveDraft(DeploymentEntity deployment) {
        return deployment.getActiveDraftId() == null
            ? null
            : deploymentDraftRepository.findById(deployment.getActiveDraftId()).orElse(null);
    }

    private DeploymentVersionEntity resolveVersion(DeploymentEntity deployment) {
        return deployment.getActiveVersionId() == null
            ? null
            : deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
    }

    private boolean isRuntimeActive(DeploymentEntity deployment) {
        String status = normalize(deployment.getStatus());
        return "ACTIVE".equals(status)
            || "APPLIED_VERIFIED".equals(status)
            || "PROVISIONING".equals(status)
            || "VERSION_PUBLISHED".equals(status);
    }

    private boolean requiresRailwayLifecycle(PlatformManagedInferenceServiceEntity service) {
        String kind = normalize(service.getServiceKind());
        return "SHARED_EMBEDDING_SERVICE".equals(kind) || "SHARED_OLLAMA_SERVICE".equals(kind);
    }

    private boolean isOpenAiCompatibleProvider(String provider) {
        return ManagedDeploymentProfileCatalog.LLM_PROVIDER_OPENAI.equalsIgnoreCase(blankToEmpty(provider))
            || ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI.equalsIgnoreCase(blankToEmpty(provider));
    }

    private String normalizeRailwayServiceName(PlatformManagedInferenceServiceEntity service) {
        return switch (normalize(service.getServiceKind())) {
            case "SHARED_EMBEDDING_SERVICE" -> "shared-embeddings-" + normalizeToken(service.getServiceRef());
            case "SHARED_OLLAMA_SERVICE" -> "shared-ollama-" + normalizeToken(service.getServiceRef());
            default -> normalizeToken(service.getServiceRef());
        };
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

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() && hasText(value.asText()) ? value.asText().trim() : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeToken(String value) {
        return hasText(value)
            ? value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "")
            : "default";
    }

    private String openAiEmbeddingsEndpoint(String baseUrl) {
        String trimmed = trimToNull(baseUrl);
        if (trimmed == null) {
            return null;
        }
        return trimmed.endsWith("/embeddings") ? trimmed : trimmed.endsWith("/v1") ? trimmed + "/embeddings" : trimmed + "/v1/embeddings";
    }

    private String openAiChatEndpoint(String baseUrl) {
        String trimmed = trimToNull(baseUrl);
        if (trimmed == null) {
            return null;
        }
        return trimmed.endsWith("/chat/completions") ? trimmed : trimmed.endsWith("/v1") ? trimmed + "/chat/completions" : trimmed + "/v1/chat/completions";
    }

    private String joinUrl(String baseUrl, String path) {
        String trimmedBase = trimToNull(baseUrl);
        String trimmedPath = trimToNull(path);
        if (trimmedBase == null || trimmedPath == null) {
            return trimmedBase;
        }
        if (trimmedBase.endsWith("/") && trimmedPath.startsWith("/")) {
            return trimmedBase + trimmedPath.substring(1);
        }
        if (!trimmedBase.endsWith("/") && !trimmedPath.startsWith("/")) {
            return trimmedBase + "/" + trimmedPath;
        }
        return trimmedBase + trimmedPath;
    }

    private boolean isOpenAiEmbeddingResponse(JsonNode responseBody) {
        JsonNode data = responseBody.path("data");
        return data.isArray()
            && !data.isEmpty()
            && data.get(0).path("embedding").isArray()
            && !data.get(0).path("embedding").isEmpty();
    }

    private boolean isOpenAiChatResponse(JsonNode responseBody) {
        JsonNode choices = responseBody.path("choices");
        return choices.isArray() && !choices.isEmpty();
    }

    private JsonNode objectNode(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build managed inference probe payload.", ex);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String blankToFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String trimToNull(String value) {
        return blankToNull(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DriftResult(String status, String message) {
    }

    private record ProbeResult(PlatformManagedInferenceProbeSummary summary) {
    }

    @FunctionalInterface
    private interface ResponseValidator {
        boolean isValid(JsonNode responseBody);
    }
}
