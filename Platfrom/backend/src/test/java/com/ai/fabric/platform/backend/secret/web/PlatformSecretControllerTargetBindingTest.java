package com.ai.fabric.platform.backend.secret.web;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.secret.model.UpsertManagedPlatformSecretRequest;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretOverrideService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformSecretControllerTargetBindingTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test
    void managedSecretApiForwardsExplicitOwnerAndTargetToExistingStorageService() throws Exception {
        var secrets = mock(PlatformSecretService.class);
        var controller = new PlatformSecretController(secrets, mock(DeploymentProviderSecretOverrideService.class), mock(PlatformAuditService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(put("/api/platform/secrets/managed/MANAGED_SUPPORT_DIAGNOSTICS_STAGE").contentType("application/json")
            .content("{\"value\":\"test-only-key\",\"deploymentId\":\"dep-support\",\"targetProfileId\":\"support-stage\"}"))
            .andExpect(status().isOk());
        verify(secrets).upsertTargetManagedSecret("MANAGED_SUPPORT_DIAGNOSTICS_STAGE", "test-only-key", "dep-support", "support-stage",
            Map.of("source", "PLATFORM_SECRET_API"));
        verifyNoMoreInteractions(secrets);
    }

    @Test
    void customerCannotChooseOrRewriteManagedSecretOwnership() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var secrets = mock(PlatformSecretService.class);
            context.register(MethodSecurity.class);
            context.registerBean(PlatformSecretService.class, () -> secrets);
            context.registerBean(DeploymentProviderSecretOverrideService.class, () -> mock(DeploymentProviderSecretOverrideService.class));
            context.registerBean(PlatformAuditService.class, () -> mock(PlatformAuditService.class));
            context.register(PlatformSecretController.class);
            context.refresh();
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("customer", "unused", "ROLE_CUSTOMER_ADMIN"));
            assertThatThrownBy(() -> context.getBean(PlatformSecretController.class).upsertManagedSecret("MANAGED_SUPPORT_DIAGNOSTICS_STAGE",
                new UpsertManagedPlatformSecretRequest("test-only-key", "dep-support", "support-stage")))
                .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(secrets);
        }
    }

    @Configuration @EnableMethodSecurity static class MethodSecurity { }
}
