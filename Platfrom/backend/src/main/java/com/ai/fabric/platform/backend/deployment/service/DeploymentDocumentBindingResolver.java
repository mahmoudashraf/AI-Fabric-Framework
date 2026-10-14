package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DeploymentDocumentBindingResolver {
    public static final String DOCUMENT_STORAGE_SLOT = "slot:document-storage";
    private final DeploymentProviderResourceHandleRepository handles;
    private final PlatformSecretRepository secrets;
    private final ObjectMapper mapper;

    public DeploymentDocumentBindingResolver(DeploymentProviderResourceHandleRepository handles,
                                             PlatformSecretRepository secrets, ObjectMapper mapper) {
        this.handles = handles;
        this.secrets = secrets;
        this.mapper = mapper;
    }

    public DeploymentProviderResourceHandleEntity resolve(String deploymentId, String targetProfileId, String reference) {
        if (!StringUtils.hasText(targetProfileId) || !StringUtils.hasText(reference)) {
            throw conflict("Document storage requires an explicit target and reviewed binding reference.");
        }
        DeploymentProviderResourceHandleEntity handle;
        if (DOCUMENT_STORAGE_SLOT.equals(reference)) {
            var matches = handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deploymentId, targetProfileId)
                .stream().filter(value -> DeploymentDocumentStorageBindingService.RESOURCE_KIND.equals(value.getResourceKind()))
                .filter(value -> "CONFIGURED".equals(value.getStatus())).toList();
            if (matches.size() != 1) throw conflict("Document storage slot must resolve to exactly one configured target binding.");
            handle = matches.getFirst();
        } else {
            if (reference.startsWith("slot:")) throw conflict("Document storage binding slot is not supported.");
            handle = handles.findById(reference).orElseThrow(() -> conflict("Document storage binding is unavailable."));
        }
        if (!deploymentId.equals(handle.getDeploymentId()) || !targetProfileId.equals(handle.getTargetProfileId())
            || !DeploymentDocumentStorageBindingService.RESOURCE_KIND.equals(handle.getResourceKind())
            || !"CONFIGURED".equals(handle.getStatus())) {
            throw conflict("Document storage binding ownership, target or lifecycle is inconsistent.");
        }
        return handle;
    }

    public ObjectNode snapshot(DeploymentEntity deployment, DeploymentVersionEntity version, String targetProfileId) {
        if (!deployment.getId().equals(version.getDeploymentId())) throw conflict("Document binding version belongs to another deployment.");
        JsonNode datasets = read(version.getMarketplaceDatasetConfigJson()).path("datasets");
        ObjectNode snapshot = mapper.createObjectNode();
        for (JsonNode dataset : datasets) {
            if (!"EXTERNAL_DOCUMENT_STORAGE".equals(dataset.path("ingestionMode").asText())) continue;
            String reference = dataset.path("sourceConnector").path("bindingRef").asText();
            var handle = resolve(deployment.getId(), targetProfileId, reference);
            JsonNode metadata = read(handle.getMetadataJson());
            String connector = dataset.path("sourceConnector").path("connectorType").asText();
            if (!connector.equals(metadata.path("connectorType").asText())) throw conflict("Document connector differs from the reviewed composition.");
            if (!snapshot.isEmpty() && !handle.getId().equals(snapshot.path("resourceHandleId").asText())) {
                throw conflict("One runtime must resolve its document datasets to the same storage binding.");
            }
            snapshot.put("schema", "DOCUMENT_STORAGE_BINDING_V1");
            snapshot.put("deploymentId", deployment.getId());
            snapshot.put("versionId", version.getId());
            snapshot.put("targetProfileId", targetProfileId);
            snapshot.put("resourceHandleId", handle.getId());
            snapshot.set("safeMetadata", metadata);
            ObjectNode revisions = mapper.createObjectNode();
            String key = DeploymentDocumentStorageBindingService.secretKey(deployment.getId(), targetProfileId);
            List<String> required = DeploymentDocumentStorageBindingService.S3_CONNECTOR.equals(connector)
                ? List.of("ENDPOINT", "REGION", "BUCKET", "ACCESS_KEY", "SECRET_KEY") : List.of("MOUNTED_ROOT");
            for (String suffix : List.of("ENDPOINT", "REGION", "BUCKET", "PREFIX", "ACCESS_KEY", "SECRET_KEY", "SESSION_TOKEN", "MOUNTED_ROOT")) {
                var secret = secrets.findById(key + "_" + suffix);
                if (required.contains(suffix) && (secret.isEmpty() || !StringUtils.hasText(secret.get().getSecretValue()))) {
                    throw conflict("Document storage credentials are incomplete for the selected target.");
                }
                if (secret.isPresent()) {
                    if (secret.get().getUpdatedAt() == null) throw conflict("Document storage credential revision evidence is unavailable.");
                    revisions.put(suffix, secret.get().getUpdatedAt().toString());
                }
            }
            snapshot.set("secretRevisions", revisions);
        }
        return snapshot;
    }

    public void freeze(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        ObjectNode snapshot = snapshot(deployment, version, release.getTargetProfileId());
        if (snapshot.isEmpty()) return;
        release.setDocumentBindingSnapshotJson(snapshot.toString());
        release.setDocumentBindingFingerprint(fingerprint(snapshot));
    }

    public boolean required(DeploymentVersionEntity version) {
        for (JsonNode dataset : read(version.getMarketplaceDatasetConfigJson()).path("datasets")) {
            if ("EXTERNAL_DOCUMENT_STORAGE".equals(dataset.path("ingestionMode").asText())) return true;
        }
        return false;
    }

    public void requireUnchanged(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        ObjectNode current = snapshot(deployment, version, release.getTargetProfileId());
        if (current.isEmpty() && !StringUtils.hasText(release.getDocumentBindingFingerprint())) return;
        if (!StringUtils.hasText(release.getDocumentBindingFingerprint())
            || !release.getDocumentBindingFingerprint().equals(fingerprint(current))
            || !release.getDocumentBindingFingerprint().equals(fingerprint(read(release.getDocumentBindingSnapshotJson())))) {
            throw conflict("Release document storage binding changed or lacks frozen evidence; apply and verify a new release.");
        }
    }

    String fingerprint(JsonNode node) {
        return DeploymentEvidenceFingerprint.hash(mapper, node);
    }

    private JsonNode read(String json) {
        try {
            JsonNode value = mapper.readTree(StringUtils.hasText(json) ? json : "{}");
            if (value == null || !value.isObject()) throw new IllegalArgumentException();
            return value;
        } catch (Exception error) {
            throw conflict("Document binding evidence must be structured JSON.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
