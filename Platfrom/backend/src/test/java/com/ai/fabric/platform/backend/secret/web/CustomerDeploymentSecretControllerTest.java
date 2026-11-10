package com.ai.fabric.platform.backend.secret.web;

import com.ai.fabric.platform.backend.secret.model.DeploymentSecretContracts;
import com.ai.fabric.platform.backend.secret.service.CustomerDeploymentSecretService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerDeploymentSecretControllerTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void writeApiReturnsOnlySafeMetadataAndDoesNotAcceptOwnershipCoordinates() throws Exception {
        var service = mock(CustomerDeploymentSecretService.class);
        var request = new DeploymentSecretContracts.Create("MY_API", "INTEGRATION_CREDENTIAL", "test-only-private", null);
        when(service.create("dep-own", request)).thenReturn(List.of(new DeploymentSecretContracts.Secret("SCOPED_REFERENCE", "MY_API", "INTEGRATION_CREDENTIAL", true, 0, null)));
        var mvc = MockMvcBuilders.standaloneSetup(new CustomerDeploymentSecretController(service)).build();
        mvc.perform(post("/api/deployments/dep-own/secrets").contentType("application/json").content("""
            {"name":"MY_API","purpose":"INTEGRATION_CREDENTIAL","value":"test-only-private",
             "deploymentId":"dep-foreign","targetProfileId":"production","ownerType":"PLATFORM"}
            """)).andExpect(status().isCreated()).andExpect(jsonPath("$[0].name").value("SCOPED_REFERENCE"))
            .andExpect(jsonPath("$[0].value").doesNotExist()).andExpect(jsonPath("$[0].secretValue").doesNotExist());
        verify(service).create("dep-own", request);
    }

    @Test void permittedConsoleRolesReachTheScopedServiceButMachineAndPartnerRolesDoNot() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(CustomerDeploymentSecretService.class);
            context.register(MethodSecurity.class); context.registerBean(CustomerDeploymentSecretService.class, () -> service);
            context.register(CustomerDeploymentSecretController.class); context.refresh();
            var controller = context.getBean(CustomerDeploymentSecretController.class);
            for (String role : List.of("CUSTOMER_ADMIN", "PLATFORM_ADMIN", "PLATFORM_OPERATOR")) {
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "unused", "ROLE_" + role));
                controller.list("dep-own");
            }
            verify(service, times(3)).list("dep-own");
            for (String role : List.of("PUBLIC_API_CLIENT", "PLATFORM_PRODUCT_SERVICE", "PARTNER_ADMIN")) {
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "unused", "ROLE_" + role));
                assertThatThrownBy(() -> controller.list("dep-own")).isInstanceOf(AccessDeniedException.class);
            }
            verifyNoMoreInteractions(service);
        }
    }
    @Configuration @EnableMethodSecurity static class MethodSecurity { }
}
