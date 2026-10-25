package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class DeploymentMarketplaceInstallOfferBoundaryTest {

    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final CustomerOfferDeploymentRepository offerDeployments = mock(CustomerOfferDeploymentRepository.class);
    private final MarketplaceCatalogService catalog = mock(MarketplaceCatalogService.class);
    private final DeploymentMarketplacePluginInstallRepository installs = mock(DeploymentMarketplacePluginInstallRepository.class);
    private DeploymentMarketplaceInstallService service;

    @BeforeEach
    void setUp() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-reviewed");
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(access.requireDeploymentEditorAccess(deployment)).thenReturn(deployment);
        when(offerDeployments.existsById(deployment.getId())).thenReturn(true);
        service = new DeploymentMarketplaceInstallService(
            deployments,
            mock(DeploymentDraftRepository.class),
            mock(DeploymentVersionRepository.class),
            access,
            offerDeployments,
            installs,
            catalog,
            mock(MarketplaceManifestService.class),
            mock(MarketplacePluginDatasetDefinitionService.class),
            mock(DeploymentMarketplaceDraftCompilerService.class),
            mock(MarketplaceEntitlementService.class),
            mock(PlatformAuditService.class),
            new ObjectMapper()
        );
    }

    @Test
    void templateOriginUsesNormalInstallValidationInsteadOfAnOfferVeto() {
        when(catalog.requirePluginEntity("plugin"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown plugin"));
        assertNotFound(() -> service.createInstall("dep-reviewed",
            new CreateDeploymentMarketplaceInstallRequest("plugin", "1.0.0", null, null)));
        assertNotFound(() -> service.updateInstall("dep-reviewed", "install",
            new UpdateDeploymentMarketplaceInstallRequest(null, null, null, null)));
        assertNotFound(() -> service.deleteInstall("dep-reviewed", "install"));
        verify(catalog).requirePluginEntity("plugin");
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
