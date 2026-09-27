package com.ai.fabric.runtime.web;

import ai.fabric.datasync.dto.DataSyncBatchRequest;
import ai.fabric.datasync.dto.DataSyncBatchResponse;
import ai.fabric.datasync.dto.DataSyncOperation;
import ai.fabric.datasync.dto.DataSyncOperationType;
import ai.fabric.datasync.dto.DataSyncTrace;
import ai.fabric.datasync.dto.DataSyncVerifiedAuthContext;
import ai.fabric.datasync.service.DataSyncService;
import ai.fabric.indexing.api.AIIndexWorkType;
import ai.fabric.indexing.api.AIProcessOperation;
import ai.fabric.indexing.api.IndexingStrategy;
import ai.fabric.indexing.api.IndexingWorkQuery;
import ai.fabric.indexing.api.IndexingWorkState;
import ai.fabric.indexing.api.IndexingWorkStatus;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeCustomerIngestionControllerTest {

    @Test
    void replacesCallerSuppliedIdentityWithScopedServerIdentityAndKeepsVerifiedProvenance() {
        Fixture fixture = fixture(identity("dep-neutral", "tenant-neutral", List.of("data-sync:upsert")));
        DataSyncBatchResponse serviceResponse = new DataSyncBatchResponse();
        serviceResponse.setSuccess(true);
        when(fixture.dataSyncService.batch(any())).thenReturn(serviceResponse);
        DataSyncVerifiedAuthContext attacker = new DataSyncVerifiedAuthContext();
        attacker.setDeploymentId("dep-attacker");
        attacker.setTenantId("tenant-attacker");
        DataSyncTrace trace = new DataSyncTrace("customer-request-1", Map.of("unsafe", "ignored"));
        trace.setAuthContext(attacker);
        DataSyncBatchRequest request = new DataSyncBatchRequest(
            trace,
            List.of(operation(DataSyncOperationType.UPSERT, "inventory-item"))
        );

        var response = fixture.controller.batch(request, fixture.servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(fixture.authResolver).requireScope(
            fixture.identity,
            "data-sync:upsert",
            "customer ingestion upsert"
        );
        ArgumentCaptor<DataSyncBatchRequest> captor = ArgumentCaptor.forClass(DataSyncBatchRequest.class);
        verify(fixture.dataSyncService).batch(captor.capture());
        DataSyncTrace verified = captor.getValue().getTrace();
        assertThat(verified.getRequestId()).isEqualTo("customer-request-1");
        assertThat(verified.getMetadata())
            .containsEntry("source", "customer-backend-ingestion")
            .containsEntry("verifiedCallerSubjectType", "TRUSTED_BACKEND")
            .containsEntry("verifiedCallerIssuer", "customer-backend");
        assertThat(verified.getAuthContext().getSubjectId()).isEqualTo("system:platform-customer-ingestion");
        assertThat(verified.getAuthContext().getSubjectType()).isEqualTo("SYSTEM_PROCESS");
        assertThat(verified.getAuthContext().getCallerType()).isEqualTo("SYSTEM_PROCESS");
        assertThat(verified.getAuthContext().getIssuer()).isEqualTo("platform-customer-ingestion");
        assertThat(verified.getAuthContext().getDeploymentId()).isEqualTo("dep-neutral");
        assertThat(verified.getAuthContext().getTenantId()).isEqualTo("tenant-neutral");
        assertThat(verified.getAuthContext().getGrantedScopes()).containsExactly("data-sync:upsert");
    }

    @Test
    void rejectsPrivateIdentityForAnotherDeployment() {
        Fixture fixture = fixture(identity("dep-other", "tenant-neutral", List.of("data-sync:upsert")));
        DataSyncBatchRequest request = new DataSyncBatchRequest(
            new DataSyncTrace(),
            List.of(operation(DataSyncOperationType.UPSERT, "inventory-item"))
        );

        assertThatThrownBy(() -> fixture.controller.batch(request, fixture.servletRequest))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void rejectsOperationForEntityTypeNotGrantedByPackage() {
        Fixture fixture = fixture(identity("dep-neutral", "tenant-neutral", List.of("data-sync:delete")));
        DataSyncBatchRequest request = new DataSyncBatchRequest(
            new DataSyncTrace(),
            List.of(operation(DataSyncOperationType.DELETE, "customer-profile"))
        );

        assertThatThrownBy(() -> fixture.controller.batch(request, fixture.servletRequest))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
    }

    @Test
    void readinessUsesPrivateIndexScopeAndReturnsOnlySafeConfiguration() {
        Fixture fixture = fixture(identity("dep-neutral", "tenant-neutral", List.of("runtime:index:overview")));

        var response = fixture.controller.readiness(fixture.servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(fixture.authResolver).requireScope(
            fixture.identity,
            "runtime:index:overview",
            "customer ingestion readiness"
        );
        assertThat(response.getBody()).isInstanceOfSatisfying(Map.class, body -> {
            assertThat(body).containsEntry("status", "READY");
            assertThat(body).containsEntry("deploymentId", "dep-neutral");
            assertThat(body).containsEntry("tenantId", "tenant-neutral");
        });
    }

    @Test
    void hidesIndexingWorkForEntityTypeOutsideImmutableIngestionContract() {
        Fixture fixture = fixture(identity("dep-neutral", "tenant-neutral", List.of("runtime:index:overview")));
        when(fixture.workQuery.findByWorkId("work-other")).thenReturn(Optional.of(new IndexingWorkStatus(
            "work-other",
            "document-chunk",
            "document-1",
            AIIndexWorkType.UPSERT,
            AIProcessOperation.UPDATE,
            IndexingStrategy.ASYNC,
            IndexingWorkState.COMPLETED,
            0,
            3,
            null,
            null,
            "document-sync",
            null,
            null,
            null,
            null,
            null,
            LocalDateTime.parse("2026-09-27T12:00:00")
        )));

        var response = fixture.controller.work("work-other", fixture.servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOfSatisfying(Map.class, body ->
            assertThat(body).containsEntry("errorCode", "INDEXING_WORK_NOT_FOUND")
        );
        verify(fixture.authResolver).requireScope(
            fixture.identity,
            "runtime:index:overview",
            "customer indexing-work status"
        );
    }

    private Fixture fixture(RuntimeResolvedIdentity identity) {
        RuntimeRequestAuthResolver authResolver = mock(RuntimeRequestAuthResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(authResolver.resolveVerifiedPrivateContext(request, "customer ingestion batch")).thenReturn(identity);
        when(authResolver.resolveVerifiedPrivateContext(request, "customer ingestion readiness")).thenReturn(identity);
        when(authResolver.resolveVerifiedPrivateContext(request, "customer indexing-work status")).thenReturn(identity);
        DataSyncService dataSyncService = mock(DataSyncService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSyncService> dataSyncProvider = mock(ObjectProvider.class);
        when(dataSyncProvider.getIfAvailable()).thenReturn(dataSyncService);
        @SuppressWarnings("unchecked")
        ObjectProvider<IndexingWorkQuery> workQueryProvider = mock(ObjectProvider.class);
        IndexingWorkQuery workQuery = mock(IndexingWorkQuery.class);
        when(workQueryProvider.getIfAvailable()).thenReturn(workQuery);
        RuntimeCustomerIngestionController controller = new RuntimeCustomerIngestionController(
            authResolver,
            properties(),
            dataSyncProvider,
            workQueryProvider
        );
        return new Fixture(controller, authResolver, dataSyncService, workQuery, request, identity);
    }

    private RuntimeAuthProperties properties() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        RuntimeAuthProperties.CustomerIngestion config = properties.getIngress().getCustomerIngestion();
        config.setEnabled(true);
        config.setDeploymentId("dep-neutral");
        config.setTenantId("tenant-neutral");
        config.setAllowedUpsertEntityTypes(List.of("inventory-item"));
        config.setAllowedDeleteEntityTypes(List.of("inventory-item"));
        config.setAllowedWorkStatusEntityTypes(List.of("inventory-item"));
        config.setWorkStatusEnabled(true);
        config.setReadinessEnabled(true);
        return properties;
    }

    private RuntimeResolvedIdentity identity(String deploymentId, String tenantId, List<String> scopes) {
        return RuntimeResolvedIdentity.builder()
            .authContext(RuntimeAuthContext.builder()
                .subjectId("backend-user")
                .subjectType(RuntimeAuthSubjectType.TRUSTED_BACKEND)
                .authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
                .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND)
                .deploymentId(deploymentId)
                .customerId("customer-neutral")
                .tenantId(tenantId)
                .issuer("customer-backend")
                .grantedScopes(scopes)
                .build())
            .warnings(List.of())
            .build();
    }

    private DataSyncOperation operation(DataSyncOperationType type, String entityType) {
        return new DataSyncOperation(type, entityType, "item-1", "content", Map.of(), Map.of(), null);
    }

    private record Fixture(
        RuntimeCustomerIngestionController controller,
        RuntimeRequestAuthResolver authResolver,
        DataSyncService dataSyncService,
        IndexingWorkQuery workQuery,
        HttpServletRequest servletRequest,
        RuntimeResolvedIdentity identity
    ) {
    }
}
