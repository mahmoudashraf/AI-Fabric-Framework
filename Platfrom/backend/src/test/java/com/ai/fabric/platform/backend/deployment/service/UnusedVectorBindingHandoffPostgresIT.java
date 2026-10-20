package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionJson;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionUnusedReservationEvidence;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectHandoffTestFixture;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationIntegrity;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.InventoryApproval;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorBindingHandoffContracts;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorBindingHandoffContracts.Receipt;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorBindingHandoffContracts.Source;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.AuthenticationType.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.target=latest",
    "platform.bootstrap.sample-enabled=false",
    "platform.bootstrap.ecommerce-demo.enabled=false",
    "platform.auth.bootstrap-admin-enabled=false",
    "platform.deployment.admission.release.worker-enabled=false",
    "platform.deployment.admission.lifecycle.worker-enabled=false",
    "platform.deployment.admission.lifecycle.retirement-worker-enabled=false",
    "platform.target-retirement.worker-enabled=false",
    "platform.provider-host-volume-reclamation.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers
class UnusedVectorBindingHandoffPostgresIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String MANAGEMENT_KEY = "synthetic-handoff-control-plane-key";
    private static final String OLD_TOKEN = "synthetic-handoff-old-serving-token";
    private static final String NEW_TOKEN = "synthetic-handoff-new-serving-token";
    private static final String APPROVAL = "synthetic-customer-approved-handoff";
    private static final long SOURCE_GATE_EPOCH = 7;
    private static final long SOURCE_GATE_ROW_VERSION = 11;
    private static final String PROVIDER_CONFIG = """
        {"vectorStrategy":"milvus","vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
         "milvusExternalBindingRequired":true,"milvusAuthenticationType":"TOKEN","milvusCollectionPrefix":"synthetic_"}
        """;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired DeploymentExistingVectorBindingService bindings;
    @Autowired DeploymentAdmissionService admission;
    @Autowired AdmissionUnusedReservationEvidence cancellationEvidence;
    @Autowired AdmissionJson json;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentDraftRepository drafts;
    @Autowired DeploymentVersionRepository versions;
    @Autowired DeploymentTargetProfileRepository targets;
    @Autowired DeploymentProviderResourceHandleRepository handles;
    @Autowired PlatformSecretService secrets;
    @Autowired TenantScopedVectorHandleResolver scopes;
    @Autowired ExternalIntegrationIntegrity integrationIntegrity;
    @Autowired MockMvc mvc;
    @MockBean ZillizCloudControlPlaneClient provider;

    private String fixtureId;
    private String customerId;
    private String targetId;
    private String clusterId;
    private String endpoint;
    private Instant now;
    private Destination old;
    private Destination destination;
    private DeploymentProviderResourceHandleEntity sourceHandle;
    private CancelUnusedReservation cancellationCommand;
    private UnusedReservationCancellationReceipt cancellation;

    @BeforeEach
    void historicalUnusedSource() throws Exception {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
        fixtureId = UUID.randomUUID().toString().substring(0, 8);
        customerId = "synthetic-customer-" + fixtureId;
        targetId = "synthetic-target-" + fixtureId;
        clusterId = "synthetic-cluster-" + fixtureId;
        endpoint = "https://synthetic-" + fixtureId + ".zilliz.com";
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("""
            INSERT INTO platform_customers(id,name,slug,status,platform_managed,created_at,updated_at)
            VALUES (?,?,?,'ACTIVE',false,?,?)
            """, customerId, customerId, customerId, Timestamp.from(now), Timestamp.from(now));
        var target = new DeploymentTargetProfileEntity();
        target.setId(targetId); target.setName(targetId); target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("synthetic-staging"); target.setActive(true); target.setSourceStrategy("IMMUTABLE_IMAGE");
        target.setProviderConfigJson("{\"serverUuid\":\"synthetic-server\",\"destinationUuid\":\"synthetic-destination\"}");
        target.setNetworkPolicyJson("{}"); target.setResourceDefaultsJson("{}");
        target.setCreatedAt(now); target.setUpdatedAt(now); targets.saveAndFlush(target);
        old = createDestination("old", customerId);
        destination = createDestination("new", customerId);
        sourceHandle = createHistoricalBinding(old);
        cancellationCommand = seedUnusedAuthorization(old, sourceHandle);
        secrets.updateSecret("ZILLIZ_CLOUD_API_KEY", MANAGEMENT_KEY);
        when(provider.getCluster(eq(clusterId), eq(MANAGEMENT_KEY))).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizClusterSummary(clusterId, "synthetic-cluster", "synthetic-project",
                "synthetic-region", "Serverless", "", 0, "RUNNING", endpoint, "Free"));
        when(provider.listServingCollections(eq(endpoint), anyString())).thenReturn(List.of());
        assertThat(bindings.resolveLocal(old.deployment(), old.version(), mapper.readTree(PROVIDER_CONFIG), targetId)
            .path("milvusRuntimeTokenSecretName").asText()).isEqualTo(secretRef("TOKEN", old));
        String gateBeforeCancellation = targetGateSnapshot(old);
        cancellation = admission.cancelUnusedReservation(old.deployment().getId(), targetId, cancellationCommand);
        assertThat(cancellation.evidence().targetGate()).isEqualTo(new UnusedTargetGate(
            "gate-" + old.deployment().getId(), SOURCE_GATE_EPOCH, SOURCE_GATE_ROW_VERSION));
        assertThat(targetGateSnapshot(old)).as("cancellation preserves the available nonzero gate exactly").isEqualTo(gateBeforeCancellation);
        sourceHandle = handles.findById(sourceHandle.getId()).orElseThrow();
        assertThat(sourceHandle.getStatus()).isEqualTo("RETAINED_UNUSED");
    }

    @AfterEach
    void onlyReadOnlyProviderCallsWereUsed() {
        try {
            assertThat(mockingDetails(provider).getInvocations()).allSatisfy(invocation ->
                assertThat(invocation.getMethod().getName()).isIn("getCluster", "listServingCollections"));
        } finally {
            PlatformTestSecurity.clearAuthentication();
        }
    }

    @Test
    void fixtureUsesAnAuthenticCommittedCancellationAndKeepsSameTenantOnlyOrdinaryEvidence() {
        assertThat(cancellation.outcome()).isEqualTo(CancellationOutcome.CANCELLED_UNUSED_NO_EFFECT);
        assertThat(cancellation.reclamationPerformed()).isFalse();
        assertThat(cancellation.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(cancellation.reservation().expiryState()).isEqualTo(ExpiryState.RELEASED);
        assertThat(cancellation.evidence().retainedBindings()).singleElement().satisfies(retained -> {
            assertThat(retained.handleId()).isEqualTo(sourceHandle.getId());
            assertThat(retained.disposition()).isEqualTo(BindingCancellationDisposition.RETAINED_UNUSED);
            assertThat(retained.identityFingerprint()).hasSize(64);
        });
        assertThat(admission.cancelUnusedReservation(old.deployment().getId(), targetId, cancellationCommand)).isEqualTo(cancellation);
        assertThat(cancellationEvidence.permitsFreshBinding(sourceHandle, customerId, old.deployment().getTenantId(),
            APPROVAL, prefix(destination))).isTrue();
        assertThat(cancellationEvidence.permitsFreshBinding(sourceHandle, customerId, destination.deployment().getTenantId(),
            APPROVAL, prefix(destination))).isFalse();
        assertThat(count("deployment_admission_events", "reservation_id=? AND event_type='CANCELLED_UNUSED_NO_EFFECT'",
            cancellation.reservation().id())).isEqualTo(1);
        assertThat(count("deployment_capacity_reservations", "id=? AND state='RELEASED' AND current_slot IS NULL",
            cancellation.reservation().id())).isEqualTo(1);
        assertNoEffects(old);
    }

    @Test
    void ordinaryBindStillRejectsCrossTenantReuseWithoutAnyWritesOrProviderCalls() {
        assertRejectedWithoutWrites(() -> bindings.bind(destination.deployment().getId(), targetId, connection(destination)));
        verifyNoInteractions(provider);
    }

    @Test
    void handoffCommitsOneNewBindingAndAuditedReceiptWithoutRewritingHistoricalOwnership() throws Exception {
        String history = row("deployment_provider_resource_handles", sourceHandle.getId());
        String sourceGate = targetGateSnapshot(old);
        var ownership = ownershipSnapshot();
        var grants = tableSnapshot("provider_usage_grants");
        var command = request(destination);

        Receipt receipt = handoff(destination, command);

        assertThat(receipt.id()).isNotBlank();
        assertThat(receipt.operationKey()).isEqualTo(command.operationKey());
        assertThat(receipt.deploymentId()).isEqualTo(destination.deployment().getId());
        assertThat(receipt.customerId()).isEqualTo(customerId);
        assertThat(receipt.tenantId()).isEqualTo(destination.deployment().getTenantId());
        assertThat(receipt.targetProfileId()).isEqualTo(targetId);
        assertThat(receipt.clusterId()).isEqualTo(clusterId);
        assertThat(receipt.versionId()).isEqualTo(destination.version().getId());
        assertThat(receipt.versionConfigHash()).isEqualTo(destination.version().getConfigHash());
        assertThat(receipt.sources()).containsExactlyElementsOf(command.sources());
        assertThat(receipt.approvalReference()).isEqualTo(APPROVAL);
        assertThat(receipt.actorId()).isEqualTo("test-admin@example.com");
        assertThat(receipt.handedOffAt()).isAfterOrEqualTo(now);
        assertThat(receipt.fingerprint()).hasSize(64);
        assertThat(receipt.commandFingerprint()).hasSize(64);
        assertThat(receipt.binding()).isEqualTo(bindings.get(destination.deployment().getId(), targetId));
        assertThat(receipt.binding().collectionPrefix()).isEqualTo(prefix(destination)).isNotEqualTo(prefix(old));
        assertThat(receipt.binding().authType()).isEqualTo(TOKEN);
        assertThat(receipt.binding().lifecycle()).isEqualTo("RETAIN_EXTERNAL");
        assertThat(row("deployment_provider_resource_handles", sourceHandle.getId())).isEqualTo(history);
        assertThat(targetGateSnapshot(old)).as("handoff must not rewrite the cancelled source gate").isEqualTo(sourceGate);
        assertThat(ownershipSnapshot()).isEqualTo(ownership);
        assertThat(tableSnapshot("provider_usage_grants")).isEqualTo(grants);
        assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=? AND status='ACTIVE'", clusterId)).isEqualTo(1);
        assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=?", clusterId)).isEqualTo(2);
        assertThat(count("deployment_unused_vector_handoffs", "deployment_id=?", receipt.deploymentId())).isEqualTo(1);
        assertThat(count("platform_audit_events", "target_id=? AND details_json LIKE ?", receipt.deploymentId(),
            "%" + receipt.id() + "%")).isEqualTo(1);
        assertThat(secrets.resolveSecret(secretRef("TOKEN", destination))).isEqualTo(NEW_TOKEN);
        assertThat(secrets.resolveSecret(secretRef("TOKEN", old))).isEqualTo(OLD_TOKEN);
        assertNoEffects(old); assertNoEffects(destination);
        assertThat(json.write(receipt)).doesNotContain(OLD_TOKEN, NEW_TOKEN, MANAGEMENT_KEY, "synthetic-historical-fingerprint-key");
        verify(provider).getCluster(clusterId, MANAGEMENT_KEY);
        verify(provider).listServingCollections(endpoint, NEW_TOKEN);

        var committed = databaseSnapshot();
        String body = mvc.perform(get(readbackUrl(destination, command.operationKey()))
                .with(authentication(SecurityContextHolder.getContext().getAuthentication())))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readValue(body, Receipt.class)).isEqualTo(receipt);
        assertThat(databaseSnapshot()).isEqualTo(committed);
    }

    @Test
    void realKnownSettlementAndCompleteInventoryAuthorizeOnlyANewBindingAndReplayAfterDestinationIndexes() throws Exception {
        var known = knownSource();
        var command = knownRequest(known, destination, inventory());
        var original = jdbc.queryForMap("SELECT * FROM deployment_provider_resource_handles WHERE id=?", known.handle().getId());
        String releases = tableSnapshot("platform_deployment_releases");
        String authorizations = tableSnapshot("deployment_admission_authorizations");
        String funding = tableSnapshot("provider_usage_grants");
        assertRejectedWithoutWrites(() -> bindings.bind(destination.deployment().getId(), targetId, connection(destination)));
        var receipt = handoff(destination, command);
        assertThat(receipt.emptyClusterReadback()).isNotNull();
        assertThat(receipt.emptyClusterReadback().collectionsByDatabase()).containsExactlyEntriesOf(Map.of("default", List.of()));
        var after = jdbc.queryForMap("SELECT * FROM deployment_provider_resource_handles WHERE id=?", known.handle().getId());
        var identity = new java.util.TreeMap<>(after); identity.put("status", original.get("status")); identity.put("updated_at", original.get("updated_at"));
        assertThat(identity).isEqualTo(original);
        assertThat(receipt.sourceTransitions()).singleElement().satisfies(transition -> {
            assertThat(transition.bindingId()).isEqualTo(known.handle().getId());
            assertThat(transition.beforeRowFingerprint()).isEqualTo(json.fingerprint(original));
            assertThat(transition.afterRowFingerprint()).isEqualTo(json.fingerprint(after));
            assertThat(transition.fromStatus()).isEqualTo("ACTIVE"); assertThat(transition.toStatus()).isEqualTo("RETAINED_UNUSED");
        });
        assertThat(tableSnapshot("platform_deployment_releases")).isEqualTo(releases);
        assertThat(tableSnapshot("deployment_admission_authorizations")).isEqualTo(authorizations);
        assertThat(tableSnapshot("provider_usage_grants")).isEqualTo(funding);
        assertThat(handles.findById(known.handle().getId()).orElseThrow().getStatus()).isEqualTo("RETAINED_UNUSED");
        assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=? AND status='ACTIVE'", clusterId)).isEqualTo(1);
        verify(provider).listServingCollections(endpoint, MANAGEMENT_KEY);
        var committed = databaseSnapshot();
        clearInvocations(provider);
        when(provider.listServingCollections(endpoint, MANAGEMENT_KEY)).thenThrow(new AssertionError("Replay must not re-inventory"));
        when(provider.listServingCollections(endpoint, NEW_TOKEN)).thenReturn(List.of(prefix(destination) + "synthetic_document"));
        assertThat(handoff(destination, command)).isEqualTo(receipt);
        assertThat(databaseSnapshot()).isEqualTo(committed);
        verify(provider, org.mockito.Mockito.never()).listServingCollections(endpoint, MANAGEMENT_KEY);
        assertRejectedWithoutWrites(() -> handoff(destination, knownRequest(known, destination,
            new InventoryApproval(inventory().expectedCredentialFingerprint(), "changed-visibility", inventory().noWriterWindowReference()))));
        assertRejectedWithoutWrites(() -> handoff(destination, knownRequest(known, destination,
            new InventoryApproval(inventory().expectedCredentialFingerprint(), inventory().fullVisibilityReviewReference(), "changed-window"))));
        assertThat(bindings.getUnusedHandoff(destination.deployment().getId(), targetId, command.operationKey())).isEqualTo(receipt);
    }

    @ParameterizedTest @ValueSource(strings = {"nonempty", "unobservable", "credential", "visibility", "window"})
    void knownSettlementStillRequiresActualCompleteEmptyInventoryAndExplicitReviews(String change) throws Exception {
        var known = knownSource(); var review = inventory();
        switch (change) {
            case "nonempty" -> when(provider.listServingCollections(endpoint, MANAGEMENT_KEY)).thenReturn(List.of("foreign_customer_data"));
            case "unobservable" -> when(provider.listServingCollections(endpoint, MANAGEMENT_KEY)).thenThrow(new IllegalStateException("denied"));
            case "credential" -> review = new InventoryApproval("0".repeat(64), review.fullVisibilityReviewReference(), review.noWriterWindowReference());
            case "visibility" -> review = new InventoryApproval(review.expectedCredentialFingerprint(), "", review.noWriterWindowReference());
            case "window" -> review = new InventoryApproval(review.expectedCredentialFingerprint(), review.fullVisibilityReviewReference(), "");
            default -> throw new AssertionError(change);
        }
        var command = knownRequest(known, destination, review);
        assertRejectedWithoutWrites(() -> handoff(destination, command));
        assertThat(secrets.resolveSecret(secretRef("TOKEN", destination))).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"receipt", "event", "release", "authorization", "handle", "reservation", "activity", "owner", "entitlement"})
    void knownSettlementProofCannotHidePostSettlementDriftOrNewEffects(String change) throws Exception {
        var known = knownSource();
        switch (change) {
            case "receipt" -> jdbc.update("UPDATE deployment_admission_reviews SET fingerprint=? WHERE kind='NO_EFFECT_SETTLE' AND payload_json::jsonb->>'id'=?", "0".repeat(64), known.proof().receiptId());
            case "event" -> jdbc.update("UPDATE deployment_admission_events SET evidence_reference='changed' WHERE reservation_id=? AND event_type='SETTLED_KNOWN_NO_EFFECT'", known.proof().reservationId());
            case "release" -> jdbc.update("UPDATE platform_deployment_releases SET status='PROVISIONING' WHERE id=?", known.proof().releaseId());
            case "authorization" -> jdbc.update("UPDATE deployment_admission_authorizations SET request_fingerprint='changed' WHERE reservation_id=?", known.proof().reservationId());
            case "handle" -> jdbc.update("UPDATE deployment_provider_resource_handles SET last_observed_status='changed' WHERE id=?", known.handle().getId());
            case "reservation" -> jdbc.update("UPDATE deployment_capacity_reservations SET revision=revision+1 WHERE id=?", known.proof().reservationId());
            case "activity" -> jdbc.update("INSERT INTO deployment_admission_activity_watermarks VALUES (?,?,'ACCEPTED_SESSION',1,?)", known.proof().reservationId(), known.proof().releaseId(), Timestamp.from(now));
            case "owner" -> jdbc.update("UPDATE platform_users SET status='DISABLED' WHERE id=?", known.ownerId());
            case "entitlement" -> jdbc.update("UPDATE platform_customer_offer_entitlements SET status='REVOKED' WHERE owner_user_id=?", known.ownerId());
            default -> throw new AssertionError(change);
        }
        clearInvocations(provider);
        assertRejectedWithoutWrites(() -> handoff(destination, knownRequest(known, destination, inventory())));
        verifyNoInteractions(provider);
    }

    @Test void knownSettlementCannotBeReinterpretedAsCancellationOrWidenCrossControlPlaneTransfer() throws Exception {
        var known = knownSource();
        var wrong = new Source(known.handle().getId(), known.proof().receiptId(), bindingFingerprint(known.handle()));
        assertRejectedWithoutWrites(() -> handoff(destination, new UnusedVectorBindingHandoffContracts.Request("wrong-kind", APPROVAL,
            List.of(source(), wrong), connection(destination))));
        var transfers = new UnusedVectorTransferEvidence(jdbc, new UnusedVectorTransferJson(), cancellationEvidence);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
            transfers.requireConfigurationOnlyDetachment(known.handle()))).hasMessageContaining("SOURCE_NOT_UNUSED");
    }

    @Test void competingKnownSettlementDestinationsCannotBothCommit() throws Exception {
        var known = knownSource(); var rival = createDestination("known-rival", customerId);
        associateOwner(rival, known.ownerId(), "rival-offer-" + fixtureId);
        var firstObserved = new CountDownLatch(1); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        when(provider.listServingCollections(endpoint, MANAGEMENT_KEY)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) { firstObserved.countDown(); if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("timeout"); }
            return List.of();
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> attempt(() -> handoff(destination, knownRequest(known, destination, inventory()))));
            try {
                assertThat(firstObserved.await(10, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> attempt(() -> handoff(rival, knownRequest(known, rival, inventory()))));
                release.countDown();
                var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
                assertThat(results).filteredOn(value -> value.receipt() != null).hasSize(1);
                assertThat(results).filteredOn(value -> value.failure() != null).hasSize(1);
                assertThat(count("deployment_unused_vector_handoffs", "payload_json::jsonb->>'clusterId'=?", clusterId)).isEqualTo(1);
                assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=? AND status='ACTIVE'", clusterId)).isEqualTo(1);
            } finally { release.countDown(); }
        }
    }

    @Test void knownSourceStatusAndEveryDestinationWriteRollBackOnFinalReceiptFailure() throws Exception {
        var known = knownSource();
        String function = "known_handoff_failure_" + fixtureId;
        jdbc.execute("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.deployment_id='"
            + destination.deployment().getId() + "' THEN IF (SELECT status FROM deployment_provider_resource_handles WHERE id='"
            + known.handle().getId() + "') <> 'RETAINED_UNUSED' THEN RAISE EXCEPTION 'transition-not-applied'; END IF; "
            + "RAISE EXCEPTION 'synthetic-known-final-failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + function + " BEFORE INSERT ON deployment_unused_vector_handoffs FOR EACH ROW EXECUTE FUNCTION " + function + "()");
        var before = databaseSnapshot();
        try {
            assertThatThrownBy(() -> handoff(destination, knownRequest(known, destination, inventory())))
                .hasStackTraceContaining("synthetic-known-final-failure");
            assertThat(databaseSnapshot()).isEqualTo(before);
            assertThat(handles.findById(known.handle().getId()).orElseThrow().getStatus()).isEqualTo("ACTIVE");
        } finally {
            jdbc.execute("DROP TRIGGER " + function + " ON deployment_unused_vector_handoffs"); jdbc.execute("DROP FUNCTION " + function + "()");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"status-alone", "transition-hash", "missing-audit", "current-after-row"})
    void retainedKnownSourceRequiresTheExactAuthenticatedPriorTransition(String change) throws Exception {
        var known = knownSource(); var command = knownRequest(known, destination, inventory());
        if (change.equals("status-alone")) jdbc.update("UPDATE deployment_provider_resource_handles SET status='RETAINED_UNUSED' WHERE id=?", known.handle().getId());
        else {
            var receipt = handoff(destination, command);
            switch (change) {
                case "transition-hash" -> jdbc.update("UPDATE deployment_unused_vector_handoffs SET fingerprint=? WHERE id=?", "0".repeat(64), receipt.id());
                case "missing-audit" -> jdbc.update("DELETE FROM platform_audit_events WHERE details_json::jsonb->>'receiptId'=?", receipt.id());
                case "current-after-row" -> jdbc.update("UPDATE deployment_provider_resource_handles SET last_observed_status='changed' WHERE id=?", known.handle().getId());
                default -> throw new AssertionError(change);
            }
        }
        clearInvocations(provider);
        assertRejectedWithoutWrites(() -> handoff(destination, command)); verifyNoInteractions(provider);
    }

    @Test void retainedKnownSourceCarriesForwardOnlyThroughItsPriorV202Transition() throws Exception {
        var known = knownSource(); var first = handoff(destination, knownRequest(known, destination, inventory()));
        var nextProof = KnownNoEffectHandoffTestFixture.settle(jdbc, json, transactionManager, integrationIntegrity,
            destination.deployment(), destination.version(), targetId, known.ownerId(), "destination-offer-" + fixtureId, now);
        var next = createDestination("carry-forward", customerId); associateOwner(next, known.ownerId(), "next-offer-" + fixtureId);
        var previousHandle = handles.findById(first.binding().bindingId()).orElseThrow();
        var sources = List.of(source(), new Source(known.handle().getId(), null, bindingFingerprint(known.handle()), known.proof().receiptId()),
            new Source(previousHandle.getId(), null, bindingFingerprint(previousHandle), nextProof.receiptId()));
        var command = new UnusedVectorBindingHandoffContracts.Request("carry-forward", APPROVAL, sources, connection(next), inventory());
        var second = handoff(next, command);
        assertThat(second.sourceTransitions()).singleElement().satisfies(value -> assertThat(value.bindingId()).isEqualTo(previousHandle.getId()));
        assertThat(handles.findById(known.handle().getId()).orElseThrow().getStatus()).isEqualTo("RETAINED_UNUSED");
        assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=? AND status='ACTIVE'", clusterId)).isEqualTo(1);
        assertThat(handoff(next, command)).isEqualTo(second);
    }

    @ParameterizedTest @ValueSource(strings = {"orphan", "unknown-outcome", "current-deployment"})
    void relevantGrantEventsMustResolveToProvedOtherDeploymentAllocations(String change) throws Exception {
        var known = knownSource(); String grant = grant();
        if (change.equals("current-deployment")) allocation(grant, known.owner().deployment().getId(), known.proof().reservationId());
        else {
            String subject = change.equals("orphan") ? "missing-allocation" : allocation(grant, destination.deployment().getId(), "different-reservation");
            financialEvent("unknown-event-" + fixtureId, subject, grant, change.equals("orphan") ? "ALLOWANCE_ALLOCATED" : "OUTCOME_UNKNOWN");
        }
        clearInvocations(provider);
        assertRejectedWithoutWrites(() -> handoff(destination, knownRequest(known, destination, inventory()))); verifyNoInteractions(provider);
    }

    @Test void legitimateDestinationSpendAndGrantApproveVoidHistoryDoNotInvalidateReplay() throws Exception {
        var known = knownSource(); var command = knownRequest(known, destination, inventory()); var receipt = handoff(destination, command);
        String grant = grant(); String allocated = allocation(grant, destination.deployment().getId(), "destination-reservation");
        financialEvent("allocated-" + fixtureId, allocated, grant, "ALLOWANCE_ALLOCATED");
        jdbc.update("UPDATE provider_usage_grants SET allocated_chat_attempts=1,allocated_total_tokens=100,allocated_cost_nano_usd=100 WHERE id=?", grant);
        String voided = "void-" + fixtureId;
        jdbc.update("INSERT INTO provider_usage_grants SELECT ?,'old-campaign',customer_id,target_profile_id,environment_name,fingerprint,payload_json,0,0,0,approved_by,approved_role,approved_at,'VOID_UNUSED' FROM provider_usage_grants WHERE id=?", voided, grant);
        financialEvent("voided-" + fixtureId, voided, voided, "GRANT_VOID_UNUSED");
        var before = databaseSnapshot(); clearInvocations(provider);
        assertThat(handoff(destination, command)).isEqualTo(receipt); assertThat(databaseSnapshot()).isEqualTo(before);
        verify(provider, org.mockito.Mockito.never()).listServingCollections(endpoint, MANAGEMENT_KEY);
    }

    @Test
    void exactReplayReturnsTheCommittedReceiptWhileChangedCommandsAndCredentialsFailClosed() {
        var command = request(destination);
        Receipt receipt = handoff(destination, command);
        var committed = databaseSnapshot();
        String gate = targetGateSnapshot(destination);
        clearInvocations(provider);

        assertThat(handoff(destination, command)).isEqualTo(receipt);
        assertThat(targetGateSnapshot(destination)).as("exact replay must not advance the destination gate").isEqualTo(gate);
        assertThat(databaseSnapshot()).isEqualTo(committed);
        var connection = command.connection();
        var changedCredential = new ExistingVectorBindingContracts.Request(connection.versionId(), connection.clusterId(),
            connection.projectId(), connection.regionId(), connection.expectedDeploymentOption(), connection.databaseName(),
            null, null, connection.approvalReference(), TOKEN, "synthetic-changed-serving-token");
        assertRejectedWithoutWrites(() -> handoff(destination, new UnusedVectorBindingHandoffContracts.Request(
            command.operationKey(), command.approvalReference(), command.sources(), changedCredential)));
        assertRejectedWithoutWrites(() -> handoff(destination, new UnusedVectorBindingHandoffContracts.Request(
            command.operationKey(), "synthetic-changed-approval", command.sources(), connection)));
        assertRejectedWithoutWrites(() -> handoff(destination, new UnusedVectorBindingHandoffContracts.Request(
            command.operationKey(), command.approvalReference(), List.of(new Source(sourceHandle.getId(), cancellation.id(), "0".repeat(64))),
            connection)));
        assertThat(databaseSnapshot()).isEqualTo(committed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"epoch", "row-version", "gate-id", "missing", "state", "operation"})
    void sourceGateChangesAfterCancellationCannotAuthorizeAHandoff(String change) {
        String deploymentId = old.deployment().getId();
        switch (change) {
            case "epoch" -> jdbc.update("UPDATE platform_target_retirement_gates SET epoch=epoch+1 WHERE deployment_id=? AND target_profile_id=?",
                deploymentId, targetId);
            case "row-version" -> jdbc.update("UPDATE platform_target_retirement_gates SET row_version=row_version+1 WHERE deployment_id=? AND target_profile_id=?",
                deploymentId, targetId);
            case "gate-id" -> jdbc.update("UPDATE platform_target_retirement_gates SET id=? WHERE deployment_id=? AND target_profile_id=?",
                "synthetic-replaced-gate-" + fixtureId, deploymentId, targetId);
            case "missing" -> jdbc.update("DELETE FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=?",
                deploymentId, targetId);
            case "state" -> jdbc.update("UPDATE platform_target_retirement_gates SET state='RETIRING' WHERE deployment_id=? AND target_profile_id=?",
                deploymentId, targetId);
            case "operation" -> jdbc.update("UPDATE platform_target_retirement_gates SET operation_id=? WHERE deployment_id=? AND target_profile_id=?",
                "synthetic-later-operation-" + fixtureId, deploymentId, targetId);
            default -> throw new AssertionError(change);
        }
        assertRejectedWithoutWrites(() -> handoff(destination, request(destination)));
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fenced", "planned-retirement", "missing-gate", "source-version-hash", "source-unpublished"})
    void committedReplayStillRejectsRetirementAndSourceVersionDriftWithoutRewritingItsReceipt(String change) {
        var command = request(destination);
        Receipt receipt = handoff(destination, command);
        switch (change) {
            case "fenced" -> jdbc.update("UPDATE platform_target_retirement_gates SET state='RETIRING' WHERE deployment_id=? AND target_profile_id=?",
                destination.deployment().getId(), targetId);
            case "planned-retirement" -> jdbc.update("""
                INSERT INTO platform_target_retirement_operations
                (id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,requested_by_actor_id,requested_by_role,
                 request_json,snapshot_json,result_json,state,step,drain_deadline,created_at,updated_at)
                VALUES (?,?,?,?,'synthetic-retirement',?,'synthetic-reviewer','PLATFORM_ADMIN','{}','{}','{}','PLANNED','DRAIN',?,?,?)
                """, "synthetic-retirement-" + fixtureId, customerId, destination.deployment().getId(), targetId, "0".repeat(64),
                Timestamp.from(now.plusSeconds(600)), Timestamp.from(now), Timestamp.from(now));
            case "missing-gate" -> jdbc.update("DELETE FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=?",
                destination.deployment().getId(), targetId);
            case "source-version-hash" -> jdbc.update("UPDATE platform_deployment_versions SET config_hash=? WHERE id=?",
                "synthetic-changed-source-version-hash", old.version().getId());
            case "source-unpublished" -> jdbc.update("UPDATE platform_deployment_versions SET status='DRAFT' WHERE id=?", old.version().getId());
            default -> throw new AssertionError(change);
        }
        clearInvocations(provider);
        assertRejectedWithoutWrites(() -> handoff(destination, command));
        assertThat(bindings.getUnusedHandoff(destination.deployment().getId(), targetId, command.operationKey())).isEqualTo(receipt);
        verifyNoInteractions(provider);
    }

    @Test
    void providerReadbackFailureCannotLeaveDestinationSecretsHandleLedgerOrAudit() {
        when(provider.listServingCollections(endpoint, NEW_TOKEN)).thenThrow(new IllegalStateException("synthetic-provider-denied"));
        assertRejectedWithoutWrites(() -> handoff(destination, request(destination)));
        assertThat(secrets.resolveSecret(secretRef("TOKEN", destination))).isNull();
        assertThat(secrets.resolveSecret(secretRef("FINGERPRINT", destination))).isNull();
    }

    @Test
    void firstHandoffRejectsEvenDestinationPrefixedCollectionsBeforeWritingSecretsOrBinding() {
        when(provider.listServingCollections(endpoint, NEW_TOKEN)).thenReturn(List.of(prefix(destination) + "synthetic_document"));
        var before = databaseSnapshot();
        assertThatThrownBy(() -> handoff(destination, request(destination))).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("UNUSED_VECTOR_HANDOFF_CLUSTER_NOT_EMPTY");
        assertThat(databaseSnapshot()).isEqualTo(before);
        assertThat(secrets.resolveSecret(secretRef("TOKEN", destination))).isNull();
        assertThat(secrets.resolveSecret(secretRef("FINGERPRINT", destination))).isNull();
        verify(provider).getCluster(clusterId, MANAGEMENT_KEY);
        verify(provider).listServingCollections(endpoint, NEW_TOKEN);
    }

    @Test
    void committedReplayMayReadCollectionsSubsequentlyCreatedUnderItsOwnDestinationPrefix() {
        var command = request(destination);
        Receipt receipt = handoff(destination, command);
        when(provider.listServingCollections(endpoint, NEW_TOKEN)).thenReturn(List.of(prefix(destination) + "synthetic_document"));
        var committed = databaseSnapshot();
        clearInvocations(provider);
        assertThat(handoff(destination, command)).isEqualTo(receipt);
        assertThat(databaseSnapshot()).isEqualTo(committed);
        verify(provider).getCluster(clusterId, MANAGEMENT_KEY);
        verify(provider).listServingCollections(endpoint, NEW_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ledger", "audit"})
    void lateInsertFailureRollsBackSecretsHandleLedgerAndAuditInTheRealTransaction(String boundary) {
        String function = "synthetic_handoff_fail_" + fixtureId;
        String trigger = function + "_trigger";
        String table = boundary.equals("ledger") ? "deployment_unused_vector_handoffs" : "platform_audit_events";
        String condition = boundary.equals("ledger") ? "NEW.deployment_id='" + destination.deployment().getId() + "'"
            : "NEW.target_id='" + destination.deployment().getId() + "' AND NEW.action='EXTERNAL_VECTOR_UNUSED_HANDOFF'";
        jdbc.execute("CREATE FUNCTION " + function + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
            + "IF " + condition + " THEN RAISE EXCEPTION 'synthetic-handoff-" + boundary + "-failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON " + table + " FOR EACH ROW EXECUTE FUNCTION "
            + function + "()");
        var before = databaseSnapshot();
        try {
            assertThatThrownBy(() -> handoff(destination, request(destination))).hasStackTraceContaining("synthetic-handoff-" + boundary + "-failure");
            assertThat(databaseSnapshot()).isEqualTo(before);
            assertThat(secrets.resolveSecret(secretRef("TOKEN", destination))).isNull();
            assertThat(secrets.resolveSecret(secretRef("FINGERPRINT", destination))).isNull();
        } finally {
            jdbc.execute("DROP TRIGGER " + trigger + " ON " + table);
            jdbc.execute("DROP FUNCTION " + function + "()");
        }
    }

    @Test
    void anOuterRollbackAlsoRollsBackTheCompleteHandoffAndAllowsAnIdenticalRetry() {
        var command = request(destination);
        var before = databaseSnapshot();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            var uncommitted = handoff(destination, command);
            assertThat(count("deployment_unused_vector_handoffs", "deployment_id=?", uncommitted.deploymentId())).isEqualTo(1);
            assertThat(count("deployment_provider_resource_handles", "id=? AND status='ACTIVE'", uncommitted.binding().bindingId())).isEqualTo(1);
            assertThat(secrets.resolveSecret(secretRef("TOKEN", destination))).isEqualTo(NEW_TOKEN);
            tx.setRollbackOnly();
        });
        assertThat(databaseSnapshot()).isEqualTo(before);
        assertThat(handoff(destination, command).operationKey()).isEqualTo(command.operationKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"receipt-id", "receipt-command", "receipt-payload", "receipt-time", "event", "released-fence", "binding-metadata"})
    void forgedOrChangedCancellationEvidenceCannotAuthorizeAHandoff(String tamper) {
        switch (tamper) {
            case "receipt-id" -> jdbc.update("UPDATE deployment_admission_unused_cancellations SET id=? WHERE id=?",
                "synthetic-forged-receipt-" + fixtureId, cancellation.id());
            case "receipt-command" -> jdbc.update("UPDATE deployment_admission_unused_cancellations SET command_fingerprint=? WHERE id=?",
                "0".repeat(64), cancellation.id());
            case "receipt-payload" -> jdbc.update("""
                UPDATE deployment_admission_unused_cancellations
                SET payload_json=jsonb_set(payload_json::jsonb,'{actorId}','"synthetic-forged-actor"')::text WHERE id=?
                """, cancellation.id());
            case "receipt-time" -> jdbc.update("UPDATE deployment_admission_unused_cancellations SET cancelled_at=cancelled_at+interval '1 second' WHERE id=?",
                cancellation.id());
            case "event" -> jdbc.update("DELETE FROM deployment_admission_events WHERE reservation_id=? AND event_type='CANCELLED_UNUSED_NO_EFFECT'",
                cancellation.reservation().id());
            case "released-fence" -> jdbc.update("UPDATE deployment_capacity_reservations SET revision=revision+1 WHERE id=?",
                cancellation.reservation().id());
            case "binding-metadata" -> jdbc.update("""
                UPDATE deployment_provider_resource_handles
                SET metadata_json=jsonb_set(metadata_json::jsonb,'{projectId}','"synthetic-tampered-project"')::text WHERE id=?
                """, sourceHandle.getId());
            default -> throw new AssertionError(tamper);
        }
        assertRejectedWithoutWrites(() -> handoff(destination, request(destination)));
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RETAINED", "USED", "OUTCOME_UNKNOWN"})
    void onlyExactRetainedUnusedSourceStatusIsEligible(String state) {
        jdbc.update("UPDATE deployment_provider_resource_handles SET status=? WHERE id=?", state, sourceHandle.getId());
        assertRejectedWithoutWrites(() -> handoff(destination, request(destination)));
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "release-binding", "resource", "budget", "consumer", "new-reservation", "event"})
    void laterOldTargetEffectsCannotBeHiddenBehindAnOtherwiseAuthenticCancellation(String effect) {
        String effectId = "synthetic-effect-" + fixtureId;
        String deploymentId = old.deployment().getId();
        switch (effect) {
            case "release" -> jdbc.update("""
                INSERT INTO platform_deployment_releases
                (id,deployment_id,deployment_version_id,status,verification_status,provisioning_status,provisioning_target,
                 target_profile_id,provider_type,provisioning_details_json,created_at,applied_at,updated_at)
                VALUES (?,?,?,'NO_EFFECT','NOT_RUN','NO_EFFECT','COOLIFY',?,'COOLIFY','{}',?,?,?)
                """, effectId, deploymentId, old.version().getId(), targetId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
            case "release-binding" -> jdbc.update("""
                INSERT INTO deployment_admission_release_bindings
                (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,customer_id,tenant_id,
                 deployment_id,target_profile_id,state,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,'NO_EFFECT',?,?)
                """, effectId, cancellation.reservation().id(), cancellation.reservation().generation(),
                cancellation.authorizationOperationKey(), old.version().getId(), old.version().getConfigHash(), customerId,
                old.deployment().getTenantId(), deploymentId, targetId, Timestamp.from(now), Timestamp.from(now));
            case "resource" -> jdbc.update("""
                INSERT INTO deployment_provider_resource_handles
                (id,deployment_id,target_profile_id,provider_type,resource_kind,provider_resource_uuid,status,metadata_json,created_at,updated_at)
                VALUES (?,?,?,'COOLIFY','APPLICATION',?,'ACTIVE','{}',?,?)
                """, effectId, deploymentId, targetId, "synthetic-owned-resource-" + fixtureId, Timestamp.from(now), Timestamp.from(now));
            case "budget" -> {
                jdbc.update("""
                    INSERT INTO provider_usage_grants
                    (id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,approved_by,approved_role,approved_at)
                    VALUES (?,?,?,?,? ,?,'{}','synthetic-reviewer','PLATFORM_ADMIN',?)
                    """, effectId, "synthetic-campaign", customerId, targetId, "synthetic-staging", "0".repeat(64), Timestamp.from(now));
                jdbc.update("""
                    INSERT INTO provider_usage_allocations
                    (approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,approval_fingerprint,
                     runtime_allocation_fingerprint,payload_json,initialization_state,created_at)
                    VALUES (?,?,?,?,?,'synthetic-staging',?,?,'{}','UNINITIALIZED',?)
                    """, effectId, effectId, customerId, deploymentId, targetId, "0".repeat(64), "0".repeat(64), Timestamp.from(now));
            }
            case "consumer" -> jdbc.update("""
                INSERT INTO platform_consumers
                (id,customer_id,consumer_id,display_name,status,bound_deployment_id,bound_target_profile_id,created_at,updated_at)
                VALUES (?,?,?,?,'ACTIVE',?,?,?,?)
                """, effectId, customerId, effectId, effectId, deploymentId, targetId, Timestamp.from(now), Timestamp.from(now));
            case "new-reservation" -> {
                var value = mapper.valueToTree(cancellation.reservation());
                var changed = (com.fasterxml.jackson.databind.node.ObjectNode) value;
                changed.put("id", effectId).put("operationKey", "synthetic-later-apply").put("generation", "synthetic-later-generation")
                    .put("requestFingerprint", "0".repeat(64)).put("revision", 0).put("state", "RESERVED").put("expiryState", "CURRENT");
                jdbc.update("""
                    INSERT INTO deployment_capacity_reservations
                    (id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,generation,revision,state,
                     current_slot,expires_at,expiry_state,payload_json,updated_at)
                    VALUES (?,?,?,?,'synthetic-later-apply',?,'synthetic-later-generation',0,'RESERVED',?,?,'CURRENT',?,?)
                    """, effectId, customerId, deploymentId, targetId, "0".repeat(64), deploymentId + ":" + targetId,
                    Timestamp.from(cancellation.reservation().expiresAt()), json.write(changed), Timestamp.from(now));
            }
            case "event" -> jdbc.update("""
                INSERT INTO deployment_admission_events(id,reservation_id,revision,event_type,actor_id,created_at)
                VALUES (?,?,?,'PROVISIONING','synthetic-worker',?)
                """, effectId, cancellation.reservation().id(), cancellation.reservation().revision() + 1, Timestamp.from(now));
            default -> throw new AssertionError(effect);
        }
        assertRejectedWithoutWrites(() -> handoff(destination, request(destination)));
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cluster", "project", "region", "option", "stopped", "endpoint", "foreign-collections"})
    void freshReadOnlyProviderIdentityAndServingScopeAreMandatory(String mismatch) {
        var observed = new ZillizCloudControlPlaneClient.ZillizClusterSummary(
            mismatch.equals("cluster") ? "synthetic-wrong-cluster" : clusterId, "synthetic-cluster",
            mismatch.equals("project") ? "synthetic-wrong-project" : "synthetic-project",
            mismatch.equals("region") ? "synthetic-wrong-region" : "synthetic-region", "Serverless", "", 0,
            mismatch.equals("stopped") ? "STOPPED" : "RUNNING",
            mismatch.equals("endpoint") ? "https://synthetic-provider.example.test" : endpoint,
            mismatch.equals("option") ? "Dedicated" : "Free");
        when(provider.getCluster(clusterId, MANAGEMENT_KEY)).thenReturn(observed);
        if (mismatch.equals("foreign-collections")) when(provider.listServingCollections(endpoint, NEW_TOKEN))
            .thenReturn(List.of(prefix(old) + "synthetic_document"));
        assertRejectedWithoutWrites(() -> handoff(destination, request(destination)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unpublished", "inactive-target", "managed-mode", "foreign-version"})
    void destinationMustUseItsExactPublishedVersionActiveTargetAndReviewedExternalMode(String mismatch) {
        var command = request(destination);
        switch (mismatch) {
            case "unpublished" -> jdbc.update("UPDATE platform_deployment_versions SET status='DRAFT' WHERE id=?", destination.version().getId());
            case "inactive-target" -> jdbc.update("UPDATE deployment_target_profiles SET active=false WHERE id=?", targetId);
            case "managed-mode" -> jdbc.update("""
                UPDATE platform_deployment_versions SET provider_config_json=jsonb_set(provider_config_json::jsonb,
                '{vectorProvisioningMode}','"PLATFORM_MANAGED"')::text WHERE id=?
                """, destination.version().getId());
            case "foreign-version" -> command = new UnusedVectorBindingHandoffContracts.Request(command.operationKey(), APPROVAL, command.sources(),
                new ExistingVectorBindingContracts.Request(old.version().getId(), clusterId, "synthetic-project", "synthetic-region",
                    "Free", "default", null, null, APPROVAL, TOKEN, NEW_TOKEN));
            default -> throw new AssertionError(mismatch);
        }
        var changed = command;
        assertRejectedWithoutWrites(() -> handoff(destination, changed));
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_OPERATOR", "CUSTOMER_ADMIN", "PLATFORM_PRODUCT_SERVICE", "ANONYMOUS"})
    void handoffRequiresExplicitPlatformAdminEvenThroughTheServiceProxy(String role) {
        if (role.equals("ANONYMOUS")) PlatformTestSecurity.clearAuthentication();
        else SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("synthetic-nonadmin@example.test", PlatformRole.valueOf(role), "Synthetic Nonadmin", "TEST"),
            null, List.of(new SimpleGrantedAuthority(PlatformRole.valueOf(role).authority()))));
        var before = databaseSnapshot();
        Throwable denied = catchThrowable(() -> handoff(destination, request(destination)));
        assertThat(denied).isNotNull();
        assertThat(denied instanceof org.springframework.security.access.AccessDeniedException
            || denied instanceof ResponseStatusException response
                && response.getStatusCode().value() == (role.equals("ANONYMOUS") ? 401 : 403)).isTrue();
        assertThat(databaseSnapshot()).isEqualTo(before);
        verifyNoInteractions(provider);
    }

    @Test
    void crossCustomerDestinationCannotClaimTheRetainedSource() {
        String otherCustomer = "synthetic-foreign-customer-" + fixtureId;
        jdbc.update("""
            INSERT INTO platform_customers(id,name,slug,status,platform_managed,created_at,updated_at)
            VALUES (?,?,?,'ACTIVE',false,?,?)
            """, otherCustomer, otherCustomer, otherCustomer, Timestamp.from(now), Timestamp.from(now));
        var foreign = createDestination("foreign", otherCustomer);
        assertRejectedWithoutWrites(() -> handoff(foreign, request(foreign)));
        verifyNoInteractions(provider);
    }

    @Test
    void incompleteHistoricalSourceSetIsRejectedBeforeProviderVerification() throws Exception {
        var secondOwner = createDestination("older", customerId);
        var secondHandle = createHistoricalBinding(secondOwner);
        var secondCancellation = admission.cancelUnusedReservation(secondOwner.deployment().getId(), targetId,
            seedUnusedAuthorization(secondOwner, secondHandle));
        assertRejectedWithoutWrites(() -> handoff(destination, request(destination)));
        verifyNoInteractions(provider);
        var allSources = List.of(source(), new Source(secondHandle.getId(), secondCancellation.id(),
            json.read(secondHandle.getMetadataJson(), com.fasterxml.jackson.databind.JsonNode.class).path("fingerprint").asText()));
        var complete = new UnusedVectorBindingHandoffContracts.Request("synthetic-complete-handoff", APPROVAL, allSources, connection(destination));
        assertThat(handoff(destination, complete).sources()).containsExactlyInAnyOrderElementsOf(allSources);
    }

    @Test
    void activeOnlyClusterIndexStillPreventsASecondActiveClaimAfterSuccess() {
        handoff(destination, request(destination));
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND indexname='uq_active_external_vector_binding_cluster'",
            String.class)).contains("UNIQUE INDEX", "provider_resource_uuid", "EXTERNAL_VECTOR_STORAGE_BINDING", "ACTIVE");
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO deployment_provider_resource_handles
            (id,deployment_id,target_profile_id,provider_type,resource_kind,provider_resource_uuid,status,metadata_json,created_at,updated_at)
            VALUES (?,?,?,'COOLIFY','EXTERNAL_VECTOR_STORAGE_BINDING',?,'ACTIVE','{}',?,?)
            """, "synthetic-conflicting-binding-" + fixtureId, old.deployment().getId(), targetId, clusterId,
            Timestamp.from(now), Timestamp.from(now))).isInstanceOf(DuplicateKeyException.class);
        assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=? AND status='ACTIVE'", clusterId)).isEqualTo(1);
    }

    @Test
    void overlappingDestinationTransactionsCannotBothClaimTheSameCluster() throws Exception {
        var rival = createDestination("rival", customerId);
        var firstObserved = new CountDownLatch(1);
        var releaseProvider = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        when(provider.getCluster(clusterId, MANAGEMENT_KEY)).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                firstObserved.countDown();
                if (!releaseProvider.await(15, TimeUnit.SECONDS)) throw new AssertionError("provider boundary timeout");
            }
            return new ZillizCloudControlPlaneClient.ZillizClusterSummary(clusterId, "synthetic-cluster", "synthetic-project",
                "synthetic-region", "Serverless", "", 0, "RUNNING", endpoint, "Free");
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> attempt(() -> handoff(destination, request(destination))));
            try {
                assertThat(firstObserved.await(10, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> {
                    secondStarted.countDown();
                    return attempt(() -> handoff(rival, request(rival)));
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean overlapped = false;
                while (System.nanoTime() < deadline) {
                    if (calls.get() > 1 || second.isDone() || jdbc.queryForObject("""
                        SELECT COUNT(*) FROM pg_stat_activity
                        WHERE datname=current_database() AND wait_event_type='Lock' AND pid<>pg_backend_pid()
                        """, Long.class) > 0) { overlapped = true; break; }
                    Thread.sleep(20);
                }
                assertThat(overlapped).as("second transaction reaches provider verification, a lock, or a bounded conflict while the first is uncommitted").isTrue();
                releaseProvider.countDown();
                var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                assertThat(results).filteredOn(result -> result.receipt() != null).hasSize(1);
                assertThat(results).filteredOn(result -> result.failure() != null).singleElement().satisfies(result ->
                    assertThat(result.failure()).isInstanceOf(ResponseStatusException.class));
                assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=? AND status='ACTIVE'", clusterId)).isEqualTo(1);
                assertThat(count("deployment_provider_resource_handles", "provider_resource_uuid=?", clusterId)).isEqualTo(2);
                assertThat(count("deployment_unused_vector_handoffs", "payload_json::jsonb->>'clusterId'=?", clusterId)).isEqualTo(1);
                Receipt winner = results.stream().filter(result -> result.receipt() != null).findFirst().orElseThrow().receipt();
                Destination loser = winner.deploymentId().equals(destination.deployment().getId()) ? rival : destination;
                assertThat(secrets.resolveSecret(secretRef("TOKEN", loser))).isNull();
                assertThat(secrets.resolveSecret(secretRef("FINGERPRINT", loser))).isNull();
                assertThat(count("deployment_provider_resource_handles", "deployment_id=?", loser.deployment().getId())).isZero();
                assertThat(count("deployment_unused_vector_handoffs", "deployment_id=?", loser.deployment().getId())).isZero();
                assertThat(count("platform_audit_events", "target_id=?", loser.deployment().getId())).isZero();
                assertThat(count("platform_target_retirement_gates", "deployment_id=?", loser.deployment().getId())).isZero();
                assertNoEffects(old); assertNoEffects(destination); assertNoEffects(rival);
            } finally {
                releaseProvider.countDown();
            }
        }
    }

    @Test
    void productionFlywayUpgradeFrom201To202PreservesEveryExistingRowAndCreatesAnEmptyLedger() {
        String schema = "synthetic_handoff_upgrade_" + fixtureId;
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").schemas(schema).defaultSchema(schema)
            .target(MigrationVersion.fromVersion("201")).load().migrate();
        try {
            copyRow(schema, "platform_customers", "id", customerId);
            copyRow(schema, "platform_customer_tenants", "id", old.deployment().getTenantId());
            copyRow(schema, "deployment_target_profiles", "id", targetId);
            copyRow(schema, "platform_deployments", "id", old.deployment().getId());
            copyRow(schema, "platform_deployment_drafts", "deployment_id", old.deployment().getId());
            copyRow(schema, "platform_deployment_versions", "id", old.version().getId());
            copyRow(schema, "deployment_provider_resource_handles", "id", sourceHandle.getId());
            copyRow(schema, "platform_target_retirement_gates", "deployment_id", old.deployment().getId());
            copyRow(schema, "deployment_admission_reviews", "record_key", cancellation.reservation().manifestId());
            copyRow(schema, "deployment_capacity_reservations", "id", cancellation.reservation().id());
            copyRow(schema, "deployment_admission_authorizations", "reservation_id", cancellation.reservation().id());
            copyRow(schema, "deployment_admission_events", "reservation_id", cancellation.reservation().id());
            copyRow(schema, "deployment_admission_unused_cancellations", "reservation_id", cancellation.reservation().id());
            var tables = jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname=? AND tablename<>'flyway_schema_history' ORDER BY tablename",
                String.class, schema);
            var before = new LinkedHashMap<String, String>();
            for (String table : tables) before.put(table, tableSnapshot(schema + "." + table));
            String index = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=? AND indexname='uq_active_external_vector_binding_cluster'",
                String.class, schema);

            var upgraded = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema)
                .target(MigrationVersion.fromVersion("202")).load().migrate();

            assertThat(upgraded.migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT version FROM " + schema + ".flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                String.class)).isEqualTo("202");
            before.forEach((table, snapshot) -> assertThat(tableSnapshot(schema + "." + table)).as(table).isEqualTo(snapshot));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".deployment_unused_vector_handoffs", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=? AND indexname='uq_active_external_vector_binding_cluster'",
                String.class, schema)).isEqualTo(index);
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private void copyRow(String schema, String table, String column, String id) {
        assertThat(jdbc.update("INSERT INTO " + schema + "." + table + " SELECT * FROM public." + table + " WHERE " + column + "=?", id))
            .as("synthetic historical fixture in " + table).isPositive();
    }

    private KnownSource knownSource() throws Exception {
        var owner = createDestination("known", customerId);
        var handle = createHistoricalBinding(owner);
        String ownerId = "handoff-owner-" + fixtureId;
        jdbc.update("""
            INSERT INTO platform_users(id,email,display_name,password_hash,role,status,customer_id,created_at,updated_at)
            VALUES (?,?,?,'unused','CUSTOMER_ADMIN','ACTIVE',?,?,?)
            """, ownerId, ownerId + "@example.invalid", "Synthetic owner", customerId, Timestamp.from(now), Timestamp.from(now));
        String offer = "known-offer-" + fixtureId;
        associateOwner(owner, ownerId, offer);
        associateOwner(destination, ownerId, "destination-offer-" + fixtureId);
        var proof = KnownNoEffectHandoffTestFixture.settle(jdbc, json, transactionManager, integrationIntegrity,
            owner.deployment(), owner.version(), targetId, ownerId, offer, now);
        return new KnownSource(owner, handle, ownerId, proof);
    }

    private void associateOwner(Destination owner, String userId, String offer) {
        jdbc.update("INSERT INTO platform_customer_offers VALUES (?,'{}','synthetic-offer-fingerprint','review','fixture',?)", offer, Timestamp.from(now));
        jdbc.update("""
            INSERT INTO platform_customer_offer_entitlements(id,customer_id,offer_id,owner_user_id,invitation_reference,status,revision,expires_at,created_at,updated_at)
            VALUES (?,?,?,?,?,'ACTIVE',1,?,?,?)
            """, "ent-" + offer, customerId, offer, userId, "synthetic-invitation", Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now), Timestamp.from(now));
        jdbc.update("""
            INSERT INTO platform_customer_offer_deployments(deployment_id,customer_id,tenant_id,offer_id,offer_fingerprint,
                authorization_entitlement_revision,operation_key,created_at,bootstrap_target_profile_id)
            VALUES (?,?,?,?,'synthetic-offer-fingerprint',1,'synthetic-bootstrap',?,?)
            """, owner.deployment().getId(), customerId, owner.deployment().getTenantId(), offer, Timestamp.from(now), targetId);
    }

    private InventoryApproval inventory() {
        try {
            String fingerprint = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(MANAGEMENT_KEY.getBytes(StandardCharsets.UTF_8)));
            return new InventoryApproval(fingerprint, "synthetic-full-management-visibility", "synthetic-no-external-writer-window");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String grant() {
        String id = "known-grant-" + fixtureId;
        jdbc.update("""
            INSERT INTO provider_usage_grants(id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,approved_by,approved_role,approved_at)
            VALUES (?,'synthetic-campaign',?,?,'staging',?,'{}','fixture','PLATFORM_ADMIN',?)
            """, id, customerId, targetId, "f".repeat(64), Timestamp.from(now));
        financialEvent("grant-approved-" + fixtureId, id, id, "GRANT_APPROVED"); return id;
    }

    private String allocation(String grant, String deploymentId, String reservationId) {
        String id = "allocation-" + fixtureId;
        String payload = json.write(Map.of("approvalId", id, "grantId", grant, "scope", Map.of("deploymentId", deploymentId),
            "initialAdmission", Map.of("reservationId", reservationId)));
        jdbc.update("""
            INSERT INTO provider_usage_allocations(approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,
                approval_fingerprint,runtime_allocation_fingerprint,payload_json,initialization_state,created_at)
            VALUES (?,?,?,?,?,'staging',?,?,?,'UNINITIALIZED',?)
            """, id, grant, customerId, deploymentId, targetId, "a".repeat(64), "b".repeat(64), payload, Timestamp.from(now));
        return id;
    }

    private void financialEvent(String id, String subject, String grant, String type) {
        jdbc.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES (?,?,?,?,'fixture','PLATFORM_ADMIN',?,?)",
            id, type, subject, grant, "f".repeat(64), Timestamp.from(now));
    }

    private String bindingFingerprint(DeploymentProviderResourceHandleEntity handle) {
        return json.read(handle.getMetadataJson(), com.fasterxml.jackson.databind.JsonNode.class).path("fingerprint").asText();
    }

    private UnusedVectorBindingHandoffContracts.Request knownRequest(KnownSource known, Destination destination, InventoryApproval review) {
        return new UnusedVectorBindingHandoffContracts.Request("known-handoff", APPROVAL,
            List.of(source(), new Source(known.handle().getId(), null, bindingFingerprint(known.handle()), known.proof().receiptId())),
            connection(destination), review);
    }

    private Destination createDestination(String label, String customer) {
        String deploymentId = "synthetic-" + label + "-dep-" + fixtureId;
        String tenant = "synthetic-" + label + "-tenant-" + fixtureId;
        jdbc.update("""
            INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at)
            VALUES (?,?,?,?,'ACTIVE',?,?)
            """, tenant, customer, tenant, tenant, Timestamp.from(now), Timestamp.from(now));
        var deployment = new DeploymentEntity();
        deployment.setId(deploymentId); deployment.setCustomerId(customer); deployment.setTenantId(tenant);
        deployment.setName(deploymentId); deployment.setEnvironmentName("synthetic-staging");
        deployment.setTemplateId("synthetic-template"); deployment.setBehaviorType("CONVERSATIONAL"); deployment.setStatus("CONFIGURED");
        deployment.setCreatedAt(now); deployment.setUpdatedAt(now); deployments.saveAndFlush(deployment);
        var draft = new DeploymentDraftEntity();
        draft.setId("synthetic-" + label + "-draft-" + fixtureId); draft.setDeploymentId(deploymentId);
        draft.setRevisionNumber(1); draft.setStatus("PUBLISHED"); draft.setActionsConfigJson("{}");
        draft.setEntityConfigJson("{\"ai-entities\":{\"synthetic_document\":{}}}"); draft.setRoutingConfigJson("{}");
        draft.setProviderConfigJson(PROVIDER_CONFIG); draft.setSecurityConfigJson("{}"); draft.setPromptConfigJson("{}");
        draft.setCreatedAt(now); draft.setUpdatedAt(now); drafts.saveAndFlush(draft);
        var version = new DeploymentVersionEntity();
        version.setId("synthetic-" + label + "-ver-" + fixtureId); version.setDeploymentId(deploymentId);
        version.setSourceDraftId(draft.getId()); version.setVersionLabel("synthetic-v1"); version.setStatus("PUBLISHED");
        version.setConfigHash(json.fingerprint(Map.of("fixture", fixtureId, "deployment", deploymentId)));
        version.setActionsConfigJson("{}"); version.setEntityConfigJson(draft.getEntityConfigJson()); version.setRoutingConfigJson("{}");
        version.setProviderConfigJson(PROVIDER_CONFIG); version.setSecurityConfigJson("{}"); version.setPromptConfigJson("{}");
        version.setActionsArtifactYaml("{}"); version.setEntityArtifactYaml("{}"); version.setRoutingArtifactYaml("{}");
        version.setManifestJson("{}"); version.setPublishedAt(now); versions.saveAndFlush(version);
        return new Destination(deployment, version);
    }

    private DeploymentProviderResourceHandleEntity createHistoricalBinding(Destination owner) throws Exception {
        String key = "synthetic-historical-fingerprint-key";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String credentialDigest = HexFormat.of().formatHex(mac.doFinal(mapper.writeValueAsBytes(
            mapper.createObjectNode().put("authType", "TOKEN").put("token", OLD_TOKEN))));
        var metadata = mapper.createObjectNode().put("customerId", customerId).put("tenantId", owner.deployment().getTenantId())
            .put("deploymentId", owner.deployment().getId()).put("targetProfileId", targetId).put("versionId", owner.version().getId())
            .put("configHash", owner.version().getConfigHash()).put("clusterId", clusterId).put("projectId", "synthetic-project")
            .put("regionId", "synthetic-region").put("deploymentOption", "Free").put("databaseName", "default")
            .put("approvalReference", APPROVAL).put("authType", "TOKEN").put("credentialDigest", credentialDigest);
        String requestFingerprint = DeploymentEvidenceFingerprint.hash(mapper, metadata);
        metadata.put("tokenSecretRef", secretRef("TOKEN", owner)).put("endpointHost", endpoint.substring("https://".length()))
            .put("collectionPrefix", prefix(owner)).put("credentialFingerprintSecretRef", secretRef("FINGERPRINT", owner))
            .put("lifecycle", "RETAIN_EXTERNAL").put("externalResourceOwned", false).put("requestFingerprint", requestFingerprint);
        metadata.put("fingerprint", DeploymentEvidenceFingerprint.hash(mapper, metadata));
        var handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("binding-" + owner.deployment().getId()); handle.setDeploymentId(owner.deployment().getId());
        handle.setTargetProfileId(targetId); handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind(DeploymentExistingVectorBindingService.RESOURCE_KIND); handle.setProviderResourceUuid(clusterId);
        handle.setProviderProjectUuid("synthetic-project"); handle.setFqdn(endpoint); handle.setStatus("ACTIVE");
        handle.setLastObservedStatus("RUNNING"); handle.setLastObservedAt(now); handle.setCreatedAt(now); handle.setUpdatedAt(now);
        handle.setMetadataJson(metadata.toString()); handles.saveAndFlush(handle);
        var ownership = Map.of("deploymentId", owner.deployment().getId(), "targetProfileId", targetId,
            "resourceKind", DeploymentExistingVectorBindingService.RESOURCE_KIND, "externalResourceOwned", false);
        secrets.upsertManagedSecret(secretRef("TOKEN", owner), OLD_TOKEN, ownership);
        secrets.upsertManagedSecret(secretRef("FINGERPRINT", owner), key, ownership);
        return handle;
    }

    private CancelUnusedReservation seedUnusedAuthorization(Destination owner, DeploymentProviderResourceHandleEntity handle) {
        String deploymentId = owner.deployment().getId();
        var limits = new CustomerOfferContracts.ReviewedLimits("synthetic-measurement", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1);
        var topology = new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1);
        var scope = new CustomerOfferContracts.AdmissionContext(customerId, owner.deployment().getTenantId(), deploymentId,
            null, targetId, CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "synthetic-offer", "synthetic-offer-fingerprint",
            1, "synthetic-placement-proof", CustomerOfferContracts.Operation.APPLY, "synthetic-apply", limits, topology);
        var amounts = new EnumMap<Dimension, Long>(Dimension.class);
        for (Dimension dimension : Dimension.values()) amounts.put(dimension, 1L);
        var manifest = new Manifest("manifest-" + deploymentId, deploymentId, owner.version().getId(),
            owner.version().getConfigHash(), targetId, json.fingerprint(handle.getId()), "synthetic-topology", new Vector(amounts),
            Set.of("synthetic-prerequisite"));
        var command = new Reserve(scope.operationKey(), manifest.id(), scope);
        String manifestFingerprint = json.fingerprint(manifest);
        String requestFingerprint = json.fingerprint(Map.of("customer", customerId, "tenant", scope.tenantId(),
            "deployment", deploymentId, "manifestId", manifest.id(), "manifestFingerprint", manifestFingerprint,
            "target", targetId, "customerContext", scope));
        var reservation = new Reservation("reservation-" + deploymentId, customerId, scope.tenantId(), deploymentId,
            targetId, Intent.CUSTOMER_STAGING, scope.operationKey(), requestFingerprint, "synthetic-generation-" + fixtureId,
            0, State.RESERVED, manifest.demand(), "synthetic-policy", manifest.id(), manifestFingerprint, manifest.bindingFingerprint(),
            now, now, now, now.plusSeconds(3600), 0, null, null, ExpiryState.CURRENT, "synthetic-notice-cycle", null,
            0, null, null, null, null, "synthetic-initial-readback", null, json.fingerprint(scope), scope.operation(), null);
        var authorization = new AuthorizationReceipt(reservation.id(), reservation.generation(), manifestFingerprint,
            requestFingerprint, json.fingerprint(scope), command);
        // Historical inputs are synthetic; only the production cancellation service creates cancellation proof.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            jdbc.update("""
                INSERT INTO platform_target_retirement_gates
                (id,deployment_id,target_profile_id,state,epoch,row_version,updated_at)
                VALUES (?,?,?,'AVAILABLE',?,?,?)
                """, "gate-" + deploymentId, deploymentId, targetId, SOURCE_GATE_EPOCH, SOURCE_GATE_ROW_VERSION, Timestamp.from(now));
            jdbc.update("""
                INSERT INTO deployment_admission_reviews(kind,record_key,fingerprint,payload_json,reviewed_by,reviewed_at)
                VALUES ('MANIFEST',?,?,?,'synthetic-reviewer',?)
                """, manifest.id(), manifestFingerprint, json.write(manifest), Timestamp.from(now));
            jdbc.update("""
                INSERT INTO deployment_capacity_reservations
                (id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,generation,revision,state,
                 current_slot,expires_at,expiry_state,payload_json,updated_at)
                VALUES (?,?,?,?,?,?,?,0,'RESERVED',?,?,'CURRENT',?,?)
                """, reservation.id(), customerId, deploymentId, targetId, scope.operationKey(), requestFingerprint,
                reservation.generation(), deploymentId + ":" + targetId, Timestamp.from(reservation.expiresAt()),
                json.write(reservation), Timestamp.from(now));
            jdbc.update("""
                INSERT INTO deployment_admission_authorizations
                (customer_id,deployment_id,target_profile_id,operation_key,reservation_id,generation,operation,version_id,
                 request_fingerprint,context_fingerprint,payload_json,created_at)
                VALUES (?,?,?,?,?,?,'APPLY',?,?,?,?,?)
                """, customerId, deploymentId, targetId, scope.operationKey(), reservation.id(), reservation.generation(),
                owner.version().getId(), requestFingerprint, authorization.contextFingerprint(), json.write(authorization), Timestamp.from(now));
            jdbc.update("""
                INSERT INTO deployment_admission_events(id,reservation_id,revision,event_type,actor_id,evidence_reference,created_at)
                VALUES (?,?,0,'RESERVED','synthetic-reviewer','synthetic-initial-readback',?)
                """, "event-" + deploymentId, reservation.id(), Timestamp.from(now));
        });
        return new CancelUnusedReservation(reservation.fence(), "synthetic-cancel", scope.operationKey(),
            requestFingerprint, manifestFingerprint, owner.version().getId(), owner.version().getConfigHash(), manifest.bindingFingerprint());
    }

    private String secretRef(String kind, Destination owner) {
        String token = DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(Map.of(
            "deploymentId", owner.deployment().getId(), "targetProfileId", targetId))).toUpperCase(Locale.ROOT);
        return "MANAGED_EXTERNAL_VECTOR_" + kind + "_" + token;
    }

    private String prefix(Destination owner) {
        return scopes.targetScopedProviderConfig(owner.deployment(), json.read(PROVIDER_CONFIG, com.fasterxml.jackson.databind.JsonNode.class),
            targetId).path("milvusCollectionPrefix").asText();
    }

    private ExistingVectorBindingContracts.Request connection(Destination owner) {
        return new ExistingVectorBindingContracts.Request(owner.version().getId(), clusterId, "synthetic-project", "synthetic-region",
            "Free", "default", null, null, APPROVAL, TOKEN, NEW_TOKEN);
    }

    private Source source() {
        return new Source(sourceHandle.getId(), cancellation.id(),
            json.read(sourceHandle.getMetadataJson(), com.fasterxml.jackson.databind.JsonNode.class).path("fingerprint").asText());
    }

    private UnusedVectorBindingHandoffContracts.Request request(Destination owner) {
        return new UnusedVectorBindingHandoffContracts.Request("synthetic-handoff", APPROVAL, List.of(source()), connection(owner));
    }

    private Receipt handoff(Destination owner, UnusedVectorBindingHandoffContracts.Request command) {
        return bindings.handoffUnused(owner.deployment().getId(), targetId, command);
    }

    private String readbackUrl(Destination owner, String operationKey) {
        return "/api/deployments/" + owner.deployment().getId() + "/targets/" + targetId
            + "/existing-vector-binding/handoff-unused/" + operationKey;
    }

    private void assertRejectedWithoutWrites(Runnable command) {
        var before = databaseSnapshot();
        Throwable denied = catchThrowable(command::run);
        assertThat(denied).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) denied).getStatusCode().is4xxClientError()).isTrue();
        assertThat(databaseSnapshot()).isEqualTo(before);
    }

    private Attempt attempt(Callable<Receipt> command) {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
        try {
            return new Attempt(command.call(), null);
        } catch (Exception denied) {
            return new Attempt(null, denied);
        } finally {
            PlatformTestSecurity.clearAuthentication();
        }
    }

    private Map<String, String> databaseSnapshot() {
        var result = new LinkedHashMap<String, String>();
        for (String table : List.of("deployment_provider_resource_handles", "platform_secrets", "platform_audit_events",
            "deployment_unused_vector_handoffs", "platform_target_retirement_gates", "provider_usage_grants", "provider_usage_allocations",
            "provider_usage_allowance_events", "deployment_capacity_reservations", "deployment_admission_events",
            "deployment_admission_authorizations", "deployment_admission_unused_cancellations", "deployment_admission_reviews", "deployment_admission_release_bindings", "platform_target_retirement_operations",
            "deployment_managed_dependency_graphs", "platform_deployment_releases", "platform_consumers", "platform_customers",
            "platform_customer_tenants", "platform_deployments", "platform_deployment_versions")) {
            result.put(table, tableSnapshot(table));
        }
        return result;
    }

    private Map<String, String> ownershipSnapshot() {
        var result = new LinkedHashMap<String, String>();
        for (String table : List.of("platform_customers", "platform_customer_tenants", "platform_deployments", "platform_deployment_versions"))
            result.put(table, tableSnapshot(table));
        return result;
    }

    private String tableSnapshot(String table) {
        String rows = jdbc.queryForObject("SELECT COALESCE(json_agg(row_to_json(snapshot) ORDER BY row_to_json(snapshot)::text)::text,'[]') FROM "
            + table + " snapshot", String.class);
        return json.fingerprint(rows);
    }

    private String targetGateSnapshot(Destination owner) {
        return jdbc.queryForObject("SELECT row_to_json(gate)::text FROM platform_target_retirement_gates gate WHERE deployment_id=? AND target_profile_id=?",
            String.class, owner.deployment().getId(), targetId);
    }

    private String row(String table, String id) {
        return jdbc.queryForObject("SELECT row_to_json(snapshot)::text FROM " + table + " snapshot WHERE id=?", String.class, id);
    }

    private long count(String table, String predicate, Object... arguments) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + predicate, Long.class, arguments);
    }

    private void assertNoEffects(Destination owner) {
        for (String table : List.of("platform_deployment_releases", "deployment_admission_release_bindings",
            "deployment_managed_dependency_graphs", "provider_usage_allocations")) {
            assertThat(count(table, "deployment_id=?", owner.deployment().getId())).as(table).isZero();
        }
        assertThat(count("platform_consumers", "bound_deployment_id=?", owner.deployment().getId())).isZero();
        assertThat(count("deployment_capacity_reservations", "deployment_id=? AND state<>'RELEASED'", owner.deployment().getId())).isZero();
    }

    private record Destination(DeploymentEntity deployment, DeploymentVersionEntity version) { }
    private record KnownSource(Destination owner, DeploymentProviderResourceHandleEntity handle, String ownerId,
                               KnownNoEffectHandoffTestFixture.SettledSource proof) { }
    private record Attempt(Receipt receipt, Exception failure) { }
}
