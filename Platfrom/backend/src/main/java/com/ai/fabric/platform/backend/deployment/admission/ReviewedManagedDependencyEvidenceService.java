package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecRepository;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Vector;

/** Resolves one reviewed dependency topology and proves that it fits the complete admission envelope. */
@Service
public class ReviewedManagedDependencyEvidenceService {
    public record Evidence(String templateVersionId, String fingerprint, String reference) { }

    private final ManagedDependencySpecRepository specs;
    private final ManagedDependencySpecValidator validator;
    private final CustomerOfferDeploymentRepository offerDeployments;
    private final CustomerOfferRepository offers;
    private final CustomerOfferCodec offerCodec;
    private final ObjectMapper mapper;

    public ReviewedManagedDependencyEvidenceService(ManagedDependencySpecRepository specs,
                                                     ManagedDependencySpecValidator validator,
                                                     CustomerOfferDeploymentRepository offerDeployments,
                                                     CustomerOfferRepository offers,
                                                     CustomerOfferCodec offerCodec,
                                                     ObjectMapper mapper) {
        this.specs = specs;
        this.validator = validator;
        this.offerDeployments = offerDeployments;
        this.offers = offers;
        this.offerCodec = offerCodec;
        this.mapper = mapper;
    }

    public Optional<Evidence> resolve(Definition definition, Vector envelope) {
        if (definition == null || definition.template() == null || envelope == null) {
            throw invalid("REVIEWED_DEPENDENCY_CONTEXT_REQUIRED");
        }
        String templateVersionId = definition.template().versionId();
        List<com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecEntity> matches =
            specs.findByTemplateVersionIdAndActiveTrue(templateVersionId);
        if (definition.topology().dependencies() == 0) {
            if (!matches.isEmpty()) throw invalid("UNEXPECTED_REVIEWED_DEPENDENCY_SPEC");
            return Optional.empty();
        }
        if (matches.size() != 1) throw invalid("REVIEWED_DEPENDENCY_SPEC_MISSING_OR_AMBIGUOUS");
        var reviewed = matches.getFirst();
        if (!templateVersionId.equals(reviewed.getTemplateVersionId())) {
            throw invalid("REVIEWED_DEPENDENCY_SPEC_CHANGED");
        }
        ManagedDependencySpec spec;
        try {
            spec = mapper.readValue(reviewed.getSpecJson(), ManagedDependencySpec.class);
            validator.validate(spec);
        } catch (RuntimeException ex) {
            throw invalid("REVIEWED_DEPENDENCY_SPEC_INVALID");
        } catch (Exception ex) {
            throw invalid("REVIEWED_DEPENDENCY_SPEC_INVALID");
        }
        String fingerprint = validator.fingerprint(spec);
        if (!fingerprint.equals(reviewed.getFingerprint())) {
            throw invalid("REVIEWED_DEPENDENCY_SPEC_FINGERPRINT_CHANGED");
        }
        requireEnvelope(envelope, spec);
        return Optional.of(new Evidence(templateVersionId, fingerprint,
            "managed-dependency-spec:" + fingerprint));
    }

    public Optional<Evidence> resolveForDeployment(String customerId, String tenantId, String deploymentId,
                                                   String topologyReference, Vector envelope) {
        var association = offerDeployments.findById(deploymentId).orElse(null);
        if (association == null) {
            if (topologyReference != null && topologyReference.startsWith("offer-topology:")) {
                throw invalid("REVIEWED_DEPENDENCY_OFFER_ASSOCIATION_REQUIRED");
            }
            return Optional.empty();
        }
        if (!association.getCustomerId().equals(customerId) || !association.getTenantId().equals(tenantId)) {
            throw invalid("REVIEWED_DEPENDENCY_OFFER_SCOPE_CHANGED");
        }
        var offer = offers.findById(association.getOfferId())
            .orElseThrow(() -> invalid("REVIEWED_DEPENDENCY_OFFER_REQUIRED"));
        String expectedTopologyReference = "offer-topology:" + offer.getId() + ":" + offer.getFingerprint();
        if (!association.getOfferFingerprint().equals(offer.getFingerprint())
            || !expectedTopologyReference.equals(topologyReference)) {
            throw invalid("REVIEWED_DEPENDENCY_OFFER_CHANGED");
        }
        try {
            return resolve(offerCodec.read(offer.getDefinitionJson(), Definition.class), envelope);
        } catch (AdmissionFailure ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalid("REVIEWED_DEPENDENCY_OFFER_INVALID");
        }
    }

    private void requireEnvelope(Vector envelope, ManagedDependencySpec spec) {
        ManagedDependencySpec.Capacity capacity;
        try {
            capacity = spec.totalCapacity();
        } catch (RuntimeException ex) {
            throw invalid("REVIEWED_DEPENDENCY_CAPACITY_INVALID");
        }
        long databases = spec.services().stream().filter(service -> service.database() != null).count();
        if (below(envelope, Dimension.CPU_MILLICORES, capacity.milliCpu())
            || below(envelope, Dimension.MEMORY_BYTES, bytes(capacity.memoryMiB()))
            || below(envelope, Dimension.DISK_BYTES, bytes(capacity.diskMiB()))
            || below(envelope, Dimension.DATABASES, databases)
            || below(envelope, Dimension.DEPENDENCIES, capacity.resources())) {
            throw invalid("REVIEWED_DEPENDENCY_CAPACITY_EXCEEDS_ADMISSION_ENVELOPE");
        }
    }

    private boolean below(Vector envelope, Dimension dimension, long required) {
        Long actual = envelope.amounts() == null ? null : envelope.amounts().get(dimension);
        return actual == null || actual < required;
    }

    private long bytes(long mebibytes) {
        try {
            return Math.multiplyExact(mebibytes, 1024L * 1024L);
        } catch (ArithmeticException ex) {
            throw invalid("REVIEWED_DEPENDENCY_CAPACITY_INVALID");
        }
    }

    private AdmissionFailure invalid(String code) {
        return new AdmissionFailure(HttpStatus.PRECONDITION_FAILED, code);
    }
}
