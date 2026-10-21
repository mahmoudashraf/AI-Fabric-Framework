package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.Duration;
import java.util.List;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;

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
    private final DeploymentRuntimeAuthorityService runtimeAuthority = mock(DeploymentRuntimeAuthorityService.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final HttpClient httpClient = mock(HttpClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary offerBoundary =
        mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary.class);
    private final com.ai.fabric.platform.backend.deployment.admission.CustomerSourcePrerequisiteRefreshService sourcePrerequisites =
        mock(com.ai.fabric.platform.backend.deployment.admission.CustomerSourcePrerequisiteRefreshService.class);

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mixedModesRequireBothAudiencesBeforeTheRuntimeWrite() throws Exception {
        configureDocumentDeployment();
        when(offerBoundary.reviewedDocumentAudiences(any())).thenReturn(java.util.Set.of(DocumentAudience.PUBLIC, DocumentAudience.AUTHENTICATED));
        var version = documentVersion();
        version.setKnowledgeSourceConfigJson("""
            {"sources":[{"datasetRef":"document-knowledge","authModes":[
              "PUBLIC_RUNTIME_ANONYMOUS","PUBLIC_RUNTIME_AUTHENTICATED","PRIVATE_RUNTIME_BACKEND_MEDIATED"]}]}
            """);
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(version));
        doReturn(runtimeResponse(202, "{}")).when(httpClient).send(any(), any());

        service().index(target, "source-1", "mixed-modes-1");

        verify(sourcePrerequisites).ensureFresh(target, Operation.SOURCE_INDEX, "mixed-modes-1",
            java.util.Set.of(DocumentAudience.PUBLIC, DocumentAudience.AUTHENTICATED));
        for (DocumentAudience audience : DocumentAudience.values()) {
            verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"), eq(Operation.SOURCE_INDEX),
                eq("mixed-modes-1"), eq(java.util.Map.of(Setting.DOCUMENT_AUDIENCE,
                    objectMapper.getNodeFactory().textNode(audience.name()))), any(), any(), eq(audience));
        }
    }

    @Test
    void publicAuthorizationCannotMaskAnAuthenticatedAudienceRejection() throws Exception {
        configureDocumentDeployment();
        when(offerBoundary.reviewedDocumentAudiences(any())).thenReturn(java.util.Set.of(DocumentAudience.PUBLIC, DocumentAudience.AUTHENTICATED));
        var version = documentVersion();
        version.setKnowledgeSourceConfigJson("""
            {"sources":[{"datasetRef":"document-knowledge","authModes":[
              "PUBLIC_RUNTIME_ANONYMOUS","PUBLIC_RUNTIME_AUTHENTICATED"]}]}
            """);
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(version));
        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "AUDIENCE_FORBIDDEN"))
            .when(offerBoundary).require(any(), any(), any(), any(), any(), any(), any(), any(), eq(DocumentAudience.AUTHENTICATED));

        assertThatThrownBy(() -> service().index(target, "source-1", "mixed-deny-1"))
            .hasMessageContaining("AUDIENCE_FORBIDDEN");
        verify(httpClient, never()).send(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"connector", "sources"})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void publicOnlyOwnerCanReadMixedTransportDocumentSources(String operation) throws Exception {
        configurePublicOnlyMixedDeployment();
        doReturn(runtimeResponse(200, "{}")).when(httpClient).send(any(), any());

        var result = "connector".equals(operation) ? service().connectorStatus(target) : service().list(target);

        assertThat(result.statusCode()).isEqualTo(200);
        verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"), eq(Operation.READ),
            org.mockito.ArgumentMatchers.isNull(), eq(java.util.Map.of()), any(), any(), eq(DocumentAudience.PUBLIC));
        verify(offerBoundary, never()).require(any(), any(), any(), any(), any(), any(), any(), any(), eq(DocumentAudience.AUTHENTICATED));
        org.mockito.Mockito.verifyNoInteractions(sourcePrerequisites);
        verify(runtimeAuthority).issuePlatformProxyHeaders("dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of("documents:read"), Duration.ofMinutes(5));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void publicOnlyOwnerSourceMutationRefreshesOnlyTheReviewedAudience() throws Exception {
        configurePublicOnlyMixedDeployment();
        doReturn(runtimeResponse(202, "{}")).when(httpClient).send(any(), any());

        service().index(target, "source-1", "public-index-1");

        verify(sourcePrerequisites).ensureFresh(target, Operation.SOURCE_INDEX, "public-index-1", java.util.Set.of(DocumentAudience.PUBLIC));
        verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"), eq(Operation.SOURCE_INDEX), eq("public-index-1"),
            eq(java.util.Map.of(Setting.DOCUMENT_AUDIENCE, objectMapper.getNodeFactory().textNode("PUBLIC"))), any(), any(), eq(DocumentAudience.PUBLIC));
        verify(offerBoundary, never()).require(any(), any(), any(), any(), any(), any(), any(), any(), eq(DocumentAudience.AUTHENTICATED));
    }

    @Test void registrationPreservesRuntimeVisibilityAndMetadataWithoutGrantingAnotherAudience() throws Exception {
        configurePublicOnlyMixedDeployment();
        var transport = mock(DeploymentRuntimeJsonTransport.class);
        var body = objectMapper.readTree("""
            {"datasetId":"document-knowledge","objectReference":"policy.txt","visibility":"tenant",
             "metadata":{"publicationStatus":"PUBLIC_APPROVED"}}
            """);
        when(transport.send(any(), any(), any(), any(), any(), any())).thenReturn(
            new DeploymentRuntimeJsonTransport.Response(201, objectMapper.createObjectNode()));
        var service = new DeploymentDocumentKnowledgeOperationsService(targetContextService, runtimeAuthority, auditService,
            objectMapper, transport, offerBoundary, sourcePrerequisites);

        assertThat(service.register(target, body, "public-register-1").statusCode()).isEqualTo(201);

        verify(transport).send(eq("https://runtime.example"), eq("POST"), eq("/api/documents/sources"), any(), eq(body), eq("public-register-1"));
        verify(sourcePrerequisites).ensureFresh(target, Operation.SOURCE_INDEX, "public-register-1", java.util.Set.of(DocumentAudience.PUBLIC));
        verify(runtimeAuthority).issuePlatformProxyHeaders("dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of("documents:register"), Duration.ofMinutes(5));
    }

    @ParameterizedTest @ValueSource(strings = {"AUTHENTICATED", "authenticated", "unsupported"})
    void registrationCannotSmuggleAnUnsupportedOrForbiddenAudienceThroughVisibility(String visibility) throws Exception {
        configurePublicOnlyMixedDeployment();
        var body = objectMapper.createObjectNode().put("datasetId", "document-knowledge")
            .put("objectReference", "policy.txt").put("visibility", visibility);

        assertThatThrownBy(() -> service().register(target, body, "visibility-1"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("visibility is not allowed");

        org.mockito.Mockito.verifyNoInteractions(sourcePrerequisites, runtimeAuthority);
        verify(httpClient, never()).send(any(), any());
    }

    @Test void disjointReviewedAndSourceAudiencesFailBeforeProbesOrRuntimeCalls() throws Exception {
        configurePublicOnlyMixedDeployment(); var version = documentVersion();
        version.setKnowledgeSourceConfigJson("""
            {"sources":[{"datasetRef":"document-knowledge","authModes":["PRIVATE_RUNTIME_BACKEND_MEDIATED"]}]}
            """);
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(version));

        assertThatThrownBy(() -> service().index(target, "source-1", "disjoint-1")).hasMessageContaining("SETTING_FORBIDDEN");

        org.mockito.Mockito.verifyNoInteractions(sourcePrerequisites, runtimeAuthority);
        verify(httpClient, never()).send(any(), any());
    }

    @Test void emptyReviewedAudiencesDoNotBypassTheOwningAuthorizationBoundary() throws Exception {
        configureDocumentDeployment(); authenticate(PlatformRole.CUSTOMER_ADMIN);
        when(offerBoundary.reviewedDocumentAudiences(any())).thenReturn(java.util.Set.of());
        org.mockito.Mockito.doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.SETTING_FORBIDDEN))
            .when(offerBoundary).require(any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service().list(target)).hasMessageContaining("SETTING_FORBIDDEN");

        verify(httpClient, never()).send(any(), any());
        org.mockito.Mockito.verifyNoInteractions(runtimeAuthority);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "\"PUBLIC_RUNTIME_ANONYMOUS\"", "[\"UNKNOWN\"]",
        "[\"PUBLIC_RUNTIME_ANONYMOUS\",\"UNKNOWN\"]", "[null]", "[\"\"]"})
    void invalidOrMissingSourceModesFailBeforePrerequisiteOrRuntimeCalls(String modes) throws Exception {
        configureDocumentDeployment();
        var version = documentVersion();
        version.setKnowledgeSourceConfigJson("{\"sources\":[{\"datasetRef\":\"document-knowledge\",\"authModes\":" + modes + "}]}");
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(version));

        assertThatThrownBy(() -> service().index(target, "source-1", "invalid-modes-1"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("authModes");
        org.mockito.Mockito.verifyNoInteractions(sourcePrerequisites, offerBoundary, runtimeAuthority);
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void missingKnowledgeSourceMappingFailsClosed() throws Exception {
        configureDocumentDeployment();
        var version = documentVersion();
        version.setKnowledgeSourceConfigJson("{\"sources\":[]}");
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", false)).thenReturn(context(version));
        assertThatThrownBy(() -> service().list(target)).hasMessageContaining("audience evidence is required");
        verify(httpClient, never()).send(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "refresh", "index", "reconcile", "remove", "cleanup"})
    void everyMutationRequiresACallerKeyBeforeResolvingAnyRuntime(String operation) throws Exception {
        for (String key : new String[] {null, "", " ", " key", "key ", "key\n", "x".repeat(129)}) {
            assertThatThrownBy(() -> mutate(operation, key)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Idempotency-Key is required");
        }
        org.mockito.Mockito.verifyNoInteractions(targetContextService, sourcePrerequisites, offerBoundary, runtimeAuthority, httpClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"register", "refresh", "index", "reconcile", "remove", "cleanup"})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void everyMutationPropagatesTheExactCallerKey(String operation) throws Exception {
        configureDocumentDeployment();
        doReturn(runtimeResponse(200, "{}")).when(httpClient).send(any(), any());
        mutate(operation, "stable-key-1");
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any());
        assertThat(request.getValue().headers().firstValue("Idempotency-Key")).contains("stable-key-1");
    }

    private Object mutate(String operation, String key) throws Exception {
        var service = service();
        return switch (operation) {
            case "register" -> service.register(target, objectMapper.readTree("{\"datasetId\":\"document-knowledge\",\"objectReference\":\"policy.txt\"}"), key);
            case "refresh" -> service.refresh(target, "source-1", key);
            case "index" -> service.index(target, "source-1", key);
            case "reconcile" -> service.reconcile(target, "source-1", key);
            case "remove" -> service.removeIndex(target, "source-1", key);
            case "cleanup" -> service.cleanupRetention(target, key);
            default -> throw new IllegalArgumentException(operation);
        };
    }

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
        verify(sourcePrerequisites).ensureFresh(target,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_INDEX,
            "index-1", java.util.Set.of(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC));
        var settings = ArgumentCaptor.forClass(java.util.Map.class);
        verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_INDEX),
            eq("index-1"), settings.capture(), any(), any(),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC));
        assertThat(settings.getValue().get(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Setting.DOCUMENT_AUDIENCE))
            .isEqualTo(objectMapper.getNodeFactory().textNode("PUBLIC"));
    }

    @Test
    void failedRecurringSourceProbePreventsTheRuntimeWrite() throws Exception {
        configureDocumentDeployment();
        var version = documentVersion();
        version.setMarketplaceDatasetConfigJson("{\"datasets\":[{\"datasetId\":\"policies\",\"ingestionMode\":\"EXTERNAL_DOCUMENT_STORAGE\"}]}");
        version.setKnowledgeSourceConfigJson("{\"sources\":[{\"datasetRef\":\"policies\",\"authModes\":[\"PUBLIC_RUNTIME_ANONYMOUS\"]}]}");
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(version));
        org.mockito.Mockito.doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_FAILED,
            "SOURCE_CONNECTOR_PROBE_FAILED")).when(sourcePrerequisites).ensureFresh(eq(target), any(), eq("index-1"), any());
        assertThatThrownBy(() -> service().index(target, "source-1", "index-1"))
            .hasMessageContaining("SOURCE_CONNECTOR_PROBE_FAILED");
        verify(httpClient, never()).send(any(), any());
        verify(offerBoundary).reviewedDocumentAudiences(any());
        verify(offerBoundary, never()).require(any(), any(), any(), any(), any(), any(), any(), any(), any());
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

        verify(runtimeAuthority).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1", List.of("documents:index"), Duration.ofMinutes(5));
        verify(auditService).record(eq("DOCUMENT_SOURCE_INDEX_REQUESTED"), eq("DEPLOYMENT"), eq("dep-1"), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void readOperationUsesReadOnlyScope() throws Exception {
        configureDocumentDeployment();
        doReturn(runtimeResponse(200, "{\"status\":\"READY\"}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        service().connectorStatus(target);

        verify(runtimeAuthority).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1", List.of("documents:read"), Duration.ofMinutes(5));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void retentionCleanupUsesIndexScopeAndRecordsSourcePreservation() throws Exception {
        configureDocumentDeployment();
        doReturn(runtimeResponse(200, "{\"manifestsDeleted\":2,\"customerSourceObjectsDeleted\":false}"))
            .when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var result = service().cleanupRetention(target, "cleanup-1");

        assertThat(result.statusCode()).isEqualTo(200);
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString())
            .isEqualTo("https://runtime.example/api/documents/retention/cleanup");
        assertThat(request.getValue().headers().firstValue("Idempotency-Key")).contains("cleanup-1");
        verify(runtimeAuthority).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1", List.of("documents:index"), Duration.ofMinutes(5));
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
            runtimeAuthority,
            auditService,
            objectMapper,
            httpClient,
            offerBoundary,
            sourcePrerequisites
        );
    }

    private void configureDocumentDeployment() {
        authenticate();
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", false)).thenReturn(context(documentVersion()));
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(documentVersion()));
        when(runtimeAuthority.issuePlatformProxyHeaders(any(), any(), any(), any(), any(), any()))
            .thenReturn(java.util.Map.of(
                RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, "private-runtime-key",
                RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER, "Bearer signed-assertion"));
    }

    private void configurePublicOnlyMixedDeployment() {
        configureDocumentDeployment(); authenticate(PlatformRole.CUSTOMER_ADMIN);
        var version = documentVersion();
        version.setKnowledgeSourceConfigJson("""
            {"sources":[{"datasetRef":"document-knowledge","authModes":[
              "PUBLIC_RUNTIME_ANONYMOUS","PUBLIC_RUNTIME_AUTHENTICATED","PRIVATE_RUNTIME_BACKEND_MEDIATED"]}]}
            """);
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", false)).thenReturn(context(version));
        when(targetContextService.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context(version));
        when(offerBoundary.reviewedDocumentAudiences(any())).thenReturn(java.util.Set.of(DocumentAudience.PUBLIC));
        org.mockito.Mockito.doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.SETTING_FORBIDDEN))
            .when(offerBoundary).require(any(), any(), any(), any(), any(), any(), any(), any(), eq(DocumentAudience.AUTHENTICATED));
    }

    private DeploymentTargetContextService.Context context(DeploymentVersionEntity version) {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("target-1");
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setRuntimeAuthorityFingerprint("sha256:authority-1");
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
        version.setKnowledgeSourceConfigJson("""
            {"sources":[{"datasetRef":"document-knowledge","authModes":["PUBLIC_RUNTIME_ANONYMOUS"]}]}
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
        authenticate(PlatformRole.PLATFORM_OPERATOR);
    }

    private void authenticate(PlatformRole role) {
        PlatformPrincipal principal = new PlatformPrincipal(
            "operator@example.com",
            role,
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
