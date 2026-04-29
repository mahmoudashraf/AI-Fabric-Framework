package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.ExecuteRailwayWorkspaceCleanupRequest;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceCleanupExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceCleanupOwnerSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceCleanupSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceOrphanServiceSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayWorkspaceProjectCleanupSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.Instant;

@Service
public class RailwayWorkspaceCleanupService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final VectorizationRunnerRegistrationRepository vectorizationRunnerRegistrationRepository;
    private final PlatformManagedProductServiceRepository platformManagedProductServiceRepository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public RailwayWorkspaceCleanupService(PlatformProvisioningProperties provisioningProperties,
                                          RailwayGraphqlClient railwayGraphqlClient,
                                          DeploymentRepository deploymentRepository,
                                          DeploymentReleaseRepository deploymentReleaseRepository,
                                          VectorizationRunnerRegistrationRepository vectorizationRunnerRegistrationRepository,
                                          PlatformManagedProductServiceRepository platformManagedProductServiceRepository,
                                          PlatformAuditService platformAuditService,
                                          ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.deploymentRepository = deploymentRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.vectorizationRunnerRegistrationRepository = vectorizationRunnerRegistrationRepository;
        this.platformManagedProductServiceRepository = platformManagedProductServiceRepository;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public RailwayWorkspaceCleanupSummary getSummary() {
        if (!"RAILWAY_API".equalsIgnoreCase(provisioningProperties.mode())) {
            return unavailable("Railway workspace cleanup is only available when platform provisioning mode is RAILWAY_API.");
        }
        if (!hasText(provisioningProperties.workspaceId())) {
            return unavailable("RAILWAY_WORKSPACE_ID is missing, so workspace cleanup cannot inspect Railway projects.");
        }

        List<RailwayGraphqlClient.RailwayWorkspaceSummary> accessibleWorkspaces = railwayGraphqlClient.listAccessibleWorkspaces();
        RailwayGraphqlClient.RailwayWorkspaceSummary workspace = accessibleWorkspaces.stream()
            .filter(item -> provisioningProperties.workspaceId().equals(item.id()))
            .findFirst()
            .orElse(null);
        if (workspace == null) {
            return unavailable("The configured Railway workspace is not accessible to the current API token.");
        }

        OwnershipIndex ownershipIndex = buildOwnershipIndex();
        List<RailwayWorkspaceProjectCleanupSummary> projects = new ArrayList<>();
        int orphanProjectCount = 0;
        int orphanServiceCount = 0;

        for (RailwayGraphqlClient.RailwayProjectSnapshot project : railwayGraphqlClient.listProjectsInWorkspace(workspace.id())) {
            RailwayWorkspaceProjectCleanupSummary summary = summarizeProject(project, ownershipIndex);
            if (summary != null) {
                projects.add(summary);
                if ("ORPHAN".equals(summary.ownershipState())) {
                    orphanProjectCount += 1;
                }
                orphanServiceCount += summary.orphanServices().size();
            }
        }

        String status = projects.isEmpty() ? "READY" : orphanProjectCount > 0 || orphanServiceCount > 0 ? "ATTENTION" : "READY";
        String summaryMessage = projects.isEmpty()
            ? "No Railway workspace cleanup candidates were found."
            : orphanProjectCount > 0 || orphanServiceCount > 0
                ? orphanProjectCount + " orphan project(s) and " + orphanServiceCount
                    + " orphan service(s) are available for selective cleanup."
                : "Railway workspace inventory is fully owned by active platform deployment records.";

        return new RailwayWorkspaceCleanupSummary(
            true,
            status,
            workspace.id(),
            workspace.name(),
            projects.size(),
            orphanProjectCount,
            orphanServiceCount,
            List.copyOf(projects),
            summaryMessage
        );
    }

    public RailwayWorkspaceCleanupExecutionSummary cleanupOrphans(ExecuteRailwayWorkspaceCleanupRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirm=true is required before orphan Railway cleanup can run.");
        }
        String reason = trimToNull(request.reason());
        if (!hasText(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A cleanup reason is required before orphan Railway cleanup can run.");
        }

        RailwayWorkspaceCleanupSummary summary = getSummary();
        if (!summary.available()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, summary.summaryMessage());
        }

        Set<String> selectedProjectIds = normalizedIds(request.projectIds());
        Set<String> selectedServiceIds = normalizedIds(request.serviceIds());
        if (selectedProjectIds.isEmpty() && selectedServiceIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one orphan Railway project or service to delete.");
        }

        Map<String, RailwayWorkspaceProjectCleanupSummary> projectsById = new LinkedHashMap<>();
        Map<String, RailwayWorkspaceOrphanServiceSummary> servicesById = new LinkedHashMap<>();
        for (RailwayWorkspaceProjectCleanupSummary project : summary.projects()) {
            projectsById.put(project.projectId(), project);
            for (RailwayWorkspaceOrphanServiceSummary service : project.orphanServices()) {
                servicesById.put(service.serviceId(), service);
            }
        }

        for (String projectId : selectedProjectIds) {
            RailwayWorkspaceProjectCleanupSummary project = projectsById.get(projectId);
            if (project == null || !project.deletable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected Railway project is not deletable through workspace cleanup: " + projectId);
            }
        }
        for (String serviceId : selectedServiceIds) {
            RailwayWorkspaceOrphanServiceSummary service = servicesById.get(serviceId);
            if (service == null || !service.deletable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected Railway service is not deletable through workspace cleanup: " + serviceId);
            }
        }

        List<String> deletedProjectIds = new ArrayList<>();
        List<String> deletedServiceIds = new ArrayList<>();
        List<String> skippedIds = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (String projectId : selectedProjectIds) {
            try {
                railwayGraphqlClient.deleteProject(projectId);
                deletedProjectIds.add(projectId);
            } catch (RailwayProvisioningException ex) {
                failures.add(projectId + ": " + ex.getMessage());
            }
        }

        for (String serviceId : selectedServiceIds) {
            RailwayWorkspaceOrphanServiceSummary service = servicesById.get(serviceId);
            if (service == null) {
                continue;
            }
            if (selectedProjectIds.contains(service.projectId())) {
                skippedIds.add(serviceId);
                continue;
            }
            try {
                railwayGraphqlClient.deleteService(serviceId);
                deletedServiceIds.add(serviceId);
            } catch (RailwayProvisioningException ex) {
                failures.add(serviceId + ": " + ex.getMessage());
            }
        }

        platformAuditService.record(
            "RAILWAY_WORKSPACE_ORPHAN_CLEANUP_EXECUTED",
            "RAILWAY_WORKSPACE",
            summary.workspaceId(),
            Map.of(
                "reason", reason,
                "deletedProjectCount", deletedProjectIds.size(),
                "deletedServiceCount", deletedServiceIds.size(),
                "failureCount", failures.size()
            )
        );

        String status = failures.isEmpty() ? "COMPLETED" : deletedProjectIds.isEmpty() && deletedServiceIds.isEmpty() ? "FAILED" : "PARTIAL";
        String message = failures.isEmpty()
            ? "Workspace cleanup deleted " + deletedProjectIds.size() + " Railway project(s) and "
                + deletedServiceIds.size() + " Railway service(s)."
            : deletedProjectIds.isEmpty() && deletedServiceIds.isEmpty()
                ? "Workspace cleanup failed: " + String.join(" | ", failures)
                : "Workspace cleanup removed " + deletedProjectIds.size() + " project(s) and "
                    + deletedServiceIds.size() + " service(s), but some items failed: " + String.join(" | ", failures);
        return new RailwayWorkspaceCleanupExecutionSummary(
            status,
            message,
            deletedProjectIds.size(),
            deletedServiceIds.size(),
            List.copyOf(deletedProjectIds),
            List.copyOf(deletedServiceIds),
            List.copyOf(skippedIds)
        );
    }

    private RailwayWorkspaceProjectCleanupSummary summarizeProject(RailwayGraphqlClient.RailwayProjectSnapshot project,
                                                                  OwnershipIndex ownershipIndex) {
        List<RailwayWorkspaceCleanupOwnerSummary> projectOwners = ownershipIndex.projectOwnersById().getOrDefault(project.id(), List.of());
        boolean anyOwnedServices = false;
        boolean anyPlatformCandidate = looksLikePlatformManagedProject(project.name());
        List<RailwayWorkspaceOrphanServiceSummary> orphanServices = new ArrayList<>();

        for (RailwayGraphqlClient.RailwayServiceSummary service : project.services()) {
            List<RailwayWorkspaceCleanupOwnerSummary> serviceOwners = ownershipIndex.serviceOwnersById().getOrDefault(service.id(), List.of());
            if (serviceOwners.isEmpty()) {
                serviceOwners = ownershipIndex.serviceOwnersByName().getOrDefault(service.name(), List.of());
            }
            anyOwnedServices |= !serviceOwners.isEmpty();
            RailwayGraphqlClient.RailwayServiceSourceSummary source = railwayGraphqlClient.getServiceSource(service.id());
            boolean platformCandidate = looksLikePlatformManagedService(service.name(), source.name(), firstRepo(source));
            anyPlatformCandidate |= platformCandidate;
            if (!serviceOwners.isEmpty()) {
                continue;
            }
            orphanServices.add(new RailwayWorkspaceOrphanServiceSummary(
                service.id(),
                service.name(),
                project.id(),
                project.name(),
                "ORPHAN",
                platformCandidate,
                platformCandidate,
                firstRepo(source),
                firstBranch(source),
                List.of(),
                platformCandidate
                    ? "This Railway service is not referenced by any current platform deployment and matches the platform-managed service profile."
                    : "This Railway service is not referenced by any current platform deployment, but it does not match the safe platform-managed service profile."
            ));
        }

        boolean orphanProject = projectOwners.isEmpty() && !anyOwnedServices;
        boolean deletableProject = orphanProject
            && anyPlatformCandidate
            && (project.services().isEmpty() || orphanServices.size() == project.services().size());
        if (!orphanProject && orphanServices.isEmpty()) {
            return null;
        }

        String ownershipState = orphanProject ? "ORPHAN" : "PARTIAL_ORPHAN";
        String summaryMessage = orphanProject
            ? deletableProject
                ? "This Railway project is fully orphaned and all discovered services match the platform-managed profile."
                : "This Railway project is fully orphaned, but not every service matches the safe platform-managed profile for deletion."
            : orphanServices.size() + " Railway service(s) in this project are not referenced by any current platform deployment.";

        return new RailwayWorkspaceProjectCleanupSummary(
            project.id(),
            project.name(),
            ownershipState,
            anyPlatformCandidate,
            deletableProject,
            project.services().size(),
            List.copyOf(projectOwners),
            List.copyOf(orphanServices),
            summaryMessage
        );
    }

    private OwnershipIndex buildOwnershipIndex() {
        Map<String, List<RailwayWorkspaceCleanupOwnerSummary>> projectOwnersById = new LinkedHashMap<>();
        Map<String, List<RailwayWorkspaceCleanupOwnerSummary>> serviceOwnersById = new LinkedHashMap<>();
        Map<String, List<RailwayWorkspaceCleanupOwnerSummary>> serviceOwnersByName = new LinkedHashMap<>();
        for (DeploymentEntity deployment : deploymentRepository.findAllByOrderByCreatedAtDesc()) {
            DeploymentReleaseEntity latestRelease = deploymentReleaseRepository
                .findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId())
                .orElse(null);
            if (latestRelease == null) {
                continue;
            }
            JsonNode details = readJson(latestRelease.getProvisioningDetailsJson());
            String projectId = firstNonBlank(text(details, "railway", "projectId"), text(details, "projectId"));
            String runtimeServiceId = text(details, "railway", "services", "runtime", "serviceId");
            String connectorServiceId = text(details, "railway", "services", "restConnector", "serviceId");
            if (!hasText(projectId) && !hasText(runtimeServiceId) && !hasText(connectorServiceId)) {
                continue;
            }
            RailwayWorkspaceCleanupOwnerSummary owner = new RailwayWorkspaceCleanupOwnerSummary(
                deployment.getId(),
                deployment.getName(),
                deployment.getEnvironmentName(),
                deployment.getArchivedAt() != null
            );
            appendOwner(projectOwnersById, projectId, owner);
            appendOwner(serviceOwnersById, runtimeServiceId, owner);
            appendOwner(serviceOwnersById, connectorServiceId, owner);
        }
        Instant now = Instant.now();
        for (VectorizationRunnerRegistrationEntity registration : vectorizationRunnerRegistrationRepository.findAll()) {
            if (!hasText(registration.getDeploymentId())
                || !hasText(registration.getRunnerInstanceId())
                || registration.getTokenExpiresAt() != null && registration.getTokenExpiresAt().isBefore(now)) {
                continue;
            }
            DeploymentEntity deployment = deploymentRepository.findById(registration.getDeploymentId()).orElse(null);
            if (deployment == null) {
                continue;
            }
            RailwayWorkspaceCleanupOwnerSummary owner = new RailwayWorkspaceCleanupOwnerSummary(
                deployment.getId(),
                deployment.getName(),
                deployment.getEnvironmentName(),
                deployment.getArchivedAt() != null
            );
            appendOwner(serviceOwnersByName, registration.getRunnerInstanceId(), owner);
        }
        for (PlatformManagedProductServiceEntity productService : platformManagedProductServiceRepository.findAll()) {
            if (!hasText(productService.getRailwayProjectId()) && !hasText(productService.getRailwayServiceId())) {
                continue;
            }
            String ownerId = firstNonBlank(productService.getDeploymentId(), "product-service:" + productService.getServiceRef());
            String ownerName = firstNonBlank(productService.getDisplayName(), productService.getServiceRef());
            RailwayWorkspaceCleanupOwnerSummary owner = new RailwayWorkspaceCleanupOwnerSummary(
                ownerId,
                ownerName,
                productService.getEnvironmentScope(),
                false
            );
            appendOwner(projectOwnersById, productService.getRailwayProjectId(), owner);
            appendOwner(serviceOwnersById, productService.getRailwayServiceId(), owner);
        }
        return new OwnershipIndex(projectOwnersById, serviceOwnersById, serviceOwnersByName);
    }

    private void appendOwner(Map<String, List<RailwayWorkspaceCleanupOwnerSummary>> index,
                             String resourceId,
                             RailwayWorkspaceCleanupOwnerSummary owner) {
        if (!hasText(resourceId)) {
            return;
        }
        index.computeIfAbsent(resourceId, ignored -> new ArrayList<>()).add(owner);
    }

    private Set<String> normalizedIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : ids) {
            if (hasText(id)) {
                normalized.add(id.trim());
            }
        }
        return Set.copyOf(normalized);
    }

    private boolean looksLikePlatformManagedProject(String projectName) {
        if (!hasText(projectName)) {
            return false;
        }
        String normalized = projectName.trim().toLowerCase(Locale.ROOT);
        return normalized.matches(".*-[a-z0-9]{8}$");
    }

    private boolean looksLikePlatformManagedService(String serviceName,
                                                    String fallbackServiceName,
                                                    String repository) {
        String candidate = firstNonBlank(serviceName, fallbackServiceName);
        if (hasText(candidate)) {
            String normalized = candidate.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith(provisioningProperties.runtimeServiceNamePrefix().toLowerCase(Locale.ROOT) + "-")
                || normalized.startsWith(provisioningProperties.connectorServiceNamePrefix().toLowerCase(Locale.ROOT) + "-")) {
                return true;
            }
        }
        return hasText(repository) && repository.equalsIgnoreCase(provisioningProperties.repository());
    }

    private String firstRepo(RailwayGraphqlClient.RailwayServiceSourceSummary source) {
        if (source == null || source.repoTriggers() == null) {
            return null;
        }
        return source.repoTriggers().stream()
            .map(RailwayGraphqlClient.RailwayDeploymentTriggerSummary::repository)
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private String firstBranch(RailwayGraphqlClient.RailwayServiceSourceSummary source) {
        if (source == null || source.repoTriggers() == null) {
            return null;
        }
        return source.repoTriggers().stream()
            .map(RailwayGraphqlClient.RailwayDeploymentTriggerSummary::branch)
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private RailwayWorkspaceCleanupSummary unavailable(String message) {
        return new RailwayWorkspaceCleanupSummary(
            false,
            "BLOCKED",
            trimToNull(provisioningProperties.workspaceId()),
            null,
            0,
            0,
            0,
            List.of(),
            message
        );
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Railway ownership details JSON.", ex);
        }
    }

    private String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String field : path) {
            if (current == null) {
                return null;
            }
            current = current.path(field);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText();
        return value == null || value.isBlank() ? null : value.trim();
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
        return StringUtils.hasText(value);
    }

    private record OwnershipIndex(
        Map<String, List<RailwayWorkspaceCleanupOwnerSummary>> projectOwnersById,
        Map<String, List<RailwayWorkspaceCleanupOwnerSummary>> serviceOwnersById,
        Map<String, List<RailwayWorkspaceCleanupOwnerSummary>> serviceOwnersByName
    ) {
    }
}
