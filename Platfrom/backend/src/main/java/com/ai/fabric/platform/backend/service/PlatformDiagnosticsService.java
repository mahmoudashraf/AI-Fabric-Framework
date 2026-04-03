package com.ai.fabric.platform.backend.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentHostedVerificationLogParser;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.deployment.service.RailwayPreflightService;
import com.ai.fabric.platform.backend.deployment.service.RailwayProvisioningException;
import com.ai.fabric.platform.backend.model.PlatformDiagnosticsSummary;
import com.ai.fabric.platform.backend.model.PlatformRailwayLogsResponse;
import com.ai.fabric.platform.backend.model.PlatformRailwayServiceDiscoverySummary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Service
public class PlatformDiagnosticsService {

    private final PlatformProperties properties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final PlatformProvisioningProperties provisioningProperties;
    private final RailwayPreflightService railwayPreflightService;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final DeploymentHostedVerificationRunRepository hostedVerificationRunRepository;
    private final DeploymentHostedVerificationLogParser hostedVerificationLogParser;

    public PlatformDiagnosticsService(PlatformProperties properties,
                                      PlatformDeliveryProperties deliveryProperties,
                                      PlatformProvisioningProperties provisioningProperties,
                                      RailwayPreflightService railwayPreflightService,
                                      RailwayGraphqlClient railwayGraphqlClient,
                                      DeploymentHostedVerificationRunRepository hostedVerificationRunRepository,
                                      DeploymentHostedVerificationLogParser hostedVerificationLogParser) {
        this.properties = properties;
        this.deliveryProperties = deliveryProperties;
        this.provisioningProperties = provisioningProperties;
        this.railwayPreflightService = railwayPreflightService;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.hostedVerificationRunRepository = hostedVerificationRunRepository;
        this.hostedVerificationLogParser = hostedVerificationLogParser;
    }

