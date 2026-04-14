package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class DeploymentArtifactService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentArtifactService.class);
    private static final String SIGNING_SECRET_NAME = "PLATFORM_ARTIFACT_SIGNING_KEY";
    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";
    private static final String EXPIRES_PARAM = "expires";
    private static final String SIGNATURE_PARAM = "sig";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository versionRepository;
    private final PlatformDeliveryProperties deliveryProperties;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final DeploymentAccessService deploymentAccessService;

    public DeploymentArtifactService(DeploymentRepository deploymentRepository,
                                     DeploymentVersionRepository versionRepository,
                                     PlatformDeliveryProperties deliveryProperties,
                                     PlatformSecretService platformSecretService,
                                     PlatformAuditService platformAuditService,
                                     DeploymentAccessService deploymentAccessService) {
        this.deploymentRepository = deploymentRepository;
        this.versionRepository = versionRepository;
        this.deliveryProperties = deliveryProperties;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.deploymentAccessService = deploymentAccessService;
    }

    public DeploymentArtifactBundleSummary getBundleSummary(String deploymentId, String versionId) {
        DeploymentVersionEntity version = getVersion(deploymentId, versionId);
        deploymentAccessService.requireDeploymentAccess(
            deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId))
        );
        return toBundleSummary(version);
    }

    public DeploymentArtifactBundleSummary toBundleSummary(DeploymentVersionEntity version) {
        return new DeploymentArtifactBundleSummary(
            version.getDeploymentId(),
            version.getId(),
            version.getVersionLabel(),
            version.getConfigHash(),
            artifactUrl(version, "ai-actions.yml"),
            artifactUrl(version, "ai-entity-config.yml"),
            artifactUrl(version, "actions-routing.yml"),
            artifactUrl(version, "ai-prompt-config.json"),
            artifactUrl(version, "ai-knowledge-source-config.json"),
            artifactUrl(version, "ai-shell-config.json"),
            artifactUrl(version, "ai-automation-config.json"),
            artifactUrl(version, "deployment-manifest.json")
        );
    }

    public String readActionsArtifact(String deploymentId,
                                      String versionId,
                                      Long expires,
                                      String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "ai-actions.yml", expires, signature)
            .getActionsArtifactYaml();
    }

    public String readEntityArtifact(String deploymentId,
                                     String versionId,
                                     Long expires,
                                     String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "ai-entity-config.yml", expires, signature)
            .getEntityArtifactYaml();
    }

    public String readRoutingArtifact(String deploymentId,
                                      String versionId,
                                      Long expires,
                                      String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "actions-routing.yml", expires, signature)
            .getRoutingArtifactYaml();
    }

    public String readPromptArtifact(String deploymentId,
                                     String versionId,
                                     Long expires,
                                     String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "ai-prompt-config.json", expires, signature)
            .getPromptConfigJson();
    }

    public String readKnowledgeSourceArtifact(String deploymentId,
                                              String versionId,
                                              Long expires,
                                              String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "ai-knowledge-source-config.json", expires, signature)
            .getKnowledgeSourceConfigJson();
    }

    public String readShellArtifact(String deploymentId,
                                    String versionId,
                                    Long expires,
                                    String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "ai-shell-config.json", expires, signature)
            .getShellConfigJson();
    }

    public String readAutomationArtifact(String deploymentId,
                                         String versionId,
                                         Long expires,
                                         String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "ai-automation-config.json", expires, signature)
            .getAutomationConfigJson();
    }

    public String readManifestArtifact(String deploymentId,
                                       String versionId,
                                       Long expires,
                                       String signature) {
        return authorizeArtifactAccess(deploymentId, versionId, "deployment-manifest.json", expires, signature)
            .getManifestJson();
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

    private DeploymentVersionEntity authorizeArtifactAccess(String deploymentId,
                                                            String versionId,
                                                            String artifactName,
                                                            Long expires,
                                                            String signature) {
        DeploymentVersionEntity version = getVersion(deploymentId, versionId);
        if (PlatformSecurityContext.isAuthenticated()) {
            deploymentAccessService.requireDeploymentAccess(
                deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId))
            );
            return version;
        }
        if (!deliveryProperties.signedArtifactsEnabled()) {
            return version;
        }

        if (expires == null || !StringUtils.hasText(signature)) {
            rejectArtifactAccess(deploymentId, versionId, artifactName, "Missing signed artifact access parameters.");
        }
        if (Instant.ofEpochSecond(expires).isBefore(Instant.now())) {
            rejectArtifactAccess(deploymentId, versionId, artifactName, "Artifact access URL has expired.");
        }

        String signingKey = requireSigningKeyForAccess(deploymentId, versionId, artifactName);
        String expectedSignature = sign(
            deploymentId,
            versionId,
            artifactName,
            expires,
            signingKey
        );
        if (!MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            signature.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            rejectArtifactAccess(deploymentId, versionId, artifactName, "Artifact access signature is invalid.");
        }
        return version;
    }

    private String artifactUrl(DeploymentVersionEntity version, String artifactName) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(deliveryProperties.publicBaseUrl())
            .path("/api/deployments/{deploymentId}/versions/{versionId}/artifacts/{artifactName}");
        if (deliveryProperties.signedArtifactsEnabled()) {
            long expires = signedExpiry(version);
            String signingKey = requireSigningKey();
            builder.queryParam(EXPIRES_PARAM, expires);
            builder.queryParam(
                SIGNATURE_PARAM,
                sign(version.getDeploymentId(), version.getId(), artifactName, expires, signingKey)
            );
        }
        return builder.buildAndExpand(version.getDeploymentId(), version.getId(), artifactName).toUriString();
    }

    private long signedExpiry(DeploymentVersionEntity version) {
        Instant publishedAt = version.getPublishedAt() == null ? Instant.EPOCH : version.getPublishedAt();
        return publishedAt.plus(deliveryProperties.artifactUrlTtl()).getEpochSecond();
    }

    private String signingKey() {
        return platformSecretService.resolveSecret(SIGNING_SECRET_NAME);
    }

    private String requireSigningKey() {
        String signingKey = signingKey();
        if (!StringUtils.hasText(signingKey)) {
            throw new IllegalStateException("PLATFORM_ARTIFACT_SIGNING_KEY is required when signed artifact delivery is enabled.");
        }
        return signingKey;
    }

    private String requireSigningKeyForAccess(String deploymentId,
                                              String versionId,
                                              String artifactName) {
        String signingKey = signingKey();
        if (!StringUtils.hasText(signingKey)) {
            rejectArtifactAccess(deploymentId, versionId, artifactName, "Artifact signing key is not configured.");
        }
        return signingKey;
    }

    private String sign(String deploymentId,
                        String versionId,
                        String artifactName,
                        long expires,
                        String signingKey) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), SIGNATURE_ALGORITHM));
            byte[] digest = mac.doFinal(canonicalPayload(deploymentId, versionId, artifactName, expires)
                .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign deployment artifact URL.", ex);
        }
    }

    private String canonicalPayload(String deploymentId,
                                    String versionId,
                                    String artifactName,
                                    long expires) {
        return deploymentId + ":" + versionId + ":" + artifactName + ":" + expires;
    }

    private void rejectArtifactAccess(String deploymentId,
                                      String versionId,
                                      String artifactName,
                                      String reason) {
        log.warn(
            "Rejected deployment artifact access: deploymentId={}, versionId={}, artifactName={}, reason={}",
            deploymentId,
            versionId,
            artifactName,
            reason
        );
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("deploymentId", deploymentId);
        details.put("versionId", versionId);
        details.put("artifactName", artifactName);
        details.put("reason", reason);
        platformAuditService.record(
            "ARTIFACT_ACCESS_REJECTED",
            "DEPLOYMENT_ARTIFACT",
            versionId,
            details
        );
        throw new ResponseStatusException(UNAUTHORIZED, reason);
    }
}
