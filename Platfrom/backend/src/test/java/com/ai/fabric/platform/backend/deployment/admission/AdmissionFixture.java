package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

abstract class AdmissionFixture {
    static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");
    static final String TARGET = "reviewed-staging";
    static final Placement STAGING = new Placement("zf25hgk9694bt7q0zwb98ado", "xjhfu65nacrr30xax5cp0ry7", "46.224.145.148");
    ObjectMapper mapper;
    AdmissionJson json;
    DriverManagerDataSource dataSource;
    JdbcTemplate jdbc;
    AdmissionStore store;

    @BeforeEach
    void database() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper);
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:admission_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=10000", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/test-migration/V120_13__durable_capacity_admission_and_staging_expiry_test.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/test-migration/V120_22_2__admission_allocation_authorizations_test.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource); store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }
    static Vector vector(long value) {
        EnumMap<Dimension, Long> values = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) values.put(dimension, value);
        return new Vector(values);
    }
    static Vector with(Vector vector, Dimension dimension, long value) {
        EnumMap<Dimension, Long> values = new EnumMap<>(vector.amounts()); values.put(dimension, value); return new Vector(values);
    }
    FleetPolicy policy(long capacity) {
        // Deliberately synthetic unit-test values, not a production resource offer.
        Budget budget = new Budget(vector(capacity), vector(0), vector(0), "measurement-proof", NOW.minusSeconds(1), NOW.plusSeconds(10000));
        return new FleetPolicy("policy1", budget, Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, "profile-fingerprint", budget)),
            new LifecyclePolicy(1000, 100, 100, 2, 3000, 200, 10, 10, 20, 2, 60, 3600, "published-lifecycle", "approved-mail"),
            "restore-proof", "privacy-proof", "support-owner");
    }
    CustomerGrant grant(long capacity) {
        return new CustomerGrant("customer", vector(capacity), Map.of(TARGET, vector(capacity)), NOW.plusSeconds(5000), "verified-owner", "payer", "keep-entitlement");
    }
    Manifest manifest(String id, String deployment) {
        Map<com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite, String> customerReferences = Map.of(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite.APPROVED_POLICY, "approved-policy",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite.IMMUTABLE_ARTIFACTS, "immutable-artifacts",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite.STORAGE, "storage",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite.IDENTITY, "identity",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite.NOTIFICATIONS, "notifications",
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite.OPERATING_POLICY, "operating-policy"
        );
        return new Manifest(id, deployment, "version", "config-hash", TARGET, "binding-fingerprint", "reviewed-topology-with-db-volume-index-inbox",
            vector(1), Set.of("approved-policy", "immutable-artifacts", "storage", "identity", "notifications", "operating-policy", "dependency-topology"),
            customerReferences);
    }
    void approve(long capacity) {
        store.approvePolicy(policy(capacity), "operator", NOW); store.approveGrant(grant(capacity), "operator", NOW);
    }
    AdmissionContext context(String manifest) { return store.context("customer", "tenant", store.reviewed("MANIFEST", manifest, Manifest.class).deploymentId(), manifest); }
    PrerequisiteReadback evidence(AdmissionContext c, Instant now) {
        return new PrerequisiteReadback(now, now.plusSeconds(60), "real-prerequisite-readback", c.target().placement(), c.target().profileFingerprint(),
            c.manifestFingerprint(), c.manifest().bindingFingerprint(), c.manifest().prerequisiteReferences(), c.policy().restoreEvidenceReference(),
            c.policy().privacyEvidenceReference(), c.grant().ownerContactEvidenceReference(), c.policy().lifecycle().noticeTransportReference());
    }
    Reservation reserve(String manifest, String key) {
        var c = context(manifest); return store.reserve(c, new Reserve(key, manifest), evidence(c, NOW), "owner", NOW);
    }
    Reservation one() {
        approve(1); store.approveManifest(manifest("manifest1", "deployment1"), "operator", NOW); return reserve("manifest1", "op1");
    }
    ResourceReadback readback(Reservation r, Instant now, boolean absent, Long sessions, Long humanWork) {
        return new ResourceReadback(r.id(), r.generation(), r.revision(), r.workerToken(), r.manifestFingerprint(), r.bindingFingerprint(),
            STAGING, now, "inventory-and-drain-proof", true, true, absent, true, true, true, 0, sessions, 0L, humanWork, 0L);
    }
}
