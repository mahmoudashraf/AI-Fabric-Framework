package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.PlatformBackendApplication;
import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionJson;
import com.ai.fabric.platform.backend.deployment.admission.DeploymentAdmissionService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorBindingHandoffContracts;
import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.AuthenticationType.TOKEN;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Two actual independent CP databases, real Flyway/JPA/transactions; only provider reads are mocked. */
class UnusedVectorTransferPostgresIT {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static Cp sourceCp, destinationCp;
    static final String KEY = "synthetic-full-visibility-read-key";
    static final String TOKEN_VALUE = "synthetic-serving-token";
    static final String APPROVAL = "synthetic-original-vector-approval";
    static final String CONFIG = "{\"vectorStrategy\":\"milvus\",\"vectorProvisioningMode\":\"EXTERNAL_EXISTING\",\"vectorStoragePosture\":\"SHARED\",\"milvusExternalBindingRequired\":true,\"milvusAuthenticationType\":\"TOKEN\",\"milvusCollectionPrefix\":\"synthetic_\"}";
    String suffix, cluster, endpoint;
    Owner old, current, destination;
    ExistingVectorBindingContracts.Summary oldBinding, currentBinding;
    UnusedReservationCancellationReceipt cancelled;
    ReleaseRequest request;

    @BeforeAll static void boot() {
        POSTGRES.start();
        var root = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        root.execute("CREATE DATABASE transfer_destination");
        sourceCp = new Cp("source", POSTGRES.getJdbcUrl());
        destinationCp = new Cp("destination", "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/transfer_destination");
    }
    @AfterAll static void close() {
        if (destinationCp != null) destinationCp.context.close();
        if (sourceCp != null) sourceCp.context.close();
        POSTGRES.stop();
    }
    @BeforeEach void fixture() {
        authenticate("PLATFORM_ADMIN", "SESSION");
        reset(sourceCp.provider, destinationCp.provider);
        suffix = UUID.randomUUID().toString().substring(0, 8);
        cluster = "transfer-cluster-" + suffix; endpoint = "https://transfer-" + suffix + ".zilliz.com";
        for (Cp cp : List.of(sourceCp, destinationCp)) {
            cp.secrets.updateSecret("ZILLIZ_CLOUD_API_KEY", KEY);
            when(cp.provider.getCluster(eq(cluster), eq(KEY))).thenReturn(new ZillizCloudControlPlaneClient.ZillizClusterSummary(
                cluster, "synthetic", "project", "region", "Serverless", "", 0, "RUNNING", endpoint, "Free"));
            when(cp.provider.listServingCollections(eq(endpoint), anyString())).thenReturn(List.of());
        }
        old = owner(sourceCp, "old", "source-customer-" + suffix);
        current = owner(sourceCp, "current", old.customer());
        destination = owner(destinationCp, "destination", "destination-customer-" + suffix);
        oldBinding = sourceCp.bindings.bind(old.dep(), old.target(), connection(old, APPROVAL));
        cancelled = sourceCp.admission.cancelUnusedReservation(old.dep(), old.target(), unusedAuthorization(old, oldBinding));
        var handoff = new UnusedVectorBindingHandoffContracts.Request("handoff-" + suffix, APPROVAL,
            List.of(new UnusedVectorBindingHandoffContracts.Source(oldBinding.bindingId(), cancelled.id(), oldBinding.fingerprint())), connection(current, APPROVAL));
        currentBinding = sourceCp.bindings.handoffUnused(current.dep(), current.target(), handoff).binding();
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_capacity_reservations WHERE deployment_id=?", Integer.class, current.dep())).isZero();
        request = new ReleaseRequest("release-" + suffix, "reviewed-transfer-" + suffix, currentBinding.bindingId(), scope(destinationCp, destination),
            List.of(new Source(currentBinding.bindingId(), currentBinding.fingerprint(), null), new Source(oldBinding.bindingId(), oldBinding.fingerprint(), cancelled.id())), inventory());
    }
    @AfterEach void noProviderMutations() {
        for (Cp cp : List.of(sourceCp, destinationCp)) assertThat(mockingDetails(cp.provider).getInvocations())
            .allSatisfy(i -> assertThat(i.getMethod().getName()).isIn("getCluster", "listServingCollections"));
        SecurityContextHolder.clearContext();
    }

