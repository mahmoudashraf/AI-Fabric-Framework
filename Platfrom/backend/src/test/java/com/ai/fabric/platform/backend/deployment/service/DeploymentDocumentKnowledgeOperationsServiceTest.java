package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
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

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

class DeploymentDocumentKnowledgeOperationsServiceTest {

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
    void indexUsesDeploymentLocalRuntimeNarrowScopeAndCallerIdempotencyKey() throws Exception {
        configureDocumentDeployment();
        doReturn(runtimeResponse(202, "{\"operation\":\"INDEX_SUBMITTED\"}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var result = service().index("dep-1", "source / one", "index-request-1");

        assertThat(result.statusCode()).isEqualTo(202);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString())
            .isEqualTo("https://runtime.example/api/documents/sources/source%20%2F%20one/index");
        assertThat(request.getValue().headers().firstValue("Idempotency-Key"))
            .contains("index-request-1");
        assertThat(request.getValue().headers().firstValue(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER))
            .contains("private-runtime-key");

        ArgumentCaptor<RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims> claims =
            ArgumentCaptor.forClass(RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims.class);
        verify(assertionSigningService).toAuthorizationHeaderValue(claims.capture());
        assertThat(claims.getValue().audiences()).containsExactly("dep-1");
        assertThat(claims.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(claims.getValue().grantedScopes()).containsExactly("documents:index");
        verify(auditService).record(eq("DOCUMENT_SOURCE_INDEX_REQUESTED"), eq("DEPLOYMENT"), eq("dep-1"), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void readOperationUsesReadOnlyScope() throws Exception {
        configureDocumentDeployment();
        doReturn(runtimeResponse(200, "{\"status\":\"READY\"}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        service().connectorStatus("dep-1");

        ArgumentCaptor<RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims> claims =
            ArgumentCaptor.forClass(RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims.class);
        verify(assertionSigningService).toAuthorizationHeaderValue(claims.capture());
        assertThat(claims.getValue().grantedScopes()).containsExactly("documents:read");
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void retentionCleanupUsesIndexScopeAndRecordsSourcePreservation() throws Exception {
        configureDocumentDeployment();
        doReturn(runtimeResponse(200, "{\"manifestsDeleted\":2,\"customerSourceObjectsDeleted\":false}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var result = service().cleanupRetention("dep-1");

        assertThat(result.statusCode()).isEqualTo(200);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString())
            .isEqualTo("https://runtime.example/api/documents/retention/cleanup");
        ArgumentCaptor<RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims> claims =
            ArgumentCaptor.forClass(RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims.class);
        verify(assertionSigningService).toAuthorizationHeaderValue(claims.capture());
        assertThat(claims.getValue().grantedScopes()).containsExactly("documents:index");
        verify(auditService).record(eq("DOCUMENT_RETENTION_CLEANUP_COMPLETED"), eq("DEPLOYMENT"), eq("dep-1"), any());
    }

    @Test
    void registrationRejectsRawContentBeforeCallingTheRuntime() throws Exception {
        configureDocumentDeployment();

        assertThatThrownBy(() -> service().register(
            "dep-1",
            objectMapper.readTree("""
                {"datasetId":"document-knowledge","objectReference":"safe.txt","content":"raw bytes"}
                """),
            "register-1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported document operation field: content");

        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void oversizedRuntimeResponseIsRejectedAtThePlatformBoundary() throws Exception {
        configureDocumentDeployment();
        byte[] oversized = new byte[(2 * 1024 * 1024) + 1];
        doReturn(runtimeResponse(200, oversized))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertThatThrownBy(() -> service().list("dep-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("response exceeded the Platform boundary");
    }

    @Test
    void activeVersionWithoutDocumentDatasetFailsClosed() throws Exception {
        authenticate();
        DeploymentEntity deployment = deployment();
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        DeploymentVersionEntity version = documentVersion();
        version.setMarketplaceDatasetConfigJson("{\"datasets\":[]}");
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service().list("dep-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not claim Document Knowledge Operations");

        verify(httpClient, never()).send(any(), any());
    }

    private DeploymentDocumentKnowledgeOperationsService service() {
        return new DeploymentDocumentKnowledgeOperationsService(
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

    private void configureDocumentDeployment() {
        authenticate();
        DeploymentEntity deployment = deployment();
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(accessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(documentVersion()));
        when(secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn("private-runtime-key");
        when(assertionSigningService.isConfigured()).thenReturn(true);
        when(assertionSigningService.toAuthorizationHeaderValue(any())).thenReturn("Bearer signed-assertion");
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setActiveVersionId("ver-1");
        return deployment;
    }

    private DeploymentVersionEntity documentVersion() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-1");
        version.setMarketplaceDatasetConfigJson("""
            {"datasets":[{"datasetId":"document-knowledge","ingestionMode":"EXTERNAL_DOCUMENT_STORAGE"}]}
            """);
        return version;
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<java.io.InputStream> runtimeResponse(int status, String body) {
        return runtimeResponse(status, body.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<java.io.InputStream> runtimeResponse(int status, byte[] body) {
        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body));
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
