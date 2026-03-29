package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentArtifactService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository versionRepository;
    private final PlatformDeliveryProperties deliveryProperties;

    public DeploymentArtifactService(DeploymentRepository deploymentRepository,
                                     DeploymentVersionRepository versionRepository,
                                     PlatformDeliveryProperties deliveryProperties) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.deliveryProperties = deliveryProperties;
    }

    public DeploymentArtifactBundleSummary getBundleSummary(String deploymentId, String versionId) {
        DeploymentVersionEntity version = getVersion(deploymentId, versionId);
        return toBundleSummary(version);
    }

    public DeploymentArtifactBundleSummary toBundleSummary(DeploymentVersionEntity version) {
        return new DeploymentArtifactBundleSummary(
            version.getDeploymentId(),
            version.getId(),
            version.getVersionLabel(),
            version.getConfigHash(),
            artifactUrl(version.getDeploymentId(), version.getId(), "ai-actions.yml"),
            artifactUrl(version.getDeploymentId(), version.getId(), "ai-entity-config.yml"),
            artifactUrl(version.getDeploymentId(), version.getId(), "actions-routing.yml"),
            artifactUrl(version.getDeploymentId(), version.getId(), "deployment-manifest.json")
        );
    }

    public String readActionsArtifact(String deploymentId, String versionId) {
        return getVersion(deploymentId, versionId).getActionsArtifactYaml();
    }

    public String readEntityArtifact(String deploymentId, String versionId) {
        return getVersion(deploymentId, versionId).getEntityArtifactYaml();
    }

    public String readRoutingArtifact(String deploymentId, String versionId) {
        return getVersion(deploymentId, versionId).getRoutingArtifactYaml();
    }

    public String readManifestArtifact(String deploymentId, String versionId) {
        return getVersion(deploymentId, versionId).getManifestJson();
    }

    public DeploymentVersionEntity getVersion(String deploymentId, String versionId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        DeploymentVersionEntity version = versionRepository.findById(versionId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Version not found: " + versionId));
        if (!deployment.getId().equals(version.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Version does not belong to deployment: " + deploymentId);
        }
        return version;
    }

    private String artifactUrl(String deploymentId, String versionId, String artifactName) {
        return UriComponentsBuilder.fromUriString(deliveryProperties.publicBaseUrl())
            .path("/api/deployments/{deploymentId}/versions/{versionId}/artifacts/{artifactName}")
            .buildAndExpand(deploymentId, versionId, artifactName)
            .toUriString();
    }
}