    public PlatformDiagnosticsSummary getSummary() {
        RailwayPreflightSummary preflight = null;
        String preflightError = null;
        try {
            preflight = railwayPreflightService.run();
        } catch (Exception ex) {
            preflightError = ex.getMessage();
        }

        PlatformRailwayServiceDiscoverySummary railwayService = discoverRailwayService();
        return new PlatformDiagnosticsSummary(
            properties.name(),
            properties.stage(),
            properties.currentPhase(),
            deliveryProperties.publicBaseUrl(),
            provisioningProperties.mode(),
            provisioningProperties.workspaceId(),
            provisioningProperties.repository(),
            provisioningProperties.branch(),
            buildSummaryMessage(railwayService, preflight, preflightError),
            preflight,
            preflightError,
            railwayService,
            hostedVerificationRunRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toRunSummary)
                .toList()
        );
    }

    public PlatformRailwayLogsResponse fetchLogs(String source,
                                                 Integer limit,
                                                 String filter,
                                                 String startDate,
                                                 String endDate) {
        String normalizedSource = normalizeSource(source);
        int requestedLimit = normalizeLimit(limit);
        PlatformRailwayServiceDiscoverySummary railwayService = discoverRailwayService();
        if (!railwayService.available()) {
            return unavailable(normalizedSource, requestedLimit, filter, startDate, endDate, railwayService.summaryMessage(), railwayService);
        }
        if (railwayService.latestDeploymentId() == null) {
            return unavailable(
                normalizedSource,
                requestedLimit,
                filter,
                startDate,
                endDate,
                "Platform Railway service was discovered, but no latest deployment id is available yet.",
                railwayService
            );
        }

        List<RailwayLogEntrySummary> entries;
        try {
            entries = switch (normalizedSource) {
                case "build" -> railwayGraphqlClient.fetchBuildLogs(
                    railwayService.latestDeploymentId(),
                    requestedLimit,
                    filter,
                    startDate,
                    endDate
                );
                case "http" -> railwayGraphqlClient.fetchHttpLogs(
                    railwayService.latestDeploymentId(),
                    requestedLimit,
                    filter,
                    startDate,
                    endDate
                );
                default -> railwayGraphqlClient.fetchDeploymentLogs(
                    railwayService.latestDeploymentId(),
                    requestedLimit,
                    filter,
                    startDate,
                    endDate
                );
            };
        } catch (RailwayProvisioningException ex) {
            return unavailable(normalizedSource, requestedLimit, filter, startDate, endDate, ex.getMessage(), railwayService);
        }

        return new PlatformRailwayLogsResponse(
            normalizedSource,
            true,
            entries.isEmpty() ? "No logs returned for the current query window." : "Fetched platform Railway logs successfully.",
            railwayService.projectId(),
            railwayService.environmentId(),
            railwayService.serviceId(),
            railwayService.serviceName(),
            railwayService.latestDeploymentId(),
            requestedLimit,
            blankToNull(filter),
            blankToNull(startDate),
            blankToNull(endDate),
            Instant.now(),
            entries
        );
    }

    PlatformRailwayServiceDiscoverySummary discoverRailwayService() {
        if (!"RAILWAY_API".equalsIgnoreCase(provisioningProperties.mode())) {
            return unavailableService("Platform diagnostics can only discover Railway logs when provisioning mode is RAILWAY_API.");
        }
        if (blankToNull(provisioningProperties.workspaceId()) == null) {
            return unavailableService("Platform diagnostics require a configured Railway workspace id.");
        }
        String publicHost = resolvePublicHost();
        if (publicHost == null) {
            return unavailableService("Platform diagnostics require platform.delivery.public-base-url to resolve the live domain.");
        }

        try {
            for (RailwayGraphqlClient.RailwayProjectSnapshot project : railwayGraphqlClient.listProjectsInWorkspace(provisioningProperties.workspaceId())) {
                for (RailwayGraphqlClient.RailwayEnvironmentSummary environment : project.environments()) {
                    for (RailwayGraphqlClient.RailwayServiceSummary service : project.services()) {
                        List<RailwayGraphqlClient.RailwayServiceDomainSummary> domains = railwayGraphqlClient.listServiceDomains(
                            project.id(),
                            environment.id(),
                            service.id()
                        );
                        RailwayGraphqlClient.RailwayServiceDomainSummary matchingDomain = domains.stream()
                            .filter(domain -> publicHost.equalsIgnoreCase(domain.domain()))
                            .findFirst()
                            .orElse(null);
                        if (matchingDomain == null) {
                            continue;
                        }
                        RailwayGraphqlClient.RailwayServiceInstanceSummary instance = railwayGraphqlClient.getServiceInstance(environment.id(), service.id());
                        RailwayGraphqlClient.RailwayServiceSourceSummary source = railwayGraphqlClient.getServiceSource(service.id());
                        RailwayGraphqlClient.RailwayDeploymentSummary latestDeployment = railwayGraphqlClient.listServiceDeployments(service.id(), 5)
                            .stream()
                            .findFirst()
                            .orElse(null);
                        RailwayGraphqlClient.RailwayDeploymentTriggerSummary trigger = source.repoTriggers().stream().findFirst().orElse(null);
                        return new PlatformRailwayServiceDiscoverySummary(
                            true,
                            "Platform Railway service resolved from public domain " + publicHost + ".",
                            publicHost,
                            project.id(),
                            project.name(),
                            environment.id(),
                            environment.name(),
                            service.id(),
                            service.name(),
                            matchingDomain.domain(),
                            latestDeployment == null ? null : latestDeployment.id(),
                            latestDeployment == null ? null : latestDeployment.status(),
                            latestDeployment == null ? null : latestDeployment.url(),
                            latestDeployment == null ? null : latestDeployment.staticUrl(),
                            latestDeployment == null ? null : latestDeployment.createdAt(),
                            instance.rootDirectory(),
                            instance.dockerfilePath(),
                            instance.healthcheckPath(),
                            instance.upstreamUrl(),
                            instance.sourceRepo(),
                            instance.sourceImage(),
                            trigger == null ? null : trigger.repository(),
                            trigger == null ? null : trigger.branch()
                        );
                    }
                }
            }
            return unavailableService("No Railway service in the configured workspace matched platform public host " + publicHost + ".");
        } catch (Exception ex) {
            return unavailableService("Failed to resolve the platform Railway service: " + ex.getMessage());
        }
    }

    private DeploymentHostedVerificationRunSummary toRunSummary(DeploymentHostedVerificationRunEntity run) {
        return new DeploymentHostedVerificationRunSummary(
            run.getId(),
            run.getDeploymentId(),
            run.getReleaseId(),
            run.getDeploymentVersionId(),
            run.getVerificationProfile(),
            run.getRunnerType(),
            run.getScriptPath(),
            run.getStatus(),
            run.isVerifyWrite(),
            run.getSummaryMessage(),
            run.getLogOutput(),
            hostedVerificationLogParser.parse(run.getLogOutput()),
            run.getExitCode(),
            run.getCreatedAt(),
            run.getStartedAt(),
            run.getCompletedAt()
        );
    }

    private String buildSummaryMessage(PlatformRailwayServiceDiscoverySummary railwayService,
                                       RailwayPreflightSummary preflight,
                                       String preflightError) {
        if (!railwayService.available()) {
            return railwayService.summaryMessage();
        }
        if (preflightError != null) {
            return "Platform Railway service resolved, but Railway preflight could not be loaded: " + preflightError;
        }
        if (preflight != null && !preflight.ready()) {
            return "Platform Railway service resolved. Preflight still has blocking checks.";
        }
        return "Platform Railway service resolved and recent hosted verification diagnostics are available.";
    }

    private String resolvePublicHost() {
        try {
            URI uri = URI.create(deliveryProperties.publicBaseUrl());
            return blankToNull(uri.getHost());
        } catch (Exception ex) {
            return null;
        }
    }

    private PlatformRailwayServiceDiscoverySummary unavailableService(String message) {
        return new PlatformRailwayServiceDiscoverySummary(
            false,
            message,
            resolvePublicHost(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private PlatformRailwayLogsResponse unavailable(String source,
                                                    int requestedLimit,
                                                    String filter,
                                                    String startDate,
                                                    String endDate,
                                                    String message,
                                                    PlatformRailwayServiceDiscoverySummary railwayService) {
        return new PlatformRailwayLogsResponse(
            source,
            false,
            message,
            railwayService.projectId(),
            railwayService.environmentId(),
            railwayService.serviceId(),
            railwayService.serviceName(),
            railwayService.latestDeploymentId(),
            requestedLimit,
            blankToNull(filter),
            blankToNull(startDate),
            blankToNull(endDate),
            Instant.now(),
            List.of()
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return 200;
        }
        return Math.max(1, Math.min(limit, 500));
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "deployment";
        }
        return switch (source.trim().toLowerCase()) {
            case "build" -> "build";
            case "http" -> "http";
            default -> "deployment";
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
