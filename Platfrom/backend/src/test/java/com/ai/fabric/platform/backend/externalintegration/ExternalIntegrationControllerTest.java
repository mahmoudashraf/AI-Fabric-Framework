package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExternalIntegrationControllerTest {
    final ObjectMapper mapper = new ObjectMapper();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    ObjectNode validBody() {
        return mapper.createObjectNode().put("versionId", "version").put("targetProfileId", "target")
            .put("bindingSlotId", "slot").put("selectedProfileId", "profile").put("approvalId", "approval")
            .put("expectedEntitlementRevision", 0).put("operationKey", "operation")
            .set("credentials", mapper.createObjectNode().put("apiKey", "synthetic-credential-sentinel"));
    }

    @Test void cancellationOnlyJacksonFixIsNotGlobalAndExternalRequestsRequireExplicitTypedRevisionAndCredentials() throws Exception {
        assertThat(mapper.treeToValue(validBody(), BindRequest.class).expectedEntitlementRevision()).isZero();
        for (String field : new String[]{"expectedEntitlementRevision", "credentials", "approvalId"}) {
            for (boolean missing : new boolean[]{true, false}) {
                ObjectNode body = validBody(); if (missing) body.remove(field); else body.putNull(field);
                assertThatThrownBy(() -> mapper.treeToValue(body, BindRequest.class));
            }
        }
        for (String invalid : new String[]{"true", "\"0\"", "0.1", "9223372036854775808", "-1", "{}", "[]"}) {
            ObjectNode body = validBody(); body.set("expectedEntitlementRevision", mapper.readTree(invalid));
            assertThatThrownBy(() -> mapper.treeToValue(body, BindRequest.class));
        }
        for (String invalid : new String[]{"true", "1", "null", "[]", "{}"}) {
            ObjectNode body = validBody(); ((ObjectNode) body.path("credentials")).set("apiKey", mapper.readTree(invalid));
            assertThatThrownBy(() -> mapper.treeToValue(body, BindRequest.class));
        }
        ObjectNode injected = validBody(); injected.put("endpointUrl", "https://evil.example.com");
        assertThatThrownBy(() -> mapper.treeToValue(injected, BindRequest.class));
        ObjectNode wrong = validBody(); wrong.put("secretRef", "ZILLIZ_CLOUD_API_KEY");
        assertThatThrownBy(() -> mapper.treeToValue(wrong, BindRequest.class));
    }

    @Test void malformedHttpReturnsConstantSanitized400WithoutErrorDispatchAndValidRequestIsForwarded() throws Exception {
        var service = mock(ExternalIntegrationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ExternalIntegrationController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        for (String body : new String[]{"{\"credentials\":\"synthetic-credential-sentinel\",", validBody().put("expectedEntitlementRevision", "bad").toString()}) {
            var result = mvc.perform(post("/api/customer-self-service/catalogue/offer/deployments/dep/external-integrations/bind")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTERNAL_INTEGRATION_INVALID_JSON")).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic-credential-sentinel", "Json", "Exception", "stack", "credentials", "bad");
            assertThat(result.getResponse().getForwardedUrl()).isNull();
        }
        when(service.bind(eq("offer"), eq("dep"), any())).thenReturn(new Summary("binding", "approval", "dep", "version", "target", "slot", "profile", "CONFIGURED_LOCAL_CREDENTIALS"));
        mvc.perform(post("/api/customer-self-service/catalogue/offer/deployments/dep/external-integrations/bind")
            .contentType(MediaType.APPLICATION_JSON).content(validBody().toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("binding"));
        verify(service, times(1)).bind(eq("offer"), eq("dep"), any());
    }

    @Test void realMethodSecurityRequiresCustomerForBindingAndStaffAdminForReview() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(SecurityConfig.class)) {
            var controller = context.getBean(ExternalIntegrationController.class);
            BindRequest bind = mapper.treeToValue(validBody(), BindRequest.class);
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> controller.bind("offer", "dep", bind)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            ExternalIntegrationFixture.authenticate(PlatformRole.PLATFORM_ADMIN, "staff");
            assertThatThrownBy(() -> controller.bind("offer", "dep", bind)).isInstanceOf(AccessDeniedException.class);
            ExternalIntegrationFixture.authenticate(PlatformRole.CUSTOMER_ADMIN, "customer");
            assertThatCode(() -> controller.bind("offer", "dep", bind)).doesNotThrowAnyException();
            assertThatThrownBy(() -> controller.approve("offer", "dep", null)).isInstanceOf(AccessDeniedException.class);
        }
    }
    @Configuration @EnableMethodSecurity static class SecurityConfig {
        @Bean ExternalIntegrationService service() { return mock(ExternalIntegrationService.class); }
        @Bean ExternalIntegrationController controller(ExternalIntegrationService service) { return new ExternalIntegrationController(service); }
    }
}
