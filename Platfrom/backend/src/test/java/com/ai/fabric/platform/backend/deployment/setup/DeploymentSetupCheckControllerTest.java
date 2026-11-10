package com.ai.fabric.platform.backend.deployment.setup;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static com.ai.fabric.platform.backend.deployment.setup.SetupCheckContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeploymentSetupCheckControllerTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    @Test void methodSecurityAdmitsConsoleRolesAndRejectsMachineRoles() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(DeploymentSetupCheckService.class);
            context.register(Security.class); context.registerBean(DeploymentSetupCheckService.class, () -> service);
            context.register(DeploymentSetupCheckController.class); context.refresh();
            var controller = context.getBean(DeploymentSetupCheckController.class);
            for (String role : List.of("CUSTOMER_ADMIN", "PLATFORM_ADMIN", "PLATFORM_OPERATOR")) {
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "unused", "ROLE_" + role));
                controller.get("dep-one"); controller.run("dep-one", new Request(Mode.CHANGED));
            }
            verify(service, times(3)).inspect("dep-one"); verify(service, times(3)).run("dep-one", Mode.CHANGED);
            for (String role : List.of("PUBLIC_API_CLIENT", "PLATFORM_PRODUCT_SERVICE", "PARTNER_ADMIN")) {
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "unused", "ROLE_" + role));
                assertThatThrownBy(() -> controller.get("dep-one")).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(() -> controller.run("dep-one", null)).isInstanceOf(AccessDeniedException.class);
            }
            verifyNoMoreInteractions(service);
        }
    }
    @Test void inspectIsReadOnlyRunModesAreExplicitAndInvalidModeIsRejected() throws Exception {
        var service = mock(DeploymentSetupCheckService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DeploymentSetupCheckController(service)).build();
        mvc.perform(get("/api/deployments/dep-one/setup-checks")).andExpect(status().isOk());
        verify(service).inspect("dep-one"); verify(service, never()).run(any(), any());
        mvc.perform(post("/api/deployments/dep-one/setup-checks").contentType("application/json").content("{\"mode\":\"FAILED\"}"))
                .andExpect(status().isOk());
        verify(service).run("dep-one", Mode.FAILED);
        mvc.perform(post("/api/deployments/dep-one/setup-checks").contentType("application/json").content("{\"mode\":\"EXECUTE_ACTIONS\"}"))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }
    @Configuration @EnableMethodSecurity static class Security { }
}
