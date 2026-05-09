package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentConfigReferenceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentConfigTemplateSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorStateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveReadbackSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceOfTruthGeneratedSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceOfTruthSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class DeploymentSourceOfTruthService {

    private final DeploymentArtifactService deploymentArtifactService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService;
    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final DeploymentTenantScopedVectorService deploymentTenantScopedVectorService;
    private final ObjectMapper objectMapper;

    public DeploymentSourceOfTruthService(DeploymentArtifactService deploymentArtifactService,
                                          DeploymentSourceResolver deploymentSourceResolver,
                                          RailwayProvisioningPlanService railwayProvisioningPlanService,
                                          DeploymentRailwayLiveReadbackService deploymentRailwayLiveReadbackService,
                                          DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                          DeploymentTenantScopedVectorService deploymentTenantScopedVectorService,
                                          ObjectMapper objectMapper) {
        this.deploymentArtifactService = deploymentArtifactService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.deploymentRailwayLiveReadbackService = deploymentRailwayLiveReadbackService;
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.deploymentTenantScopedVectorService = deploymentTenantScopedVectorService;
        this.objectMapper = objectMapper;
    }

    public DeploymentSourceOfTruthSummary build(DeploymentEntity deployment,
                                                DeploymentDraftEntity draft,
                                                DeploymentTemplateSummary template,
                                                DeploymentVersionEntity latestPublishedVersion,
                                                DeploymentVersionEntity liveVersion,
                                                DeploymentReleaseEntity latestRelease,
                                                DeploymentReleaseSummary latestReleaseSummary) {
        DeploymentSourceSummary source = deploymentSourceResolver.summarize(deployment);
        DeploymentConfigTemplateSourceSummary templateSource = new DeploymentConfigTemplateSourceSummary(
            template.id(),
            template.name(),
            template.description(),
            template.llmProvider(),
            template.embeddingProvider(),
            template.vectorStrategy(),
            template.managedVectorProvisioningDefault()
                && ManagedDeploymentProfileCatalog.supportsPlatformManagedVector(template.vectorStrategy())
                ? ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED
                : ManagedDeploymentProfileCatalog.defaultVectorProvisioningMode(template.vectorStrategy(), false),
            template.runtimeProfile(),
            template.connectorProfile(),
            source.repository(),
            source.branch(),
            source.repositoryOverride(),
            source.branchOverride(),
            source.overrideActive()
        );

        DeploymentConfigReferenceSummary draftReference = new DeploymentConfigReferenceSummary(
            "DRAFT",
            draft.getId(),
            "Draft r" + draft.getRevisionNumber(),
            null,
            draft.getUpdatedAt(),
            true
        );
        DeploymentConfigReferenceSummary latestPublishedReference = latestPublishedVersion == null
            ? new DeploymentConfigReferenceSummary("LATEST_PUBLISHED", null, "Not published", null, null, false)
            : new DeploymentConfigReferenceSummary(
                "LATEST_PUBLISHED",
                latestPublishedVersion.getId(),
                latestPublishedVersion.getVersionLabel(),
                latestPublishedVersion.getConfigHash(),
                latestPublishedVersion.getPublishedAt(),
                true
            );
        DeploymentConfigReferenceSummary liveReference = liveVersion == null
            ? new DeploymentConfigReferenceSummary("LIVE", null, "Not applied", null, null, false)
            : new DeploymentConfigReferenceSummary(
                "LIVE",
                liveVersion.getId(),
                liveVersion.getVersionLabel(),
                liveVersion.getConfigHash(),
                liveVersion.getPublishedAt(),
                true
            );

        DeploymentArtifactBundleSummary latestPublishedArtifacts = latestPublishedVersion == null
            ? null
            : deploymentArtifactService.toBundleSummary(latestPublishedVersion);
        DeploymentArtifactBundleSummary liveArtifacts = liveVersion == null
            ? null
            : deploymentArtifactService.toBundleSummary(liveVersion);

        DeploymentVersionEntity referenceVersion = liveVersion != null ? liveVersion : latestPublishedVersion;
        RailwayProvisioningPlanSummary plan = referenceVersion == null
            ? null
            : railwayProvisioningPlanService.buildPlan(deployment, referenceVersion);
        RailwayProvisioningPlanSummary livePlan = liveVersion == null
            ? null
            : railwayProvisioningPlanService.buildPlan(deployment, liveVersion);
        DeploymentRailwayLiveReadbackSummary liveRailwayReadback = deploymentRailwayLiveReadbackService.build(
            deployment,
            latestRelease,
            livePlan
        );
        DeploymentManagedVectorStateSummary managedVector = deploymentManagedVectorResourceService.buildStateSummary(
            deployment.getId(),
            referenceVersion == null ? readJson(draft.getProviderConfigJson()) : readJson(referenceVersion.getProviderConfigJson()),
            referenceVersion == null ? readJson(draft.getEntityConfigJson()) : readJson(referenceVersion.getEntityConfigJson()),
            deployment.getActiveVersionId()
        );
        DeploymentTenantScopedVectorSummary tenantScopedVector = deploymentTenantScopedVectorService.build(
            deployment,
            referenceVersion == null ? readJson(draft.getProviderConfigJson()) : readJson(referenceVersion.getProviderConfigJson())
        );

        DeploymentSourceOfTruthGeneratedSummary generated = new DeploymentSourceOfTruthGeneratedSummary(
            plan == null ? null : plan.mode(),
            plan == null ? null : plan.artifactStrategy(),
            plan == null ? null : plan.projectName(),
            plan == null ? source.repository() : plan.repository(),
            plan == null ? source.branch() : plan.branch(),
            plan == null ? null : plan.services().runtime().serviceName(),
            plan == null ? null : plan.services().runtime().dockerfilePath(),
            deployment.getRuntimeBaseUrl(),
            StringUtils.hasText(deployment.getConnectorBaseUrl()),
            plan == null ? null : plan.services().restConnector().serviceName(),
            plan == null ? null : plan.services().restConnector().dockerfilePath(),
            plan == null || plan.services() == null || plan.services().vectorizationRunner() == null
                ? null
                : plan.services().vectorizationRunner().serviceName(),
            plan == null || plan.services() == null || plan.services().vectorizationRunner() == null
                ? null
                : plan.services().vectorizationRunner().dockerfilePath()
        );

        return new DeploymentSourceOfTruthSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            templateSource,
            draftReference,
            latestPublishedReference,
            liveReference,
            latestRelease == null ? null : latestReleaseSummary,
            latestPublishedArtifacts,
            liveArtifacts,
            managedVector,
            tenantScopedVector,
            generated,
            liveRailwayReadback,
            liveRailwayReadback,
            summaryMessage(source, latestPublishedVersion, liveVersion, latestRelease, liveRailwayReadback, managedVector, tenantScopedVector)
        );
    }

    private String summaryMessage(DeploymentSourceSummary source,
                                  DeploymentVersionEntity latestPublishedVersion,
                                  DeploymentVersionEntity liveVersion,
                                  DeploymentReleaseEntity latestRelease,
                                  DeploymentRailwayLiveReadbackSummary liveRailwayReadback,
                                  DeploymentManagedVectorStateSummary managedVector,
                                  DeploymentTenantScopedVectorSummary tenantScopedVector) {
        String baseSummary;
        if (latestPublishedVersion == null) {
            baseSummary = "The deployment still runs from draft-only inputs. Publish a version to create immutable provenance artifacts.";
        } else if (liveVersion == null) {
            baseSummary = "A published version exists, but no live apply has fixed the source of truth for runtime and internal connector outputs yet.";
        } else if (!latestPublishedVersion.getId().equals(liveVersion.getId())) {
            if (latestRelease != null
                && latestPublishedVersion.getId().equals(latestRelease.getDeploymentVersionId())
                && "FAILED".equalsIgnoreCase(latestRelease.getStatus())) {
                baseSummary = "Live deployment provenance remains at " + liveVersion.getVersionLabel()
                    + " because the latest apply of " + latestPublishedVersion.getVersionLabel() + " failed before completion.";
            } else {
                baseSummary = "Live deployment provenance points at " + liveVersion.getVersionLabel()
                    + " while the latest published source of truth is " + latestPublishedVersion.getVersionLabel() + ".";
            }
        } else if (source.overrideActive()) {
            baseSummary = "Live deployment provenance is aligned and currently uses repository or branch overrides from the deployment workspace.";
        } else if (latestRelease != null && !"APPLIED_VERIFIED".equalsIgnoreCase(latestRelease.getStatus())) {
            baseSummary = "Live deployment provenance is aligned, but the latest release status is "
                + latestRelease.getStatus().toLowerCase(Locale.ROOT).replace('_', ' ') + ".";
        } else {
            baseSummary = "Template, source branch, published artifact bundle, and live deployment provenance are aligned.";
        }
        if (managedVector != null && managedVector.managedRequested()) {
            baseSummary += " " + managedVector.summaryMessage();
        }
        if (tenantScopedVector != null && tenantScopedVector.sharedStorage()) {
            baseSummary += " " + tenantScopedVector.summaryMessage();
            if (tenantScopedVector.registry() != null
                && !"INFO".equalsIgnoreCase(tenantScopedVector.registry().status())) {
                baseSummary += " " + tenantScopedVector.registry().message();
            }
        }
        if (liveRailwayReadback == null || !liveRailwayReadback.available()) {
            return baseSummary;
        }
        if ("WARNING".equalsIgnoreCase(liveRailwayReadback.status())) {
            if (latestPublishedVersion != null
                && liveVersion != null
                && !latestPublishedVersion.getId().equals(liveVersion.getId())
                && latestRelease != null
                && latestPublishedVersion.getId().equals(latestRelease.getDeploymentVersionId())) {
                return baseSummary + " Railway live read-back shows partial rollout drift toward the newer published version that should be reconciled.";
            }
            return baseSummary + " Railway live read-back found provider drift that should be reconciled.";
        }
        if ("BLOCKED".equalsIgnoreCase(liveRailwayReadback.status())) {
            return baseSummary + " Railway live read-back is currently unavailable.";
        }
        return baseSummary + " Railway live provider state matches the platform-managed deployment plan.";
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read deployment source-of-truth provider config.", ex);
        }
    }
}
