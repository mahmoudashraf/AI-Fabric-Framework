package com.ai.fabric.runtime;

import com.ai.fabric.runtime.admin.RuntimeConnectorAdminProxyService;
import com.ai.fabric.runtime.web.admin.RuntimeConnectorAdminProxyController;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeConnectorAdminProxyControllerTest {

    @Test
    void overviewRequiresAdminApiKey() {
        RuntimeConnectorAdminProxyService proxyService = mock(RuntimeConnectorAdminProxyService.class);
        RuntimeConnectorAdminProxyController controller = new RuntimeConnectorAdminProxyController(proxyService);
        ReflectionTestUtils.setField(controller, "adminApiKey", "admin-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        ResponseEntity<String> response = controller.overview(new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(proxyService);
    }

    @Test
    void overviewReturnsConnectorProxyResponseForAuthorizedAdmin() {
        RuntimeConnectorAdminProxyService proxyService = mock(RuntimeConnectorAdminProxyService.class);
        when(proxyService.forwardGet("/api/admin/overview"))
            .thenReturn(proxyResponse(
                200,
                "{\"success\":true,\"surface\":\"connector-overview\"}",
                "application/json"
            ));

        RuntimeConnectorAdminProxyController controller = new RuntimeConnectorAdminProxyController(proxyService);
        ReflectionTestUtils.setField(controller, "adminApiKey", "admin-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ADMIN-API-KEY", "admin-secret");

        ResponseEntity<String> response = controller.overview(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("connector-overview");
    }

    @Test
    void configReturnsConnectorOverviewForAuthorizedAdmin() {
        RuntimeConnectorAdminProxyService proxyService = mock(RuntimeConnectorAdminProxyService.class);
        when(proxyService.forwardGet("/api/admin/overview"))
            .thenReturn(proxyResponse(
                200,
                "{\"success\":true,\"surface\":\"connector-config\"}",
                "application/json"
            ));

        RuntimeConnectorAdminProxyController controller = new RuntimeConnectorAdminProxyController(proxyService);
        ReflectionTestUtils.setField(controller, "adminApiKey", "admin-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ADMIN-API-KEY", "admin-secret");

        ResponseEntity<String> response = controller.config(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("connector-config");
        verify(proxyService).forwardGet("/api/admin/overview");
    }

    @Test
    void logsReturnsConnectorLogfileForAuthorizedAdmin() {
        RuntimeConnectorAdminProxyService proxyService = mock(RuntimeConnectorAdminProxyService.class);
        when(proxyService.forwardGet("/actuator/logfile"))
            .thenReturn(proxyResponse(
                200,
                "connector log line",
                "text/plain"
            ));

        RuntimeConnectorAdminProxyController controller = new RuntimeConnectorAdminProxyController(proxyService);
        ReflectionTestUtils.setField(controller, "adminApiKey", "admin-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ADMIN-API-KEY", "admin-secret");

        ResponseEntity<String> response = controller.logs(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("connector log line");
        verify(proxyService).forwardGet("/actuator/logfile");
    }

    @Test
    void proxyAllowsConnectorAdminAndActuatorReadsForAuthorizedAdmin() {
        RuntimeConnectorAdminProxyService proxyService = mock(RuntimeConnectorAdminProxyService.class);
        when(proxyService.forwardGet("/api/admin/actions/example?include=details"))
            .thenReturn(proxyResponse(
                200,
                "{\"success\":true,\"surface\":\"connector-proxy\"}",
                "application/json"
            ));

        RuntimeConnectorAdminProxyController controller = new RuntimeConnectorAdminProxyController(proxyService);
        ReflectionTestUtils.setField(controller, "adminApiKey", "admin-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ADMIN-API-KEY", "admin-secret");

        LinkedMultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("include", "details");

        ResponseEntity<String> response = controller.proxy("/api/admin/actions/example", queryParams, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("connector-proxy");
        verify(proxyService).forwardGet("/api/admin/actions/example?include=details");
    }

    @Test
    void proxyRejectsUnsupportedPaths() {
        RuntimeConnectorAdminProxyService proxyService = mock(RuntimeConnectorAdminProxyService.class);
        RuntimeConnectorAdminProxyController controller = new RuntimeConnectorAdminProxyController(proxyService);
        ReflectionTestUtils.setField(controller, "adminApiKey", "admin-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-ADMIN-API-KEY", "admin-secret");

        ResponseEntity<String> response = controller.proxy("/actions/execute", new LinkedMultiValueMap<>(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("proxyPath must target /api/admin/** or /actuator/**");
        verifyNoInteractions(proxyService);
    }

    private RuntimeConnectorAdminProxyService.ProxyResponse proxyResponse(int status, String body, String contentType) {
        try {
            Class<?> proxyResponseClass = Class.forName("com.ai.fabric.runtime.admin.RuntimeConnectorAdminProxyService$ProxyResponse");
            Object instance = proxyResponseClass.getDeclaredConstructor(int.class, String.class, String.class)
                .newInstance(status, body, contentType);
            return (RuntimeConnectorAdminProxyService.ProxyResponse) instance;
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
