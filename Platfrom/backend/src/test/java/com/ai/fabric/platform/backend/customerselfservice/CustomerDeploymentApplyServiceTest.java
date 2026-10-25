package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentApplyContracts.Request;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import org.junit.jupiter.api.*;
import java.util.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerDeploymentApplyServiceTest {
    private final CustomerOfferEntitlementService actors = mock(CustomerOfferEntitlementService.class);
    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerSelfServiceOperationBoundary boundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final CustomerReviewedCompositionService composition = mock(CustomerReviewedCompositionService.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final DeploymentService engine = mock(DeploymentService.class);
    private final DeploymentReleaseSummary result = mock(DeploymentReleaseSummary.class);
    private final CustomerDeploymentApplyService service = new CustomerDeploymentApplyService(actors, associations,
        deployments, versions, authorization, boundary, composition, commands, engine, CustomerOfferTestFixtures.codec(),
        mock(PlatformAuditService.class));
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private final Authorization approved = new Authorization(null, null);

    @BeforeEach void fixture() {
        when(actors.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        when(versions.findById("version")).thenReturn(Optional.of(version));
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        when(result.id()).thenReturn("release");
        when(engine.applyVersion(eq("deployment"), eq("version"), isNull(), anyString(), nullable(String.class), anyString())).thenReturn(result);
    }

    @Test void usesExplicitProductionTargetAndChangedRuntimeThroughTheNormalIdempotentEngine() {
        var request = new Request("version", "production-target", "new-runtime", "upgrade-key");
        assertThat(service.apply("historical-offer", "deployment", request)).isSameAs(result);
        verify(authorization).authorize(eq("customer"), argThat(r -> r.targetProfileId().equals("production-target") && r.operation() == Operation.APPLY));
        verify(boundary).requireArtifact(approved, "new-runtime");
        verify(boundary).requireVersion(approved, version);
        verify(engine).applyVersion("deployment", "version", null, "production-target", "new-runtime", "upgrade-key");
        verifyNoInteractions(associations, composition, commands);
        verify(actors, never()).requireEntitlement(any(), any(), any(), anyBoolean());
    }

    @Test void sourceArtifactMayBeAbsentForTheNormalNonImageSourcePath() {
        assertThat(service.apply(null, "deployment", new Request("version", "git-target", null, "git-apply"))).isSameAs(result);
        verify(boundary, never()).requireArtifact(any(), any());
        verify(engine).applyVersion("deployment", "version", null, "git-target", null, "git-apply");
    }

    @Test void replayIsDelegatedToTheNormalEngineNotBootstrapGrantReceipts() {
        var request = new Request("version", "production-target", "new-runtime", "upgrade-key");
        assertThat(service.apply("offer", "deployment", request)).isSameAs(result);
        assertThat(service.apply("offer", "deployment", request)).isSameAs(result);
        verify(engine, times(2)).applyVersion("deployment", "version", null, "production-target", "new-runtime", "upgrade-key");
        verifyNoInteractions(commands);
    }

    @Test void foreignDeploymentOrVersionAndDraftVersionNeverReachApply() {
        var request = new Request("version", "production-target", "runtime", "key");
        deployment.setCustomerId("foreign");
        assertThatThrownBy(() -> service.apply("offer", "deployment", request)).hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        deployment.setCustomerId("customer"); version.setDeploymentId("foreign");
        assertThatThrownBy(() -> service.apply("offer", "deployment", request)).hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        version.setDeploymentId("deployment"); version.setStatus("DRAFT");
        assertThatThrownBy(() -> service.apply("offer", "deployment", request)).hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        verifyNoInteractions(engine);
    }
}
