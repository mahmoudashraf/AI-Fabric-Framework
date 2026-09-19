package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentAgenticExecutionRequest;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentSmartBrainTriggerRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
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

class DeploymentBehaviorOperationsServiceTest {

    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
    private final DeploymentAccessService accessService = mock(DeploymentAccessService.class);
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
    void agenticSubmitUsesDeploymentLocalRuntimeAndNarrowExecutionScope() throws Exception {
        authenticate();
        DeploymentEntity deployment = deployment("AGENTIC_SPECIALIST_TEAM");
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(version("AGENTIC_SPECIALIST_TEAM")));
        configurePrivateRuntime();
        HttpResponse<String> response = runtimeResponse(
            202,
            "{\"executionId\":\"exec-1\",\"status\":\"ACCEPTED\"}"
        );
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var result = service().submitAgentic(
            "dep-1",
            new SubmitDeploymentAgenticExecutionRequest("Analyze runtime state", "request-1")
        );

        assertThat(result.statusCode()).isEqualTo(202);
        assertThat(result.body().path("executionId").asText()).isEqualTo("exec-1");
        assertThat(result.body().path("idempotencyKey").asText()).isEqualTo("request-1");
        ArgumentCaptor<HttpRequest> forwarded = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(forwarded.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(forwarded.getValue().uri().toString())
            .isEqualTo("https://runtime.example/api/agentic/v1/executions");
        assertThat(forwarded.getValue().headers().firstValue("Idempotency-Key")).contains("request-1");

        ArgumentCaptor<RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims> claims =
            ArgumentCaptor.forClass(RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims.class);
        verify(assertionSigningService).toAuthorizationHeaderValue(claims.capture());
        assertThat(claims.getValue().issuer()).isEqualTo("platform-runtime:SESSION");
        assertThat(claims.getValue().subjectType()).isEqualTo("INTERNAL_PLATFORM_USER");
        assertThat(claims.getValue().audiences()).containsExactly("dep-1");
        assertThat(claims.getValue().grantedScopes())
            .containsExactly("agentic:deployment-intelligence:execute");
        verify(auditService).record(eq("DEPLOYMENT_AGENTIC_EXECUTION_SUBMITTED"), eq("DEPLOYMENT"), eq("dep-1"), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void agenticReplayForwardsTheOriginalIdempotencyKey() throws Exception {
        authenticate();
        DeploymentEntity deployment = deployment("AGENTIC_SPECIALIST_TEAM");
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(version("AGENTIC_SPECIALIST_TEAM")));
        configurePrivateRuntime();
        doReturn(runtimeResponse(200, "{\"executionId\":\"exec-1\",\"status\":\"COMPLETED\",\"replayed\":true}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var result = service().replayAgentic(
            "dep-1",
            "exec-1",
            new SubmitDeploymentAgenticExecutionRequest("Analyze runtime state", "request-1")
        );

        assertThat(result.body().path("idempotencyKey").asText()).isEqualTo("request-1");
        ArgumentCaptor<HttpRequest> forwarded = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(forwarded.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(forwarded.getValue().uri().toString())
            .isEqualTo("https://runtime.example/api/agentic/v1/executions/exec-1/replay");
        assertThat(forwarded.getValue().headers().firstValue("Idempotency-Key")).contains("request-1");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void smartBrainTriggerForwardsStructuredCloudEventMediaType() throws Exception {
        authenticate();
        DeploymentEntity deployment = deployment("SMART_BRAIN");
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(version("SMART_BRAIN")));
        configurePrivateRuntime();
        doReturn(runtimeResponse(202, "{\"operationId\":\"op-1\",\"status\":\"QUEUED\"}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var result = service().triggerSmartBrain(
            "dep-1",
            "event-analysis",
            new SubmitDeploymentSmartBrainTriggerRequest(
                "event-request-1",
                objectMapper.readTree("""
                    {"specversion":"1.0","id":"event-1","source":"/test","type":"analysis.requested","data":{}}
                    """)
            )
        );

        assertThat(result.body().path("operationId").asText()).isEqualTo("op-1");
        ArgumentCaptor<HttpRequest> forwarded = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(forwarded.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(forwarded.getValue().uri().toString())
            .isEqualTo("https://runtime.example/api/smart-brain/v1/triggers/event-analysis");
        assertThat(forwarded.getValue().headers().firstValue("Content-Type"))
            .contains("application/cloudevents+json");
    }

    @Test
    void behaviorMismatchFailsBeforeCallingRuntime() throws Exception {
        authenticate();
        DeploymentEntity deployment = deployment("CONVERSATIONAL");
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(version("CONVERSATIONAL")));

        assertThatThrownBy(() -> service().submitAgentic(
            "dep-1",
            new SubmitDeploymentAgenticExecutionRequest("Analyze", "request-1")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("requires an active AGENTIC_SPECIALIST_TEAM deployment");

        verify(httpClient, never()).send(any(), any());
    }

    private DeploymentBehaviorOperationsService service() {
        return new DeploymentBehaviorOperationsService(
            deploymentRepository,
            versionRepository,
            accessService,
            secretService,
            assertionSigningService,
            auditService,
            objectMapper,
            httpClient
        );
    }

    private void configurePrivateRuntime() {
        when(secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn("private-runtime-key");
        when(assertionSigningService.isConfigured()).thenReturn(true);
        when(assertionSigningService.toAuthorizationHeaderValue(any())).thenReturn("Bearer signed-assertion");
    }

    private DeploymentEntity deployment(String behaviorType) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setBehaviorType(behaviorType);
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setActiveVersionId("ver-1");
        return deployment;
    }

    private DeploymentVersionEntity version(String behaviorType) {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-1");
        version.setBehaviorConfigJson("{\"type\":\"" + behaviorType + "\"}");
        return version;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> runtimeResponse(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private void authenticate() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "operator@example.com",
            PlatformRole.PLATFORM_OPERATOR,
            "Operator",
            "SESSION"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(principal.role().authority()))
            )
        );
    }
}
