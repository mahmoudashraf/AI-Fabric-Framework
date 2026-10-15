package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyFixtures;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecRepository;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewedManagedDependencyEvidenceServiceTest {
    private final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
    private final ReviewedManagedDependencyEvidenceService service =
        new ReviewedManagedDependencyEvidenceService(specs, validator, associations, offers, codec, mapper);
    private Definition definition;
    private ManagedDependencySpecEntity reviewed;

    @BeforeEach
    void setUp() throws Exception {
        var topology = ManagedDependencyFixtures.topology();
        reviewed = new ManagedDependencySpecEntity();
        reviewed.setId("spec");
        reviewed.setTemplateVersionId("template-version");
        reviewed.setSpecJson(mapper.writeValueAsString(topology));
        reviewed.setFingerprint(validator.fingerprint(topology));
        reviewed.setReviewedBy("operator");
        reviewed.setReviewedAt(Instant.parse("2026-10-15T12:00:00Z"));
        reviewed.setReviewReference("review");
        reviewed.setActive(true);
        when(specs.findByTemplateVersionIdAndActiveTrue("template-version")).thenReturn(List.of(reviewed));

        ReviewedTopology topologyEnvelope = new ReviewedTopology(1, 3_750, 5_888, 16_384, 2, 2, 4, 1);
        definition = new Definition("1", "Reviewed offer", "template",
            new PluginPin("template", "template-version", "manifest"), List.of(), List.of(),
            List.of(new TargetPermission("target", TargetIntent.CUSTOMER_STAGING, "profile")),
            EnumSet.of(Operation.APPLY), List.of(), List.of(), Set.of(DocumentAudience.PUBLIC),
            EnumSet.allOf(Prerequisite.class),
            new ReviewedLimits("measurement", 1, 1, 4_000, 6_144, 16_384, 2, 2, 4,
                1, 24, 24, 24), topologyEnvelope,
            "approved", "operating", "identity", "storage");
    }

    @Test
    void resolvesExactOfferTemplateSpecAndRevalidatesItForDeployment() {
        var envelope = envelope(3_750, 5_888, 16_384, 2, 4);
        var association = new CustomerOfferDeploymentEntity("deployment", "customer", "tenant", "offer",
            "offer-fingerprint", 1, "operation", Instant.parse("2026-10-15T12:00:00Z"));
        var offer = new CustomerOfferEntity("offer", codec.write(definition), "offer-fingerprint", "review",
            "operator", Instant.parse("2026-10-15T12:00:00Z"));
        when(associations.findById("deployment")).thenReturn(Optional.of(association));
        when(offers.findById("offer")).thenReturn(Optional.of(offer));

        var evidence = service.resolveForDeployment("customer", "tenant", "deployment",
            "offer-topology:offer:offer-fingerprint", envelope).orElseThrow();

        assertThat(evidence.templateVersionId()).isEqualTo("template-version");
        assertThat(evidence.reference()).isEqualTo("managed-dependency-spec:" + reviewed.getFingerprint());
    }

    @Test
    void fingerprintDriftAndUndersizedEnvelopeFailClosed() {
        reviewed.setFingerprint("f".repeat(64));
        assertThatThrownBy(() -> service.resolve(definition, envelope(3_750, 5_888, 16_384, 2, 4)))
            .hasMessageContaining("REVIEWED_DEPENDENCY_SPEC_FINGERPRINT_CHANGED");

        reviewed.setFingerprint(validator.fingerprint(ManagedDependencyFixtures.topology()));
        assertThatThrownBy(() -> service.resolve(definition, envelope(500, 1_000, 1_000, 0, 1)))
            .hasMessageContaining("REVIEWED_DEPENDENCY_CAPACITY_EXCEEDS_ADMISSION_ENVELOPE");
    }

    @Test
    void unassociatedStaffAdmissionIsNotTreatedAsCustomerOfferEvidence() {
        when(associations.findById("staff-deployment")).thenReturn(Optional.empty());

        assertThat(service.resolveForDeployment("customer", "tenant", "staff-deployment",
            "staff-reviewed-topology", envelope(3_750, 5_888, 16_384, 2, 4))).isEmpty();
        assertThatThrownBy(() -> service.resolveForDeployment("customer", "tenant", "staff-deployment",
            "offer-topology:offer:offer-fingerprint", envelope(3_750, 5_888, 16_384, 2, 4)))
            .hasMessageContaining("REVIEWED_DEPENDENCY_OFFER_ASSOCIATION_REQUIRED");
    }

    private AdmissionContracts.Vector envelope(long cpu, long memoryMiB, long diskMiB,
                                                long databases, long dependencies) {
        EnumMap<Dimension, Long> values = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) values.put(dimension, 0L);
        values.put(Dimension.CPU_MILLICORES, cpu);
        values.put(Dimension.MEMORY_BYTES, memoryMiB * 1024L * 1024L);
        values.put(Dimension.DISK_BYTES, diskMiB * 1024L * 1024L);
        values.put(Dimension.DATABASES, databases);
        values.put(Dimension.DEPENDENCIES, dependencies);
        return new AdmissionContracts.Vector(values);
    }
}
