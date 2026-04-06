package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentTenantMigrationRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantMigrationExecutionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantMigrationPreviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.PreviewDeploymentTenantMigrationRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTenantMigrationService;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformTenantRequest;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSummary;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DeploymentTenantMigrationIntegrationTest {

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentTenantMigrationService deploymentTenantMigrationService;

    @Autowired
    private PlatformCustomerTenantService platformCustomerTenantService;

    @Test
    void migrationCreatesNewTenantBoundDeploymentFromCurrentDraft() {
        PlatformCustomerSummary customer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Migration Customer", "Customer for tenant migration")
        );
        PlatformTenantSummary sourceTenant = platformCustomerTenantService.createTenant(
            customer.id(),
            new CreatePlatformTenantRequest("Source Tenant", "Current owner")
        );
        PlatformTenantSummary targetTenant = platformCustomerTenantService.createTenant(
            customer.id(),
            new CreatePlatformTenantRequest("Target Tenant", "Future owner")
        );

        DeploymentSummary sourceDeployment = deploymentService.createDeployment(new CreateDeploymentRequest(
            "Migration Source",
            "stage",
            "dev-openai-lucene",
            "default",
            "LOCAL_MANAGED",
            customer.id(),
            sourceTenant.id()
        ));
        DeploymentDraftResponse sourceDraft = deploymentService.getActiveDraftForDeployment(sourceDeployment.id());
        DeploymentVersionSummary publishedVersion = deploymentService.publishDraft(sourceDraft.id());

        DeploymentTenantMigrationPreviewSummary preview = deploymentTenantMigrationService.previewMigration(
            sourceDeployment.id(),
            new PreviewDeploymentTenantMigrationRequest(
                customer.id(),
                targetTenant.id(),
                "Migration Source - Target",
                "stage"
            )
        );

        assertThat(preview.status()).isEqualTo("READY");
        assertThat(preview.sourceDeploymentId()).isEqualTo(sourceDeployment.id());
        assertThat(preview.targetTenantId()).isEqualTo(targetTenant.id());
        assertThat(preview.publishedVersionCount()).isEqualTo(1);
        assertThat(preview.releaseCount()).isZero();

        DeploymentTenantMigrationExecutionSummary created = deploymentTenantMigrationService.createMigrationDeployment(
            sourceDeployment.id(),
            new CreateDeploymentTenantMigrationRequest(
                customer.id(),
                targetTenant.id(),
                "Migration Source - Target",
                "stage",
                "Move the deployment to the target tenant without mutating history."
            )
        );

        assertThat(created.deploymentId()).isNotEqualTo(sourceDeployment.id());
        assertThat(created.customerId()).isEqualTo(customer.id());
        assertThat(created.tenantId()).isEqualTo(targetTenant.id());

        DeploymentOverviewSummary migratedOverview = deploymentService.listDeploymentOverviews(false).stream()
            .filter(item -> item.id().equals(created.deploymentId()))
            .findFirst()
            .orElseThrow();
        DeploymentOverviewSummary sourceOverview = deploymentService.listDeploymentOverviews(false).stream()
            .filter(item -> item.id().equals(sourceDeployment.id()))
            .findFirst()
            .orElseThrow();

        assertThat(migratedOverview.binding()).isNotNull();
        assertThat(migratedOverview.binding().tenantId()).isEqualTo(targetTenant.id());
        assertThat(migratedOverview.binding().customerId()).isEqualTo(customer.id());
        assertThat(sourceOverview.binding()).isNotNull();
        assertThat(sourceOverview.binding().tenantId()).isEqualTo(sourceTenant.id());
        assertThat(sourceOverview.activeVersion()).isEqualTo("draft");
        assertThat(migratedOverview.activeVersion()).isEqualTo("draft");

        DeploymentDraftResponse migratedDraft = deploymentService.getActiveDraftForDeployment(created.deploymentId());
        assertThat(migratedDraft.actionsConfig()).isEqualTo(sourceDraft.actionsConfig());
        assertThat(migratedDraft.entityConfig()).isEqualTo(sourceDraft.entityConfig());
        assertThat(migratedDraft.routingConfig()).isEqualTo(sourceDraft.routingConfig());
        assertThat(migratedDraft.providerConfig()).isEqualTo(sourceDraft.providerConfig());
        assertThat(migratedDraft.securityConfig()).isEqualTo(sourceDraft.securityConfig());
        assertThat(migratedDraft.promptConfig()).isEqualTo(sourceDraft.promptConfig());
    }

    @Test
    void migrationBlocksCrossCustomerReassignment() {
        PlatformCustomerSummary sourceCustomer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Source Customer", "Source ownership")
        );
        PlatformTenantSummary sourceTenant = platformCustomerTenantService.createTenant(
            sourceCustomer.id(),
            new CreatePlatformTenantRequest("Source Tenant", "Current source tenant")
        );
        PlatformCustomerSummary targetCustomer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Target Customer", "Different customer")
        );
        PlatformTenantSummary targetTenant = platformCustomerTenantService.createTenant(
            targetCustomer.id(),
            new CreatePlatformTenantRequest("Target Tenant", "Target tenant in another customer")
        );

        DeploymentSummary sourceDeployment = deploymentService.createDeployment(new CreateDeploymentRequest(
            "Cross Customer Source",
            "dev",
            "dev-openai-lucene",
            "default",
            "LOCAL_MANAGED",
            sourceCustomer.id(),
            sourceTenant.id()
        ));
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(sourceDeployment.id());
        deploymentService.publishDraft(draft.id());

        assertThatThrownBy(() -> deploymentTenantMigrationService.previewMigration(
            sourceDeployment.id(),
            new PreviewDeploymentTenantMigrationRequest(targetCustomer.id(), targetTenant.id(), null, null)
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(responseStatusException.getReason()).contains("same customer boundary");
            });
    }
}
