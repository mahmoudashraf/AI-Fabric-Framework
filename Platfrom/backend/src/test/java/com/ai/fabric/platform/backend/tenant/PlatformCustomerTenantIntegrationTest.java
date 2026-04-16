package com.ai.fabric.platform.backend.tenant;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeleteDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.entity.TenantScopedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentTenantBindingRequest;
import com.ai.fabric.platform.backend.deployment.repository.TenantScopedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDeletionService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorHandleSummary;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesRequest;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PlatformCustomerTenantIntegrationTest {

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private PlatformCustomerTenantService platformCustomerTenantService;

    @Autowired
    private PlatformCustomerConsumerService platformCustomerConsumerService;

    @Autowired
    private DeploymentDeletionService deploymentDeletionService;

    @Autowired
    private TenantScopedVectorResourceRepository tenantScopedVectorResourceRepository;

    @BeforeEach
    void authenticate() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        PlatformTestSecurity.clearAuthentication();
    }

    @Test
    void createDeploymentWithoutExplicitBindingUsesInternalCustomerAndAutoTenant() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Internal Binding Smoke", "dev", "dev-openai-lucene")
        );

        assertThat(deployment.binding()).isNotNull();
        assertThat(deployment.binding().customerId()).isEqualTo(PlatformCustomerTenantService.INTERNAL_CUSTOMER_ID);
        assertThat(deployment.binding().customerPlatformManaged()).isTrue();
        assertThat(deployment.binding().tenantId()).startsWith("ten-");

        PlatformCustomerSummary internalCustomer = platformCustomerTenantService.listCustomers().stream()
            .filter(customer -> PlatformCustomerTenantService.INTERNAL_CUSTOMER_ID.equals(customer.id()))
            .findFirst()
            .orElseThrow();
        assertThat(internalCustomer.tenants())
            .anyMatch(tenant -> deployment.id().equals(tenant.boundDeploymentId())
                && tenant.sharedVector() != null
                && tenant.sharedVector().latestSummary().contains("No shared vector handles"));
    }

    @Test
    void createDeploymentWithCustomerOnlyAutoCreatesDedicatedTenantUnderThatCustomer() {
        PlatformCustomerSummary customer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Acme Holdings", "Enterprise boundary")
        );

        DeploymentSummary deployment = deploymentService.createDeployment(new CreateDeploymentRequest(
            "Acme Stage",
            "stage",
            "dev-openai-lucene",
            "default",
            null,
            customer.id(),
            null
        ));

        assertThat(deployment.binding()).isNotNull();
        assertThat(deployment.binding().customerId()).isEqualTo(customer.id());
        assertThat(deployment.binding().tenantId()).startsWith("ten-");
        assertThat(deployment.binding().tenantPlatformManaged()).isFalse();

        PlatformCustomerSummary refreshed = platformCustomerTenantService.listCustomers().stream()
            .filter(item -> item.id().equals(customer.id()))
            .findFirst()
            .orElseThrow();
        assertThat(refreshed.tenants())
            .anyMatch(tenant -> deployment.id().equals(tenant.boundDeploymentId())
                && deployment.binding().tenantId().equals(tenant.id())
                && tenant.sharedVector() != null);
    }

    @Test
    void deploymentBindingCanBeChangedBeforePublishButLocksAfterHistoryExists() {
        PlatformCustomerSummary customer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Beta Retail", "Customer for mutable binding")
        );
        var tenant = platformCustomerTenantService.createTenant(
            customer.id(),
            new CreatePlatformTenantRequest("Beta Tenant", "Primary tenant")
        );
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Mutable Binding", "dev", "dev-openai-lucene")
        );

        DeploymentOverviewSummary rebound = deploymentService.updateDeploymentTenantBinding(
            deployment.id(),
            new UpdateDeploymentTenantBindingRequest(customer.id(), tenant.id())
        );

        assertThat(rebound.binding()).isNotNull();
        assertThat(rebound.binding().customerId()).isEqualTo(customer.id());
        assertThat(rebound.binding().tenantId()).isEqualTo(tenant.id());

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        deploymentService.publishDraft(draft.id());

        assertThatThrownBy(() -> deploymentService.updateDeploymentTenantBinding(
            deployment.id(),
            new UpdateDeploymentTenantBindingRequest(customer.id(), tenant.id())
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(responseStatusException.getReason()).contains("only be changed before any versions are published");
            });
    }

    @Test
    void tenantSharedHandleHistoryCanBeReviewedAndPurgedAfterAllActiveHandlesAreGone() {
        PlatformCustomerSummary customer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Shared Handle Customer", "Customer for shared handle cleanup")
        );
        var tenant = platformCustomerTenantService.createTenant(
            customer.id(),
            new CreatePlatformTenantRequest("Retail Tenant", "Tenant with detached shared handle history")
        );

        tenantScopedVectorResourceRepository.save(sharedHandle(
            "tsv-detached",
            customer.id(),
            tenant.id(),
            null,
            "DETACHED",
            Instant.parse("2026-04-02T10:00:00Z")
        ));

        List<PlatformTenantSharedVectorHandleSummary> handles =
            platformCustomerTenantService.listTenantSharedVectorHandles(tenant.id());

        assertThat(handles).hasSize(1);
        assertThat(handles.getFirst().cleanupEligible()).isTrue();

        PurgePlatformTenantSharedVectorHandlesSummary purge =
            platformCustomerTenantService.purgeTenantSharedVectorHandles(
                tenant.id(),
                new PurgePlatformTenantSharedVectorHandlesRequest(
                    List.of("tsv-detached"),
                    false,
                    true,
                    "PURGE DETACHED HANDLES",
                    "Provider retention is closed and audit history can now be purged."
                )
            );

        assertThat(purge.purgedCount()).isEqualTo(1);
        assertThat(tenantScopedVectorResourceRepository.findByTenantIdOrderByUpdatedAtDesc(tenant.id())).isEmpty();
    }

    @Test
    void deploymentDeleteIsBlockedWhileConsumerRemainsBound() {
        PlatformCustomerSummary customer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Delete Guard Customer", "Customer for delete guard")
        );
        DeploymentSummary deployment = deploymentService.createDeployment(new CreateDeploymentRequest(
            "Delete Guard Deployment",
            "dev",
            "dev-openai-lucene",
            "default",
            null,
            customer.id(),
            null
        ));
        platformCustomerConsumerService.createConsumer(
            customer.id(),
            new CreatePlatformConsumerRequest(
                "delete-guard-consumer",
                "Delete guard consumer",
                "Bound to deployment before delete.",
                deployment.id(),
                "Initial binding."
            )
        );
        deploymentService.archiveDeployment(deployment.id());

        assertThatThrownBy(() -> deploymentDeletionService.queueDeleteDeployment(
            deployment.id(),
            new DeleteDeploymentRequest(false, null, "Attempt delete while consumer is still bound.")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(responseStatusException.getReason()).contains("consumer is bound");
            });
    }

    @Test
    void deploymentCustomerMoveIsBlockedWhileConsumerRemainsBound() {
        PlatformCustomerSummary customerA = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Source Customer", "Source customer")
        );
        PlatformCustomerSummary customerB = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Target Customer", "Target customer")
        );
        var tenantB = platformCustomerTenantService.createTenant(
            customerB.id(),
            new CreatePlatformTenantRequest("Target Tenant", "Target tenant")
        );
        DeploymentSummary deployment = deploymentService.createDeployment(new CreateDeploymentRequest(
            "Consumer Move Guard",
            "dev",
            "dev-openai-lucene",
            "default",
            null,
            customerA.id(),
            null
        ));
        platformCustomerConsumerService.createConsumer(
            customerA.id(),
            new CreatePlatformConsumerRequest(
                "move-guard-consumer",
                "Move guard consumer",
                "Bound during customer move attempt.",
                deployment.id(),
                "Initial binding."
            )
        );

        assertThatThrownBy(() -> deploymentService.updateDeploymentTenantBinding(
            deployment.id(),
            new UpdateDeploymentTenantBindingRequest(customerB.id(), tenantB.id())
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(responseStatusException.getReason()).contains("consumer is bound");
            });

        platformCustomerConsumerService.updateBinding(
            customerA.id(),
            "move-guard-consumer",
            new UpdatePlatformConsumerBindingRequest(null, "Unbind before migration.")
        );

        DeploymentOverviewSummary rebound = deploymentService.updateDeploymentTenantBinding(
            deployment.id(),
            new UpdateDeploymentTenantBindingRequest(customerB.id(), tenantB.id())
        );
        assertThat(rebound.binding()).isNotNull();
        assertThat(rebound.binding().customerId()).isEqualTo(customerB.id());
    }

    private TenantScopedVectorResourceEntity sharedHandle(String id,
                                                          String customerId,
                                                          String tenantId,
                                                          String deploymentId,
                                                          String resourceStatus,
                                                          Instant updatedAt) {
        TenantScopedVectorResourceEntity entity = new TenantScopedVectorResourceEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setTenantId(tenantId);
        entity.setDeploymentId(deploymentId);
        entity.setVendor("pinecone");
        entity.setVectorStrategy("pinecone");
        entity.setVectorProvisioningMode("EXTERNAL_EXISTING");
        entity.setVectorStoragePosture("SHARED");
        entity.setResourceStatus(resourceStatus);
        entity.setScopeType("NAMESPACE_PREFIX");
        entity.setRegistryKey("pinecone|namespace_prefix|shared-root|" + tenantId + "|");
        entity.setRootResourceLabel("Pinecone Index");
        entity.setRootResourceValue("shared-root");
        entity.setScopePrefix(tenantId);
        entity.setTenantHandle(tenantId);
        entity.setScopePattern(tenantId + "__<entity-type>");
        entity.setLifecycleOwner("CUSTOMER_MANAGED");
        entity.setDetailsJson("{\"summaryStatus\":\"READY\",\"summaryMessage\":\"Historical handle retained for audit.\"}");
        entity.setCreatedAt(updatedAt.minusSeconds(3600));
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
