package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
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

    private final DeploymentTargetContextService targetContextService = mock(DeploymentTargetContextService.class);
    private final DeploymentTargetReference target = new DeploymentTargetReference("dep-1", "target-1", "rel-1");
    private final PlatformSecretService secretService = mock(PlatformSecretService.class);
    private final RuntimePrivateAssertionSigningService assertionSigningService =
        mock(RuntimePrivateAssertionSigningService.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary offerBoundary =
        mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary.class);

    @Test
    void rejectedSourceOperationNeverCallsTheDeploymentRuntime() throws Exception {
        configureDocumentDeployment();
        org.mockito.Mockito.doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException
            .deny(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.OPERATION_FORBIDDEN))
            .when(offerBoundary).require(any(), eq("target-1"), eq("rel-1"),
                eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_INDEX),
                eq("index-1"), any(), any(), any(), any());
        assertThatThrownBy(() -> service().index(target, "source-1", "index-1")).hasMessageContaining("OPERATION_FORBIDDEN");
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void documentAudienceIsDerivedFromTheImmutableKnowledgeSourceContract() throws Exception {
        configureDocumentDeployment();
        var version = documentVersion();
        version.setMarketplaceDatasetConfigJson("{\"datasets\":[{\"datasetId\":\"policies\",\"ingestionMode\":\"EXTERNAL_DOCUMENT_STORAGE\"}]}");
        version.setKnowledgeSourceConfigJson("{\"sources\":[{\"datasetRef\":\"policies\",\"authModes\":[\"PUBLIC_RUNTIME_ANONYMOUS\"]}]}");
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(version));
        doReturn(runtimeResponse(202, "{}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        service().index(target, "source-1", "index-1");
        var settings = ArgumentCaptor.forClass(java.util.Map.class);
        verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_INDEX),
            eq("index-1"), settings.capture(), any(), any(),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC));
        assertThat(settings.getValue().get(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Setting.DOCUMENT_AUDIENCE))
            .isEqualTo(objectMapper.getNodeFactory().textNode("PUBLIC"));
    }

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

        var result = service().index(target, "source / one", "index-request-1");

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
        assertThat(claims.getValue().issuer())
            .isEqualTo(RuntimePrivateAccessSupport.ISSUER_PLATFORM_DOCUMENT_OPERATIONS);
        assertThat(claims.getValue().grantedScopes()).containsExactly("documents:index");
        verify(auditService).record(eq("DOCUMENT_SOURCE_INDEX_REQUESTED"), eq("DEPLOYMENT"), eq("dep-1"), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void readOperationUsesReadOnlyScope() throws Exception {
        configureDocumentDeployment();
        doReturn(runtimeResponse(200, "{\"status\":\"READY\"}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        service().connectorStatus(target);

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

        var result = service().cleanupRetention(target);

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
            target,
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

        assertThatThrownBy(() -> service().list(target))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("response exceeded the Platform boundary");
    }

    @Test
    void activeVersionWithoutDocumentDatasetFailsClosed() throws Exception {
        configureDocumentDeployment();
        DeploymentVersionEntity version = documentVersion();
        version.setMarketplaceDatasetConfigJson("{\"datasets\":[]}");
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", false)).thenReturn(context(version));

        assertThatThrownBy(() -> service().list(target))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not claim Document Knowledge Operations");

        verify(httpClient, never()).send(any(), any());
    }

    private DeploymentDocumentKnowledgeOperationsService service() {
        return new DeploymentDocumentKnowledgeOperationsService(
            targetContextService,
            secretService,
            assertionSigningService,
            auditService,
            objectMapper,
            httpClient,
            offerBoundary
        );
    }

    private void configureDocumentDeployment() {
        authenticate();
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", false)).thenReturn(context(documentVersion()));
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(documentVersion()));
        when(secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn("private-runtime-key");
        when(assertionSigningService.isConfigured()).thenReturn(true);
        when(assertionSigningService.toAuthorizationHeaderValue(any())).thenReturn("Bearer signed-assertion");
    }

    private DeploymentTargetContextService.Context context(DeploymentVersionEntity version) {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("target-1");
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        return new DeploymentTargetContextService.Context(deployment(), profile, release, version, "https://runtime.example", null);
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setRuntimeBaseUrl("https://wrong-global-runtime.example");
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
