package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSourcePrerequisiteEvidenceTest {

    @Test
    void prerequisiteOrderAndFingerprintRemainStableAcrossPersistenceRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        AdmissionJson json = new AdmissionJson(mapper);
        Set<Prerequisite> unordered = new HashSet<>(Arrays.asList(Prerequisite.values()));
        var scope = new CustomerSourcePrerequisiteEvidence.Scope(
            "customer", "tenant", "deployment", "target", "release", TargetIntent.CUSTOMER_STAGING,
            "offer", "offer-fingerprint", 2, "reservation", "generation", "manifest-fingerprint",
            "binding-fingerprint", "version", "version-hash", "profile-fingerprint", "storage-fingerprint",
            1, "credential-fingerprint", "runtime-fingerprint");
        var evidence = new CustomerSourcePrerequisiteEvidence(
            "evidence", scope, "placement", unordered, "connector-proof",
            Instant.parse("2026-10-15T12:00:00Z"), Instant.parse("2026-10-15T12:05:00Z"));

        String persisted = json.write(evidence);
        var reloaded = json.read(persisted, CustomerSourcePrerequisiteEvidence.class);
        List<String> expectedOrder = Arrays.stream(Prerequisite.values()).map(Enum::name).toList();
        List<String> persistedOrder = StreamSupport.stream(
            mapper.readTree(persisted).path("passed").spliterator(), false).map(node -> node.asText()).toList();

        assertThat(evidence.passed()).containsExactly(Prerequisite.values());
        assertThat(persistedOrder).containsExactlyElementsOf(expectedOrder);
        assertThat(json.fingerprint(reloaded)).isEqualTo(json.fingerprint(evidence));
    }
}