    @Test void realNoReservationReleaseAndReviewedCrossCustomerClaimPreserveHistoryAndCloseSource() {
        String oldMeta = sourceCp.jdbc.queryForObject("SELECT metadata_json FROM deployment_provider_resource_handles WHERE id=?", String.class, oldBinding.bindingId());
        ReleaseReceipt receipt = sourceCp.transfer.release(request);
        assertThat(receipt.sources()).hasSize(2);
        assertThat(receipt.sources()).anyMatch(s -> s.cancellationReceiptId() == null).anyMatch(s -> cancelled.id().equals(s.cancellationReceiptId()));
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_provider_resource_handles WHERE provider_resource_uuid=? AND status='ACTIVE'", Integer.class, cluster)).isZero();
        assertThat(sourceCp.jdbc.queryForObject("SELECT metadata_json FROM deployment_provider_resource_handles WHERE id=?", String.class, oldBinding.bindingId())).isEqualTo(oldMeta);
        assertThat(sourceCp.transfer.release(request)).isEqualTo(receipt);
        ReleaseReadback readback = sourceCp.transfer.releaseReadback(receipt.id());
        ReviewReceipt review = enroll(readback);
        ClaimRequest claim = claim(review);
        ClaimReceipt claimed = destinationCp.transfer.claim(claim);
        String committed = destinationCp.snapshot();
        assertThat(destinationCp.transfer.claim(claim)).isEqualTo(claimed);
        assertThat(destinationCp.snapshot()).isEqualTo(committed);
        assertThat(claimed.destination().customerId()).isEqualTo(destination.customer()).isNotEqualTo(old.customer());
        assertThat(claimed.binding().collectionPrefix()).isEqualTo(destination.prefix()).isNotEqualTo(current.prefix());
        assertThat(destinationCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_provider_resource_handles WHERE provider_resource_uuid=? AND status='ACTIVE'", Integer.class, cluster)).isEqualTo(1);
        assertThatThrownBy(() -> sourceCp.bindings.bind(current.dep(), current.target(), connection(current, APPROVAL))).hasMessageContaining("RELEASED_FOR_TRANSFER");
        assertThatThrownBy(() -> sourceCp.jdbc.update("UPDATE deployment_provider_resource_handles SET status='ACTIVE' WHERE id=?", currentBinding.bindingId())).hasMessageContaining("permanently fenced");
        assertThat(jsonText(receipt)).doesNotContain(KEY, TOKEN_VALUE);
        assertThat(jsonText(review)).doesNotContain(KEY, TOKEN_VALUE);
        assertThat(destinationCp.jdbc.queryForObject("SELECT COUNT(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void explicitConfigurationOnlyDetachmentIsAtomicPreservesDraftsAndHasImmutableReplay() {
        var consumer = configuredConsumer();
        var command = withDetachments(List.of(expectation(consumer, 1, 0)));
        String appBefore = scopedRows("ai_workspace_installations", "id", consumer.appId());
        String draftBefore = scopedRows("platform_deployment_drafts", "deployment_id", current.dep());
        String versionBefore = scopedRows("platform_deployment_versions", "deployment_id", current.dep());
        String creationBefore = scopedRows("platform_customer_entitlement_commands", "id", consumer.creationId());
        String historyBefore = scopedRows("platform_consumer_binding_history", "id", consumer.historyId());
        String cancellationBefore = scopedRows("deployment_admission_unused_cancellations", "id", cancelled.id());

        assertThatThrownBy(() -> sourceCp.context.getBean(PlatformCustomerConsumerService.class).updateBinding(
            current.customer(), consumer.consumerId(), new UpdatePlatformConsumerBindingRequest(null, null, null,
                "Synthetic generic detach", 1L, "generic-detach-" + suffix)))
            .hasMessageContaining("Reviewed customer deployments");

        ReleaseReceipt receipt = sourceCp.transfer.release(command);
        assertThat(receipt.consumerDetachments()).singleElement().satisfies(detached -> {
            assertThat(detached.consumerEntityId()).isEqualTo(consumer.entityId());
            assertThat(detached.fromBindingRevision()).isEqualTo(1);
            assertThat(detached.toBindingRevision()).isEqualTo(2);
            assertThat(detached.preservedDraftWorkspaces()).containsExactly(new DraftWorkspaceExpectation(consumer.appId(), 0));
            var history = sourceCp.jdbc.queryForMap("SELECT * FROM platform_consumer_binding_history WHERE id=?", detached.historyId());
            assertThat(history.get("operation_key")).isEqualTo(detached.operationKey());
            assertThat(history.get("from_deployment_id")).isEqualTo(current.dep());
            assertThat(history.get("from_target_profile_id")).isEqualTo(current.target());
            assertThat(history.get("to_deployment_id")).isNull();
            assertThat(history.get("to_target_profile_id")).isNull();
            assertThat(((Number) history.get("from_binding_revision")).longValue()).isEqualTo(1);
            assertThat(((Number) history.get("to_binding_revision")).longValue()).isEqualTo(2);
        });
        var assignment = sourceCp.jdbc.queryForMap("SELECT * FROM platform_consumers WHERE id=?", consumer.entityId());
        assertThat(assignment.get("status")).isEqualTo("ACTIVE");
        assertThat(assignment.get("bound_deployment_id")).isNull();
        assertThat(assignment.get("bound_target_profile_id")).isNull();
        assertThat(assignment.get("bound_release_id")).isNull();
        assertThat(((Number) assignment.get("binding_revision")).longValue()).isEqualTo(2);
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM platform_consumer_binding_history WHERE consumer_entity_id=?", Integer.class, consumer.entityId())).isEqualTo(2);
        assertThat(scopedRows("ai_workspace_installations", "id", consumer.appId())).isEqualTo(appBefore);
        assertThat(scopedRows("platform_deployment_drafts", "deployment_id", current.dep())).isEqualTo(draftBefore);
        assertThat(scopedRows("platform_deployment_versions", "deployment_id", current.dep())).isEqualTo(versionBefore);
        assertThat(scopedRows("platform_customer_entitlement_commands", "id", consumer.creationId())).isEqualTo(creationBefore);
        assertThat(scopedRows("platform_consumer_binding_history", "id", consumer.historyId())).isEqualTo(historyBefore);
        assertThat(scopedRows("deployment_admission_unused_cancellations", "id", cancelled.id())).isEqualTo(cancellationBefore);
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_capacity_reservations WHERE deployment_id=?", Integer.class, current.dep())).isZero();
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployment_releases WHERE deployment_id=?", Integer.class, current.dep())).isZero();
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM platform_audit_events WHERE action='CUSTOMER_CONSUMER_BINDING_UPDATED' AND target_id=?", Integer.class, consumer.consumerId())).isEqualTo(1);
        assertThat(sourceCp.transferJson.receiptHash(receipt)).isEqualTo(receipt.fingerprint());
        String committed = sourceCp.snapshot();
        assertThat(sourceCp.transfer.release(command)).isEqualTo(receipt);
        assertThat(sourceCp.transferJson.read(sourceCp.transfer.releaseReadback(receipt.id()).releaseJson(), ReleaseReceipt.class)).isEqualTo(receipt);
        assertThat(sourceCp.snapshot()).isEqualTo(committed);
        assertThatThrownBy(() -> sourceCp.jdbc.update("UPDATE deployment_vector_transfer_releases SET payload_json='{}' WHERE id=?", receipt.id()))
            .hasMessageContaining("immutable");
        assertThat(jsonText(receipt)).doesNotContain(KEY, TOKEN_VALUE);
    }

    @ParameterizedTest @ValueSource(strings = {"omitted", "stale-consumer", "changed-app", "missing-app", "extra-app",
        "deleted-app", "unlisted-app", "active-app", "formerly-active-app", "disabled-app", "foreign-app",
        "missing-provenance", "wrong-provenance", "wrong-tenant-provenance", "reservation", "release", "bound-release", "foreign-consumer"})
    void configurationOnlyDetachmentRejectsAnyUnreviewedAssignmentWorkspaceOrRuntimeEffect(String change) {
        var consumer = configuredConsumer();
        List<UnusedConsumerDetachment> expected = List.of(expectation(consumer, 1, 0));
        switch (change) {
            case "omitted" -> expected = List.of();
            case "stale-consumer" -> sourceCp.jdbc.update("UPDATE platform_consumers SET binding_revision=2 WHERE id=?", consumer.entityId());
            case "changed-app" -> sourceCp.jdbc.update("UPDATE ai_workspace_installations SET row_version=1 WHERE id=?", consumer.appId());
            case "missing-app" -> expected = List.of(new UnusedConsumerDetachment(consumer.entityId(), 1, List.of()));
            case "extra-app" -> expected = List.of(new UnusedConsumerDetachment(consumer.entityId(), 1,
                List.of(new DraftWorkspaceExpectation(consumer.appId(), 0), new DraftWorkspaceExpectation("unknown-app", 0))));
            case "deleted-app" -> sourceCp.jdbc.update("DELETE FROM ai_workspace_installations WHERE id=?", consumer.appId());
            case "unlisted-app" -> draftWorkspace("unlisted-" + suffix, consumer.entityId());
            case "active-app" -> sourceCp.jdbc.update("UPDATE ai_workspace_installations SET status='ACTIVE' WHERE id=?", consumer.appId());
            case "formerly-active-app" -> sourceCp.jdbc.update("UPDATE ai_workspace_installations SET activated_at=? WHERE id=?", time(), consumer.appId());
            case "disabled-app" -> sourceCp.jdbc.update("UPDATE ai_workspace_installations SET disabled_at=? WHERE id=?", time(), consumer.appId());
            case "foreign-app" -> {
                owner(sourceCp, "foreign-app-owner", "foreign-app-customer-" + suffix);
                sourceCp.jdbc.update("UPDATE ai_workspace_installations SET customer_id=? WHERE id=?", "foreign-app-customer-" + suffix, consumer.appId());
            }
            case "missing-provenance" -> sourceCp.jdbc.update("DELETE FROM platform_customer_entitlement_commands WHERE id=?", consumer.creationId());
            case "wrong-provenance" -> sourceCp.jdbc.update("UPDATE platform_customer_entitlement_commands SET result_json='{}' WHERE id=?", consumer.creationId());
            case "wrong-tenant-provenance" -> sourceCp.jdbc.update("UPDATE platform_customer_offer_deployments SET tenant_id=? WHERE deployment_id=?", old.tenant(), current.dep());
            case "reservation" -> unusedAuthorization(current, currentBinding);
            case "release" -> sourceCp.jdbc.update("""
                INSERT INTO platform_deployment_releases(id,deployment_id,deployment_version_id,target_profile_id,status,
                    verification_status,provisioning_status,provisioning_target,provisioning_details_json,created_at,applied_at,updated_at)
                VALUES (?,?,?,?,'APPLIED_VERIFIED','PASSED','READY','COOLIFY','{}',?,?,?)
                """, "runtime-" + suffix, current.dep(), current.version().getId(), current.target(), time(), time(), time());
            case "bound-release" -> sourceCp.jdbc.update("UPDATE platform_consumers SET bound_release_id=? WHERE id=?", "runtime-" + suffix, consumer.entityId());
            case "foreign-consumer" -> {
                owner(sourceCp, "foreign-consumer-owner", "foreign-consumer-customer-" + suffix);
                sourceCp.jdbc.update("UPDATE platform_consumers SET customer_id=? WHERE id=?", "foreign-consumer-customer-" + suffix, consumer.entityId());
            }
            default -> throw new AssertionError(change);
        }
        var attempted = withDetachments(expected);
        String before = sourceCp.snapshot();
        assertThatThrownBy(() -> sourceCp.transfer.release(attempted)).isInstanceOf(RuntimeException.class);
        assertThat(sourceCp.snapshot()).isEqualTo(before);
        assertThat(sourceCp.jdbc.queryForObject("SELECT bound_deployment_id FROM platform_consumers WHERE id=?", String.class, consumer.entityId())).isEqualTo(current.dep());
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM platform_consumer_binding_history WHERE consumer_entity_id=?", Integer.class, consumer.entityId())).isEqualTo(1);
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_vector_transfer_releases WHERE cluster_id=?", Integer.class, cluster)).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"payload", "workspace", "operation-key"})
    void successfulConfigurationDetachmentCannotBeReplayedWithChangedApprovalOrKey(String change) {
        var consumer = configuredConsumer();
        var command = withDetachments(List.of(expectation(consumer, 1, 0)));
        sourceCp.transfer.release(command);
        ReleaseRequest altered = switch (change) {
            case "payload" -> withDetachments(List.of());
            case "workspace" -> withDetachments(List.of(expectation(consumer, 1, 1)));
            case "operation-key" -> new ReleaseRequest("different-" + suffix, command.approvalReference(), command.bindingId(),
                command.destination(), command.sources(), command.inventoryApproval(), command.consumerDetachments());
            default -> throw new AssertionError(change);
        };
        String before = sourceCp.snapshot();
        assertThatThrownBy(() -> sourceCp.transfer.release(altered)).isInstanceOf(RuntimeException.class);
        assertThat(sourceCp.snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER_CONSUMER_BINDING_UPDATED", "UNUSED_VECTOR_SOURCE_RELEASED"})
    void configurationDetachmentAndReleaseFullyRollBackWhenEitherAuditFails(String action) {
        var consumer = configuredConsumer();
        var command = withDetachments(List.of(expectation(consumer, 1, 0)));
        String before = sourceCp.snapshot();
        sourceCp.jdbc.execute("CREATE FUNCTION detachment_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='"
            + action + "' THEN RAISE EXCEPTION 'synthetic detachment audit failure'; END IF; RETURN NEW; END $$");
        sourceCp.jdbc.execute("CREATE TRIGGER detachment_audit_failure BEFORE INSERT ON platform_audit_events FOR EACH ROW EXECUTE FUNCTION detachment_audit_failure()");
        try {
            assertThatThrownBy(() -> sourceCp.transfer.release(command)).isInstanceOf(RuntimeException.class);
            assertThat(sourceCp.snapshot()).isEqualTo(before);
            assertThat(sourceCp.jdbc.queryForObject("SELECT binding_revision FROM platform_consumers WHERE id=?", Long.class, consumer.entityId())).isEqualTo(1);
        } finally {
            sourceCp.jdbc.execute("DROP TRIGGER detachment_audit_failure ON platform_audit_events");
            sourceCp.jdbc.execute("DROP FUNCTION detachment_audit_failure()");
        }
        assertThat(sourceCp.transfer.release(command).consumerDetachments()).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"consumer", "history", "app"})
    void configurationDetachmentRowLocksFailClosedWithoutPartialRelease(String resource) throws Exception {
        var consumer = configuredConsumer();
        var command = withDetachments(List.of(expectation(consumer, 1, 0)));
        String table = switch (resource) {
            case "consumer" -> "platform_consumers";
            case "history" -> "platform_consumer_binding_history";
            case "app" -> "ai_workspace_installations";
            default -> throw new AssertionError(resource);
        };
        String id = resource.equals("consumer") ? consumer.entityId() : resource.equals("history") ? consumer.historyId() : consumer.appId();
        String before = sourceCp.snapshot();
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var holder = workers.submit(() -> new org.springframework.transaction.support.TransactionTemplate(
                sourceCp.context.getBean(org.springframework.transaction.PlatformTransactionManager.class)).execute(tx -> {
                    sourceCp.jdbc.queryForList("SELECT id FROM " + table + " WHERE id=? FOR UPDATE", String.class, id);
                    held.countDown();
                    try { if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("synthetic lock timeout"); }
                    catch (InterruptedException ex) { throw new IllegalStateException(ex); }
                    return true;
                }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                var attempted = workers.submit(() -> attempt(() -> sourceCp.transfer.release(command)));
                assertThat(attempted.get(5, TimeUnit.SECONDS).failure()).isNotNull();
                assertThat(sourceCp.snapshot()).isEqualTo(before);
            } finally { release.countDown(); }
            holder.get(10, TimeUnit.SECONDS);
        }
        assertThat(sourceCp.transfer.release(command).consumerDetachments()).hasSize(1);
    }

    @Test void archivedSourceKeepsPermanentTerminalReadbackAndExactReplay() {
        ReleaseReceipt receipt = sourceCp.transfer.release(request);
        var lifecycle = sourceCp.context.getBean(DeploymentService.class);
        lifecycle.archiveDeployment(old.dep());
        lifecycle.archiveDeployment(current.dep());
        String committed = sourceCp.snapshot();
        assertThat(sourceCp.transfer.release(request)).isEqualTo(receipt);
        assertThat(sourceCp.transferJson.read(sourceCp.transfer.releaseReadback(receipt.id()).releaseJson(), ReleaseReceipt.class)).isEqualTo(receipt);
        assertThat(sourceCp.snapshot()).isEqualTo(committed);
        assertThatThrownBy(() -> sourceCp.jdbc.update("UPDATE deployment_provider_resource_handles SET status='ACTIVE' WHERE id=?", currentBinding.bindingId()))
            .hasMessageContaining("permanently fenced");
    }

    @Test void providerRefreshSkipsTerminalExternalReferencesAndLifecycleActionsStayBlocked() {
        ReleaseReceipt receipt = sourceCp.transfer.release(request);
        var resources = sourceCp.context.getBean(DeploymentProviderResourceActionService.class);
        String before = sourceCp.snapshot();
        int calls = mockingDetails(sourceCp.provider).getInvocations().size();
        assertThat(resources.listResources(null, current.dep(), current.target(), true)).singleElement()
            .satisfies(h -> assertThat(h.status()).isEqualTo(ExternalVectorClusterLocks.RELEASED));
        assertThatThrownBy(() -> resources.status(currentBinding.bindingId())).hasMessageContaining("not provider-managed lifecycle resources");
        assertThatThrownBy(() -> resources.delete(currentBinding.bindingId(), null)).hasMessageContaining("not provider-managed lifecycle resources");
        assertThat(mockingDetails(sourceCp.provider).getInvocations()).hasSize(calls);
        assertThat(sourceCp.snapshot()).isEqualTo(before);
        assertThat(sourceCp.transferJson.read(sourceCp.transfer.releaseReadback(receipt.id()).releaseJson(), ReleaseReceipt.class)).isEqualTo(receipt);
    }

    @ParameterizedTest @ValueSource(strings = {"owner", "version"})
    void terminalReadbackNeverAcceptsChangedOwnerOrVersion(String change) {
        ReleaseReceipt receipt = sourceCp.transfer.release(request);
        sourceCp.jdbc.update("UPDATE platform_deployments SET archived_at=? WHERE id=?", time(), current.dep());
        if (change.equals("owner")) {
            String replacement = "replacement-tenant-" + suffix;
            sourceCp.jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)",
                replacement, current.customer(), replacement, replacement, time(), time());
            sourceCp.jdbc.update("UPDATE platform_deployments SET tenant_id=? WHERE id=?", replacement, current.dep());
        } else sourceCp.jdbc.update("UPDATE platform_deployment_versions SET config_hash=? WHERE id=?", "0".repeat(64), current.version().getId());
        assertThatThrownBy(() -> sourceCp.transfer.releaseReadback(receipt.id())).hasMessageContaining("SOURCE_NOT_UNUSED");
        assertThatThrownBy(() -> sourceCp.transfer.release(request)).hasMessageContaining("SOURCE_NOT_UNUSED");
    }

    @ParameterizedTest @ValueSource(strings = {"missing-history", "wrong-cancellation", "version", "reservation", "consumer", "gate"})
    void sourceEffectsOrIncompleteHistoryCannotProduceARelease(String change) {
        ReleaseRequest altered = request;
        switch (change) {
            case "missing-history" -> altered = new ReleaseRequest(request.operationKey(), request.approvalReference(), request.bindingId(), request.destination(), List.of(request.sources().getFirst()), inventory());
            case "wrong-cancellation" -> altered = new ReleaseRequest(request.operationKey(), request.approvalReference(), request.bindingId(), request.destination(), List.of(request.sources().getFirst(), new Source(oldBinding.bindingId(), oldBinding.fingerprint(), "forged")), inventory());
            case "version" -> sourceCp.jdbc.update("UPDATE platform_deployment_versions SET status='DRAFT' WHERE id=?", current.version().getId());
            case "reservation" -> unusedAuthorization(current, currentBinding);
            case "consumer" -> sourceCp.jdbc.update("INSERT INTO platform_consumers(id,consumer_id,customer_id,display_name,status,bound_deployment_id,bound_target_profile_id,binding_revision,created_at,updated_at) VALUES (?,?,?,'Synthetic consumer','ACTIVE',?,?,0,?,?)", "consumer-" + suffix, "consumer-" + suffix, current.customer(), current.dep(), current.target(), time(), time());
            case "gate" -> sourceCp.jdbc.update("UPDATE platform_target_retirement_gates SET state='RETIRED' WHERE deployment_id=?", current.dep());
            default -> throw new AssertionError();
        }
        ReleaseRequest attempted = altered;
        String before = sourceCp.snapshot();
        assertThatThrownBy(() -> sourceCp.transfer.release(attempted)).isInstanceOf(RuntimeException.class);
        assertThat(sourceCp.snapshot()).isEqualTo(before);
    }

    @Test void releaseSecretAndLedgerRollbackOnAuditFailure() {
        String before = sourceCp.snapshot();
        sourceCp.jdbc.execute("CREATE FUNCTION transfer_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='UNUSED_VECTOR_SOURCE_RELEASED' THEN RAISE EXCEPTION 'synthetic transfer audit failure'; END IF; RETURN NEW; END $$");
        sourceCp.jdbc.execute("CREATE TRIGGER transfer_audit_failure BEFORE INSERT ON platform_audit_events FOR EACH ROW EXECUTE FUNCTION transfer_audit_failure()");
        try { assertThatThrownBy(() -> sourceCp.transfer.release(request)).isInstanceOf(RuntimeException.class); assertThat(sourceCp.snapshot()).isEqualTo(before); }
        finally { sourceCp.jdbc.execute("DROP TRIGGER transfer_audit_failure ON platform_audit_events"); sourceCp.jdbc.execute("DROP FUNCTION transfer_audit_failure()"); }
        assertThat(sourceCp.transfer.release(request).id()).isNotBlank();
    }

    @Test void changedSourceReplayOrDestinationScopeCannotRepointTheProof() {
        ReleaseReceipt release = sourceCp.transfer.release(request);
        Scope different = new Scope(request.destination().controlPlane(), request.destination().customerId(), request.destination().tenantId(),
            request.destination().deploymentId(), request.destination().targetProfileId(), request.destination().versionId(), request.destination().versionConfigHash(), "different_");
        assertThatThrownBy(() -> sourceCp.transfer.release(new ReleaseRequest(request.operationKey(), request.approvalReference(), request.bindingId(), different, request.sources(), inventory()))).isInstanceOf(RuntimeException.class);
        ReleaseReadback readback = sourceCp.transfer.releaseReadback(release.id());
        assertThatThrownBy(() -> sourceCp.transfer.registerReviewedSourceProof(reviewCommand(readback))).isInstanceOf(RuntimeException.class);
        ReviewReceipt review = enroll(readback);
        assertThatThrownBy(() -> destinationCp.transfer.claim(new ClaimRequest("claim-" + suffix, review.id(), connection(destination, "unreviewed"), inventory()))).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"empty-inventory", "credential", "local-history"})
    void destinationCannotBypassFreshInventoryOrLocalHistory(String change) {
        ReviewReceipt review = enroll(sourceCp.transfer.releaseReadback(sourceCp.transfer.release(request).id()));
        if (change.equals("empty-inventory")) when(destinationCp.provider.listServingCollections(endpoint, KEY)).thenReturn(List.of("foreign_collection"));
        ClaimRequest command = claim(review);
        if (change.equals("credential")) command = new ClaimRequest(command.operationKey(), command.reviewId(), command.connection(), new InventoryApproval("0".repeat(64), "review", "window"));
        if (change.equals("local-history")) destinationCp.bindings.bind(destination.dep(), destination.target(), connection(destination, "other-approval"));
        String before = destinationCp.snapshot(); ClaimRequest attempted = command;
        assertThatThrownBy(() -> destinationCp.transfer.claim(attempted)).isInstanceOf(RuntimeException.class);
        assertThat(destinationCp.snapshot()).isEqualTo(before);
    }

    @Test void claimRollbackAndConcurrentExactReplayHaveOneBindingAndOneReceipt() throws Exception {
        ReviewReceipt review = enroll(sourceCp.transfer.releaseReadback(sourceCp.transfer.release(request).id()));
        ClaimRequest command = claim(review);
        destinationCp.jdbc.execute("CREATE FUNCTION transfer_claim_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='UNUSED_VECTOR_DESTINATION_CLAIMED' THEN RAISE EXCEPTION 'synthetic claim audit failure'; END IF; RETURN NEW; END $$");
        destinationCp.jdbc.execute("CREATE TRIGGER transfer_claim_failure BEFORE INSERT ON platform_audit_events FOR EACH ROW EXECUTE FUNCTION transfer_claim_failure()");
        String before = destinationCp.snapshot();
        try { assertThatThrownBy(() -> destinationCp.transfer.claim(command)).isInstanceOf(RuntimeException.class); assertThat(destinationCp.snapshot()).isEqualTo(before); }
        finally { destinationCp.jdbc.execute("DROP TRIGGER transfer_claim_failure ON platform_audit_events"); destinationCp.jdbc.execute("DROP FUNCTION transfer_claim_failure()"); }
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { authenticate("PLATFORM_ADMIN", "SESSION"); start.await(); try { return destinationCp.transfer.claim(command); } finally { SecurityContextHolder.clearContext(); } });
            var b = pool.submit(() -> { authenticate("PLATFORM_ADMIN", "SESSION"); start.await(); try { return destinationCp.transfer.claim(command); } finally { SecurityContextHolder.clearContext(); } });
            start.countDown(); assertThat(a.get(30, TimeUnit.SECONDS)).isEqualTo(b.get(30, TimeUnit.SECONDS));
        }
        assertThat(destinationCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_vector_transfer_claims WHERE review_id=?", Integer.class, review.id())).isEqualTo(1);
    }

    @Test void emptyClusterOrdinaryBindsShareTheAdvisoryLockBeforeAnyHandleExists() throws Exception {
        cluster = "first-bind-" + suffix;
        endpoint = "https://first-bind-" + suffix + ".zilliz.com";
        Owner firstOwner = owner(sourceCp, "race-first", current.customer());
        Owner secondOwner = owner(sourceCp, "race-second", current.customer());
        CountDownLatch providerEntered = new CountDownLatch(1), releaseProvider = new CountDownLatch(1);
        when(sourceCp.provider.listServingCollections(eq(endpoint), anyString())).thenReturn(List.of());
        when(sourceCp.provider.getCluster(eq(cluster), eq(KEY))).thenAnswer(invocation -> {
            providerEntered.countDown();
            if (!releaseProvider.await(10, TimeUnit.SECONDS)) throw new AssertionError("provider latch expired");
            return new ZillizCloudControlPlaneClient.ZillizClusterSummary(cluster, "synthetic", "project", "region", "Serverless", "", 0, "RUNNING", endpoint, "Free");
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> attempt(() -> sourceCp.bindings.bind(firstOwner.dep(), firstOwner.target(), connection(firstOwner, APPROVAL))));
            try {
                assertThat(providerEntered.await(10, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> attempt(() -> sourceCp.bindings.bind(secondOwner.dep(), secondOwner.target(), connection(secondOwner, APPROVAL))));
                boolean advisoryWait = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline && !second.isDone()) {
                    if (sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM pg_locks WHERE locktype='advisory' AND NOT granted", Integer.class) > 0) { advisoryWait = true; break; }
                    Thread.sleep(20);
                }
                assertThat(advisoryWait).as("second first-bind transaction waits on the shared empty-cluster advisory lock").isTrue();
                releaseProvider.countDown();
                var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                assertThat(outcomes).filteredOn(o -> o.failure() == null).hasSize(1);
                assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement().satisfies(o ->
                    assertThat(o.failure()).hasMessageContaining("EXTERNAL_VECTOR_CLUSTER_CHANGED"));
                assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_provider_resource_handles WHERE provider_resource_uuid=? AND status='ACTIVE'", Integer.class, cluster)).isEqualTo(1);
            } finally { releaseProvider.countDown(); }
        }
    }

    @Test void concurrentChangedClaimCredentialsCannotCreateASecondBindingOrReceipt() throws Exception {
        ReviewReceipt review = enroll(sourceCp.transfer.releaseReadback(sourceCp.transfer.release(request).id()));
        ClaimRequest exact = claim(review);
        var c = exact.connection();
        ClaimRequest changed = new ClaimRequest(exact.operationKey(), exact.reviewId(), new ExistingVectorBindingContracts.Request(
            c.versionId(), c.clusterId(), c.projectId(), c.regionId(), c.expectedDeploymentOption(), c.databaseName(), null, null, c.approvalReference(), TOKEN, "different-synthetic-serving-token"), inventory());
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); return attempt(() -> destinationCp.transfer.claim(exact)); });
            var second = workers.submit(() -> { start.await(); return attempt(() -> destinationCp.transfer.claim(changed)); });
            start.countDown();
            var outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(o -> o.failure() == null).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.failure() != null).singleElement().satisfies(o ->
                assertThat(o.failure()).hasMessageContaining("BINDING_IMMUTABLE"));
        }
        assertThat(destinationCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_vector_transfer_claims WHERE review_id=?", Integer.class, review.id())).isEqualTo(1);
        assertThat(destinationCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_provider_resource_handles WHERE provider_resource_uuid=? AND status='ACTIVE'", Integer.class, cluster)).isEqualTo(1);
    }

    @Test void competingSourceReleaseCommandsCannotRepointThePermanentTombstone() throws Exception {
        var alternate = new ReleaseRequest("alternate-" + suffix, request.approvalReference(), request.bindingId(), request.destination(), request.sources(), inventory());
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); return attempt(() -> sourceCp.transfer.release(request)); });
            var second = workers.submit(() -> { start.await(); return attempt(() -> sourceCp.transfer.release(alternate)); });
            start.countDown();
            var outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(o -> o.failure() == null).hasSize(1);
            assertThat(outcomes).filteredOn(o -> o.failure() != null).hasSize(1);
        }
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_vector_transfer_releases WHERE cluster_id=?", Integer.class, cluster)).isEqualTo(1);
        assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_provider_resource_handles WHERE provider_resource_uuid=? AND status=?", Integer.class, cluster, ExternalVectorClusterLocks.RELEASED)).isEqualTo(2);
        Owner fresh = owner(sourceCp, "fresh-after-transfer", current.customer());
        assertThatThrownBy(() -> sourceCp.bindings.bind(fresh.dep(), fresh.target(), connection(fresh, APPROVAL))).hasMessageContaining("RELEASED_FOR_TRANSFER");
    }

    @Test void claimReplayAllowsLegitimateNewDataButNeverChangedCredentialsOrApproval() {
        ReviewReceipt review = enroll(sourceCp.transfer.releaseReadback(sourceCp.transfer.release(request).id()));
        ClaimRequest command = claim(review);
        ClaimReceipt receipt = destinationCp.transfer.claim(command);
        when(destinationCp.provider.listServingCollections(endpoint, TOKEN_VALUE)).thenReturn(List.of(destination.prefix() + "synthetic_document"));
        String before = destinationCp.snapshot();
        assertThat(destinationCp.transfer.claim(command)).isEqualTo(receipt);
        assertThat(destinationCp.snapshot()).isEqualTo(before);
        var c = command.connection();
        var changed = new ExistingVectorBindingContracts.Request(c.versionId(), c.clusterId(), c.projectId(), c.regionId(), c.expectedDeploymentOption(), c.databaseName(), null, null, c.approvalReference(), TOKEN, "changed-synthetic-token");
        assertThatThrownBy(() -> destinationCp.transfer.claim(new ClaimRequest(command.operationKey(), command.reviewId(), changed, inventory())))
            .hasMessageContaining("BINDING_IMMUTABLE");
        assertThatThrownBy(() -> destinationCp.transfer.claim(new ClaimRequest("other-operation-" + suffix, command.reviewId(), c, inventory())))
            .hasMessageContaining("EVIDENCE_CHANGED");
        assertThat(destinationCp.snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"response-bytes", "source-fingerprint", "source-origin", "destination-origin"})
    void reviewedEnrollmentRejectsTamperedOrRepointedSourceEvidence(String change) {
        ReleaseReadback readback = sourceCp.transfer.releaseReadback(sourceCp.transfer.release(request).id());
        ReviewRequest command = reviewCommand(readback);
        String raw = command.authenticatedReadbackJson();
        if (change.equals("response-bytes")) raw = raw.replace("authenticated", "unauthenticated").replace(readback.releaseJsonDigest(), "0".repeat(64));
        if (change.equals("source-origin")) raw = raw.replace("https://source.example.test", "https://untrusted.example.test");
        if (change.equals("destination-origin")) raw = raw.replace("https://destination.example.test", "https://untrusted.example.test");
        ReviewRequest tampered = new ReviewRequest(command.operationKey(), command.approvalReference(), raw,
            change.equals("source-fingerprint") ? "0".repeat(64) : command.expectedSourceReceiptFingerprint(), command.retrievalReference());
        String before = destinationCp.snapshot();
        assertThatThrownBy(() -> destinationCp.transfer.registerReviewedSourceProof(tampered)).isInstanceOf(RuntimeException.class);
        assertThat(destinationCp.snapshot()).isEqualTo(before);
    }

    @Test void sourceEnrollmentPersistsExactAuthenticatedBytesAndIsAppendOnlyAndSingleUse() {
        ReleaseReadback readback = sourceCp.transfer.releaseReadback(sourceCp.transfer.release(request).id());
        ReviewRequest command = reviewCommand(readback);
        ReviewReceipt receipt = destinationCp.transfer.registerReviewedSourceProof(command);
        assertThat(receipt.authenticatedReadbackJson()).isEqualTo(command.authenticatedReadbackJson());
        assertThat(receipt.authenticatedResponseDigest()).isEqualTo(destinationCp.transferJson.bytesHash(command.authenticatedReadbackJson()));
        assertThat(receipt.sourceReceiptJson()).isEqualTo(readback.releaseJson());
        assertThat(receipt.retrievalReference()).isEqualTo(command.retrievalReference());
        assertThat(receipt.reviewerActorId()).isEqualTo("synthetic-transfer-admin");
        String before = destinationCp.snapshot();
        assertThat(destinationCp.transfer.registerReviewedSourceProof(command)).isEqualTo(receipt);
        assertThat(destinationCp.snapshot()).isEqualTo(before);
        assertThatThrownBy(() -> destinationCp.jdbc.update("UPDATE deployment_vector_transfer_reviews SET actor_id='other' WHERE id=?", receipt.id())).hasMessageContaining("immutable");
        assertThatThrownBy(() -> destinationCp.transfer.registerReviewedSourceProof(new ReviewRequest("another-" + suffix, command.approvalReference(), command.authenticatedReadbackJson(), command.expectedSourceReceiptFingerprint(), command.retrievalReference()))).isInstanceOf(RuntimeException.class);
        assertThat(destinationCp.snapshot()).isEqualTo(before);
    }

    @Test void actual204To205UpgradePreservesExistingCancellationAndHandoffHistory() {
        String schema = "transfer_upgrade_" + suffix;
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target(MigrationVersion.fromVersion("204")).load().migrate();
        try {
            copy(schema, "platform_customers", "id", old.customer());
            for (Owner owner : List.of(old, current)) copy(schema, "platform_customer_tenants", "id", owner.tenant());
            copy(schema, "deployment_target_profiles", "id", old.target());
            for (Owner owner : List.of(old, current)) {
                copy(schema, "platform_deployments", "id", owner.dep());
                copy(schema, "platform_deployment_drafts", "deployment_id", owner.dep());
                copy(schema, "platform_deployment_versions", "id", owner.version().getId());
                copy(schema, "deployment_provider_resource_handles", "deployment_id", owner.dep());
                copy(schema, "platform_target_retirement_gates", "deployment_id", owner.dep());
            }
            copy(schema, "deployment_admission_reviews", "record_key", cancelled.reservation().manifestId());
            copy(schema, "deployment_capacity_reservations", "id", cancelled.reservation().id());
            copy(schema, "deployment_admission_authorizations", "reservation_id", cancelled.reservation().id());
            copy(schema, "deployment_admission_events", "reservation_id", cancelled.reservation().id());
            copy(schema, "deployment_admission_unused_cancellations", "id", cancelled.id());
            copy(schema, "deployment_unused_vector_handoffs", "deployment_id", current.dep());
            var tables = sourceCp.jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname=? AND tablename<>'flyway_schema_history' ORDER BY tablename", String.class, schema);
            Map<String, String> before = new TreeMap<>();
            for (String table : tables) before.put(table, tableSnapshot(schema + "." + table));
            String activeIndex = sourceCp.jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=? AND indexname='uq_active_external_vector_binding_cluster'", String.class, schema);
            var migrated = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target(MigrationVersion.fromVersion("205")).load().migrate();
            assertThat(migrated.migrationsExecuted).isEqualTo(1);
            before.forEach((table, rows) -> assertThat(tableSnapshot(schema + "." + table)).as(table).isEqualTo(rows));
            assertThat(sourceCp.jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=? AND indexname='uq_active_external_vector_binding_cluster'", String.class, schema)).isEqualTo(activeIndex);
            for (String table : List.of("deployment_vector_transfer_releases", "deployment_vector_transfer_release_bindings", "deployment_vector_transfer_reviews", "deployment_vector_transfer_claims"))
                assertThat(sourceCp.jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + "." + table, Integer.class)).isZero();
        } finally { sourceCp.jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }

    @ParameterizedTest @ValueSource(strings = {"PLATFORM_OPERATOR", "CUSTOMER_ADMIN", "PLATFORM_PRODUCT_SERVICE"})
    void customerAndNonAdminStaffCannotReleaseOrReview(String role) {
        authenticate(role, "SESSION"); String before = sourceCp.snapshot();
        assertThatThrownBy(() -> sourceCp.transfer.release(request)).hasMessageContaining("403");
        assertThatThrownBy(() -> destinationCp.transfer.registerReviewedSourceProof(null)).hasMessageContaining("403");
        assertThat(sourceCp.snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"PUBLIC_API", "CONSUMER_RUNTIME", "IMPERSONATED"})
    void adminRoleWithoutExplicitStaffAuthenticationCannotTransfer(String mode) {
        authenticate("PLATFORM_ADMIN", mode);
        assertThatThrownBy(() -> sourceCp.transfer.release(request)).hasMessageContaining("403");
        assertThatThrownBy(() -> destinationCp.transfer.registerReviewedSourceProof(null)).hasMessageContaining("403");
    }

    private Outcome attempt(Callable<?> command) {
        authenticate("PLATFORM_ADMIN", "SESSION");
        try { return new Outcome(command.call(), null); }
        catch (Exception ex) { return new Outcome(null, ex); }
        finally { SecurityContextHolder.clearContext(); }
    }
    private record Outcome(Object value, Exception failure) { }
    private void copy(String schema, String table, String column, String id) {
        assertThat(sourceCp.jdbc.update("INSERT INTO " + schema + "." + table + " SELECT * FROM public." + table + " WHERE " + column + "=?", id)).isPositive();
    }
    private String tableSnapshot(String table) {
        return sourceCp.jdbc.queryForObject("SELECT COALESCE(json_agg(row_to_json(r) ORDER BY row_to_json(r)::text)::text,'[]') FROM " + table + " r", String.class);
    }

    private record ConfiguredConsumer(String entityId, String consumerId, String appId, String creationId, String historyId) { }

    private ConfiguredConsumer configuredConsumer() {
        String offer = "offer-" + suffix, user = "owner-" + suffix, entitlement = "entitlement-" + suffix;
        String creationKey = "create-config-" + suffix;
        var consumer = new ConfiguredConsumer("consumer-" + suffix, "configured-" + suffix, "draft-app-" + suffix,
            "creation-" + suffix, "initial-history-" + suffix);
        sourceCp.jdbc.update("""
            INSERT INTO platform_users(id,email,display_name,password_hash,role,status,created_at,updated_at)
            VALUES (?,?,'Synthetic owner','synthetic-unusable-password','CUSTOMER_ADMIN','ACTIVE',?,?)
            """, user, user + "@example.test", time(), time());
        sourceCp.jdbc.update("""
            INSERT INTO platform_customer_offers(id,definition_json,fingerprint,review_reference,reviewed_by,reviewed_at)
            VALUES (?,'{}',?,'synthetic-review','synthetic-admin',?)
            """, offer, sourceCp.transferJson.hash(offer), time());
        sourceCp.jdbc.update("""
            INSERT INTO platform_customer_offer_entitlements(id,customer_id,offer_id,owner_user_id,invitation_reference,
                status,revision,expires_at,created_at,updated_at)
            VALUES (?,?,?,?,'synthetic-invitation','ACTIVE',1,?,?,?)
            """, entitlement, current.customer(), offer, user, Timestamp.from(Instant.now().plusSeconds(3600)), time(), time());
        sourceCp.jdbc.update("""
            INSERT INTO platform_customer_offer_deployments(deployment_id,customer_id,tenant_id,offer_id,offer_fingerprint,
                authorization_entitlement_revision,operation_key,created_at,bootstrap_target_profile_id)
            VALUES (?,?,?,?,?,1,?,?,?)
            """, current.dep(), current.customer(), current.tenant(), offer, sourceCp.transferJson.hash(offer),
            "bootstrap-" + suffix, time(), current.target());
        sourceCp.jdbc.update("""
            INSERT INTO platform_consumers(id,consumer_id,customer_id,display_name,status,bound_deployment_id,
                bound_target_profile_id,binding_revision,created_at,updated_at)
            VALUES (?,?,?,'Synthetic reviewed configuration','ACTIVE',?,?,1,?,?)
            """, consumer.entityId(), consumer.consumerId(), current.customer(), current.dep(), current.target(), time(), time());
        var creationResult = new LinkedHashMap<String, Object>();
        creationResult.put("consumerId", consumer.consumerId()); creationResult.put("customerId", current.customer());
        creationResult.put("boundDeploymentId", current.dep()); creationResult.put("boundTargetProfileId", current.target());
        creationResult.put("boundReleaseId", null); creationResult.put("bindingRevision", 1);
        String result = jsonText(creationResult);
        sourceCp.jdbc.update("""
            INSERT INTO platform_consumer_binding_history(id,consumer_entity_id,consumer_id,customer_id,to_deployment_id,
                to_target_profile_id,from_binding_revision,to_binding_revision,operation_key,request_fingerprint,result_json,
                actor_id,actor_role,created_at)
            VALUES (?,?,?,?,?,?,0,1,?,?,?,?,'CUSTOMER_ADMIN',?)
            """, consumer.historyId(), consumer.entityId(), consumer.consumerId(), current.customer(), current.dep(),
            current.target(), creationKey, sourceCp.transferJson.hash(creationKey), result, user, time());
        sourceCp.jdbc.update("""
            INSERT INTO platform_customer_entitlement_commands(id,entitlement_id,operation_key,request_fingerprint,
                command_type,actor_id,result_json,created_at)
            VALUES (?,?,?,?,'CREATE_STAGING_CONSUMER',?,?,?)
            """, consumer.creationId(), entitlement, creationKey, sourceCp.transferJson.hash(creationKey), user, result, time());
        draftWorkspace(consumer.appId(), consumer.entityId());
        return consumer;
    }

    private void draftWorkspace(String id, String consumerId) {
        var app = new AIWorkspaceInstallationEntity();
        app.setId(id); app.setInstallationId("public-" + id); app.setCustomerId(current.customer()); app.setConsumerEntityId(consumerId);
        app.setDisplayName("Synthetic preserved draft"); app.setStatus(AIWorkspaceInstallationStatus.DRAFT);
        app.setExperiencePackCode("synthetic"); app.setExperiencePackVersion("1");
        app.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        app.setConnectionProfileCode("synthetic"); app.setConnectionProfileVersion("1");
        app.setDeliveryMode(AIWorkspaceDeliveryMode.EMBEDDED); app.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC);
        app.setAuthProfileCode("synthetic"); app.setAuthProfileVersion("1"); app.setAuthConfigurationJson("{\"preserveAuth\":true}");
        app.setConnectionConfigurationJson("{}"); app.setAllowedOriginsJson("[\"https://synthetic.example.test\"]");
        app.setConfigurationJson("{\"preserveDraft\":true}");
        app.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS)); app.setUpdatedAt(app.getCreatedAt());
        sourceCp.context.getBean(AIWorkspaceInstallationRepository.class).saveAndFlush(app);
    }

    private UnusedConsumerDetachment expectation(ConfiguredConsumer consumer, long revision, long appVersion) {
        return new UnusedConsumerDetachment(consumer.entityId(), revision,
            List.of(new DraftWorkspaceExpectation(consumer.appId(), appVersion)));
    }

    private ReleaseRequest withDetachments(List<UnusedConsumerDetachment> detachments) {
        return new ReleaseRequest(request.operationKey(), request.approvalReference(), request.bindingId(),
            request.destination(), request.sources(), request.inventoryApproval(), detachments);
    }

    private String scopedRows(String table, String column, String id) {
        return sourceCp.jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text)::text,'[]') FROM "
            + table + " r WHERE " + column + "=?", String.class, id);
    }

    private ReviewReceipt enroll(ReleaseReadback r) { return destinationCp.transfer.registerReviewedSourceProof(reviewCommand(r)); }
    private ReviewRequest reviewCommand(ReleaseReadback r) {
        ReleaseReceipt receipt = sourceCp.transferJson.read(r.releaseJson(), ReleaseReceipt.class);
        return new ReviewRequest("review-" + suffix, "destination-approval-" + suffix, jsonText(r), receipt.fingerprint(), "authenticated-tls-retrieval-" + suffix);
    }
    private ClaimRequest claim(ReviewReceipt r) { return new ClaimRequest("claim-" + suffix, r.id(), connection(destination, r.approvalReference()), inventory()); }
    private InventoryApproval inventory() { return new InventoryApproval(sourceCp.transferJson.bytesHash(KEY), "reviewed-full-visibility-" + suffix, "attended-no-writer-window-" + suffix); }
    private String jsonText(Object value) { return sourceCp.transferJson.write(value); }
    private Scope scope(Cp cp, Owner o) { return new Scope(new ControlPlane(cp.id, "https://" + cp.id + ".example.test"), o.customer(), o.tenant(), o.dep(), o.target(), o.version().getId(), o.version().getConfigHash(), o.prefix()); }
    private ExistingVectorBindingContracts.Request connection(Owner o, String approval) { return new ExistingVectorBindingContracts.Request(o.version().getId(), cluster, "project", "region", "Free", "default", null, null, approval, TOKEN, TOKEN_VALUE); }
    private Timestamp time() { return Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)); }
    private void authenticate(String role, String mode) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new PlatformPrincipal("synthetic-transfer-admin", PlatformRole.valueOf(role), "Synthetic", mode), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)))); }

    private Owner owner(Cp cp, String label, String customer) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        cp.jdbc.update("INSERT INTO platform_customers(id,name,slug,status,platform_managed,created_at,updated_at) VALUES (?,?,?,'ACTIVE',false,?,?) ON CONFLICT(id) DO NOTHING", customer, customer, customer, time(), time());
        String dep = label + "-dep-" + suffix, tenant = label + "-tenant-" + suffix, targetId = "target-" + suffix;
        cp.jdbc.update("INSERT INTO platform_customer_tenants(id,customer_id,name,slug,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)", tenant, customer, tenant, tenant, time(), time());
        var targetRepo = cp.context.getBean(DeploymentTargetProfileRepository.class);
        if (!targetRepo.existsById(targetId)) {
            var t = new DeploymentTargetProfileEntity(); t.setId(targetId); t.setName(targetId); t.setProviderType(DeploymentProviderType.COOLIFY); t.setEnvironmentName("synthetic-staging"); t.setActive(true); t.setSourceStrategy("IMMUTABLE_IMAGE");
            t.setProviderConfigJson("{}"); t.setNetworkPolicyJson("{}"); t.setResourceDefaultsJson("{}"); t.setCreatedAt(now); t.setUpdatedAt(now); targetRepo.saveAndFlush(t);
        }
        var d = new DeploymentEntity(); d.setId(dep); d.setCustomerId(customer); d.setTenantId(tenant); d.setName(dep); d.setEnvironmentName("synthetic-staging"); d.setTemplateId("synthetic"); d.setBehaviorType("CONVERSATIONAL"); d.setStatus("CONFIGURED"); d.setCreatedAt(now); d.setUpdatedAt(now); cp.context.getBean(DeploymentRepository.class).saveAndFlush(d);
        var draft = new DeploymentDraftEntity(); draft.setId(label + "-draft-" + suffix); draft.setDeploymentId(dep); draft.setRevisionNumber(1); draft.setStatus("PUBLISHED"); draft.setActionsConfigJson("{}"); draft.setEntityConfigJson("{\"ai-entities\":{\"synthetic_document\":{}}}"); draft.setRoutingConfigJson("{}"); draft.setProviderConfigJson(CONFIG); draft.setSecurityConfigJson("{}"); draft.setPromptConfigJson("{}"); draft.setCreatedAt(now); draft.setUpdatedAt(now); cp.context.getBean(DeploymentDraftRepository.class).saveAndFlush(draft);
        var v = new DeploymentVersionEntity(); v.setId(label + "-ver-" + suffix); v.setDeploymentId(dep); v.setSourceDraftId(draft.getId()); v.setVersionLabel("synthetic"); v.setStatus("PUBLISHED"); v.setConfigHash(cp.transferJson.hash(dep)); v.setActionsConfigJson("{}"); v.setEntityConfigJson(draft.getEntityConfigJson()); v.setRoutingConfigJson("{}"); v.setProviderConfigJson(CONFIG); v.setSecurityConfigJson("{}"); v.setPromptConfigJson("{}"); v.setActionsArtifactYaml("{}"); v.setEntityArtifactYaml("{}"); v.setRoutingArtifactYaml("{}"); v.setManifestJson("{}"); v.setPublishedAt(now); cp.context.getBean(DeploymentVersionRepository.class).saveAndFlush(v);
        String prefix = cp.context.getBean(TenantScopedVectorHandleResolver.class).targetScopedProviderConfig(d, cp.transferJson.read(CONFIG, com.fasterxml.jackson.databind.JsonNode.class), targetId).path("milvusCollectionPrefix").asText();
        return new Owner(cp, customer, tenant, dep, targetId, v, prefix);
    }
    private CancelUnusedReservation unusedAuthorization(Owner o, ExistingVectorBindingContracts.Summary binding) {
        Cp cp = o.cp(); Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS); AdmissionJson j = cp.context.getBean(AdmissionJson.class);
        var limits = new CustomerOfferContracts.ReviewedLimits("synthetic", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1);
        var topology = new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1);
        var c = new CustomerOfferContracts.AdmissionContext(o.customer(), o.tenant(), o.dep(), null, o.target(), CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "synthetic-offer", "synthetic-fingerprint", 1, "synthetic-placement", CustomerOfferContracts.Operation.APPLY, "apply-" + suffix, limits, topology);
        var amounts = new EnumMap<Dimension, Long>(Dimension.class); for (Dimension d : Dimension.values()) amounts.put(d, 1L);
        var m = new Manifest("manifest-" + o.dep(), o.dep(), o.version().getId(), o.version().getConfigHash(), o.target(), j.fingerprint(binding.bindingId()), "synthetic-topology", new com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Vector(amounts), Set.of("synthetic-prerequisite"));
        var reserve = new Reserve(c.operationKey(), m.id(), c);
        String mfp = j.fingerprint(m), rfp = j.fingerprint(Map.of("customer", o.customer(), "tenant", o.tenant(), "deployment", o.dep(), "manifestId", m.id(), "manifestFingerprint", mfp, "target", o.target(), "customerContext", c));
        var r = new Reservation("reservation-" + o.dep(), o.customer(), o.tenant(), o.dep(), o.target(), Intent.CUSTOMER_STAGING, c.operationKey(), rfp, "generation-" + suffix, 1, State.RESERVED, m.demand(), "synthetic-policy", m.id(), mfp, m.bindingFingerprint(), now, now, now, now.plusSeconds(3600), 0, null, null, ExpiryState.CURRENT, "notice", null, 0, null, null, null, null, null, null, j.fingerprint(c), c.operation(), null);
        var a = new AuthorizationReceipt(r.id(), r.generation(), mfp, rfp, j.fingerprint(c), reserve);
        cp.jdbc.update("INSERT INTO deployment_admission_reviews(kind,record_key,fingerprint,payload_json,reviewed_by,reviewed_at) VALUES ('MANIFEST',?,?,?,'synthetic',?)", m.id(), mfp, j.write(m), time());
        cp.jdbc.update("INSERT INTO deployment_capacity_reservations(id,customer_id,deployment_id,target_profile_id,operation_key,request_fingerprint,generation,revision,state,current_slot,expires_at,expiry_state,payload_json,updated_at) VALUES (?,?,?,?,?,?,?,1,'RESERVED',?,?,'CURRENT',?,?)", r.id(), o.customer(), o.dep(), o.target(), c.operationKey(), rfp, r.generation(), o.dep() + "|" + o.target(), Timestamp.from(r.expiresAt()), j.write(r), time());
        cp.jdbc.update("INSERT INTO deployment_admission_authorizations(customer_id,deployment_id,target_profile_id,operation_key,reservation_id,generation,operation,version_id,request_fingerprint,context_fingerprint,payload_json,created_at) VALUES (?,?,?,?,?,?,'APPLY',?,?,?,?,?)", o.customer(), o.dep(), o.target(), c.operationKey(), r.id(), r.generation(), o.version().getId(), rfp, a.contextFingerprint(), j.write(a), time());
        cp.jdbc.update("INSERT INTO deployment_admission_events(id,reservation_id,revision,event_type,actor_id,created_at) VALUES (?,?,1,'RESERVED','synthetic',?)", "event-" + o.dep(), r.id(), time());
        return new CancelUnusedReservation(r.fence(), "cancel-" + suffix, c.operationKey(), rfp, mfp, o.version().getId(), o.version().getConfigHash(), m.bindingFingerprint());
    }
    record Owner(Cp cp, String customer, String tenant, String dep, String target, DeploymentVersionEntity version, String prefix) { }
    static class Cp {
        final String id; final ConfigurableApplicationContext context; final JdbcTemplate jdbc; final UnusedVectorTransferService transfer;
        final UnusedVectorTransferJson transferJson; final DeploymentExistingVectorBindingService bindings; final DeploymentAdmissionService admission;
        final PlatformSecretService secrets; final ZillizCloudControlPlaneClient provider;
        Cp(String id, String url) {
            this.id = id;
            context = new SpringApplicationBuilder(PlatformBackendApplication.class, ProviderConfig.class).profiles("test").run(
                "--server.port=0", "--spring.datasource.url=" + url, "--spring.datasource.username=" + POSTGRES.getUsername(), "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.driver-class-name=org.postgresql.Driver", "--spring.flyway.locations=classpath:db/migration", "--spring.flyway.target=206", "--spring.main.allow-bean-definition-overriding=true",
                "--platform.bootstrap.sample-enabled=false", "--platform.bootstrap.ecommerce-demo.enabled=false", "--platform.auth.bootstrap-admin-enabled=false",
                "--platform.deployment.admission.release.worker-enabled=false", "--platform.deployment.admission.lifecycle.worker-enabled=false",
                "--platform.deployment.admission.lifecycle.retirement-worker-enabled=false", "--platform.target-retirement.worker-enabled=false",
                "--platform.external-vector-transfer.control-plane-id=" + id, "--platform.external-vector-transfer.control-plane-origin=https://" + id + ".example.test",
                "--platform.external-vector-transfer.peer-origins={\"source\":\"https://source.example.test\",\"destination\":\"https://destination.example.test\"}");
            jdbc = context.getBean(JdbcTemplate.class); transfer = context.getBean(UnusedVectorTransferService.class); transferJson = context.getBean(UnusedVectorTransferJson.class);
            bindings = context.getBean(DeploymentExistingVectorBindingService.class); admission = context.getBean(DeploymentAdmissionService.class); secrets = context.getBean(PlatformSecretService.class); provider = context.getBean(ZillizCloudControlPlaneClient.class);
        }
        String snapshot() {
            var result = new TreeMap<String, String>();
            for (String table : List.of("deployment_provider_resource_handles", "platform_secrets", "platform_audit_events",
                "deployment_vector_transfer_releases", "deployment_vector_transfer_release_bindings", "deployment_vector_transfer_reviews",
                "deployment_vector_transfer_claims", "platform_target_retirement_gates", "platform_consumers",
                "platform_consumer_binding_history", "ai_workspace_installations", "platform_deployment_drafts",
                "platform_deployment_versions", "platform_customer_entitlement_commands", "platform_customer_offer_deployments",
                "deployment_capacity_reservations", "deployment_admission_unused_cancellations", "platform_deployment_releases"))
                result.put(table, jdbc.queryForObject("SELECT COALESCE(json_agg(row_to_json(r) ORDER BY row_to_json(r)::text)::text,'[]') FROM " + table + " r", String.class));
            return transferJson.hash(result);
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfig {
        @Bean @Primary ZillizCloudControlPlaneClient transferProvider() { return mock(ZillizCloudControlPlaneClient.class); }
    }
}
