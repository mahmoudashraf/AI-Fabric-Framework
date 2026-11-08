package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.marketplace.entity.*;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CustomerPluginLibraryUsageTest {
    @Test void savedAndAppliedUsageUseSeparateExactVersionEvidenceNotTheDeploymentActiveVersionPointer() {
        var plugins = mock(MarketplacePluginRepository.class);
        var versions = mock(MarketplacePluginVersionRepository.class);
        var installs = mock(DeploymentMarketplacePluginInstallRepository.class);
        var deployments = mock(DeploymentRepository.class);
        var releases = mock(DeploymentReleaseRepository.class);
        var deploymentVersions = mock(DeploymentVersionRepository.class);
        var access = mock(CustomerOfferEntitlementService.class);
        var mapper = new ObjectMapper();
        var library = new CustomerPluginLibraryService(plugins, versions, installs, deployments, releases,
            deploymentVersions, mock(MarketplaceManifestService.class), access, mock(PlatformAuditService.class), mapper);
        var plugin = new MarketplacePluginEntity(); plugin.setId("private"); plugin.setOwnerCustomerId("owner");
        plugin.setPluginType("DATA");
        plugin.setVisibility("CUSTOMER_PRIVATE"); plugin.setDraftManifestJson("{}");
        when(plugins.findById("private")).thenReturn(Optional.of(plugin));
        var old = new MarketplacePluginVersionEntity(); old.setId("old"); old.setPluginId("private"); old.setVersion("1.0.0");
        var latest = new MarketplacePluginVersionEntity(); latest.setId("new"); latest.setPluginId("private"); latest.setVersion("2.0.0");
        when(versions.findById("new")).thenReturn(Optional.of(latest));
        var deployment = new DeploymentEntity(); deployment.setId("brain"); deployment.setCustomerId("owner");
        deployment.setActiveVersionId("unproven-pointer");
        when(deployments.findByCustomerIdOrderByUpdatedAtDesc("owner")).thenReturn(List.of(deployment));
        var install = new DeploymentMarketplacePluginInstallEntity(); install.setId("install"); install.setPluginId("private");
        install.setPluginVersionId("new"); install.setStatus("DISABLED");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("brain")).thenReturn(List.of(install));
        var applied = new DeploymentReleaseEntity(); applied.setId("applied"); applied.setDeploymentVersionId("proven-version");
        applied.setTargetProfileId("staging"); applied.setStatus("APPLIED_VERIFIED"); applied.setAppliedAt(Instant.parse("2026-11-08T10:00:00Z"));
        var queued = new DeploymentReleaseEntity(); queued.setId("queued"); queued.setStatus("QUEUED");
        queued.setDeploymentVersionId("unproven-pointer"); queued.setAppliedAt(Instant.parse("2026-11-08T11:00:00Z"));
        when(releases.findByDeploymentIdOrderByCreatedAtDesc("brain")).thenReturn(List.of(queued, applied));
        var provenVersion = new DeploymentVersionEntity(); provenVersion.setId("proven-version");
        provenVersion.setCompositionProvenanceJson("{\"marketplaceInstalls\":[{\"pluginId\":\"private\",\"pluginVersionId\":\"old\",\"pluginVersion\":\"1.0.0\"}]}");
        when(deploymentVersions.findById("proven-version")).thenReturn(Optional.of(provenVersion));
        var result = library.get("private", "owner").installs();
        assertThat(result.savedCount()).isEqualTo(1); assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.saved().getFirst().pluginVersion()).isEqualTo("2.0.0");
        assertThat(result.saved().getFirst().status()).isEqualTo("DISABLED");
        assertThat(result.applied().getFirst().pluginVersion()).isEqualTo("1.0.0");
        assertThat(result.applied().getFirst().releaseId()).isEqualTo("applied");
        verify(deploymentVersions, never()).findById("unproven-pointer");
    }
}
