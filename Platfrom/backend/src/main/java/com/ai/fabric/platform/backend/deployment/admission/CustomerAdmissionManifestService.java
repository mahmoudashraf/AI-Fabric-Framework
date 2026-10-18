package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.ReviewedTopology;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.MilvusTokenRuntimeArtifactGate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Materializes the per-version admission manifest from reviewed server-owned inputs. */
@Service
public class CustomerAdmissionManifestService {
    private static final String ACTOR = "system:customer-admission-manifest";

    private final AdmissionStore store;
    private final AdmissionJson json;
    private final DeploymentService deployments;
    private final DeploymentDocumentBindingResolver documentBindings;
    private final ReviewedManagedDependencyEvidenceService dependencyEvidence;
    private final PlatformAuditService audit;

    @Autowired(required = false)
    private MilvusTokenRuntimeArtifactGate milvusTokenArtifacts;

    public CustomerAdmissionManifestService(AdmissionStore store, AdmissionJson json,
                                            DeploymentService deployments,
                                            DeploymentDocumentBindingResolver documentBindings,
                                            ReviewedManagedDependencyEvidenceService dependencyEvidence,
                                            PlatformAuditService audit) {
        this.store = store;
        this.json = json;
        this.deployments = deployments;
        this.documentBindings = documentBindings;
        this.dependencyEvidence = dependencyEvidence;
        this.audit = audit;
    }

    public Manifest prepare(DeploymentEntity deployment, DeploymentVersionEntity version, Authorization authorization) {
        if (deployment == null || version == null || authorization == null || authorization.context() == null
            || authorization.definition() == null || !deployment.getId().equals(version.getDeploymentId())
            || !deployment.getId().equals(authorization.context().deploymentId())
            || !deployment.getTenantId().equals(authorization.context().tenantId())
            || !deployment.getCustomerId().equals(authorization.context().customerId())
            || !"PUBLISHED".equalsIgnoreCase(version.getStatus())
            || !authorization.definition().topology().equals(authorization.context().topology())) {
            throw invalid("CUSTOMER_ADMISSION_MANIFEST_SCOPE_INVALID");
        }

        JsonNode provider = version.getProviderConfigJson() == null ? null
            : json.read(version.getProviderConfigJson(), JsonNode.class);
        if (MilvusTokenRuntimeArtifactGate.required(provider)) {
            if (milvusTokenArtifacts == null) throw invalid("MILVUS_TOKEN_ARTIFACT_GATE_REQUIRED");
            milvusTokenArtifacts.requireReviewedCandidates(version, provider,
                authorization.definition().artifacts().stream().map(pin -> pin.artifactId()).toList());
        }
        FleetPolicy policy = store.activePolicy();
        CustomerGrant grant = store.customerGrant(deployment.getCustomerId());
        String targetId = authorization.context().targetProfileId();
        if (!policy.targets().containsKey(targetId) || !grant.targetLimits().containsKey(targetId)) {
            throw invalid("CUSTOMER_ADMISSION_TARGET_NOT_REVIEWED");
        }

        ObjectNode documentBinding = documentBindings.snapshot(deployment, version, targetId);
        if (authorization.definition().admissionPrerequisites().contains(Prerequisite.STORAGE)
            && documentBinding.isEmpty()) {
            throw invalid("CUSTOMER_ADMISSION_STORAGE_BINDING_REQUIRED");
        }
        Vector demand = demand(authorization.context().topology());
        var dependency = dependencyEvidence.resolve(authorization.definition(), demand);
        var authority = deployments.prepareAuthorizedRuntimeAuthorityCandidate(deployment, version, targetId);

        String policyReference = "policy:" + policy.id();
        String versionReference = "deployment-version:" + version.getId() + ":" + version.getConfigHash();
        String storageReference = documentBinding.isEmpty()
            ? null
            : "document-binding:" + json.fingerprint(documentBinding);
        String identityReference = "binding:" + authority.fingerprint();
        String topologyReference = "offer-topology:" + authorization.context().offerId()
            + ":" + authorization.context().offerFingerprint();

        EnumMap<Prerequisite, String> customerReferences = new EnumMap<>(Prerequisite.class);
        for (Prerequisite prerequisite : authorization.definition().admissionPrerequisites()) {
            if (prerequisite == Prerequisite.CAPACITY) continue;
            String reference = switch (prerequisite) {
                case APPROVED_POLICY -> policyReference;
                case IMMUTABLE_ARTIFACTS -> versionReference;
                case STORAGE -> storageReference;
                case IDENTITY -> identityReference;
                case NOTIFICATIONS -> policy.lifecycle().noticeTransportReference();
                case OPERATING_POLICY -> policy.lifecycle().policyReference();
                case CAPACITY -> null;
            };
            if (reference == null || reference.isBlank()) {
                throw invalid("CUSTOMER_ADMISSION_PREREQUISITE_UNAVAILABLE");
            }
            customerReferences.put(prerequisite, reference);
        }

        Set<String> prerequisiteReferences = new LinkedHashSet<>(customerReferences.values());
        prerequisiteReferences.add(topologyReference);
        dependency.ifPresent(value -> prerequisiteReferences.add(value.reference()));
        prerequisiteReferences.add(policy.restoreEvidenceReference());
        prerequisiteReferences.add(policy.privacyEvidenceReference());
        prerequisiteReferences.add(policy.supportReference());
        prerequisiteReferences.add(grant.ownerContactEvidenceReference());
        prerequisiteReferences.add(grant.payerReference());

        Map<String, Object> identity = Map.of(
            "deploymentId", deployment.getId(),
            "versionId", version.getId(),
            "versionConfigHash", version.getConfigHash(),
            "targetProfileId", targetId,
            "bindingFingerprint", authority.fingerprint(),
            "topologyReference", topologyReference,
            "demand", demand,
            "prerequisiteReferences", prerequisiteReferences,
            "customerPrerequisiteReferences", customerReferences
        );
        String manifestId = "amf-" + json.fingerprint(identity).substring(0, 60);
        Manifest manifest = new Manifest(manifestId, deployment.getId(), version.getId(), version.getConfigHash(),
            targetId, authority.fingerprint(), topologyReference, demand, prerequisiteReferences, customerReferences);
        store.approveManifest(manifest, ACTOR, Instant.now());
        audit.record("CUSTOMER_ADMISSION_MANIFEST_MATERIALIZED", "DEPLOYMENT", deployment.getId(), Map.of(
            "manifestId", manifest.id(),
            "versionId", version.getId(),
            "targetProfileId", targetId,
            "offerId", authorization.context().offerId()
        ));
        return manifest;
    }

