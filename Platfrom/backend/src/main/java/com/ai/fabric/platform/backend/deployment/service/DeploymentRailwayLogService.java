package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLogsResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentRailwayLogService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final ObjectMapper objectMapper;

    public DeploymentRailwayLogService(DeploymentRepository deploymentRepository,
                                       DeploymentReleaseRepository releaseRepository,
                                       RailwayGraphqlClient railwayGraphqlClient,
                                       ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.objectMapper = objectMapper;
    }

    public DeploymentRailwayLogsResponse fetchLogs(String deploymentId,
                                                   String releaseId,
                                                   String service,
                                                   String source,
                                                   Integer limit,
                                                   String filter,
                                                   String startDate,
                                                   String endDate) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));

        DeploymentReleaseEntity release = resolveRelease(deploymentId, releaseId);
        String normalizedService = normalizeService(service);
        String normalizedSource = normalizeSource(source);
        int requestedLimit = normalizeLimit(limit);

        if (release == null) {
            return unavailable(
                deployment.getId(),
                null,
                null,
                null,
                null,
                normalizedService,
                normalizedSource,
                requestedLimit,
                filter,
                startDate,
                endDate,
                "Deployment has no release yet. Apply a version first to fetch Railway logs."
            );
        }

        if (!"RAILWAY_API".equalsIgnoreCase(release.getProvisioningTarget())) {
            return unavailable(
                deployment.getId(),
                release.getId(),
                release.getDeploymentVersionId(),
                release.getStatus(),
                release.getProvisioningTarget(),
                normalizedService,
                normalizedSource,
                requestedLimit,
                filter,
                startDate,
                endDate,
                "Railway logs are only available for releases provisioned through RAILWAY_API."
            );
        }

        JsonNode details = readProvisioningDetails(release);
        JsonNode railway = details.path("railway");
        JsonNode railwayServices = railway.path("services");
        JsonNode serviceNode = switch (normalizedService) {
            case "runtime" -> railwayServices.path("runtime");
            case "restConnector" -> railwayServices.path("restConnector");
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported service: " + normalizedService);
        };

        String railwayDeploymentId = text(serviceNode.path("deploymentId"));
        if (railwayDeploymentId == null) {
            return unavailable(
                deployment.getId(),
                release.getId(),
                release.getDeploymentVersionId(),
                release.getStatus(),
                release.getProvisioningTarget(),
                normalizedService,
                normalizedSource,
                requestedLimit,
                filter,
                startDate,
                endDate,
                "Release does not contain a Railway deployment id for service " + normalizedService + "."
            );
        }

        List<RailwayLogEntrySummary> entries;
        try {
            entries = switch (normalizedSource) {
                case "deployment" -> railwayGraphqlClient.fetchDeploymentLogs(
                    railwayDeploymentId,
                    requestedLimit,
                    filter,
                    startDate,
                    endDate
                );
                case "build" -> railwayGraphqlClient.fetchBuildLogs(
                    railwayDeploymentId,
                    requestedLimit,
                    filter,
                    startDate,
                    endDate
                );
                case "http" -> railwayGraphqlClient.fetchHttpLogs(
                    railwayDeploymentId,
                    requestedLimit,
                    filter,
                    startDate,
                    endDate
                );
                default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported source: " + normalizedSource);
            };
        } catch (RailwayProvisioningException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to fetch Railway logs: " + ex.getMessage(), ex);
        }

        return new DeploymentRailwayLogsResponse(
            deployment.getId(),
            release.getId(),
            release.getDeploymentVersionId(),
            release.getStatus(),
            release.getProvisioningTarget(),
            normalizedService,
            normalizedSource,
            true,
            entries.isEmpty() ? "No logs returned for the current query window." : "Fetched Railway logs successfully.",
            text(railway.path("projectId")),
            text(railway.path("environmentId")),
            text(serviceNode.path("serviceId")),
            text(serviceNode.path("serviceName")),
            railwayDeploymentId,
            requestedLimit,
            blankToNull(filter),
            blankToNull(startDate),
            blankToNull(endDate),
            Instant.now(),
            entries
        );
    }

    private DeploymentReleaseEntity resolveRelease(String deploymentId, String releaseId) {
        if (releaseId == null || releaseId.isBlank()) {
            return releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deploymentId).orElse(null);
        }

        DeploymentReleaseEntity release = releaseRepository.findById(releaseId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Release not found: " + releaseId));
        if (!deploymentId.equals(release.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Release does not belong to deployment: " + deploymentId);
        }
        return release;
    }

    private JsonNode readProvisioningDetails(DeploymentReleaseEntity release) {
        try {
            return objectMapper.readTree(release.getProvisioningDetailsJson());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Release provisioning details are invalid for log lookup: " + release.getId(),
                ex
            );
        }
    }

    private DeploymentRailwayLogsResponse unavailable(String deploymentId,
                                                      String releaseId,
                                                      String deploymentVersionId,
                                                      String releaseStatus,
                                                      String provisioningTarget,
                                                      String service,
                                                      String source,
                                                      int requestedLimit,
                                                      String filter,
                                                      String startDate,
                                                      String endDate,
                                                      String message) {
        return new DeploymentRailwayLogsResponse(
            deploymentId,
            releaseId,
            deploymentVersionId,
            releaseStatus,
            provisioningTarget,
            service,
            source,
            false,
            message,
            null,
            null,
            null,
            null,
            null,
            requestedLimit,
            blankToNull(filter),
            blankToNull(startDate),
            blankToNull(endDate),
            Instant.now(),
            List.of()
        );
    }

    private String normalizeService(String service) {
        if (service == null || service.isBlank()) {
            return "runtime";
        }
        return switch (service.trim().toLowerCase()) {
            case "runtime" -> "runtime";
            case "restconnector", "rest-connector", "connector" -> "restConnector";
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported service: " + service);
        };
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "deployment";
        }
        return switch (source.trim().toLowerCase()) {
            case "deployment" -> "deployment";
            case "build" -> "build";
            case "http" -> "http";
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported source: " + source);
        };
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 200;
        }
        return Math.max(1, Math.min(limit, 500));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }
}
