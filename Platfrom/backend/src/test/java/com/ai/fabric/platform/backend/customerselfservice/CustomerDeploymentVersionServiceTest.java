package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.*;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerDeploymentVersionServiceTest {
    private final CustomerOfferEntitlementService actors = mock(CustomerOfferEntitlementService.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerSelfServiceOperationBoundary boundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final CustomerEntitlementCommandRepository commands = mock(CustomerEntitlementCommandRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentService engine = mock(DeploymentService.class);
    private final CustomerDeploymentVersionService service = new CustomerDeploymentVersionService(actors, authorization,
        boundary, commands, deployments, versions, engine, CustomerOfferTestFixtures.codec(), mock(PlatformAuditService.class));
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private final Authorization approved = new Authorization(null, null);
    private final PublishRequest request = new PublishRequest("draft", "production-target", "publish-key");

    @BeforeEach void fixture() {
        when(actors.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findByIdForUpdate("deployment")).thenReturn(Optional.of(deployment));
        when(authorization.authorize(eq("customer"), any())).thenReturn(approved);
        var draft = mock(DeploymentDraftResponse.class); when(draft.id()).thenReturn("draft");
        when(engine.getActiveDraftForDeploymentForTrustedCaller("deployment")).thenReturn(draft);
        when(engine.publishDraftForTrustedCaller("draft")).thenReturn(new DeploymentVersionSummary(
            "version", "deployment", "draft", "v2", "PUBLISHED", "hash", false, Instant.now()));
        version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment");
        version.setSourceDraftId("draft"); version.setStatus("PUBLISHED"); version.setVersionLabel("v2"); version.setConfigHash("hash");
        when(versions.findById("version")).thenReturn(Optional.of(version));
    }

    @Test void publishesTheExplicitOwnedDraftThroughTheNormalCompilerWithoutAnInvitation() {
        assertThat(service.publish("old-offer", "deployment", request))
            .isEqualTo(new PublishResult("deployment", "version", "v2", "hash", "production-target"));
        verify(engine).publishDraftForTrustedCaller("draft"); verify(boundary).requireVersion(approved, version);
        verify(actors, never()).requireEntitlement(any(), any(), any(), anyBoolean());
        verifyNoInteractions(commands);
    }

    @Test void repeatedPublicationOfTheSameDraftReturnsItsActualPublishedVersion() {
        when(versions.findByDeploymentIdOrderByPublishedAtDesc("deployment")).thenReturn(List.of(version));
        assertThat(service.publish(null, "deployment", request).versionId()).isEqualTo("version");
        verify(boundary).requireVersion(approved, version);
        verify(engine, never()).publishDraftForTrustedCaller(anyString());
    }

    @Test void staleOrForeignDraftCannotPublishTheNextActiveDraft() {
        assertThatThrownBy(() -> service.publish("offer", "deployment",
            new PublishRequest("other-draft", "production-target", "key"))).hasMessageContaining(REVISION_CONFLICT.name());
        deployment.setCustomerId("foreign");
        assertThatThrownBy(() -> service.publish("offer", "deployment", request)).hasMessageContaining(RESOURCE_SCOPE_MISMATCH.name());
        verify(engine, never()).publishDraftForTrustedCaller(anyString());
    }

    @Test void authorizationFailurePreventsTheCompilerCall() {
        doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny(OPERATION_FORBIDDEN))
            .when(authorization).authorize(any(), any());
        assertThatThrownBy(() -> service.publish("offer", "deployment", request)).hasMessageContaining(OPERATION_FORBIDDEN.name());
        verifyNoInteractions(engine);
    }
}
