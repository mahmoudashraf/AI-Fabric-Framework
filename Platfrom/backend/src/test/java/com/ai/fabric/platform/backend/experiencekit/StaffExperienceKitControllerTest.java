package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.config.CustomerOnboardingProperties;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffExperienceKitControllerTest {
    private AnnotationConfigApplicationContext context;
    private StaffExperienceKitController controller;
    private ExperienceKitService kits;
    private CustomerOfferEntitlementService access;
    private MockMvc mvc;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(StaffExperienceKitController.class);
        kits = context.getBean(ExperienceKitService.class);
        access = context.getBean(CustomerOfferEntitlementService.class);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        authenticate(PlatformRole.PLATFORM_ADMIN);
    }

    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }

    @Test void selectedPathCustomerScopesCatalogueListAndSetupPreparation() throws Exception {
        mvc.perform(get("/api/staff/customers/customer-a/experience-kits/catalogue")).andExpect(status().isOk());
        mvc.perform(get("/api/staff/customers/customer-a/experience-kits")).andExpect(status().isOk());
        mvc.perform(post("/api/staff/customers/customer-a/experience-kits/prepare").contentType("application/json"))
            .andExpect(status().isOk());
        verify(kits).customerCatalogueForStaff("customer-a");
        verify(kits).listForStaff("customer-a");
        verify(access).enrollPublicTemplatesForCustomer("customer-a", List.of("public-kit-offer"));
    }

    @Test void creationUsesPathCustomerAndExistingCreateContract() throws Exception {
        mvc.perform(post("/api/staff/customers/customer-a/experience-kits").contentType("application/json")
            .content("""
                {"kitCode":"dealership-ai-experience","name":"Customer kit","offerId":"offer",
                 "targetProfileId":"stage","workspaceProfileId":"dealer","workspaceName":"Customer AI",
                 "brandColor":"#2563eb","deliveryMode":"both","appSlug":"customer-ai",
                 "websiteOrigin":"https://dealer.example.test","operationKey":"create-1"}
                """)).andExpect(status().isOk());
        verify(kits).createForStaff(eq("customer-a"), argThat(request -> request.name().equals("Customer kit")
            && request.deliveryMode().equals("both") && request.operationKey().equals("create-1")));
        verify(kits, never()).create(any());
    }

    @Test void operatorsUseCustomerCheckedStaffServices() {
        authenticate(PlatformRole.PLATFORM_OPERATOR);
        controller.list("customer-a");
        controller.prepare("customer-a");
        verify(kits).listForStaff("customer-a");
        verify(access).enrollPublicTemplatesForCustomer("customer-a", List.of("public-kit-offer"));
    }

    @ParameterizedTest @EnumSource(value=PlatformRole.class, names={"PLATFORM_ADMIN","PLATFORM_OPERATOR"}, mode=EnumSource.Mode.EXCLUDE)
    void allOtherRolesCannotUseStaffKitRoutes(PlatformRole role) {
        authenticate(role);
        assertThatThrownBy(() -> controller.list("customer-a")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.prepare("customer-a")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(kits, access);
    }

    @Test void anonymousCannotUseStaffKitRoutes() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> controller.list("customer-a")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(kits, access);
    }

    @Test void setupPreparationRejectsCrossSiteFormPosts() throws Exception {
        mvc.perform(post("/api/staff/customers/customer-a/experience-kits/prepare")
            .contentType("application/x-www-form-urlencoded")).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(kits, access);
    }

    private void authenticate(PlatformRole role) {
        var principal = new PlatformPrincipal("actor@example.test", role, "Actor", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean ExperienceKitService kits() { return mock(ExperienceKitService.class); }
        @Bean CustomerOfferEntitlementService access() { return mock(CustomerOfferEntitlementService.class); }
        @Bean StaffExperienceKitController controller(ExperienceKitService kits, CustomerOfferEntitlementService access) {
            return new StaffExperienceKitController(kits, access, new CustomerOnboardingProperties(true, List.of("public-kit-offer")));
        }
    }
}
