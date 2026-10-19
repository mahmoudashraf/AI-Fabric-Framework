package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactRole;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;

/** The promoted artifact's reviewed contract, not a template label, grants lifecycle authority. */
@Service
public class ManagedDependencyArtifactPolicy {
    public static final String SCHEMA = "loomai-managed-dependency-artifact/v1";
    public static final String CONTRACT_SCHEMA = "loomai-managed-dependency-contract/v1";
    private static final Set<String> FIELDS = Set.of("schemaVersion", "purpose", "lifecycleOwnership",
        "persistence", "initializationContract", "serviceContract");
    private static final Set<String> PURPOSES = Set.of("AI_OPERATIONAL_SERVICE", "AI_DERIVED_EVIDENCE",
        "CUSTOMER_BUSINESS_SYSTEM", "EXTERNAL_PROVIDER_FIXTURE");
    private static final Set<String> PERSISTENCE = Set.of("NONE", "AI_OPERATIONAL_STATE",
        "DERIVED_EVIDENCE", "CUSTOMER_BUSINESS_DATA");
    private final DeploymentSourceArtifactRepository artifacts;
    private final DeploymentSourceCapabilityManifestService manifests;

    public ManagedDependencyArtifactPolicy(DeploymentSourceArtifactRepository artifacts,
                                           DeploymentSourceCapabilityManifestService manifests) {
        this.artifacts = artifacts;
        this.manifests = manifests;
    }

    public void requireAllowed(ManagedDependencySpec spec) {
        for (var service : spec.services()) {
            var artifact = artifacts.findById(service.sourceArtifactId())
                .orElseThrow(() -> failure("DEPENDENCY_ARTIFACT_MISSING"));
            requireAllowed(service, artifact);
        }
    }

    public void requireAllowed(ManagedDependencySpec.Service service, DeploymentSourceArtifactEntity artifact) {
        if (!DeploymentSourceArtifactRole.MANAGED_DEPENDENCY.name().equals(artifact.getArtifactRole())
            || !"DOCKER_IMAGE".equals(artifact.getArtifactType()) || artifact.getPromotedAt() == null
            || artifact.getPromotionChannel() == null || artifact.getPromotionChannel().isBlank()
            || !service.image().equals(artifact.getImageRepository() + "@" + artifact.getImageDigest())
            || !service.sourceCommit().equals(artifact.getGitCommitSha())) {
            throw failure("DEPENDENCY_ARTIFACT_PROVENANCE_INVALID");
        }
        JsonNode contract = requireCustomerArtifact(artifact);
        if (!service.contract().name().equals(contract.path("serviceContract").asText())
            || !service.initializationContract().equals(contract.path("initializationContract").asText())) {
            throw failure("DEPENDENCY_ARTIFACT_CONTRACT_MISMATCH");
        }
        if ((service.database() != null || !service.volumes().isEmpty())
            && "NONE".equals(contract.path("persistence").asText())) {
            throw failure("DEPENDENCY_ARTIFACT_PERSISTENCE_UNPROVEN");
        }
    }

    public JsonNode requireCustomerArtifact(DeploymentSourceArtifactEntity artifact) {
        if (!DeploymentSourceArtifactRole.MANAGED_DEPENDENCY.name().equals(artifact.getArtifactRole())
            || !"DOCKER_IMAGE".equals(artifact.getArtifactType()) || artifact.getPromotedAt() == null
            || artifact.getPromotionChannel() == null || artifact.getPromotionChannel().isBlank()
            || artifact.getImageRepository() == null || artifact.getImageRepository().isBlank()
            || artifact.getImageDigest() == null || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}")
            || artifact.getGitCommitSha() == null || artifact.getGitCommitSha().isBlank()) {
            throw failure("DEPENDENCY_ARTIFACT_PROVENANCE_INVALID");
        }
        JsonNode manifest;
        try {
            manifest = manifests.read(artifact.getCapabilityManifestJson());
            var normalized = manifests.normalize(manifest);
            if (!SCHEMA.equals(manifest.path("schemaVersion").asText())
                || artifact.getCapabilityManifestHash() == null
                || !artifact.getCapabilityManifestHash().equals(normalized.hash())) {
                throw failure("DEPENDENCY_ARTIFACT_OWNERSHIP_UNPROVEN");
            }
        } catch (RuntimeException error) {
            throw failure("DEPENDENCY_ARTIFACT_OWNERSHIP_UNPROVEN");
        }
        JsonNode contract = manifest.path("managedDependencyContract");
        requireCustomerManagedContract(contract);
        return contract;
    }

    public static ObjectNode normalizeContract(JsonNode candidate, ObjectMapper mapper) {
        if (candidate == null || !candidate.isObject() || candidate.size() != FIELDS.size()) {
            throw failure("DEPENDENCY_ARTIFACT_CONTRACT_INVALID");
        }
        candidate.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) throw failure("DEPENDENCY_ARTIFACT_CONTRACT_INVALID");
        });
        for (String field : FIELDS) {
            if (!candidate.path(field).isTextual() || candidate.path(field).asText().isBlank()
                || !candidate.path(field).asText().equals(candidate.path(field).asText().trim())) {
                throw failure("DEPENDENCY_ARTIFACT_CONTRACT_INVALID");
            }
        }
        if (!CONTRACT_SCHEMA.equals(candidate.path("schemaVersion").asText())
            || !PURPOSES.contains(candidate.path("purpose").asText())
            || !Set.of("DEPLOYMENT_OWNED", "EXTERNAL_RETAINED").contains(candidate.path("lifecycleOwnership").asText())
            || !PERSISTENCE.contains(candidate.path("persistence").asText())
            || !Set.of("HTTP_SERVICE", "BASIC_INBOX_KEYS_V1").contains(candidate.path("serviceContract").asText())
            || !candidate.path("initializationContract").asText().matches("[A-Za-z0-9][A-Za-z0-9./_-]{0,127}")) {
            throw failure("DEPENDENCY_ARTIFACT_CONTRACT_INVALID");
        }
        ObjectNode result = mapper.createObjectNode();
        for (String field : java.util.List.of("schemaVersion", "purpose", "lifecycleOwnership",
            "persistence", "initializationContract", "serviceContract")) result.set(field, candidate.get(field));
        return result;
    }

    public static void requireCustomerManagedContract(JsonNode contract) {
        if (!Set.of("AI_OPERATIONAL_SERVICE", "AI_DERIVED_EVIDENCE").contains(contract.path("purpose").asText())
            || !"DEPLOYMENT_OWNED".equals(contract.path("lifecycleOwnership").asText())
            || "CUSTOMER_BUSINESS_DATA".equals(contract.path("persistence").asText())) {
            throw failure("CUSTOMER_DEPENDENCY_OWNERSHIP_FORBIDDEN");
        }
        if ("AI_DERIVED_EVIDENCE".equals(contract.path("purpose").asText())
            && "AI_OPERATIONAL_STATE".equals(contract.path("persistence").asText())) {
            throw failure("DEPENDENCY_ARTIFACT_PERSISTENCE_UNPROVEN");
        }
    }

    private static ResponseStatusException failure(String code) {
        return new ResponseStatusException(CONFLICT, code);
    }
}
