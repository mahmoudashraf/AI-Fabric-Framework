package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivityProbeSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DeploymentReleaseVerificationService {

    private final ObjectMapper objectMapper;
    private final PlatformVerificationProperties verificationProperties;
    private final PlatformSecretService platformSecretService;
    private final DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService;
    private final DeploymentArtifactService deploymentArtifactService;
    private final RailwayPreflightService railwayPreflightService;
    private final DeploymentProviderConnectivityService deploymentProviderConnectivityService;
    private final DeploymentTenantScopedVectorService deploymentTenantScopedVectorService;
    private final DeploymentVectorizationVerificationService deploymentVectorizationVerificationService;
    private final HttpClient httpClient;

    DeploymentReleaseVerificationService(ObjectMapper objectMapper,
                                         PlatformVerificationProperties verificationProperties,
                                         PlatformSecretService platformSecretService,
                                         DeploymentArtifactService deploymentArtifactService,
                                         RailwayPreflightService railwayPreflightService,
                                         DeploymentProviderConnectivityService deploymentProviderConnectivityService,
                                         DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                         DeploymentVectorizationVerificationService deploymentVectorizationVerificationService) {
        this(
            objectMapper,
            verificationProperties,
            platformSecretService,
            new DeploymentProviderSecretResolutionService(platformSecretService),
            deploymentArtifactService,
            railwayPreflightService,
            deploymentProviderConnectivityService,
            deploymentTenantScopedVectorService,
            deploymentVectorizationVerificationService
        );
    }

    @Autowired
    public DeploymentReleaseVerificationService(ObjectMapper objectMapper,
                                                PlatformVerificationProperties verificationProperties,
                                                PlatformSecretService platformSecretService,
                                                DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                                DeploymentArtifactService deploymentArtifactService,
                                                RailwayPreflightService railwayPreflightService,
                                                DeploymentProviderConnectivityService deploymentProviderConnectivityService,
                                                DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                                DeploymentVectorizationVerificationService deploymentVectorizationVerificationService) {
        this.objectMapper = objectMapper;
        this.verificationProperties = verificationProperties;
        this.platformSecretService = platformSecretService;
        this.deploymentProviderSecretResolutionService = deploymentProviderSecretResolutionService;
        this.deploymentArtifactService = deploymentArtifactService;
        this.railwayPreflightService = railwayPreflightService;
        this.deploymentProviderConnectivityService = deploymentProviderConnectivityService;
        this.deploymentTenantScopedVectorService = deploymentTenantScopedVectorService;
        this.deploymentVectorizationVerificationService = deploymentVectorizationVerificationService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(verificationProperties.timeout())
            .build();
    }

    public DeploymentVerificationRunEntity verify(DeploymentEntity deployment,
                                                  DeploymentVersionEntity version,
                                                  DeploymentReleaseEntity release,
                                                  String verificationType) {
        Instant now = Instant.now();
        ArrayNode checks = objectMapper.createArrayNode();
        DeploymentArtifactBundleSummary artifacts = deploymentArtifactService.toBundleSummary(version);
        if ("PRE_APPLY".equalsIgnoreCase(verificationType)) {
            verifyPreApply(checks, deployment, version, release, artifacts);
        } else {
            VerificationExpectations expectations = buildExpectations(version, artifacts);
            addBooleanCheck(
                checks,
                "active_version_matches_release",
                version.getId().equals(deployment.getActiveVersionId()),
                "Deployment active version matches the applied release version."
            );
            addBooleanCheck(
                checks,
                "runtime_base_url_present",
                hasText(deployment.getRuntimeBaseUrl()),
                "Runtime base URL is populated."
            );
            addBooleanCheck(
                checks,
                "connector_base_url_present",
                hasText(deployment.getConnectorBaseUrl()),
                "Connector base URL is populated."
            );
            addBooleanCheck(
                checks,
                "provisioning_details_present",
                hasText(release.getProvisioningDetailsJson()),
                "Provisioning details were captured for this release."
            );
            addBooleanCheck(
                checks,
                "version_manifest_present",
                hasText(version.getManifestJson()),
                "Compiled manifest exists for the release version."
            );
            verifyLiveEndpoints(checks, deployment, release, expectations);
        }

        int passed = 0;
        int warning = 0;
        int failed = 0;
        int skipped = 0;
        for (JsonNode check : checks) {
            String status = check.path("status").asText();
            if ("PASSED".equals(status)) {
                passed += 1;
            } else if ("WARNING".equals(status)) {
                warning += 1;
            } else if ("FAILED".equals(status)) {
                failed += 1;
            } else if ("SKIPPED".equals(status)) {
                skipped += 1;
            }
        }

        DeploymentVerificationRunEntity run = new DeploymentVerificationRunEntity();
        run.setId(generateId("vrf"));
        run.setDeploymentId(deployment.getId());
        run.setReleaseId(release.getId());
        run.setDeploymentVersionId(version.getId());
        run.setVerificationType(verificationType);
        run.setStatus(failed == 0 ? "PASSED" : "FAILED");
        run.setSummaryMessage(buildSummaryMessage(passed, warning, failed, skipped));
        run.setChecksJson(checks.toPrettyString());
        run.setCreatedAt(now);
        run.setCompletedAt(now);
        return run;
    }

    private void verifyPreApply(ArrayNode checks,
                                DeploymentEntity deployment,
                                DeploymentVersionEntity version,
                                DeploymentReleaseEntity release,
                                DeploymentArtifactBundleSummary artifacts) {
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());

        addBooleanCheck(
            checks,
            "version_manifest_present",
            hasText(version.getManifestJson()),
            "Compiled manifest exists for the release version."
        );
        addBooleanCheck(
            checks,
            "config_hash_present",
            hasText(version.getConfigHash()),
            "Published version has a config hash."
        );
        addBooleanCheck(
            checks,
            "provisioning_target_selected",
            hasText(release.getProvisioningTarget()),
            "Provisioning target is selected for this release."
        );
        if (!"RAILWAY_API".equalsIgnoreCase(release.getProvisioningTarget())) {
            addSkippedCheck(
                checks,
                "live_rollout_prerequisites",
                "Live deployment prerequisites are skipped because this release target is " + release.getProvisioningTarget() + "."
            );
            return;
        }

        addArtifactPresenceCheck(checks, "actions_artifact_url_present", "Actions artifact URL", artifacts.actionsArtifactUrl());
        addArtifactPresenceCheck(checks, "entities_artifact_url_present", "Entities artifact URL", artifacts.entityArtifactUrl());
        addArtifactPresenceCheck(checks, "routing_artifact_url_present", "Routing artifact URL", artifacts.routingArtifactUrl());
        addArtifactPresenceCheck(checks, "prompt_artifact_url_present", "Prompt artifact URL", artifacts.promptArtifactUrl());
        addArtifactPresenceCheck(checks, "manifest_artifact_url_present", "Manifest artifact URL", artifacts.manifestUrl());

        addArtifactFetchCheck(checks, "actions_artifact_fetch_probe", "Actions artifact", artifacts.actionsArtifactUrl());
        addArtifactFetchCheck(checks, "entities_artifact_fetch_probe", "Entities artifact", artifacts.entityArtifactUrl());
        addArtifactFetchCheck(checks, "routing_artifact_fetch_probe", "Routing artifact", artifacts.routingArtifactUrl());
        addArtifactFetchCheck(checks, "prompt_artifact_fetch_probe", "Prompt artifact", artifacts.promptArtifactUrl());
        addArtifactFetchCheck(checks, "manifest_artifact_fetch_probe", "Manifest artifact", artifacts.manifestUrl());

        verifyManagedSecrets(checks, deployment, providerConfig, securityConfig);
        verifyAuthzDeployability(checks, providerConfig, securityConfig);
        verifyTenantScopedSharedStorage(checks, deployment, providerConfig);
        verifyVectorizationControlPlane(checks, deployment, readJson(version.getEntityConfigJson()));
        verifyVectorizationRunnerRegistration(checks, deployment, readJson(version.getEntityConfigJson()), false);
        verifyManagedVectorProvisioning(checks, providerConfig, version.getEntityConfigJson());
        verifyProviderConnectivity(checks, version, providerConfig);
        verifyRailwayPreflight(checks);
    }

    private void verifyTenantScopedSharedStorage(ArrayNode checks,
                                                 DeploymentEntity deployment,
                                                 JsonNode providerConfig) {
        DeploymentTenantScopedVectorSummary summary = deploymentTenantScopedVectorService.build(deployment, providerConfig);
        if (!summary.sharedStorage()) {
            addSkippedCheck(
                checks,
                "tenant_scoped_shared_storage_boundary",
                "Tenant-scoped shared storage checks are skipped because this deployment is not using shared vector storage."
            );
            return;
        }
        if (!"READY".equalsIgnoreCase(summary.status())) {
            addCheck(
                checks,
                "tenant_scoped_shared_storage_boundary",
                "FAILED",
                blankToFallback(summary.summaryMessage(), "Shared tenant-scoped storage is not ready for rollout."),
                null
            );
            return;
        }
        if (summary.registry() != null && "BLOCKED".equalsIgnoreCase(summary.registry().status())) {
            addCheck(
                checks,
                "tenant_scoped_shared_storage_boundary",
                "FAILED",
                blankToFallback(summary.registry().message(), "Shared tenant-scoped storage failed customer-boundary or registry validation."),
                null
            );
            return;
        }
        addCheck(
            checks,
            "tenant_scoped_shared_storage_boundary",
            "PASSED",
            "Tenant-scoped shared storage is bound to a valid customer-owned provider root and is ready for rollout.",
            null
        );
    }

    private void verifyLiveEndpoints(ArrayNode checks,
                                     DeploymentEntity deployment,
                                     DeploymentReleaseEntity release,
                                     VerificationExpectations expectations) {
        if ("RAILWAY_STUB".equalsIgnoreCase(release.getProvisioningTarget())) {
            addSkippedCheck(checks, "runtime_health_http_probe",
                "Live runtime probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_health_http_probe",
                "Live connector probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_admin_overview_http_probe",
                "Runtime admin overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_auth_overview_http_probe",
                "Runtime auth overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_actions_overview_http_probe",
                "Runtime actions overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_indexing_overview_http_probe",
                "Runtime indexing overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_admin_overview_http_probe",
                "Connector admin overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_actions_overview_http_probe",
                "Connector actions overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_config_matches_expected",
                "Runtime config validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_prompt_config_matches_expected",
                "Runtime prompt config validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_auth_configuration_matches_expected",
                "Runtime auth validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_actions_match_expected",
                "Runtime action validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_entity_types_match_expected",
                "Runtime entity validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_config_matches_expected",
                "Connector config validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_actions_match_expected",
                "Connector action validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_authz_configuration_matches_expected",
                "Connector authz validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "vectorization_control_plane_ready",
                "Vectorization control-plane validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "vectorization_runner_registration_ready",
                "Vectorization runner registration validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "vectorization_runner_service_provisioned",
                "Vectorization runner provisioning validation skipped because the deployment is still using stub provisioning.");
            return;
        }

        addHttpProbe(
            checks,
            "runtime_health_http_probe",
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeHealthPath(),
            "Runtime"
        );

        Map<String, String> runtimeAdminHeaders = runtimeAdminHeaders(deployment);
        JsonProbeResult connectorHealth = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeConnectorHealthPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "connector_health_http_probe", "Connector health via runtime proxy", connectorHealth);

        JsonProbeResult runtimeOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeAdminOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "runtime_admin_overview_http_probe", "Runtime admin overview", runtimeOverview);
        validateRuntimeOverview(checks, runtimeOverview, expectations);

        JsonProbeResult runtimeAuthOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeAuthOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "runtime_auth_overview_http_probe", "Runtime auth overview", runtimeAuthOverview);
        validateRuntimeAuthOverview(checks, runtimeAuthOverview, expectations);

        JsonProbeResult runtimeActionsOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeActionsOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "runtime_actions_overview_http_probe", "Runtime actions overview", runtimeActionsOverview);
        validateRuntimeActions(checks, runtimeActionsOverview, expectations);

        JsonProbeResult runtimeIndexingOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeIndexingOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "runtime_indexing_overview_http_probe", "Runtime indexing overview", runtimeIndexingOverview);
        validateRuntimeIndexing(checks, runtimeIndexingOverview, expectations);

        JsonProbeResult connectorOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.connectorAdminOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "connector_admin_overview_http_probe", "Connector admin overview via runtime proxy", connectorOverview);
        validateConnectorOverview(checks, connectorOverview, expectations);
        validateConnectorAuthz(checks, connectorOverview, expectations);

        JsonProbeResult connectorActionsOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.connectorActionsOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "connector_actions_overview_http_probe", "Connector actions overview via runtime proxy", connectorActionsOverview);
        validateConnectorActions(checks, connectorActionsOverview, expectations);
        verifyVectorizationControlPlane(checks, deployment, expectations.entityConfig());
        verifyVectorizationRunnerRegistration(checks, deployment, expectations.entityConfig(), true);
        verifyVectorizationRunnerServiceProvisioning(checks, deployment, release, expectations.entityConfig());
    }

    private void verifyVectorizationControlPlane(ArrayNode checks,
                                                 DeploymentEntity deployment,
                                                 JsonNode entityConfig) {
        DeploymentVectorizationVerificationSummary summary = deploymentVectorizationVerificationService.build(deployment, entityConfig);
        if (!summary.planPresent() && !summary.sourceConnectionPresent() && !summary.runnerPresent()) {
            addSkippedCheck(
                checks,
                "vectorization_control_plane_ready",
                "Vectorization checks are skipped because no vectorization plan, source connection, or runner registration exists for this deployment."
            );
            return;
        }

        ObjectNode details = objectMapper.createObjectNode();
        details.put("planPresent", summary.planPresent());
        details.put("sourceConnectionPresent", summary.sourceConnectionPresent());
        details.put("activeRevisionPresent", summary.activeRevisionPresent());
        details.put("configured", summary.configured());
        details.put("runnerRequired", summary.runnerRequired());
        details.set("availableEntityTypes", toArrayNode(new LinkedHashSet<>(summary.availableEntityTypes())));
        details.set("entityScope", toArrayNode(new LinkedHashSet<>(summary.entityScope())));
        if (summary.plan() != null) {
            details.put("planStatus", summary.plan().status());
            details.put("runnerMode", blankToFallback(summary.plan().runnerMode(), "UNKNOWN"));
            details.put("syncState", blankToFallback(summary.plan().syncState(), "UNKNOWN"));
        }
        if (summary.sourceConnection() != null) {
            details.put("sourceAdapter", blankToFallback(summary.sourceConnection().adapterType(), "UNKNOWN"));
            details.put("sourceAuthMode", blankToFallback(summary.sourceConnection().authMode(), "UNKNOWN"));
            details.put("sourceStatus", blankToFallback(summary.sourceConnection().status(), "UNKNOWN"));
        }

        boolean passed = summary.configured()
            && summary.plan() != null
            && hasText(summary.plan().runnerMode())
            && hasText(summary.plan().syncState())
            && summary.sourceConnection() != null
            && hasText(summary.sourceConnection().adapterType())
            && hasText(summary.sourceConnection().authMode())
            && hasText(summary.sourceConnection().status());

        addCheck(
            checks,
            "vectorization_control_plane_ready",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Vectorization plan, linked source connection, and active revision are configured for this deployment."
                : "Vectorization control plane is partially configured or missing required linked state.",
            details
        );
    }

    private void verifyVectorizationRunnerRegistration(ArrayNode checks,
                                                       DeploymentEntity deployment,
                                                       JsonNode entityConfig,
                                                       boolean requireActiveRegistration) {
        DeploymentVectorizationVerificationSummary summary = deploymentVectorizationVerificationService.build(deployment, entityConfig);
        if (!summary.planPresent() && !summary.sourceConnectionPresent() && !summary.runnerPresent()) {
            addSkippedCheck(
                checks,
                "vectorization_runner_registration_ready",
                "Vectorization runner registration checks are skipped because vectorization is not configured for this deployment."
            );
            return;
        }
        if (!summary.runnerRequired()) {
            addSkippedCheck(
                checks,
                "vectorization_runner_registration_ready",
                "Vectorization runner registration is optional for the selected runner mode."
            );
            return;
        }

        ObjectNode details = objectMapper.createObjectNode();
        details.put("runnerPresent", summary.runnerPresent());
        details.put("requireActiveRegistration", requireActiveRegistration);
        details.put("runnerMode", summary.plan() == null ? "UNKNOWN" : blankToFallback(summary.plan().runnerMode(), "UNKNOWN"));
        if (summary.runner() != null) {
            details.put("registrationStatus", blankToFallback(summary.runner().registrationStatus(), "UNKNOWN"));
            details.put("compatibilityStatus", blankToFallback(summary.runner().compatibilityStatus(), "UNKNOWN"));
            if (summary.runner().tokenExpiresAt() != null) {
                details.put("tokenExpiresAt", summary.runner().tokenExpiresAt().toString());
            }
        }

        if (!requireActiveRegistration && summary.platformManagedRunnerExpected()) {
            addCheck(
                checks,
                "vectorization_runner_registration_ready",
                "PASSED",
                summary.runner() != null
                    && "ACTIVE".equalsIgnoreCase(summary.runner().registrationStatus())
                    && (summary.runner().tokenExpiresAt() == null || !summary.runner().tokenExpiresAt().isBefore(Instant.now()))
                    ? "Platform-managed vectorization runner registration is already active before apply."
                    : "Platform-managed vectorization runner registration will be established after provisioning. Pre-apply only requires vectorization control-plane readiness.",
                details
            );
            return;
        }

        boolean passed = summary.runner() != null
            && "ACTIVE".equalsIgnoreCase(summary.runner().registrationStatus())
            && (summary.runner().tokenExpiresAt() == null || !summary.runner().tokenExpiresAt().isBefore(Instant.now()));

        addCheck(
            checks,
            "vectorization_runner_registration_ready",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Vectorization runner registration is active and its token is valid."
                : "Vectorization execution requires an active runner registration with a valid token.",
            details
        );
    }

    private void verifyVectorizationRunnerServiceProvisioning(ArrayNode checks,
                                                              DeploymentEntity deployment,
                                                              DeploymentReleaseEntity release,
                                                              JsonNode entityConfig) {
        DeploymentVectorizationVerificationSummary summary = deploymentVectorizationVerificationService.build(deployment, entityConfig);
        if (!summary.platformManagedRunnerExpected()) {
            addSkippedCheck(
                checks,
                "vectorization_runner_service_provisioned",
                "A platform-managed vectorization runner service is not expected for the selected runner mode."
            );
            return;
        }

        JsonNode runnerService = readJson(release.getProvisioningDetailsJson())
            .path("railway")
            .path("services")
            .path("vectorizationRunner");
        ObjectNode details = objectMapper.createObjectNode();
        details.put("serviceId", runnerService.path("serviceId").asText(""));
        details.put("serviceName", runnerService.path("serviceName").asText(""));
        details.put("deploymentId", runnerService.path("deploymentId").asText(""));
        details.put("deploymentStatus", runnerService.path("deploymentStatus").asText(""));

        boolean passed = runnerService.isObject()
            && (hasText(runnerService.path("serviceId").asText("")) || hasText(runnerService.path("serviceName").asText("")))
            && hasText(runnerService.path("deploymentStatus").asText(""));

        addCheck(
            checks,
            "vectorization_runner_service_provisioned",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Release provisioning details include the platform-managed vectorization runner service."
                : "Release provisioning details do not include the expected platform-managed vectorization runner service.",
            details
        );
    }

    private Map<String, String> runtimeAdminHeaders(DeploymentEntity deployment) {
        return RuntimePrivateAccessSupport.issueSystemHeaders(
            platformSecretService,
            objectMapper,
            deployment,
            "platform-release-verification",
            "release-verification-" + blankToFallback(deployment == null ? null : deployment.getId(), "unknown"),
            "platform-release-verification",
            RuntimePrivateAccessSupport.adminReadScopes(),
            Duration.ofMinutes(15)
        );
    }

    private VerificationExpectations buildExpectations(DeploymentVersionEntity version,
                                                       DeploymentArtifactBundleSummary artifacts) {
        JsonNode actionsConfig = readJson(version.getActionsConfigJson());
        JsonNode entityConfig = readJson(version.getEntityConfigJson());
        JsonNode routingConfig = readJson(version.getRoutingConfigJson());
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());

        Set<String> expectedActionNames = new LinkedHashSet<>();
        JsonNode actions = actionsConfig.path("actions");
        if (actions.isArray()) {
            for (JsonNode action : actions) {
                String name = action.path("name").asText("").trim();
                if (hasText(name)) {
                    expectedActionNames.add(name);
                }
            }
        }

        Set<String> expectedEntityTypes = new LinkedHashSet<>();
        JsonNode entities = entityConfig.path("ai-entities");
        if (entities.isObject()) {
            entities.fieldNames().forEachRemaining(name -> {
                if (hasText(name)) {
                    expectedEntityTypes.add(name.trim());
                }
            });
        }

        Set<String> expectedRoutingActions = new LinkedHashSet<>();
        JsonNode routingActions = routingConfig.path("actions");
        if (routingActions.isObject()) {
            routingActions.fieldNames().forEachRemaining(name -> {
                if (hasText(name)) {
                    expectedRoutingActions.add(name.trim());
                }
            });
        }

        boolean expectedAuthzEnabled = routingConfig.path("authz").path("enabled").asBoolean(false);
        boolean expectedRuntimeProxyEnabled = ManagedDeploymentProfileCatalog.connectorRuntimeProxyEnabled(providerConfig);
        boolean expectedTrustedBackendConfigured = platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY");
        boolean expectedPrivateAssertionValidationConfigured = platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY");
        boolean expectedPublicTokenValidationConfigured =
            platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")
                && ManagedDeploymentProfileCatalog.publicRuntimeRequested(securityConfig);
        String expectedIngressMode = "VERIFIED_CONTEXT_REQUIRED";
        String expectedPublicTokenIssuer = expectedPublicTokenValidationConfigured
            ? blankToFallback(ManagedDeploymentProfileCatalog.publicRuntimeTokenIssuer(securityConfig), "runtime-public-bootstrap")
            : "";
        Set<String> expectedPrivateAcceptedIssuers = expectedTrustedBackendConfigured
            ? csvSet(ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedIssuers(securityConfig))
            : Set.of();
        Set<String> expectedPrivateAcceptedAudiences = expectedTrustedBackendConfigured
            ? csvSet(ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedAudiences(
                securityConfig,
                artifacts.deploymentId()
            ))
            : Set.of();

        return new VerificationExpectations(
            artifacts,
            actionsConfig,
            entityConfig,
            routingConfig,
            securityConfig,
            expectedActionNames,
            expectedEntityTypes,
            expectedRoutingActions,
            expectedAuthzEnabled,
            expectedRuntimeProxyEnabled,
            expectedIngressMode,
            expectedTrustedBackendConfigured,
            expectedPrivateAssertionValidationConfigured,
            expectedPublicTokenValidationConfigured,
            true,
            true,
            expectedPrivateAcceptedIssuers,
            expectedPrivateAcceptedAudiences,
            expectedPublicTokenIssuer,
            csvSet(ManagedDeploymentProfileCatalog.publicRuntimeAcceptedIssuers(securityConfig)),
            csvSet(ManagedDeploymentProfileCatalog.publicRuntimeAcceptedAudiences(securityConfig)),
            ManagedDeploymentProfileCatalog.publicRuntimeDefaultAudience(securityConfig),
            expectedPublicTokenValidationConfigured && ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig)
        );
    }

    private void validateRuntimeOverview(ArrayNode checks,
                                         JsonProbeResult probe,
                                         VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "runtime_config_matches_expected", "Runtime config validation skipped because the admin overview probe failed.");
            return;
        }

        String entityConfigLocation = probe.body().path("entityConfigLocation").asText("");
        String promptConfigLocation = probe.body().path("promptConfigLocation").asText("");
        Set<String> actionSourcePaths = textSet(probe.body().path("actionCatalogSources"), "path");
        int actionsCount = probe.body().path("actionsCount").asInt(-1);
        Set<String> supportedEntityTypes = textSet(probe.body().path("supportedEntityTypes"));

        ObjectNode details = objectMapper.createObjectNode();
        details.put("expectedEntityConfigLocation", expectations.artifacts().entityArtifactUrl());
        details.put("actualEntityConfigLocation", entityConfigLocation);
        details.put("expectedPromptConfigLocation", expectations.artifacts().promptArtifactUrl());
        details.put("actualPromptConfigLocation", promptConfigLocation);
        details.put("expectedActionsArtifactUrl", expectations.artifacts().actionsArtifactUrl());
        details.put("actionsCount", actionsCount);
        details.put("expectedActionsCount", expectations.expectedActionNames().size());
        details.set("actionSourcePaths", toArrayNode(actionSourcePaths));
        details.set("supportedEntityTypes", toArrayNode(supportedEntityTypes));
        details.set("expectedEntityTypes", toArrayNode(expectations.expectedEntityTypes()));

        boolean passed = probe.body().path("success").asBoolean(false)
            && expectations.artifacts().entityArtifactUrl().equals(entityConfigLocation)
            && actionSourcePaths.contains(expectations.artifacts().actionsArtifactUrl())
            && actionsCount == expectations.expectedActionNames().size()
            && supportedEntityTypes.equals(expectations.expectedEntityTypes());

        addCheck(
            checks,
            "runtime_config_matches_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Runtime loaded the expected action catalog source and entity configuration."
                : "Runtime admin overview does not match the published platform configuration.",
            details
        );

        boolean promptConfigPassed = probe.body().path("success").asBoolean(false)
            && expectations.artifacts().promptArtifactUrl().equals(promptConfigLocation);
        addCheck(
            checks,
            "runtime_prompt_config_matches_expected",
            promptConfigPassed ? "PASSED" : "FAILED",
            promptConfigPassed
                ? "Runtime loaded the expected deployment prompt config artifact."
                : "Runtime prompt config does not match the published platform prompt artifact.",
            details.deepCopy()
        );
    }

    private void validateRuntimeAuthOverview(ArrayNode checks,
                                            JsonProbeResult probe,
                                            VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "runtime_auth_configuration_matches_expected", "Runtime auth validation skipped because the auth overview probe failed.");
            return;
        }
        validateRuntimeAuth(checks, probe.body().path("auth"), expectations);
    }

    private void validateRuntimeAuth(ArrayNode checks,
                                     JsonNode auth,
                                     VerificationExpectations expectations) {
        ObjectNode details = objectMapper.createObjectNode();
        String actualIngressMode = auth.path("ingressMode").asText("");
        boolean actualRejectConflictingRequestIdentity = auth.path("rejectConflictingRequestIdentity").asBoolean(false);
        boolean actualRejectRequestIdentityWhenVerifiedContextPresent = auth.path("rejectRequestIdentityWhenVerifiedContextPresent").asBoolean(false);
        boolean actualTrustedBackendConfigured = auth.path("trustedBackendConfigured").asBoolean(false);
        boolean actualPrivateAssertionValidationConfigured = auth.path("privateAssertionValidationConfigured").asBoolean(false);
        Set<String> actualPrivateAssertionAcceptedIssuers = textSet(auth.path("privateAssertionAcceptedIssuers"));
        Set<String> actualPrivateAssertionAcceptedAudiences = textSet(auth.path("privateAssertionAcceptedAudiences"));
        boolean actualPublicTokenValidationConfigured = auth.path("publicTokenValidationConfigured").asBoolean(false);
        String actualPublicTokenIssuer = auth.path("publicTokenIssuer").asText("");
        Set<String> actualPublicAcceptedIssuers = textSet(auth.path("publicAcceptedIssuers"));
        Set<String> actualPublicAcceptedAudiences = textSet(auth.path("publicAcceptedAudiences"));
        String actualPublicDefaultAudience = auth.path("publicDefaultAudience").asText("");
        boolean actualPublicBootstrapEnabled = auth.path("publicBootstrap").path("enabled").asBoolean(false);

        details.put("expectedIngressMode", expectations.expectedIngressMode());
        details.put("actualIngressMode", actualIngressMode);
        details.put("expectedRejectConflictingRequestIdentity", expectations.expectedRejectConflictingRequestIdentity());
        details.put("actualRejectConflictingRequestIdentity", actualRejectConflictingRequestIdentity);
        details.put("expectedRejectRequestIdentityWhenVerifiedContextPresent", expectations.expectedRejectRequestIdentityWhenVerifiedContextPresent());
        details.put("actualRejectRequestIdentityWhenVerifiedContextPresent", actualRejectRequestIdentityWhenVerifiedContextPresent);
        details.put("expectedTrustedBackendConfigured", expectations.expectedTrustedBackendConfigured());
        details.put("actualTrustedBackendConfigured", actualTrustedBackendConfigured);
        details.put("expectedPrivateAssertionValidationConfigured", expectations.expectedPrivateAssertionValidationConfigured());
        details.put("actualPrivateAssertionValidationConfigured", actualPrivateAssertionValidationConfigured);
        details.set("expectedPrivateAssertionAcceptedIssuers", toArrayNode(expectations.expectedPrivateAcceptedIssuers()));
        details.set("actualPrivateAssertionAcceptedIssuers", toArrayNode(actualPrivateAssertionAcceptedIssuers));
        details.set("expectedPrivateAssertionAcceptedAudiences", toArrayNode(expectations.expectedPrivateAcceptedAudiences()));
        details.set("actualPrivateAssertionAcceptedAudiences", toArrayNode(actualPrivateAssertionAcceptedAudiences));
        details.put("expectedPublicTokenValidationConfigured", expectations.expectedPublicTokenValidationConfigured());
        details.put("actualPublicTokenValidationConfigured", actualPublicTokenValidationConfigured);
        details.put("expectedPublicTokenIssuer", expectations.expectedPublicTokenIssuer());
        details.put("actualPublicTokenIssuer", actualPublicTokenIssuer);
        details.set("expectedPublicAcceptedIssuers", toArrayNode(expectations.expectedPublicAcceptedIssuers()));
        details.set("actualPublicAcceptedIssuers", toArrayNode(actualPublicAcceptedIssuers));
        details.set("expectedPublicAcceptedAudiences", toArrayNode(expectations.expectedPublicAcceptedAudiences()));
        details.set("actualPublicAcceptedAudiences", toArrayNode(actualPublicAcceptedAudiences));
        details.put("expectedPublicDefaultAudience", blankToFallback(expectations.expectedPublicDefaultAudience(), ""));
        details.put("actualPublicDefaultAudience", actualPublicDefaultAudience);
        details.put("expectedPublicBootstrapEnabled", expectations.expectedPublicBootstrapEnabled());
        details.put("actualPublicBootstrapEnabled", actualPublicBootstrapEnabled);

        boolean passed = auth != null
            && auth.isObject()
            && expectations.expectedIngressMode().equals(actualIngressMode)
            && expectations.expectedRejectConflictingRequestIdentity() == actualRejectConflictingRequestIdentity
            && expectations.expectedRejectRequestIdentityWhenVerifiedContextPresent() == actualRejectRequestIdentityWhenVerifiedContextPresent
            && expectations.expectedTrustedBackendConfigured() == actualTrustedBackendConfigured
            && expectations.expectedPrivateAssertionValidationConfigured() == actualPrivateAssertionValidationConfigured
            && expectations.expectedPublicTokenValidationConfigured() == actualPublicTokenValidationConfigured
            && expectations.expectedPublicBootstrapEnabled() == actualPublicBootstrapEnabled;
        if (passed && expectations.expectedTrustedBackendConfigured()) {
            passed = expectations.expectedPrivateAcceptedIssuers().equals(actualPrivateAssertionAcceptedIssuers)
                && expectations.expectedPrivateAcceptedAudiences().equals(actualPrivateAssertionAcceptedAudiences);
        }
        if (passed && expectations.expectedPublicTokenValidationConfigured()) {
            passed = expectations.expectedPublicTokenIssuer().equals(actualPublicTokenIssuer)
                && expectations.expectedPublicAcceptedIssuers().equals(actualPublicAcceptedIssuers)
                && expectations.expectedPublicAcceptedAudiences().equals(actualPublicAcceptedAudiences)
                && blankToFallback(expectations.expectedPublicDefaultAudience(), "").equals(actualPublicDefaultAudience);
        }

        addCheck(
            checks,
            "runtime_auth_configuration_matches_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Runtime auth posture matches the published deployment security configuration."
                : "Runtime auth posture does not match the published deployment security configuration.",
            details
        );
    }

    private void validateRuntimeActions(ArrayNode checks,
                                        JsonProbeResult probe,
                                        VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "runtime_actions_match_expected", "Runtime action validation skipped because the actions overview probe failed.");
            return;
        }

        Set<String> loadedActionNames = textSet(probe.body().path("actions"), "name");
        int count = probe.body().path("count").asInt(-1);

        ObjectNode details = objectMapper.createObjectNode();
        details.put("count", count);
        details.put("expectedCount", expectations.expectedActionNames().size());
        details.set("loadedActionNames", toArrayNode(loadedActionNames));
        details.set("expectedActionNames", toArrayNode(expectations.expectedActionNames()));

        boolean passed = probe.body().path("success").asBoolean(false)
            && count == expectations.expectedActionNames().size()
            && loadedActionNames.equals(expectations.expectedActionNames());

        addCheck(
            checks,
            "runtime_actions_match_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Runtime actions overview matches the published action catalog."
                : "Runtime actions overview does not match the published action catalog.",
            details
        );
    }

    private void validateRuntimeIndexing(ArrayNode checks,
                                         JsonProbeResult probe,
                                         VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "runtime_entity_types_match_expected", "Runtime entity validation skipped because the indexing overview probe failed.");
            return;
        }

        Set<String> entityTypes = textSet(probe.body().path("entityTypes"));
        Set<String> countsByEntityType = fieldNames(probe.body().path("countsByEntityType"));

        ObjectNode details = objectMapper.createObjectNode();
        details.set("entityTypes", toArrayNode(entityTypes));
        details.set("countsByEntityTypeKeys", toArrayNode(countsByEntityType));
        details.set("expectedEntityTypes", toArrayNode(expectations.expectedEntityTypes()));
        details.put("supportsVectorScan", probe.body().path("supportsVectorScan").asBoolean(false));

        boolean passed = probe.body().path("success").asBoolean(false)
            && entityTypes.equals(expectations.expectedEntityTypes())
            && countsByEntityType.containsAll(expectations.expectedEntityTypes());

        addCheck(
            checks,
            "runtime_entity_types_match_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Runtime indexing overview matches the expected entity types."
                : "Runtime indexing overview does not match the expected entity types.",
            details
        );
    }

    private void validateConnectorOverview(ArrayNode checks,
                                           JsonProbeResult probe,
                                           VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "connector_config_matches_expected", "Connector config validation skipped because the admin overview probe failed.");
            return;
        }

        JsonNode runtimeProxy = probe.body().path("runtimeProxy");
        JsonNode connector = probe.body().path("connector");

        String routingConfigLocation = probe.body().path("routingConfigLocation").asText("");
        boolean runtimeProxyEnabled = runtimeProxy.path("enabled").asBoolean(false);
        String runtimeProxyBaseUrl = runtimeProxy.path("baseUrl").asText("");
        boolean apiKeyConfigured = connector.path("inboundAuth").path("apiKey").path("valueConfigured").asBoolean(false);
        int actionsCount = probe.body().path("actionsCount").asInt(-1);

        ObjectNode details = objectMapper.createObjectNode();
        details.put("expectedRoutingConfigLocation", expectations.artifacts().routingArtifactUrl());
        details.put("actualRoutingConfigLocation", routingConfigLocation);
        details.put("expectedRuntimeProxyEnabled", expectations.expectedRuntimeProxyEnabled());
        details.put("runtimeProxyEnabled", runtimeProxyEnabled);
        details.put("runtimeProxyBaseUrl", runtimeProxyBaseUrl);
        details.put("connectorApiKeyConfigured", apiKeyConfigured);
        details.put("actionsCount", actionsCount);
        details.put("expectedActionsCount", expectations.expectedRoutingActions().size());

        boolean passed = probe.body().path("success").asBoolean(false)
            && expectations.artifacts().routingArtifactUrl().equals(routingConfigLocation)
            && runtimeProxyEnabled == expectations.expectedRuntimeProxyEnabled()
            && (!expectations.expectedRuntimeProxyEnabled() || hasText(runtimeProxyBaseUrl))
            && actionsCount == expectations.expectedRoutingActions().size()
            && apiKeyConfigured == !expectations.routingConfig().path("connector").path("inbound-auth").path("allow-unauthenticated").asBoolean(false);

        addCheck(
            checks,
            "connector_config_matches_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Connector overview matches the published routing and runtime proxy configuration."
                : "Connector overview does not match the published routing and runtime proxy configuration.",
            details
        );
    }

    private void validateConnectorAuthz(ArrayNode checks,
                                        JsonProbeResult probe,
                                        VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "connector_authz_configuration_matches_expected", "Connector authz validation skipped because the admin overview probe failed.");
            return;
        }

        JsonNode authz = probe.body().path("authz");
        boolean enabled = authz.path("enabled").asBoolean(false);
        String path = authz.path("path").asText("");
        String upstreamBaseUrl = authz.path("upstream").path("baseUrl").asText("");

        ObjectNode details = objectMapper.createObjectNode();
        details.put("expectedEnabled", expectations.expectedAuthzEnabled());
        details.put("actualEnabled", enabled);
        details.put("path", path);
        details.put("upstreamBaseUrl", upstreamBaseUrl);

        boolean passed = enabled == expectations.expectedAuthzEnabled()
            && (!enabled || (hasText(path) && hasText(upstreamBaseUrl)));

        addCheck(
            checks,
            "connector_authz_configuration_matches_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Connector authz configuration matches the published routing configuration."
                : "Connector authz configuration does not match the published routing configuration.",
            details
        );
    }

    private void validateConnectorActions(ArrayNode checks,
                                          JsonProbeResult probe,
                                          VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "connector_actions_match_expected", "Connector action validation skipped because the actions overview probe failed.");
            return;
        }

        Set<String> routedActionIds = textSet(probe.body().path("actions"), "actionId");
        int count = probe.body().path("count").asInt(-1);

        ObjectNode details = objectMapper.createObjectNode();
        details.put("count", count);
        details.put("expectedCount", expectations.expectedRoutingActions().size());
        details.set("routedActionIds", toArrayNode(routedActionIds));
        details.set("expectedRoutingActionIds", toArrayNode(expectations.expectedRoutingActions()));

        boolean passed = probe.body().path("success").asBoolean(false)
            && count == expectations.expectedRoutingActions().size()
            && routedActionIds.equals(expectations.expectedRoutingActions());

        addCheck(
            checks,
            "connector_actions_match_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Connector actions overview matches the published routing configuration."
                : "Connector actions overview does not match the published routing configuration.",
            details
        );
    }

    private void verifyManagedSecrets(ArrayNode checks,
                                      DeploymentEntity deployment,
                                      JsonNode providerConfig,
                                      JsonNode securityConfig) {
        String llmProvider = ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig);
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        for (Map.Entry<String, String> entry : ManagedDeploymentProfileCatalog.providerSecretNamesByLlmSelection(providerConfig).entrySet()) {
            addProviderSecretCheck(
                checks,
                deployment.getId(),
                "llm_provider_" + entry.getKey() + "_secret_available",
                entry.getValue(),
                entry.getKey() + " credential is available for the selected LLM profile."
            );
        }
        addProviderSecretCheck(
            checks,
            deployment.getId(),
            "embedding_provider_secret_available",
            resolveEmbeddingSecretName(embeddingProvider),
            "Embedding provider credential is available for the selected deployment profile."
        );
        String requiredVectorSecretName = ManagedDeploymentProfileCatalog.requiredVectorSecretName(providerConfig);
        if (hasText(requiredVectorSecretName)) {
            addProviderSecretCheck(
                checks,
                deployment.getId(),
                vectorStrategy + "_secret_available",
                requiredVectorSecretName,
                "Required vector database credential is available for the selected deployment profile."
            );
        }
        for (String optionalVectorSecretName : ManagedDeploymentProfileCatalog.optionalVectorSecretNames(providerConfig)) {
            DeploymentProviderSecretResolutionService.ResolvedSecretValue resolved =
                resolveOptionalProviderSecret(deployment.getId(), optionalVectorSecretName);
            if (resolved.resolved()) {
                addProviderSecretCheck(
                    checks,
                    deployment.getId(),
                    optionalVectorSecretName.toLowerCase(Locale.ROOT) + "_available",
                    optionalVectorSecretName,
                    optionalVectorSecretName + " is available for the selected vector database profile."
                );
            } else {
                addSkippedCheck(
                    checks,
                    optionalVectorSecretName.toLowerCase(Locale.ROOT) + "_available",
                    optionalVectorSecretName + " is optional and not present. Deployment will continue without it."
                );
            }
        }
        if (ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityConfig)) {
            addSecretCheck(
                checks,
                "connector_api_key_available",
                "CONNECTOR_API_KEY",
                "Connector inbound API key is available."
            );
            addSecretCheck(
                checks,
                "runtime_connector_api_key_available",
                "ACTIONS_CONNECTOR_API_KEY",
                "Runtime-to-connector API key is available."
            );
        } else {
            addSkippedCheck(
                checks,
                "connector_api_key_available",
                "Connector API key enforcement is disabled for this deployment profile."
            );
            addSkippedCheck(
                checks,
                "runtime_connector_api_key_available",
                "Runtime-to-connector API key is not required when connector API key enforcement is disabled."
            );
        }
        if (ManagedDeploymentProfileCatalog.adminApiKeyEnabled(securityConfig)) {
            addSecretCheck(
                checks,
                "runtime_trusted_backend_api_key_available",
                RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME,
                "Trusted backend machine credential is available for protected runtime admin endpoints."
            );
            addSecretCheck(
                checks,
                "runtime_private_assertion_signing_key_available",
                "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY",
                "Private assertion signing key is available for protected runtime admin endpoints."
            );
        } else {
            addSkippedCheck(
                checks,
                "runtime_trusted_backend_api_key_available",
                "Private runtime admin protection is not required because admin endpoint protection is disabled."
            );
            addSkippedCheck(
                checks,
                "runtime_private_assertion_signing_key_available",
                "Private runtime admin protection is not required because admin endpoint protection is disabled."
            );
        }
    }

    private void verifyAuthzDeployability(ArrayNode checks,
                                          JsonNode providerConfig,
                                          JsonNode securityConfig) {
        String authzMode = ManagedDeploymentProfileCatalog.resolveAuthzMode(securityConfig);
        String connectorProfile = ManagedDeploymentProfileCatalog.resolveConnectorProfile(providerConfig);
        String configuredBaseUrl = securityConfig.path("authzBaseUrl").asText("").trim();

        ObjectNode details = objectMapper.createObjectNode();
        details.put("authzMode", authzMode);
        details.put("connectorProfile", connectorProfile);
        details.put("configuredAuthzBaseUrl", configuredBaseUrl);

        boolean passed = ManagedDeploymentProfileCatalog.AUTHZ_MODE_DENY_ALL.equals(authzMode)
            || hasText(configuredBaseUrl)
            || ManagedDeploymentProfileCatalog.CONNECTOR_PROFILE_HOSTED.equals(connectorProfile);
        String message;
        if (ManagedDeploymentProfileCatalog.AUTHZ_MODE_DENY_ALL.equals(authzMode)) {
            message = "Runtime authz mode is DENY_ALL, so no upstream authz target is required.";
        } else if (hasText(configuredBaseUrl)) {
            message = "Runtime authz mode has an explicit upstream base URL.";
        } else if (ManagedDeploymentProfileCatalog.CONNECTOR_PROFILE_HOSTED.equals(connectorProfile)) {
            message = "Runtime authz mode will use the platform-managed connector base URL during provisioning.";
        } else {
            message = "REMOTE_HTTP authz requires either a configured authz base URL or a hosted connector profile.";
        }
        addCheck(
            checks,
            "runtime_authz_target_resolves",
            passed ? "PASSED" : "FAILED",
            message,
            details
        );
    }

    private void verifyManagedVectorProvisioning(ArrayNode checks,
                                                 JsonNode providerConfig,
                                                 String entityConfigJson) {
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        JsonNode entityConfig = readJson(entityConfigJson);
        if (ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig)) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("indexName", ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig));
            details.put("cloud", ManagedDeploymentProfileCatalog.pineconeCloud(providerConfig));
            details.put("region", ManagedDeploymentProfileCatalog.pineconeRegion(providerConfig));
            details.put("metric", ManagedDeploymentProfileCatalog.pineconeMetric(providerConfig));
            boolean ready = hasText(ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig))
                && hasText(ManagedDeploymentProfileCatalog.pineconeRegion(providerConfig))
                && platformSecretService.isSecretPresent("PINECONE_API_KEY");
            addCheck(
                checks,
                "managed_vector_provisioning_ready",
                ready ? "PASSED" : "FAILED",
                ready
                    ? "Managed Pinecone serverless index provisioning prerequisites are satisfied."
                    : "Managed Pinecone serverless provisioning requires pineconeIndexName, pineconeRegion, and PINECONE_API_KEY.",
                details
            );
            return;
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("qdrantCloudAccountId", ManagedDeploymentProfileCatalog.qdrantCloudAccountId(providerConfig));
            details.put("qdrantCloudProviderId", ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerConfig));
            details.put("qdrantCloudRegionId", ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig));
            details.put("qdrantCloudPackageId", ManagedDeploymentProfileCatalog.qdrantCloudPackageId(providerConfig));
            details.put("entityTypeCount", entityConfig.path("ai-entities").size());
            boolean ready = hasText(ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig))
                && entityConfig.path("ai-entities").size() > 0
                && platformSecretService.isSecretPresent("QDRANT_CLOUD_MANAGEMENT_API_KEY");
            addCheck(
                checks,
                "managed_vector_provisioning_ready",
                ready ? "PASSED" : "FAILED",
                ready
                    ? "Managed Qdrant Cloud cluster provisioning prerequisites are satisfied."
                    : "Managed Qdrant Cloud provisioning requires qdrantCloudRegionId, at least one configured entity type, and QDRANT_CLOUD_MANAGEMENT_API_KEY.",
                details
            );
            return;
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig)) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("qdrantHost", ManagedDeploymentProfileCatalog.qdrantHost(providerConfig));
            details.put("entityTypeCount", entityConfig.path("ai-entities").size());
            boolean ready = hasText(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig))
                && entityConfig.path("ai-entities").size() > 0;
            addCheck(
                checks,
                "managed_vector_provisioning_ready",
                ready ? "PASSED" : "FAILED",
                ready
                    ? "Managed Qdrant collection provisioning prerequisites are satisfied."
                    : "Managed Qdrant collection provisioning requires qdrantHost and at least one configured entity type.",
                details
            );
            return;
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerConfig)) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("zillizCloudProjectId", ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig));
            details.put("zillizCloudRegionId", ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig));
            details.put("zillizCloudClusterPlan", ManagedDeploymentProfileCatalog.zillizCloudClusterPlan(providerConfig));
            details.put("zillizCloudCuType", ManagedDeploymentProfileCatalog.zillizCloudCuType(providerConfig));
            details.put("zillizCloudCuSize", ManagedDeploymentProfileCatalog.zillizCloudCuSize(providerConfig));
            String clusterPlan = ManagedDeploymentProfileCatalog.zillizCloudClusterPlan(providerConfig);
            boolean dedicatedPlan = "Standard".equals(clusterPlan) || "Enterprise".equals(clusterPlan);
            boolean ready = hasText(ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig))
                && hasText(ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig))
                && platformSecretService.isSecretPresent("ZILLIZ_CLOUD_API_KEY")
                && (!dedicatedPlan
                    || (hasText(ManagedDeploymentProfileCatalog.zillizCloudCuType(providerConfig))
                    && ManagedDeploymentProfileCatalog.zillizCloudCuSize(providerConfig) > 0));
            addCheck(
                checks,
                "managed_vector_provisioning_ready",
                ready ? "PASSED" : "FAILED",
                ready
                    ? "Managed Zilliz Cloud provisioning prerequisites are satisfied."
                    : "Managed Zilliz Cloud provisioning requires zillizCloudProjectId, zillizCloudRegionId, and ZILLIZ_CLOUD_API_KEY. Dedicated plans also require zillizCloudCuType and zillizCloudCuSize.",
                details
            );
            return;
        }
        addSkippedCheck(
            checks,
            "managed_vector_provisioning_ready",
            "No managed external vector provisioning is enabled for this deployment profile."
        );
    }

    private void verifyProviderConnectivity(ArrayNode checks,
                                            DeploymentVersionEntity version,
                                            JsonNode providerConfig) {
        DeploymentProviderConnectivitySummary summary = deploymentProviderConnectivityService.probe(
            version.getDeploymentId(),
            "Published version " + version.getVersionLabel(),
            providerConfig,
            readJson(version.getEntityConfigJson())
        );

        ObjectNode summaryDetails = objectMapper.createObjectNode();
        summaryDetails.put("llmProvider", summary.llmProvider());
        summaryDetails.put("embeddingProvider", summary.embeddingProvider());
        summaryDetails.put("vectorStrategy", summary.vectorStrategy());
        summaryDetails.put("managedVectorProvisioningEnabled", summary.managedVectorProvisioningEnabled());
        summaryDetails.put("managedVectorProvisioningMode", summary.managedVectorProvisioningMode());
        summaryDetails.put("probeCount", summary.probes().size());
        addCheck(
            checks,
            "provider_connectivity_summary",
            summary.probes().stream().anyMatch(probe -> "FAILED".equals(probe.status()) || "BLOCKED".equals(probe.status()))
                ? "FAILED"
                : summary.probes().stream().allMatch(probe -> "SKIPPED".equals(probe.status()))
                    ? "SKIPPED"
                    : "PASSED",
            summary.summaryMessage(),
            summaryDetails
        );

        for (DeploymentProviderConnectivityProbeSummary probe : summary.probes()) {
            ObjectNode details = objectMapper.createObjectNode();
            if (hasText(probe.endpoint())) {
                details.put("endpoint", probe.endpoint());
            }
            details.put("providerSummary", summary.summaryMessage());
            if (summary.managedVectorProvisioningEnabled()) {
                details.put("managedVectorProvisioningMode", summary.managedVectorProvisioningMode());
            }
            addCheck(
                checks,
                "provider_connectivity_" + probe.key(),
                switch (probe.status()) {
                    case "READY" -> "PASSED";
                    case "SKIPPED" -> "SKIPPED";
                    default -> "FAILED";
                },
                probe.message(),
                details
            );
        }
    }

    private void verifyRailwayPreflight(ArrayNode checks) {
        RailwayPreflightSummary preflight = railwayPreflightService.run();
        for (RailwayPreflightCheckSummary check : preflight.checks()) {
            ObjectNode details = objectMapper.createObjectNode();
            if (hasText(check.details())) {
                details.put("details", check.details());
            }
            details.put("mode", preflight.mode());
            if (hasText(preflight.workspaceId())) {
                details.put("workspaceId", preflight.workspaceId());
            }
            if (hasText(preflight.repository())) {
                details.put("repository", preflight.repository());
            }
            if (hasText(preflight.branch())) {
                details.put("branch", preflight.branch());
            }
            addCheck(
                checks,
                "railway_preflight_" + check.key(),
                normalizeVerificationStatus(check.status()),
                check.message(),
                details
            );
        }
    }

    private void addSecretCheck(ArrayNode checks,
                                String name,
                                String secretName,
                                String message) {
        if (!hasText(secretName)) {
            addSkippedCheck(checks, name, "No managed secret is required for this provider profile.");
            return;
        }
        ObjectNode details = objectMapper.createObjectNode();
        details.put("secretName", secretName);
        boolean present = platformSecretService.isSecretPresent(secretName);
        addCheck(
            checks,
            name,
            present ? "PASSED" : "FAILED",
            present ? message : "Required platform secret is missing: " + secretName,
            details
        );
    }

    private void addProviderSecretCheck(ArrayNode checks,
                                        String deploymentId,
                                        String name,
                                        String secretPurpose,
                                        String message) {
        if (!hasText(secretPurpose)) {
            addSkippedCheck(checks, name, "No managed secret is required for this provider profile.");
            return;
        }
        if (isPlatformManagedSecretPurpose(secretPurpose)) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("secretPurpose", secretPurpose);
            boolean present = platformSecretService.isSecretPresent(secretPurpose);
            details.put("reasonCode", present ? "PLATFORM_SECRET_PRESENT" : "PLATFORM_SECRET_MISSING");
            details.put("bindingMode", "PLATFORM_GLOBAL");
            details.put("scopeType", "PLATFORM_SECRET");
            addCheck(
                checks,
                name,
                present ? "PASSED" : "FAILED",
                present
                    ? message
                    : "Required platform-managed secret is missing: " + secretPurpose,
                details
            );
            return;
        }
        DeploymentProviderSecretResolutionService.ResolvedSecretValue resolved =
            resolveOptionalProviderSecret(deploymentId, secretPurpose);
        ObjectNode details = objectMapper.createObjectNode();
        details.put("secretPurpose", secretPurpose);
        details.put("reasonCode", resolved.summary().reasonCode());
        details.put("bindingMode", resolved.summary().bindingMode());
        if (hasText(resolved.summary().scopeType())) {
            details.put("scopeType", resolved.summary().scopeType());
        }
        if (hasText(resolved.summary().ownerType())) {
            details.put("ownerType", resolved.summary().ownerType());
        }
        if (hasText(resolved.primarySecretName())) {
            details.put("secretName", resolved.primarySecretName());
        }
        if (hasText(resolved.secondarySecretName())) {
            details.put("secondarySecretName", resolved.secondarySecretName());
        }
        addCheck(
            checks,
            name,
            resolved.resolved() ? "PASSED" : "FAILED",
            resolved.resolved() ? message : resolved.summary().diagnosticMessage(),
            details
        );
    }

    private DeploymentProviderSecretResolutionService.ResolvedSecretValue resolveOptionalProviderSecret(String deploymentId,
                                                                                                        String secretPurpose) {
        return switch (secretPurpose) {
            case "MILVUS_USERNAME", "MILVUS_PASSWORD", "MILVUS_RUNTIME_CREDENTIALS" ->
                deploymentProviderSecretResolutionService.resolve(deploymentId, "MILVUS_RUNTIME_CREDENTIALS", null, null);
            default -> deploymentProviderSecretResolutionService.resolve(deploymentId, secretPurpose, null);
        };
    }

    private boolean isPlatformManagedSecretPurpose(String secretPurpose) {
        return switch (secretPurpose) {
            case "QDRANT_CLOUD_MANAGEMENT_API_KEY", "ZILLIZ_CLOUD_API_KEY" -> true;
            default -> false;
        };
    }

    private void addArtifactPresenceCheck(ArrayNode checks,
                                          String name,
                                          String label,
                                          String url) {
        ObjectNode details = objectMapper.createObjectNode();
        if (hasText(url)) {
            details.put("url", url);
        }
        addCheck(
            checks,
            name,
            hasText(url) ? "PASSED" : "FAILED",
            hasText(url)
                ? label + " is published as a signed artifact URL."
                : label + " is missing from the published deployment artifact bundle.",
            details
        );
    }

    private void addArtifactFetchCheck(ArrayNode checks,
                                       String name,
                                       String label,
                                       String url) {
        ArtifactProbeResult probe = probeArtifact(url);
        ObjectNode details = objectMapper.createObjectNode();
        if (probe.uri() != null) {
            details.put("url", probe.uri().toString());
        }
        if (probe.httpStatus() != null) {
            details.put("httpStatus", probe.httpStatus());
        }
        if (probe.durationMs() != null) {
            details.put("durationMs", probe.durationMs());
        }
        if (probe.errorMessage() != null) {
            details.put("error", probe.errorMessage());
        }
        addCheck(
            checks,
            name,
            probe.success() ? "PASSED" : "FAILED",
            probe.success()
                ? label + " can be fetched from the platform delivery URL."
                : label + " could not be fetched from the platform delivery URL.",
            details
        );
    }

    private JsonProbeResult probeJson(String baseUrl,
                                      String path,
                                      Map<String, String> headers) {
        if (!hasText(baseUrl)) {
            return JsonProbeResult.failure("Base URL is missing.");
        }

        URI uri;
        try {
            uri = buildProbeUri(baseUrl, path);
        } catch (IllegalArgumentException ex) {
            return JsonProbeResult.failure("Probe URI is invalid: " + ex.getMessage());
        }

        long startedAt = System.nanoTime();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(verificationProperties.timeout())
            .header("Accept", "application/json")
            .GET();
        headers.forEach(requestBuilder::header);

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            JsonNode body = null;
            String parseError = null;
            String rawBody = response.body();
            if (hasText(rawBody)) {
                try {
                    body = objectMapper.readTree(rawBody);
                } catch (Exception ex) {
                    parseError = ex.getMessage();
                }
            }
            return new JsonProbeResult(uri, response.statusCode(), durationMs, body, rawBody, parseError, null, response.headers());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return JsonProbeResult.failure(uri, "Probe was interrupted.");
        } catch (Exception ex) {
            return JsonProbeResult.failure(uri, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private ArtifactProbeResult probeArtifact(String url) {
        if (!hasText(url)) {
            return ArtifactProbeResult.failure("Artifact URL is missing.");
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception ex) {
            return ArtifactProbeResult.failure("Artifact URL is invalid: " + ex.getMessage());
        }

        long startedAt = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(verificationProperties.timeout())
            .header("Accept", "*/*")
            .GET()
            .build();

        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            return new ArtifactProbeResult(uri, response.statusCode(), durationMs, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ArtifactProbeResult.failure(uri, "Artifact probe was interrupted.");
        } catch (Exception ex) {
            return ArtifactProbeResult.failure(uri, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void addProbeCheck(ArrayNode checks,
                               String name,
                               String label,
                               JsonProbeResult probe) {
        ObjectNode details = objectMapper.createObjectNode();
        if (probe.uri() != null) {
            details.put("url", probe.uri().toString());
        }
        if (probe.httpStatus() != null) {
            details.put("httpStatus", probe.httpStatus());
        }
        if (probe.durationMs() != null) {
            details.put("durationMs", probe.durationMs());
        }
        if (probe.parseError() != null) {
            details.put("parseError", probe.parseError());
        }
        if (probe.errorMessage() != null) {
            details.put("error", probe.errorMessage());
        }
        if (probe.rawBody() != null && !probe.rawBody().isBlank()) {
            details.put("bodySnippet", abbreviate(probe.rawBody()));
        }
        addCheck(
            checks,
            name,
            probe.success() ? "PASSED" : "FAILED",
            probe.success()
                ? label + " responded with JSON successfully."
                : label + " probe failed.",
            details
        );
    }

    private void addHttpProbe(ArrayNode checks,
                              String name,
                              String baseUrl,
                              String path,
                              String label) {
        if (!hasText(baseUrl)) {
            addCheck(checks, name, "FAILED", label + " base URL is missing; cannot run live probe.", null);
            return;
        }

        URI uri;
        try {
            uri = buildProbeUri(baseUrl, path);
        } catch (IllegalArgumentException ex) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("baseUrl", baseUrl);
            details.put("path", path);
            addCheck(checks, name, "FAILED", label + " health URI is invalid: " + ex.getMessage(), details);
            return;
        }

        long startedAt = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(verificationProperties.timeout())
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            details.put("httpStatus", response.statusCode());
            details.put("durationMs", durationMs);

            boolean healthy = isHealthyResponse(response, details);
            addCheck(
                checks,
                name,
                healthy ? "PASSED" : "FAILED",
                healthy
                    ? label + " health endpoint responded successfully."
                    : label + " health endpoint did not report a healthy state.",
                details
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            addCheck(checks, name, "FAILED", label + " health probe was interrupted.", details);
        } catch (Exception ex) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            details.put("error", ex.getClass().getSimpleName());
            addCheck(checks, name, "FAILED", label + " health probe failed: " + ex.getMessage(), details);
        }
    }

    private boolean isHealthyResponse(HttpResponse<String> response, ObjectNode details) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return false;
        }

        String body = response.body();
        if (!hasText(body)) {
            return true;
        }

        try {
            JsonNode payload = objectMapper.readTree(body);
            JsonNode status = payload.path("status");
            if (status.isTextual()) {
                details.put("bodyStatus", status.asText());
                return "UP".equalsIgnoreCase(status.asText());
            }
        } catch (Exception ex) {
            details.put("bodySnippet", abbreviate(body));
        }

        return true;
    }

    private URI buildProbeUri(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment version config JSON.", ex);
        }
    }

    private Set<String> textSet(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node == null || node.isMissingNode()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (hasText(value)) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private Set<String> textSet(JsonNode node, String field) {
        Set<String> values = new LinkedHashSet<>();
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.path(field).asText("").trim();
            if (hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        node.fieldNames().forEachRemaining(values::add);
        return values;
    }

    private Set<String> csvSet(String value) {
        Set<String> values = new LinkedHashSet<>();
        if (!hasText(value)) {
            return values;
        }
        for (String item : value.split(",")) {
            String trimmed = item == null ? "" : item.trim();
            if (hasText(trimmed)) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private ArrayNode toArrayNode(Set<String> values) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        values.forEach(arrayNode::add);
        return arrayNode;
    }

    private void addBooleanCheck(ArrayNode checks, String name, boolean passed, String message) {
        addCheck(checks, name, passed ? "PASSED" : "FAILED", message, null);
    }

    private void addSkippedCheck(ArrayNode checks, String name, String message) {
        addCheck(checks, name, "SKIPPED", message, null);
    }

    private void addDependentCheckSkipped(ArrayNode checks, String name, String message) {
        addCheck(checks, name, "SKIPPED", message, null);
    }

    private void addCheck(ArrayNode checks,
                          String name,
                          String status,
                          String message,
                          ObjectNode details) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("status", status);
        node.put("message", message);
        if (details != null && !details.isEmpty()) {
            node.set("details", details);
        }
        checks.add(node);
    }

    private String buildSummaryMessage(int passed, int warning, int failed, int skipped) {
        StringBuilder summary = new StringBuilder();
        summary.append(passed).append(" passed");
        if (warning > 0) {
            summary.append(", ").append(warning).append(" warning");
            if (warning != 1) {
                summary.append("s");
            }
        }
        summary.append(", ").append(failed).append(" failed, ").append(skipped).append(" skipped");
        return summary.toString();
    }

    private String normalizeVerificationStatus(String status) {
        if (!hasText(status)) {
            return "FAILED";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PASSED", "FAILED", "WARNING", "SKIPPED" -> normalized;
            default -> "FAILED";
        };
    }

    private String resolveProviderSecretName(String llmProvider) {
        return ManagedDeploymentProfileCatalog.secretNameForLlmProvider(llmProvider);
    }

    private String resolveEmbeddingSecretName(String embeddingProvider) {
        return ManagedDeploymentProfileCatalog.secretNameForEmbeddingProvider(embeddingProvider);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToFallback(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String abbreviate(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.length() <= 240 ? value : value.substring(0, 237) + "...";
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record JsonProbeResult(
        URI uri,
        Integer httpStatus,
        Long durationMs,
        JsonNode body,
        String rawBody,
        String parseError,
        String errorMessage,
        HttpHeaders headers
    ) {
        private static JsonProbeResult failure(String message) {
            return new JsonProbeResult(null, null, null, null, null, null, message, HttpHeaders.of(Map.of(), (a, b) -> true));
        }

        private static JsonProbeResult failure(URI uri, String message) {
            return new JsonProbeResult(uri, null, null, null, null, null, message, HttpHeaders.of(Map.of(), (a, b) -> true));
        }

        private boolean success() {
            return errorMessage == null
                && parseError == null
                && httpStatus != null
                && httpStatus >= 200
                && httpStatus < 300
                && body != null
                && !body.isMissingNode();
        }
    }

    private record ArtifactProbeResult(
        URI uri,
        Integer httpStatus,
        Long durationMs,
        String errorMessage
    ) {
        private static ArtifactProbeResult failure(String message) {
            return new ArtifactProbeResult(null, null, null, message);
        }

        private static ArtifactProbeResult failure(URI uri, String message) {
            return new ArtifactProbeResult(uri, null, null, message);
        }

        private boolean success() {
            return errorMessage == null
                && httpStatus != null
                && httpStatus >= 200
                && httpStatus < 300;
        }
    }

    private record VerificationExpectations(
        DeploymentArtifactBundleSummary artifacts,
        JsonNode actionsConfig,
        JsonNode entityConfig,
        JsonNode routingConfig,
        JsonNode securityConfig,
        Set<String> expectedActionNames,
        Set<String> expectedEntityTypes,
        Set<String> expectedRoutingActions,
        boolean expectedAuthzEnabled,
        boolean expectedRuntimeProxyEnabled,
        String expectedIngressMode,
        boolean expectedTrustedBackendConfigured,
        boolean expectedPrivateAssertionValidationConfigured,
        boolean expectedPublicTokenValidationConfigured,
        boolean expectedRejectConflictingRequestIdentity,
        boolean expectedRejectRequestIdentityWhenVerifiedContextPresent,
        Set<String> expectedPrivateAcceptedIssuers,
        Set<String> expectedPrivateAcceptedAudiences,
        String expectedPublicTokenIssuer,
        Set<String> expectedPublicAcceptedIssuers,
        Set<String> expectedPublicAcceptedAudiences,
        String expectedPublicDefaultAudience,
        boolean expectedPublicBootstrapEnabled
    ) {
    }
}
