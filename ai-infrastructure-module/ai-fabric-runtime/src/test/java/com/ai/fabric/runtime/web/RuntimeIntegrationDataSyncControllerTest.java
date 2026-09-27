package com.ai.fabric.runtime.web;

import ai.fabric.datasync.dto.DataSyncBatchRequest;
import ai.fabric.datasync.dto.DataSyncBatchResponse;
import ai.fabric.datasync.dto.DataSyncTrace;
import ai.fabric.datasync.dto.DataSyncVerifiedAuthContext;
import ai.fabric.datasync.service.DataSyncService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeIntegrationDataSyncControllerTest {

    @Test
    void authenticatesConnectorAndReplacesCallerSuppliedIdentityWithDeploymentIdentity() {
        RuntimeRequestAuthResolver authResolver = mock(RuntimeRequestAuthResolver.class);
        RuntimeAuthProperties properties = properties();
        DataSyncService dataSyncService = mock(DataSyncService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSyncService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dataSyncService);
        DataSyncBatchResponse serviceResponse = new DataSyncBatchResponse();
        serviceResponse.setSuccess(true);
        when(dataSyncService.batch(org.mockito.ArgumentMatchers.any())).thenReturn(serviceResponse);
        RuntimeIntegrationDataSyncController controller = new RuntimeIntegrationDataSyncController(
            authResolver,
            properties,
            provider
        );
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        DataSyncTrace suppliedTrace = new DataSyncTrace("connector-sync-1", Map.of("untrusted", "ignored"));
        DataSyncVerifiedAuthContext suppliedAuth = new DataSyncVerifiedAuthContext();
        suppliedAuth.setDeploymentId("dep-attacker");
        suppliedAuth.setTenantId("tenant-attacker");
        suppliedTrace.setAuthContext(suppliedAuth);
        DataSyncBatchRequest request = new DataSyncBatchRequest(suppliedTrace, List.of());

        var response = controller.batch(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authResolver).requireIntegrationServiceIngress(servletRequest, "integration data-sync batch");
        ArgumentCaptor<DataSyncBatchRequest> requestCaptor = ArgumentCaptor.forClass(DataSyncBatchRequest.class);
        verify(dataSyncService).batch(requestCaptor.capture());
        DataSyncTrace verifiedTrace = requestCaptor.getValue().getTrace();
        assertThat(verifiedTrace.getRequestId()).isEqualTo("connector-sync-1");
        assertThat(verifiedTrace.getMetadata()).containsExactly(
            Map.entry("source", "deployment-integration-connector")
        );
        assertThat(verifiedTrace.getAuthContext().getSubjectId())
            .isEqualTo("system:platform-deployment-integration");
        assertThat(verifiedTrace.getAuthContext().getAuthMode())
            .isEqualTo("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        assertThat(verifiedTrace.getAuthContext().getDeploymentId()).isEqualTo("dep-neutral");
        assertThat(verifiedTrace.getAuthContext().getTenantId()).isEqualTo("tenant-neutral");
        assertThat(verifiedTrace.getAuthContext().getGrantedScopes())
            .containsExactly("data-sync:upsert", "data-sync:delete");
    }

    @Test
    void reportsUnavailableWhenDataSyncServiceIsAbsent() {
        RuntimeRequestAuthResolver authResolver = mock(RuntimeRequestAuthResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSyncService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        RuntimeIntegrationDataSyncController controller = new RuntimeIntegrationDataSyncController(
            authResolver,
            properties(),
            provider
        );

        var response = controller.batch(new DataSyncBatchRequest(), mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private RuntimeAuthProperties properties() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        RuntimeAuthProperties.IntegrationService integration = properties.getIngress().getIntegrationService();
        integration.setEnabled(true);
        integration.setApiKeyValue("fixture-key");
        integration.setDeploymentId("dep-neutral");
        integration.setTenantId("tenant-neutral");
        return properties;
    }
}
