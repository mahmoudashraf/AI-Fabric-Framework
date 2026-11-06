package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentHumanReviewDecisionRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentHumanReviewServiceTest {

    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final PlatformCustomerAccessService customerAccess = new PlatformCustomerAccessService(users, customers, mock(com.ai.fabric.platform.backend.security.service.CustomerStaffAccessPolicy.class));
    private final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final PlatformUserEntity owner = new PlatformUserEntity();
    private DeploymentReleaseEntity production;
    private DeploymentVersionEntity productionVersion;

    @BeforeEach
    void setUp() {
        authenticate(PlatformRole.CUSTOMER_ADMIN, "SUPABASE");
        owner.setId("usr-owner");
        owner.setRole("CUSTOMER_ADMIN");
        owner.setStatus("ACTIVE");
        owner.setCustomerId("customer-1");
        when(users.findById("usr-owner")).thenReturn(Optional.of(owner));
        customer("customer-1");
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setActiveVersionId("unrelated-global-version");
        deployment.setRuntimeBaseUrl("https://wrong-global-runtime.example");
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        production = release("production", "rel-production", "ver-production");
        productionVersion = version("ver-production");
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("dep-1", "production"))
            .thenReturn(List.of(production));
        when(authority.issueReviewedIntegrationSessionHeaders(any(), any(), any(), anyString(), anyList()))
            .thenReturn(Map.of(
                RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, "target-private-key",
                RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER, "Bearer target-reviewer-assertion"
            ));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPABASE", "SESSION"})
    void inboxUsesSelectedProductionReleaseNotGlobalEndpointOrVersion(String mode) throws Exception {
        authenticate(PlatformRole.CUSTOMER_ADMIN, mode);
        runtimeResponse("[]");

        var result = service().inbox("dep-1", "production", 500);

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body().isArray()).isTrue();
        verify(authority).issueReviewedIntegrationSessionHeaders(deployment, productionVersion, production,
            "owner@example.com", List.of("review:tasks:view"));
        HttpRequest request = forwardedRequest();
        assertThat(request.uri().toString()).isEqualTo("https://production.example/api/reviews/v1/tasks?limit=200");
        assertThat(request.headers().firstValue(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER))
            .contains("target-private-key");
        assertThat(request.headers().firstValue(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER))
            .contains("Bearer target-reviewer-assertion");
        verify(versions, never()).findById("unrelated-global-version");
    }

    @Test
    void detailUsesSelectedStagingReleaseAndViewScopeOnly() throws Exception {
        var staging = release("staging", "rel-staging", "ver-staging");
        var stagingVersion = version("ver-staging");
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("dep-1", "staging"))
            .thenReturn(List.of(staging));
        runtimeResponse("{\"taskId\":\"task-1\"}");

        service().detail("dep-1", "staging", "task-1");

        assertThat(forwardedRequest().uri().toString()).isEqualTo("https://staging.example/api/reviews/v1/tasks/task-1");
        verify(authority).issueReviewedIntegrationSessionHeaders(deployment, stagingVersion, staging,
            "owner@example.com", List.of("review:tasks:view"));
        verify(releases, never()).findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("dep-1", "production");
    }

    @Test
    void decisionUsesExactReleaseAndDecisionScopesAndRetainsAudit() throws Exception {
        runtimeResponse("{\"taskId\":\"task-1\",\"status\":\"APPROVED\"}");

        var result = service().decide("dep-1", "production", "task-1",
            new SubmitDeploymentHumanReviewDecisionRequest("APPROVE", 3, "decision-1"));

        assertThat(result.body().path("status").asText()).isEqualTo("APPROVED");
        verify(authority).issueReviewedIntegrationSessionHeaders(deployment, productionVersion, production,
            "owner@example.com", List.of("review:tasks:view", "review:tasks:decide"));
        assertThat(forwardedRequest().method()).isEqualTo("POST");
        assertThat(forwardedRequest().uri().toString())
            .isEqualTo("https://production.example/api/reviews/v1/tasks/task-1/decisions");
        verify(audit).record(eq("DEPLOYMENT_HUMAN_REVIEW_DECIDED"), eq("DEPLOYMENT"), eq("dep-1"),
            eq(Map.of("taskId", "task-1", "decision", "APPROVE", "decisionId", "decision-1",
                "reviewer", "owner@example.com", "targetProfileId", "production", "releaseId", "rel-production")));
    }

    @Test
    void differentCustomerOwnerIsRejectedBeforeTargetLookupOrSigning() {
        owner.setCustomerId("other-customer");
        customer("other-customer");

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(releases, authority, http);
    }

    @ParameterizedTest
    @CsvSource({"PLATFORM_ADMIN,SESSION", "CUSTOMER_ADMIN,API_KEY"})
    void nonCustomerSessionCannotReview(PlatformRole role, String mode) {
        authenticate(role, mode);

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("customer administrator");

        verifyNoInteractions(releases, authority, http);
    }

    @ParameterizedTest
    @CsvSource({"APPLIED_VERIFIED,FAILED", "APPLIED_UNVERIFIED,PASSED", "VERIFYING,RUNNING"})
    void currentUnverifiedReleaseCannotFallBackToOlderVerifiedRelease(String status, String verification) {
        production.setStatus(status);
        production.setVerificationStatus(verification);
        var older = release("production", "rel-older", "ver-older");
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("dep-1", "production"))
            .thenReturn(List.of(production, older));

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("current verified release");

        verifyNoInteractions(authority, http);
    }

    @Test
    void targetWithoutActiveReleaseCannotUseAnotherTargetOrHistoricalRelease() {
        production.setProvisioningStatus("SUPERSEDED");

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no active release");

        verifyNoInteractions(authority, http);
    }

    @Test
    void newerNonServingAttemptDoesNotReplaceCurrentActiveRelease() throws Exception {
        var attempt = release("production", "rel-attempt", "ver-attempt");
        attempt.setProvisioningStatus("FAILED");
        when(releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc("dep-1", "production"))
            .thenReturn(List.of(attempt, production));
        runtimeResponse("[]");

        service().inbox("dep-1", "production", 50);

        verify(authority).issueReviewedIntegrationSessionHeaders(deployment, productionVersion, production,
            "owner@example.com", List.of("review:tasks:view"));
    }

    @Test
    void humanReviewMustBeEnabledOnSelectedReleaseVersion() {
        productionVersion.setBehaviorConfigJson("{\"executionExtensions\":[]}");

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("not enabled on the selected target");

        verifyNoInteractions(authority, http);
    }

    @Test
    void foreignReleaseVersionIsRejectedBeforeSigning() {
        productionVersion.setDeploymentId("other-deployment");

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("release version is unavailable");

        verifyNoInteractions(authority, http);
    }

    @Test
    void releaseFromAnotherTargetIsRejectedBeforeSigning() {
        production.setTargetProfileId("staging");

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("current verified release");

        verifyNoInteractions(authority, http);
    }

    @Test
    void unavailableReleaseAuthorityDoesNotFallBackOrCallRuntime() {
        when(authority.issueReviewedIntegrationSessionHeaders(deployment, productionVersion, production,
            "owner@example.com", List.of("review:tasks:view")))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Frozen release authority is unavailable."));

        assertThatThrownBy(() -> service().inbox("dep-1", "production", 50))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(http);
    }

    @Test
    void explicitTargetIsRequiredInsteadOfUsingGlobalPointer() {
        assertThatThrownBy(() -> service().inbox("dep-1", " ", 50))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("targetProfileId is required");

        verifyNoInteractions(releases, authority, http);
    }

    private DeploymentHumanReviewService service() {
        return new DeploymentHumanReviewService(deployments, releases, versions, customerAccess, authority,
            new DeploymentReleaseEndpointService(mapper), audit, mapper, http);
    }

    private DeploymentReleaseEntity release(String target, String id, String versionId) {
        var release = new DeploymentReleaseEntity();
        release.setId(id);
        release.setDeploymentId("dep-1");
        release.setTargetProfileId(target);
        release.setDeploymentVersionId(versionId);
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningStatus("ACTIVE");
        release.setProvisioningDetailsJson("{\"runtimeBaseUrl\":\"https://" + target + ".example\"}");
        return release;
    }

    private DeploymentVersionEntity version(String id) {
        var version = new DeploymentVersionEntity();
        version.setId(id);
        version.setDeploymentId("dep-1");
        version.setBehaviorConfigJson("{\"executionExtensions\":[\"HUMAN_REVIEW\"]}");
        when(versions.findById(id)).thenReturn(Optional.of(version));
        return version;
    }

    private void customer(String id) {
        var customer = new PlatformCustomerEntity();
        customer.setId(id);
        customer.setStatus("ACTIVE");
        when(customers.findById(id)).thenReturn(Optional.of(customer));
    }

    @SuppressWarnings("unchecked")
    private void runtimeResponse(String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        doReturn(response).when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private HttpRequest forwardedRequest() throws Exception {
        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        return request.getValue();
    }

    private void authenticate(PlatformRole role, String mode) {
        var principal = new PlatformPrincipal("owner@example.com", role, "Customer owner", mode, "usr-owner");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(principal.role().authority()))));
    }
}
