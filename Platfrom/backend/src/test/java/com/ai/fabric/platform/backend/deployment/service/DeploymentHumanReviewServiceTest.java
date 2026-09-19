package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentHumanReviewDecisionRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentHumanReviewServiceTest {

    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
    private final PlatformCustomerAccessService customerAccessService = mock(PlatformCustomerAccessService.class);
    private final PlatformSecretService secretService = mock(PlatformSecretService.class);
    private final RuntimePrivateAssertionSigningService assertionSigningService =
        mock(RuntimePrivateAssertionSigningService.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void customerAdministratorDecisionUsesScopedEndUserAssertionAndKeepsRuntimeKeyServerSide() throws Exception {
        authenticate(new PlatformPrincipal(
            "owner@example.com",
            PlatformRole.CUSTOMER_ADMIN,
            "Customer owner",
            "SESSION"
        ));
        DeploymentEntity deployment = deploymentWithReview();
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(reviewVersion()));
        when(secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn("runtime-private-key");
        when(assertionSigningService.isConfigured()).thenReturn(true);
        when(assertionSigningService.toAuthorizationHeaderValue(any())).thenReturn("Bearer reviewer-assertion");

        HttpResponse<String> runtimeResponse = mock(HttpResponse.class);
        when(runtimeResponse.statusCode()).thenReturn(200);
        when(runtimeResponse.body()).thenReturn("{\"taskId\":\"task-1\",\"status\":\"APPROVED\"}");
        doReturn(runtimeResponse).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        DeploymentHumanReviewService service = service();
        var result = service.decide(
            "dep-1",
            "task-1",
            new SubmitDeploymentHumanReviewDecisionRequest("APPROVE", 3, "decision-1")
        );

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body().path("status").asText()).isEqualTo("APPROVED");
        verify(customerAccessService).requireDeploymentCustomerAccess("customer-1");

        ArgumentCaptor<RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims> claims =
            ArgumentCaptor.forClass(RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims.class);
        verify(assertionSigningService).toAuthorizationHeaderValue(claims.capture());
        assertThat(claims.getValue().subjectId()).isEqualTo("owner@example.com");
        assertThat(claims.getValue().subjectType()).isEqualTo("END_USER");
        assertThat(claims.getValue().deploymentId()).isEqualTo("dep-1");
        assertThat(claims.getValue().customerId()).isEqualTo("customer-1");
        assertThat(claims.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(claims.getValue().grantedScopes())
            .containsExactly("review:tasks:view", "review:tasks:decide");

        ArgumentCaptor<HttpRequest> forwarded = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(forwarded.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(forwarded.getValue().headers().firstValue(
            RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER
        )).contains("runtime-private-key");
        assertThat(forwarded.getValue().headers().firstValue(
            RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER
        )).contains("Bearer reviewer-assertion");
        assertThat(forwarded.getValue().uri().toString())
            .isEqualTo("https://runtime.example/api/reviews/v1/tasks/task-1/decisions");
        verify(auditService).record(eq("DEPLOYMENT_HUMAN_REVIEW_DECIDED"), eq("DEPLOYMENT"), eq("dep-1"), any());
    }

    @Test
    void platformAdministratorCannotActAsCustomerReviewer() {
        authenticate(new PlatformPrincipal(
            "platform@example.com",
            PlatformRole.PLATFORM_ADMIN,
            "Platform administrator",
            "SESSION"
        ));
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deploymentWithReview()));

        assertThatThrownBy(() -> service().inbox("dep-1", 50))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("customer administrator");

        verify(customerAccessService, never()).requireDeploymentCustomerAccess(any());
        verify(assertionSigningService, never()).toAuthorizationHeaderValue(any());
    }

    @Test
    void deploymentWithoutHumanReviewExtensionIsRejectedBeforeRuntimeCall() throws Exception {
        authenticate(new PlatformPrincipal(
            "owner@example.com",
            PlatformRole.CUSTOMER_ADMIN,
            "Customer owner",
            "SESSION"
        ));
        DeploymentEntity deployment = deploymentWithReview();
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        DeploymentVersionEntity version = reviewVersion();
        version.setBehaviorConfigJson("{\"executionExtensions\":[]}");
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service().inbox("dep-1", 50))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Human Review is not enabled");

        verify(httpClient, never()).send(any(), any());
    }

    private DeploymentHumanReviewService service() {
        return new DeploymentHumanReviewService(
            deploymentRepository,
            versionRepository,
            customerAccessService,
            secretService,
            assertionSigningService,
            auditService,
            objectMapper,
            httpClient
        );
    }

    private DeploymentEntity deploymentWithReview() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setActiveVersionId("ver-1");
        return deployment;
    }

    private DeploymentVersionEntity reviewVersion() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-1");
        version.setBehaviorConfigJson("{\"executionExtensions\":[\"HUMAN_REVIEW\"]}");
        return version;
    }

    private void authenticate(PlatformPrincipal principal) {
        var authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
