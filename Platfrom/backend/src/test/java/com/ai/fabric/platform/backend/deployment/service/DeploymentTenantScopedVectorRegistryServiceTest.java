package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.TenantScopedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.repository.TenantScopedVectorResourceRepository;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
}
