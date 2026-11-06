package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.security.service.CustomerStaffGrantService;
import com.ai.fabric.platform.backend.security.web.CustomerStaffAccessController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerStaffAccessControllerTest {
    private AnnotationConfigApplicationContext context;
    private CustomerStaffGrantService service;
    private CustomerStaffAccessController controller;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(CustomerStaffGrantService.class); controller = context.getBean(CustomerStaffAccessController.class);
        mvc = MockMvcBuilders.standaloneSetup(controller).build(); authenticate(PlatformRole.CUSTOMER_ADMIN);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test void listGrantAndRevokeUseOnlyTheAuthenticatedWorkspaceAndReturnTheBoundedSummary() throws Exception {
        var summary = new CustomerStaffGrantService.Summary("grant-a", "customer-a", "operator", "staff@example.test", "Staff", "ACTIVE", Instant.now(), null);
        when(service.list()).thenReturn(List.of(summary)); when(service.grant("staff@example.test")).thenReturn(summary);
        when(service.revoke("grant-a")).thenReturn(new CustomerStaffGrantService.Summary(summary.id(), summary.customerId(), summary.userId(),
            summary.email(), summary.displayName(), "REVOKED", summary.grantedAt(), Instant.now()));
        mvc.perform(get("/api/customer/staff-access")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$[0].id").value("grant-a"));
        mvc.perform(post("/api/customer/staff-access").contentType("application/json")
                .content("{\"email\":\"staff@example.test\",\"customerId\":\"foreign\",\"userId\":\"global-admin\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.customerId").value("customer-a")).andExpect(jsonPath("$.userId").value("operator"))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.grantedBy").doesNotExist());
        mvc.perform(delete("/api/customer/staff-access/grant-a")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REVOKED")).andExpect(jsonPath("$.revokedAt").isNotEmpty());
        verify(service).list(); verify(service).grant("staff@example.test"); verify(service).revoke("grant-a");
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"email\":\"\"}", "{\"email\":\"not-email\"}"})
    void invalidEmailContractDoesNotReachService(String body) throws Exception {
        mvc.perform(post("/api/customer/staff-access").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"application/x-www-form-urlencoded", "multipart/form-data", "text/plain"})
    void crossSiteSimpleContentTypesCannotGrantEvenWhenTheBodyContainsValidJson(String contentType) throws Exception {
        mvc.perform(post("/api/customer/staff-access").header("Origin", "https://untrusted.example.test")
                .contentType(contentType).content("{\"email\":\"staff@example.test\"}"))
            .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @ParameterizedTest @EnumSource(value = PlatformRole.class, names = "CUSTOMER_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void globalAdminsOperatorsAndAllOtherRolesCannotUseCustomerGrantEndpoints(PlatformRole role) {
        authenticate(role);
        for (Runnable call : List.<Runnable>of(controller::list,
            () -> controller.grant(new CustomerStaffAccessController.Request("staff@example.test")), () -> controller.revoke("grant-a"))) {
            assertThatThrownBy(call::run).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(service);
    }

    @Test void anonymousCannotUseAnyGrantEndpoint() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(controller::list).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> controller.grant(new CustomerStaffAccessController.Request("staff@example.test")))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> controller.revoke("grant-a")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(service);
    }

    private void authenticate(PlatformRole role) {
        var principal = new PlatformPrincipal("owner@example.test", role, "Owner", "SESSION", "owner");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(role.authority()))));
    }
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean CustomerStaffGrantService service() { return mock(CustomerStaffGrantService.class); }
        @Bean CustomerStaffAccessController controller(CustomerStaffGrantService service) { return new CustomerStaffAccessController(service); }
    }
}
