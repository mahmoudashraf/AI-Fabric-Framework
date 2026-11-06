package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.security.service.StaffCustomerAccessService;
import com.ai.fabric.platform.backend.security.web.StaffCustomerAccessController;
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

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StaffCustomerAccessControllerTest {
    private AnnotationConfigApplicationContext context;
    private StaffCustomerAccessService service;
    private StaffCustomerAccessController controller;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(StaffCustomerAccessService.class); controller = context.getBean(StaffCustomerAccessController.class);
        mvc = MockMvcBuilders.standaloneSetup(controller).build(); authenticate(PlatformRole.PLATFORM_ADMIN);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test void selectedPathCustomerAndOnlyRecipientFieldsReachTheService() throws Exception {
        when(service.prepare(eq("customer-a"), any())).thenReturn(new StaffCustomerAccessService.PreparedAccess(
            "user-a", "recipient@example.test", "Recipient", "customer-a", false, true));
        mvc.perform(post("/api/staff/customers/customer-a/access").contentType("application/json")
            .content("{\"email\":\"recipient@example.test\",\"displayName\":\"Recipient\",\"customerId\":\"other\",\"role\":\"PLATFORM_ADMIN\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.userId").value("user-a")).andExpect(jsonPath("$.customerId").value("customer-a"))
            .andExpect(jsonPath("$.identityLinked").value(false)).andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
        verify(service).prepare(eq("customer-a"), eq(new StaffCustomerAccessService.Request("recipient@example.test", "Recipient")));
    }

    @ParameterizedTest @ValueSource(strings = {
        "{\"email\":\"not-email\",\"displayName\":\"Recipient\"}",
        "{\"email\":\"\",\"displayName\":\"Recipient\"}",
        "{\"email\":\"recipient@example.test\",\"displayName\":\"\"}",
        "{\"email\":\"recipient@example.test\",\"displayName\":\"R\"}"
    })
    void malformedRecipientNeverReachesService(String body) throws Exception {
        mvc.perform(post("/api/staff/customers/customer-a/access").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }

    @ParameterizedTest @EnumSource(value = PlatformRole.class, names = "PLATFORM_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void methodSecurityDeniesAllOtherRoles(PlatformRole role) {
        authenticate(role);
        assertThatThrownBy(() -> controller.prepare("customer-a", new StaffCustomerAccessService.Request("recipient@example.test", "Recipient")))
            .isInstanceOf(AccessDeniedException.class); verifyNoInteractions(service);
    }
    @Test void methodSecurityDeniesAnonymous() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> controller.prepare("customer-a", new StaffCustomerAccessService.Request("recipient@example.test", "Recipient")))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class); verifyNoInteractions(service);
    }
    @Test void concurrentUniquenessFailureIsAConflictWithoutDatabaseDiagnostics() throws Exception {
        when(service.prepare(anyString(), any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("private-db-detail"));
        mvc.perform(post("/api/staff/customers/customer-a/access").contentType("application/json")
            .content("{\"email\":\"recipient@example.test\",\"displayName\":\"Recipient\"}"))
            .andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-db-detail"))));
    }
    private void authenticate(PlatformRole role) {
        var principal = new PlatformPrincipal("staff@example.test", role, "Staff", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(role.authority()))));
    }
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean StaffCustomerAccessService service() { return mock(StaffCustomerAccessService.class); }
        @Bean StaffCustomerAccessController controller(StaffCustomerAccessService service) { return new StaffCustomerAccessController(service); }
    }
}