    private Vector demand(ReviewedTopology topology) {
        if (topology == null) throw invalid("CUSTOMER_ADMISSION_TOPOLOGY_REQUIRED");
        EnumMap<Dimension, Long> amounts = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) amounts.put(dimension, 0L);
        amounts.put(Dimension.ENVIRONMENTS, 1L);
        amounts.put(Dimension.REPLICAS, (long) topology.replicas());
        amounts.put(Dimension.CPU_MILLICORES, topology.cpuMillis());
        amounts.put(Dimension.MEMORY_BYTES, bytes(topology.memoryMiB()));
        amounts.put(Dimension.DISK_BYTES, bytes(topology.diskMiB()));
        amounts.put(Dimension.DATABASES, (long) topology.databases());
        amounts.put(Dimension.INDEXES, (long) topology.indexes());
        amounts.put(Dimension.DEPENDENCIES, (long) topology.dependencies());
        amounts.put(Dimension.CONCURRENT_APPLIES, (long) topology.concurrentApplies());
        return new Vector(amounts);
    }

    private long bytes(long mebibytes) {
        try { return Math.multiplyExact(mebibytes, 1024L * 1024L); }
        catch (ArithmeticException ex) { throw invalid("CUSTOMER_ADMISSION_TOPOLOGY_OVERFLOW"); }
    }

    private AdmissionFailure invalid(String code) {
        return new AdmissionFailure(HttpStatus.PRECONDITION_FAILED, code);
    }
}
