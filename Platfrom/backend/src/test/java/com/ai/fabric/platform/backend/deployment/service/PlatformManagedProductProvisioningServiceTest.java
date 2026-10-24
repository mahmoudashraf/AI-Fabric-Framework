package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.provider.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.deployment.provider.ProviderHostCommandRunner;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformManagedProductProvisioningServiceTest {

    @Test
    void managedDependencyReclamationFailsClosedWhenPhysicalVolumeAbsenceCannotBeReadBack() {
        var fixture = new OwnedReclamationVerificationFixture(false);
        assertThatThrownBy(() -> fixture.service.verifyOwnedDependenciesReclaimed(fixture.connection,"graph",fixture.resources))
            .hasMessageContaining("DEPENDENCY_VOLUME_RECLAMATION_UNPROVEN");
    }

    @Test
    void managedDependencyReclamationAcceptsExactDurableVolumeReceiptAndProviderAbsence() {
        var fixture = new OwnedReclamationVerificationFixture(true);
        fixture.service.verifyOwnedDependenciesReclaimed(fixture.connection,"graph",fixture.resources);
        assertThat(fixture.ownership.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership",String.class))
            .containsOnly("DELETED").hasSize(2);
    }

    @Test
    void coolifySourceInputsUseApiCompatibleFormats() {
        assertThat(PlatformManagedProductProvisioningService.coolifyGitRepository("mahmoudashraf/AI-Fabric-Framework"))
            .isEqualTo("https://github.com/mahmoudashraf/AI-Fabric-Framework.git");
        assertThat(PlatformManagedProductProvisioningService.coolifyGitRepository("https://github.com/example/repo.git"))
            .isEqualTo("https://github.com/example/repo.git");
        assertThat(PlatformManagedProductProvisioningService.coolifyGitRepository("git@github.com:example/repo.git"))
            .isEqualTo("git@github.com:example/repo.git");
        assertThat(PlatformManagedProductProvisioningService.coolifyDockerfileLocation("product-services/mcp/deploy/Dockerfile"))
            .isEqualTo("/product-services/mcp/deploy/Dockerfile");
        assertThat(PlatformManagedProductProvisioningService.coolifyDockerfileLocation("/deploy/Dockerfile"))
            .isEqualTo("/deploy/Dockerfile");
        assertThatThrownBy(() -> PlatformManagedProductProvisioningService.coolifyGitRepository("https://gitlab.com/example/repo.git"))
            .isInstanceOf(RailwayProvisioningConfigurationException.class);
    }

    @Test
    void legacyRailwayMutationsFailClosedWithoutProviderCallsOrLocalChanges() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.jdbc.update("UPDATE platform_managed_product_services SET service_kind='SHOPIFY_BRIDGE_SERVICE',railway_project_id='legacy',status='ACTIVE' WHERE id='registry'");
        var before = f.row().getDetailsJson();
        assertThatThrownBy(() -> f.provisioning.reconcile("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> f.provisioning.forceRecreate("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.row().getDetailsJson()).isEqualTo(before);
        verifyNoInteractions(f.railway,f.api,f.secrets);
    }

    @Test
    void reconcileCoolifyManagedShopifyBridgeDoesNotUseRailwayLifecycle() {
        var f = sharedShopifyFixture();
        f.provisioning.reconcile("shared");
        assertThat(f.row().getStatus()).isEqualTo("ACTIVE");
        verify(f.api).createPublicApplication(eq(f.connection),any());
        verifyNoInteractions(f.railway);
    }

    @Test
    void forceRecreateNeverDeletesAnUnreceiptedDomainMatchedApp() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.details("{\"providerType\":\"COOLIFY\",\"targetProfileId\":\"target\",\"coolifyApplicationUuid\":\"missing\"}");
        f.applications.put("foreign",f.app("foreign","mcp-gateway-shared","https://shared.example.test",null));
        assertThatThrownBy(() -> f.provisioning.forceRecreate("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        verify(f.api,never()).delete(any(),anyString(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        assertThat(f.applications).containsKey("foreign");
    }

    @Test
    void reconcileCoolifyMcpGatewayWithoutTargetProfileUsesDefaultRestartableProfile() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.provisioning.reconcile("shared");
        assertThat(f.row().getDetailsJson()).contains("targetProfileId").contains("target");
        assertThat(f.row().getEnvironmentScope()).isEqualTo("staging");
        assertThat(f.journalState()).isEqualTo("OWNED");
    }

    @Test
    void reconcileCoolifyManagedServicePreservesExistingDomainAliases() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.provisioning.reconcile("shared");
        var app = f.applications.values().iterator().next();
        f.applications.put(app.uuid(),f.app(app.uuid(),app.name(),app.fqdn()+",https://alias.example.test",null));
        org.mockito.Mockito.clearInvocations(f.api);
        f.provisioning.reconcile("shared");
        var request = ArgumentCaptor.forClass(CoolifyCreatePublicApplicationRequest.class);
        verify(f.api).updatePublicApplication(eq(f.connection),eq(app.uuid()),request.capture());
        assertThat(request.getValue().domains()).contains("https://alias.example.test").contains("https://shared.example.test");
        assertThat(f.effects).isEqualTo(1);
    }

    @Test
    void reconcileStandaloneWorkspaceUsesTheRecordedImmutableDockerImage() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.standaloneWorkspace();
        f.provisioning.reconcile("shared");
        var request = ArgumentCaptor.forClass(CoolifyCreateDockerImageApplicationRequest.class);
        verify(f.api).createDockerImageApplication(eq(f.connection),request.capture());
        assertThat(request.getValue().imageRepository()).isEqualTo("registry.example.test/workspace");
        assertThat(request.getValue().imageTag()).isEqualTo("sha256-" + "b".repeat(64));
        assertThat(f.row().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void reconcileCoolifyManagedServiceCannotRetargetVisibleOwnedUuid() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.provisioning.reconcile("shared");
        var before = f.row().getDetailsJson();
        org.mockito.Mockito.clearInvocations(f.api);
        assertThatThrownBy(() -> f.provisioning.reconcile("shared","different-target")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.row().getDetailsJson()).isEqualTo(before);
        verifyNoInteractions(f.api);
    }

    @Test
    void reconcileCoolifyMcpGatewayCanUseExplicitInitialPlatformServiceProfile() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.provisioning.reconcile("shared","target");
        assertThat(f.row().getDetailsJson()).contains("targetProfileId").contains("serviceOwnershipReceiptId");
        assertThat(f.row().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void decommissionRejectsServicesWithDependentStores() {
        var f = new PlatformManagedServiceOwnershipFixture();
        when(f.stores.countByProductServiceId("registry")).thenReturn(2L);
        assertThatThrownBy(() -> f.provisioning.decommission("shared")).hasMessageContaining("dependent store");
        verifyNoInteractions(f.api,f.secrets);
    }

    @Test
    void defaultShopifySecretNamesAreResolvedWhenProvisioningPropertiesDoNotOverrideThem() {
        var f = sharedShopifyFixture();
        f.provisioning.reconcile("shared");
        verify(f.secrets).resolveSecret("SHOPIFY_APP_API_KEY");
        verify(f.secrets).resolveSecret("SHOPIFY_APP_API_SECRET");
        verify(f.secrets).resolveSecret("SHOPIFY_WEBHOOK_SHARED_SECRET");
        @SuppressWarnings("unchecked") var captor = ArgumentCaptor.forClass(List.class);
        verify(f.api).updateEnvironmentVariables(eq(f.connection),anyString(),captor.capture());
        @SuppressWarnings("unchecked") List<CoolifyEnvVar> env = captor.getValue();
        assertThat(env).anySatisfy(value -> {
            assertThat(value.key()).isEqualTo("SHOPIFY_BRIDGE_SHARED_SECRET");
            assertThat(value.value()).isEqualTo("synthetic-api-key");
        });
    }

    @Test
    void refreshRailwayBindingNeverRepairsOrAdoptsHistoricalLinkage() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.jdbc.update("UPDATE platform_managed_product_services SET service_kind='SHOPIFY_BRIDGE_SERVICE',railway_project_id='deleted-project',railway_service_id='old-service',status='ACTIVE' WHERE id='registry'");
        var before = f.row().getDetailsJson();
        assertThatThrownBy(() -> f.provisioning.refreshRailwayBindingFromWorkspace("shared")).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(f.row().getDetailsJson()).isEqualTo(before);
        verifyNoInteractions(f.railway,f.api,f.secrets);
    }

    private PlatformManagedServiceOwnershipFixture sharedShopifyFixture() {
        var f = new PlatformManagedServiceOwnershipFixture();
        f.jdbc.update("UPDATE platform_managed_product_services SET service_kind='SHOPIFY_BRIDGE_SERVICE' WHERE id='registry'");
        var gateway = new PlatformManagedProductServiceEntity();
        gateway.setId("gateway"); gateway.setBaseUrl("https://gateway.example.test");
        gateway.setSecretName("MANAGED_PRODUCT_GATEWAY_API_KEY");
        when(f.repository.findByServiceRefIgnoreCase("mcp-execution-gateway")).thenReturn(Optional.of(gateway));
        when(f.secrets.resolveSecret("MANAGED_PRODUCT_GATEWAY_API_KEY")).thenReturn("gateway-key");
        return f;
    }

}
