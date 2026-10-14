package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyActionResponse;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApplicationSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AIWorkspaceDomainRouteService {

    private static final String MANAGED_DOMAINS_FIELD = "aiWorkspaceManagedDomains";

    private final AIWorkspaceDomainRepository domainRepository;
    private final PlatformManagedProductServiceRepository serviceRepository;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final CoolifyTargetProfileResolver targetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final PlatformAIWorkspaceProperties properties;
    private final PlatformProductProvisioningProperties provisioningProperties;
    private final PlatformAuditService auditService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AIWorkspaceDomainRouteService(AIWorkspaceDomainRepository domainRepository,
                                         PlatformManagedProductServiceRepository serviceRepository,
                                         DeploymentTargetProfileRepository targetProfileRepository,
                                         CoolifyTargetProfileResolver targetProfileResolver,
                                         CoolifyApiClient coolifyApiClient,
                                         PlatformAIWorkspaceProperties properties,
                                         PlatformProductProvisioningProperties provisioningProperties,
                                         PlatformAuditService auditService,
                                         ObjectMapper objectMapper) {
        this.domainRepository = domainRepository;
        this.serviceRepository = serviceRepository;
        this.targetProfileRepository = targetProfileRepository;
        this.targetProfileResolver = targetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.properties = properties;
        this.provisioningProperties = provisioningProperties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean reconcileIfAvailable() {
        PlatformManagedProductServiceEntity service = resolveService(false);
        if (service == null) return false;
        try {
            reconcile(service);
            return true;
        } catch (RuntimeException ex) {
            recordFailure(service, ex);
            return false;
        }
    }

    @Transactional
    public void reconcileRequired() {
        reconcile(resolveService(true));
    }

    private void reconcile(PlatformManagedProductServiceEntity service) {
        ObjectNode details = details(service);
        String applicationUuid = text(details, "coolifyApplicationUuid");
        String targetProfileId = text(details, "targetProfileId");
        if (applicationUuid == null || targetProfileId == null) {
            throw conflict("The shared standalone AI Workspace service is not linked to Coolify.");
        }
        var profile = targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> conflict("Standalone AI Workspace target profile is unavailable: " + targetProfileId));
        var connection = targetProfileResolver.requireConnection(profile);
        var application = coolifyApiClient.getApplication(connection, applicationUuid)
            .orElseThrow(() -> conflict("Standalone AI Workspace Coolify application is unavailable."));

        LinkedHashSet<String> previousManaged = new LinkedHashSet<>();
        JsonNode previous = details.path(MANAGED_DOMAINS_FIELD);
        if (previous.isArray()) previous.forEach(value -> {
            String key = domainKey(value.asText(null));
            if (key != null) previousManaged.add(key);
        });

        LinkedHashMap<String, String> desiredManaged = new LinkedHashMap<>();
        domainRepository.findAllByOrderByHostname().stream()
            .filter(domain -> domain.getStatus() != AIWorkspaceDomainStatus.DISABLED)
            .filter(domain -> domain.getDomainType() == AIWorkspaceDomainType.PLATFORM_DEFAULT
                || domain.getStatus() == AIWorkspaceDomainStatus.ROUTING_PENDING
                || domain.getStatus() == AIWorkspaceDomainStatus.VERIFIED)
            .forEach(domain -> desiredManaged.put(domain.getHostname(), "https://" + domain.getHostname()));

        LinkedHashMap<String, String> reconciled = new LinkedHashMap<>();
        for (String candidate : splitDomains(application.fqdn())) {
            String key = domainKey(candidate);
            if (key != null && !previousManaged.contains(key)) reconciled.putIfAbsent(key, candidate);
        }
        desiredManaged.forEach(reconciled::put);
        if (reconciled.isEmpty()) {
            throw conflict("Standalone AI Workspace routing cannot remove every frontend domain.");
        }

        String domains = String.join(",", reconciled.values());
        boolean changed = !sameDomains(application.fqdn(), domains);
        if (changed) {
            coolifyApiClient.updateApplicationDomains(connection, applicationUuid, domains);
            awaitRouteDeployment(
                connection,
                applicationUuid,
                coolifyApiClient.start(connection, applicationUuid, true, true)
            );
        }
        service.setBaseUrl(canonicalBaseUrl(service.getBaseUrl(), reconciled));
        details.put("coolifyFqdn", domains);
        details.put("lastWorkspaceDomainReconciledAt", Instant.now().toString());
        details.put("lastWorkspaceDomainReconcileStatus", "SUCCESS");
        details.remove("lastWorkspaceDomainReconcileError");
        ArrayNode managed = details.putArray(MANAGED_DOMAINS_FIELD);
        desiredManaged.keySet().forEach(managed::add);
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        auditService.record(
            "AI_WORKSPACE_FRONTEND_DOMAINS_RECONCILED",
            "MANAGED_PRODUCT_SERVICE",
            service.getServiceRef(),
            Map.of(
                "serviceRef", service.getServiceRef(),
                "managedDomainCount", desiredManaged.size(),
                "coolifyUpdated", changed
            )
        );
    }

    private void awaitRouteDeployment(CoolifyConnection connection,
                                      String applicationUuid,
                                      CoolifyActionResponse response) {
        String deploymentUuid = response == null ? null : text(response.deploymentUuid());
        Duration timeout = provisioningProperties.requestTimeout();
        Duration pollInterval = provisioningProperties.pollInterval();
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            CoolifyDeploymentSummary deployment = deploymentUuid == null
                ? null
                : coolifyApiClient.getDeployment(connection, deploymentUuid).orElse(null);
            CoolifyApplicationSummary application = coolifyApiClient.getApplication(connection, applicationUuid).orElse(null);
            if (deploymentFailed(deployment)) {
                throw conflict("Standalone AI Workspace route deployment failed: " + deploymentUuid);
            }
            boolean deploymentReady = deploymentUuid == null || deploymentFinished(deployment);
            if (deploymentReady && applicationReady(application)) return;
            try {
                Thread.sleep(Math.max(pollInterval.toMillis(), 250L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while applying standalone AI Workspace routes.", ex);
            }
        }
        throw conflict("Timed out applying standalone AI Workspace routes: " + applicationUuid);
    }

    private boolean deploymentFinished(CoolifyDeploymentSummary deployment) {
        if (deployment == null) return false;
        String status = normalized(deployment.status());
        return status.equals("FINISHED") || status.equals("SUCCESS") || status.equals("SUCCEEDED")
            || status.equals("COMPLETED") || text(deployment.finishedAt()) != null && !deploymentFailed(deployment);
    }

    private boolean deploymentFailed(CoolifyDeploymentSummary deployment) {
        if (deployment == null) return false;
        String status = normalized(deployment.status());
        return status.contains("FAILED") || status.contains("ERROR")
            || status.contains("CANCELLED") || status.contains("CANCELED");
    }

    private boolean applicationReady(CoolifyApplicationSummary application) {
        if (application == null) return false;
        String status = normalized(application.status());
        return status.startsWith("RUNNING") && !status.contains("UNHEALTHY");
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void recordFailure(PlatformManagedProductServiceEntity service, RuntimeException ex) {
        ObjectNode details = details(service);
        String message = routeError(ex);
        details.put("lastWorkspaceDomainReconcileStatus", "FAILED");
        details.put("lastWorkspaceDomainReconcileError", message);
        details.put("lastWorkspaceDomainReconcileAttemptAt", Instant.now().toString());
        service.setDetailsJson(details.toPrettyString());
        service.setUpdatedAt(Instant.now());
        serviceRepository.save(service);
        auditService.record(
            "AI_WORKSPACE_FRONTEND_DOMAINS_RECONCILE_FAILED",
            "MANAGED_PRODUCT_SERVICE",
            service.getServiceRef(),
            Map.of("serviceRef", service.getServiceRef(), "error", message)
        );
    }

    private PlatformManagedProductServiceEntity resolveService(boolean required) {
        List<PlatformManagedProductServiceEntity> candidates = serviceRepository.findAllByOrderByDisplayNameAsc().stream()
            .filter(service -> PlatformManagedProductServiceService.STANDALONE_AI_WORKSPACE_SERVICE
                .equalsIgnoreCase(service.getServiceKind()))
            .filter(service -> !"DECOMMISSIONED".equalsIgnoreCase(service.getStatus()))
            .filter(service -> text(details(service), "coolifyApplicationUuid") != null)
            .toList();
        if (candidates.size() > 1 && properties.standaloneShellBaseUrl() != null) {
            String shellHost = domainKey(properties.standaloneShellBaseUrl());
            if (shellHost != null) {
                candidates = candidates.stream()
                    .filter(service -> splitDomains(service.getBaseUrl()).stream()
                        .map(this::domainKey)
                        .anyMatch(shellHost::equals))
                    .toList();
            }
        }
        if (candidates.size() == 1) return candidates.get(0);
        if (!required && candidates.isEmpty()) return null;
        if (candidates.isEmpty()) {
            throw conflict("The shared standalone AI Workspace service must be reconciled before app domains can be activated.");
        }
        throw conflict("Multiple standalone AI Workspace services match this Platform environment.");
    }

    private ObjectNode details(PlatformManagedProductServiceEntity service) {
        try {
            JsonNode value = service.getDetailsJson() == null ? null : objectMapper.readTree(service.getDetailsJson());
            return value != null && value.isObject() ? (ObjectNode) value.deepCopy() : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> splitDomains(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<String> values = new ArrayList<>();
        for (String candidate : value.split(",")) {
            if (candidate != null && !candidate.isBlank()) values.add(candidate.trim());
        }
        return values;
    }

    private boolean sameDomains(String left, String right) {
        LinkedHashSet<String> leftKeys = new LinkedHashSet<>();
        splitDomains(left).stream().map(this::domainKey).filter(java.util.Objects::nonNull).forEach(leftKeys::add);
        LinkedHashSet<String> rightKeys = new LinkedHashSet<>();
        splitDomains(right).stream().map(this::domainKey).filter(java.util.Objects::nonNull).forEach(rightKeys::add);
        return leftKeys.equals(rightKeys);
    }

    private String canonicalBaseUrl(String currentBaseUrl, LinkedHashMap<String, String> reconciled) {
        String configured = properties.standaloneShellBaseUrl();
        String configuredKey = domainKey(configured);
        if (configuredKey != null && reconciled.containsKey(configuredKey)) {
            return reconciled.get(configuredKey);
        }
        for (String candidate : splitDomains(currentBaseUrl)) {
            String key = domainKey(candidate);
            if (key != null && reconciled.containsKey(key)) return reconciled.get(key);
        }
        return reconciled.values().iterator().next();
    }

    private String domainKey(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String candidate = value.contains("://") ? value.trim() : "https://" + value.trim();
            String host = URI.create(candidate).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String routeError(RuntimeException ex) {
        if (ex instanceof ResponseStatusException response && response.getReason() != null) {
            return response.getReason();
        }
        return ex.getMessage() == null || ex.getMessage().isBlank()
            ? "The shared frontend route could not be reconciled."
            : ex.getMessage();
    }
}
