package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerSourcePrerequisiteRefreshServiceTest extends AdmissionFixture {
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    private final DeploymentAdmissionCustomerPrerequisiteEvidenceProvider prerequisites = mock(DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final CustomerSourceConnectorPrerequisiteProbe connector = mock(CustomerSourceConnectorPrerequisiteProbe.class);
    private final CustomerSourcePrerequisiteEvidenceStore evidence = mock(CustomerSourcePrerequisiteEvidenceStore.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final DeploymentTargetReference target = new DeploymentTargetReference("deployment", "target", "release");
    private CustomerSourcePrerequisiteRefreshService service;
    private DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot snapshot;
    private CustomerOfferContracts.AdmissionContext context;
    private CustomerOfferEntitlementEntity entitlement;

    @BeforeEach
    void setup() {
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        var profile = new DeploymentTargetProfileEntity(); profile.setId("target");
        var release = new DeploymentReleaseEntity(); release.setId("release");
        var runtime = new DeploymentTargetContextService.Context(deployment, profile, release, new DeploymentVersionEntity(),
            "https://runtime.example", "https://connector.example");
        when(targets.resolve("deployment", "target", "release", false)).thenReturn(runtime);
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "offer-hash", 2, "bootstrap", NOW)));
        context = new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", "release", "target",
            TargetIntent.CUSTOMER_STAGING, "offer", "offer-hash", 2, null, Operation.SOURCE_DISCOVER, null, null, null);
        when(authorization.prepareSourcePrerequisiteRefresh(eq("customer"), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return new Authorization(context, null);
        });
        var scope = new CustomerSourcePrerequisiteEvidence.Scope("customer", "tenant", "deployment", "target", "release",
            TargetIntent.CUSTOMER_STAGING, "offer", "offer-hash", 2, "reservation", "generation", "manifest", "binding",
            "version", "config", "profile", "document-binding", 1, "credentials", "runtime");
        snapshot = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot(scope, runtime,
            null, "S3_COMPATIBLE_OBJECT_STORAGE", "storage", Set.of(Prerequisite.STORAGE), NOW.plusSeconds(600), 60);
        when(prerequisites.sourceSnapshot(context)).thenReturn(snapshot);
        entitlement = new CustomerOfferEntitlementEntity(); entitlement.setRevision(2); entitlement.setExpiresAt(NOW.plusSeconds(500));
        when(entitlements.requireEntitlement("customer", "offer", null, true)).thenReturn(entitlement);
        var placementEvidence = new CustomerPlacementEvidenceEntity("placement", "target", TargetIntent.CUSTOMER_STAGING,
            "profile", "server", "address", "destination", NOW, NOW.plusSeconds(90));
        when(placement.probeAndRecord(profile, TargetIntent.CUSTOMER_STAGING)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return placementEvidence;
        });
        when(connector.probe(snapshot)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new CustomerSourceConnectorPrerequisiteProbe.Proof("connector-proof", NOW);
        });
        when(evidence.latest(scope)).thenReturn(Optional.empty());
        service = new CustomerSourcePrerequisiteRefreshService(associations, authorization, entitlements, targets,
            prerequisites, placement, connector, evidence, new CustomerSelfServiceProperties(null, 60),
            new DataSourceTransactionManager(dataSource), entityManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test void probesOutsideTransactionsThenReauthorizesAndPersistsAnExactBoundedRecord() {
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(evidence).save(any());

        service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC));

        var saved = org.mockito.ArgumentCaptor.forClass(CustomerSourcePrerequisiteEvidence.class);
        verify(evidence).save(saved.capture());
        assertThat(saved.getValue().scope()).isEqualTo(snapshot.scope());
        assertThat(saved.getValue().checkedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(60));
        verify(authorization, times(2)).prepareSourcePrerequisiteRefresh(eq("customer"), any());
        verify(entityManager, times(2)).clear();
        assertThat(store.held()).isEmpty();
    }

    @Test void freshExactRecordAvoidsRepeatedRemoteWork() {
        when(evidence.latest(snapshot.scope())).thenReturn(Optional.of(new CustomerSourcePrerequisiteEvidence(
            "existing", snapshot.scope(), "placement", snapshot.passed(), "proof", NOW.minusSeconds(1), NOW.plusSeconds(59))));
        service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC));
        verifyNoInteractions(connector);
        verify(placement, never()).probeAndRecord(any(), any());
        verify(evidence, never()).save(any());
    }

    @Test void rejectedEntitlementPerformsNoRemoteWorkOrEvidenceWrite() {
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "ENTITLEMENT_REVOKED"))
            .when(authorization).prepareSourcePrerequisiteRefresh(eq("customer"), any());
        assertThatThrownBy(() -> service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC)))
            .hasMessageContaining("ENTITLEMENT_REVOKED");
        verifyNoInteractions(connector, placement, evidence);
    }

    @Test void revocationDuringProbeCannotPublishEvidence() {
        when(connector.probe(snapshot)).thenAnswer(call -> {
            doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "ENTITLEMENT_REVOKED"))
                .when(authorization).prepareSourcePrerequisiteRefresh(eq("customer"), any());
            return new CustomerSourceConnectorPrerequisiteProbe.Proof("connector-proof", NOW);
        });
        assertThatThrownBy(() -> service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC)))
            .hasMessageContaining("ENTITLEMENT_REVOKED");
        verify(evidence, never()).save(any());
    }

    @Test void allocationVersionOrBindingDriftDuringProbeCannotPublishEvidence() {
        var old = snapshot.scope();
        var changedScope = new CustomerSourcePrerequisiteEvidence.Scope(old.customerId(), old.tenantId(), old.deploymentId(),
            old.targetProfileId(), old.releaseId(), old.intent(), old.offerId(), old.offerFingerprint(), old.entitlementRevision(),
            old.reservationId(), "new-generation", old.manifestFingerprint(), old.admissionBindingFingerprint(),
            old.versionId(), old.versionConfigHash(), old.profileFingerprint(), old.documentBindingFingerprint(),
            old.storageBindingRevision() + 1, "new-credentials", old.runtimeFingerprint());
        var changed = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot(changedScope, snapshot.runtime(),
            snapshot.target(), snapshot.connectorType(), snapshot.bindingRef(), snapshot.passed(), snapshot.expiresAt(), snapshot.maxAgeSeconds());
        when(prerequisites.sourceSnapshot(context)).thenReturn(snapshot, changed);
        assertThatThrownBy(() -> service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC)))
            .hasMessageContaining("SOURCE_PREREQUISITE_SCOPE_CHANGED_DURING_PROBE");
        verify(evidence, never()).save(any());
    }

    @Test void ambientTransactionIsRejectedBeforeAnyWork() {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
            service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC))))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(targets, authorization, connector, placement, evidence);
    }
}
