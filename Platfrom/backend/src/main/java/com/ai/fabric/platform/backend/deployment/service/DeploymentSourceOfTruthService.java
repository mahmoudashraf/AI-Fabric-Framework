package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentConfigReferenceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentConfigTemplateSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceOfTruthGeneratedSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceOfTruthSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class DeploymentSourceOfTruthService {

    private final DeploymentArtifactService deploymentArtifactService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;

    public DeploymentSourceOfTruthService(DeploymentArtifactService deploymentArtifactService,
                                          DeploymentSourceResolver deploymentSourceResolver,
                                          RailwayProvisioningPlanService railwayProvisioningPlanService) {
        this.deploymentArtifactService = deploymentArtifactService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
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
            template.vectorStrategy(),
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

        DeploymentSourceOfTruthGeneratedSummary generated = new DeploymentSourceOfTruthGeneratedSummary(
            plan == null ? null : plan.mode(),
            plan == null ? null : plan.artifactStrategy(),
            plan == null ? null : plan.projectName(),
            plan == null ? source.repository() : plan.repository(),
            plan == null ? source.branch() : plan.branch(),
            plan == null ? null : plan.services().runtime().serviceName(),
            plan == null ? null : plan.services().runtime().dockerfilePath(),
            deployment.getRuntimeBaseUrl(),
            plan == null ? null : plan.services().restConnector().serviceName(),
            plan == null ? null : plan.services().restConnector().dockerfilePath(),
            deployment.getConnectorBaseUrl()
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
            generated,
            summaryMessage(source, latestPublishedVersion, liveVersion, latestRelease)
        );
    }

    private String summaryMessage(DeploymentSourceSummary source,
                                  DeploymentVersionEntity latestPublishedVersion,
                                  DeploymentVersionEntity liveVersion,
                                  DeploymentReleaseEntity latestRelease) {
        if (latestPublishedVersion == null) {
            return "The deployment still runs from draft-only inputs. Publish a version to create immutable provenance artifacts.";
        }
        if (liveVersion == null) {
            return "A published version exists, but no live apply has fixed the source of truth for runtime and connector outputs yet.";
        }
        if (!latestPublishedVersion.getId().equals(liveVersion.getId())) {
            return "Live deployment provenance points at " + liveVersion.getVersionLabel()
                + " while the latest published source of truth is " + latestPublishedVersion.getVersionLabel() + ".";
        }
        if (source.overrideActive()) {
            return "Live deployment provenance is aligned and currently uses repository or branch overrides from the deployment workspace.";
        }
        if (latestRelease != null && !"APPLIED_VERIFIED".equalsIgnoreCase(latestRelease.getStatus())) {
            return "Live deployment provenance is aligned, but the latest release status is "
                + latestRelease.getStatus().toLowerCase(Locale.ROOT).replace('_', ' ') + ".";
        }
        return "Template, source branch, published artifact bundle, and live deployment provenance are aligned.";
    }
}
