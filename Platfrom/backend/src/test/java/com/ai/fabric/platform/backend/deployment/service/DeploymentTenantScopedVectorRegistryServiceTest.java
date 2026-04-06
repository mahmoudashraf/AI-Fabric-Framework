package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.TenantScopedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.repository.TenantScopedVectorResourceRepository;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorHandleSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorSummary;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesRequest;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentTenantScopedVectorRegistryServiceTest {

    @Test
    void syncResolvedHandleUpsertsAndReusesTenantScopedRecord() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            auditService,
            new ObjectMapper()
        );

        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity version = version();
        DeploymentReleaseEntity release = release();
        DeploymentTenantScopedVectorSummary summary = sharedSummary();
        TenantScopedVectorResourceEntity existing = new TenantScopedVectorResourceEntity();
        existing.setId("tsv-12345678");
        existing.setTenantId("ten-retail");
        existing.setRegistryKey("pinecone|namespace_prefix|shared-index|cust-acme--ten-retail|");
        existing.setResourceStatus("DETACHED");
        existing.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        existing.setUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"));

        when(repository.findByDeploymentIdOrderByUpdatedAtDesc("dep-12345678")).thenReturn(List.of(existing));
        when(repository.findByTenantIdAndRegistryKeyIgnoreCase("ten-retail", "pinecone|namespace_prefix|shared-index|cust-acme--ten-retail|"))
            .thenReturn(Optional.of(existing));
        stubNoCrossCustomerConflict(repository);

        service.syncResolvedHandle(deployment, version, release, summary);

        assertThat(existing.getResourceStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getDeploymentId()).isEqualTo("dep-12345678");
        assertThat(existing.getDeploymentVersionId()).isEqualTo("ver-12345678");
        assertThat(existing.getDeploymentReleaseId()).isEqualTo("rel-12345678");
        assertThat(existing.getScopePattern()).isEqualTo("cust-acme--ten-retail__<entity-type>");
        verify(repository).saveAll(any());
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    void summarizeForDeploymentWarnsBeforeFirstSuccessfulApply() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );
        DeploymentEntity deployment = deployment();
        DeploymentTenantScopedVectorSummary summary = sharedSummary();

        when(repository.findByTenantIdOrderByUpdatedAtDesc("ten-retail")).thenReturn(List.of());
        when(repository.findByTenantIdAndRegistryKeyIgnoreCase("ten-retail", "pinecone|namespace_prefix|shared-index|cust-acme--ten-retail|"))
            .thenReturn(Optional.empty());
        stubNoCrossCustomerConflict(repository);

        DeploymentTenantScopedVectorRegistrySummary registry = service.summarizeForDeployment(deployment, summary);

        assertThat(registry.status()).isEqualTo("WARNING");
        assertThat(registry.message()).contains("no registry record exists yet");
    }

    @Test
    void summarizeTenantReportsActiveAndHistoricalHandleCounts() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );
        TenantScopedVectorResourceEntity active = new TenantScopedVectorResourceEntity();
        active.setId("tsv-active");
        active.setTenantId("ten-retail");
        active.setResourceStatus("ACTIVE");
        active.setVectorStrategy("pinecone");
        active.setScopeType("NAMESPACE_PREFIX");
        active.setScopePattern("cust-acme--ten-retail__<entity-type>");
        active.setUpdatedAt(Instant.parse("2026-04-03T12:00:00Z"));
        TenantScopedVectorResourceEntity historical = new TenantScopedVectorResourceEntity();
        historical.setId("tsv-historical");
        historical.setTenantId("ten-retail");
        historical.setResourceStatus("DETACHED");
        historical.setVectorStrategy("pinecone");
        historical.setScopeType("NAMESPACE_PREFIX");
        historical.setScopePattern("cust-acme--ten-retail__<entity-type>");
        historical.setUpdatedAt(Instant.parse("2026-04-02T12:00:00Z"));

        when(repository.findByTenantIdInOrderByUpdatedAtDesc(List.of("ten-retail"))).thenReturn(List.of(active, historical));

        PlatformTenantSharedVectorSummary summary = service.summarizeTenant("ten-retail");

        assertThat(summary.activeHandleCount()).isEqualTo(1);
        assertThat(summary.historicalHandleCount()).isEqualTo(1);
        assertThat(summary.latestStatus()).isEqualTo("ACTIVE");
        assertThat(summary.latestScopePattern()).isEqualTo("cust-acme--ten-retail__<entity-type>");
    }

    @Test
    void detachForDeletedDeploymentMarksActiveRecordsAsHistorical() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            auditService,
            new ObjectMapper()
        );
        DeploymentEntity deployment = deployment();
        TenantScopedVectorResourceEntity active = new TenantScopedVectorResourceEntity();
        active.setId("tsv-active");
        active.setDeploymentId("dep-12345678");
        active.setResourceStatus("ACTIVE");
        active.setUpdatedAt(Instant.parse("2026-04-03T12:00:00Z"));

        when(repository.findByDeploymentIdOrderByUpdatedAtDesc("dep-12345678")).thenReturn(List.of(active));

        service.detachForDeletedDeployment(deployment, "Operator removed deployment");

        assertThat(active.getResourceStatus()).isEqualTo("DETACHED");
        verify(repository).saveAll(any());
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    void listTenantHandlesReturnsOrderedHandleSummaries() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );
        TenantScopedVectorResourceEntity detached = new TenantScopedVectorResourceEntity();
        detached.setId("tsv-detached");
        detached.setTenantId("ten-retail");
        detached.setResourceStatus("DETACHED");
        detached.setVendor("pinecone");
        detached.setVectorStrategy("pinecone");
        detached.setVectorProvisioningMode("EXTERNAL_EXISTING");
        detached.setVectorStoragePosture("SHARED");
        detached.setScopeType("NAMESPACE_PREFIX");
        detached.setScopePattern("cust-acme--ten-retail__<entity-type>");
        detached.setDetailsJson("{\"summaryStatus\":\"READY\",\"summaryMessage\":\"Detached for audit.\"}");
        detached.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        detached.setUpdatedAt(Instant.parse("2026-04-02T00:00:00Z"));

        when(repository.findByTenantIdOrderByUpdatedAtDesc("ten-retail")).thenReturn(List.of(detached));

        List<PlatformTenantSharedVectorHandleSummary> handles = service.listTenantHandles("ten-retail");

        assertThat(handles).hasSize(1);
        PlatformTenantSharedVectorHandleSummary summary = handles.getFirst();
        assertThat(summary.id()).isEqualTo("tsv-detached");
        assertThat(summary.cleanupEligible()).isTrue();
        assertThat(summary.summaryStatus()).isEqualTo("READY");
        assertThat(summary.summaryMessage()).isEqualTo("Detached for audit.");
    }

    @Test
    void purgeDetachedHandleHistoryDeletesSelectedDetachedRecords() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            auditService,
            new ObjectMapper()
        );
        TenantScopedVectorResourceEntity detached = new TenantScopedVectorResourceEntity();
        detached.setId("tsv-detached");
        detached.setTenantId("ten-retail");
        detached.setResourceStatus("DETACHED");

        when(repository.findByTenantIdOrderByUpdatedAtDesc("ten-retail")).thenReturn(List.of(detached));

        PurgePlatformTenantSharedVectorHandlesSummary summary = service.purgeDetachedHandleHistory(
            "ten-retail",
            "Retail",
            new PurgePlatformTenantSharedVectorHandlesRequest(
                List.of("tsv-detached"),
                false,
                true,
                "PURGE DETACHED HANDLES",
                "Provider-side delete was completed and audit retention is no longer needed."
            )
        );

        assertThat(summary.purgedCount()).isEqualTo(1);
        assertThat(summary.remainingHistoricalHandleCount()).isZero();
        verify(repository).deleteAllInBatch(List.of(detached));
        verify(auditService).record(any(), any(), any(), any());
    }

    @Test
    void purgeDetachedHandleHistoryBlocksWhileActiveHandlesRemain() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );
        TenantScopedVectorResourceEntity active = new TenantScopedVectorResourceEntity();
        active.setId("tsv-active");
        active.setTenantId("ten-retail");
        active.setResourceStatus("ACTIVE");
        TenantScopedVectorResourceEntity detached = new TenantScopedVectorResourceEntity();
        detached.setId("tsv-detached");
        detached.setTenantId("ten-retail");
        detached.setResourceStatus("DETACHED");

        when(repository.findByTenantIdOrderByUpdatedAtDesc("ten-retail")).thenReturn(List.of(active, detached));

        assertThatThrownBy(() -> service.purgeDetachedHandleHistory(
            "ten-retail",
            "Retail",
            new PurgePlatformTenantSharedVectorHandlesRequest(
                List.of("tsv-detached"),
                false,
                true,
                "PURGE DETACHED HANDLES",
                "Provider-side delete was completed and audit retention is no longer needed."
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("all active shared handles are gone");
        verify(repository, never()).deleteAllInBatch(any());
    }

    @Test
    void summarizeForDeploymentBlocksCrossCustomerSharedRootReuse() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );
        DeploymentEntity deployment = deployment();
        DeploymentTenantScopedVectorSummary summary = sharedSummary();

        TenantScopedVectorResourceEntity foreignActive = new TenantScopedVectorResourceEntity();
        foreignActive.setId("tsv-foreign");
        foreignActive.setCustomerId("cust-other");
        foreignActive.setDeploymentId("dep-other");
        foreignActive.setResourceStatus("ACTIVE");

        when(repository.findByTenantIdOrderByUpdatedAtDesc("ten-retail")).thenReturn(List.of());
        when(repository.findByVectorStrategyIgnoreCaseAndRootResourceValueIgnoreCaseAndResourceStatusIgnoreCase(
            "pinecone",
            "shared-index",
            "ACTIVE"
        )).thenReturn(List.of(foreignActive));

        DeploymentTenantScopedVectorRegistrySummary registry = service.summarizeForDeployment(deployment, summary);

        assertThat(registry.status()).isEqualTo("BLOCKED");
        assertThat(registry.recordId()).isEqualTo("tsv-foreign");
        assertThat(registry.message()).contains("must not cross customer boundaries");
    }

    @Test
    void syncResolvedHandleRejectsCrossCustomerSharedRootReuse() {
        TenantScopedVectorResourceRepository repository = mock(TenantScopedVectorResourceRepository.class);
        DeploymentTenantScopedVectorRegistryService service = new DeploymentTenantScopedVectorRegistryService(
            repository,
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );
        TenantScopedVectorResourceEntity foreignActive = new TenantScopedVectorResourceEntity();
        foreignActive.setId("tsv-foreign");
        foreignActive.setCustomerId("cust-other");
        foreignActive.setDeploymentId("dep-other");
        foreignActive.setResourceStatus("ACTIVE");

        when(repository.findByDeploymentIdOrderByUpdatedAtDesc("dep-12345678")).thenReturn(List.of());
        when(repository.findByVectorStrategyIgnoreCaseAndRootResourceValueIgnoreCaseAndResourceStatusIgnoreCase(
            "pinecone",
            "shared-index",
            "ACTIVE"
        )).thenReturn(List.of(foreignActive));

        assertThatThrownBy(() -> service.syncResolvedHandle(deployment(), version(), release(), sharedSummary()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must not cross customer boundaries");
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-12345678");
        deployment.setCustomerId("cust-acme");
        deployment.setTenantId("ten-retail");
        return deployment;
    }

    private DeploymentVersionEntity version() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-12345678");
        return version;
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-12345678");
        return release;
    }

    private DeploymentTenantScopedVectorSummary sharedSummary() {
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            "pinecone",
            "EXTERNAL_EXISTING",
            "SHARED",
            true,
            "CUSTOMER_MANAGED_EXTERNAL_RESOURCE",
            "cust-acme",
            "Acme Corp",
            "ten-retail",
            "Retail",
            "NAMESPACE_PREFIX",
            "Index",
            "shared-index",
            "cust-acme--ten-retail",
            null,
            "cust-acme--ten-retail__<entity-type>",
            true,
            "Migration requires governance.",
            "Provider-owned backup posture.",
            null,
            "Tenant scope is enforced through Pinecone namespaces."
        );
    }

    private void stubNoCrossCustomerConflict(TenantScopedVectorResourceRepository repository) {
        when(repository.findByVectorStrategyIgnoreCaseAndRootResourceValueIgnoreCaseAndResourceStatusIgnoreCase(any(), any(), any()))
            .thenReturn(List.of());
    }
}
