package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.*;
import com.ai.fabric.platform.backend.customerselfservice.web.*;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(CustomerSelfServiceControllerTest.Config.class)
@WebAppConfiguration
class CustomerSelfServiceControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired CustomerOfferEntitlementService entitlements;
    @Autowired CustomerSelfServiceCatalogueService catalogue;
    @Autowired CustomerDeploymentBootstrapService bootstrap;
    @Autowired CustomerDeploymentVersionService versions;
    @Autowired CustomerDeploymentApplyService applies;
    @Autowired CustomerAdmissionReservationService reservations;
    @Autowired CustomerDeploymentPortfolioService deploymentPortfolio;
    private MockMvc mvc;

    @BeforeEach void setup() {
        reset(entitlements, catalogue, bootstrap, versions, applies, reservations, deploymentPortfolio);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void realMethodSecurityAllowsOwnerCatalogueButDeniesStaffAndPublicIdentities() throws Exception {
        mvc.perform(as(get("/api/customer-self-service/catalogue"), PlatformRole.CUSTOMER_ADMIN)).andExpect(status().isOk());
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.PUBLIC_API_CLIENT, PlatformRole.PLATFORM_ADMIN)) {
            mvc.perform(as(get("/api/customer-self-service/catalogue"), role)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/customer-self-service/catalogue")).andExpect(status().isForbidden());
    }

    @Test void customerDeploymentPortfolioIsOwnerOnlyAndUsesServerOwnedAssociations() throws Exception {
        when(deploymentPortfolio.deployments()).thenReturn(List.of(new CustomerDeploymentSummary(
            "deployment", "Northfield", "staging", "DRAFT", "tenant", "offer", "staging-target", "fingerprint", 2, null,
            java.time.Instant.parse("2026-10-16T00:00:00Z"))));
        mvc.perform(as(get("/api/customer-self-service/deployments"), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].deploymentId").value("deployment"))
            .andExpect(jsonPath("$[0].offerId").value("offer"))
            .andExpect(jsonPath("$[0].bootstrapTargetProfileId").value("staging-target"));
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR,
            PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(as(get("/api/customer-self-service/deployments"), role)).andExpect(status().isForbidden());
        }
        verify(deploymentPortfolio, times(1)).deployments();
    }

    @Test void reviewedVersionPublicationIsCustomerOnlyAndUsesTheDedicatedContract() throws Exception {
        when(versions.publish(eq("offer"), eq("deployment"), any())).thenReturn(
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishResult(
                "deployment", "version", "v1", "config", "staging-target", 2));
        String body = "{\"targetProfileId\":\"staging-target\",\"expectedEntitlementRevision\":2,\"operationKey\":\"publish-key\"}";
        mvc.perform(as(post("/api/customer-self-service/catalogue/offer/deployments/deployment/versions")
            .contentType("application/json").content(body), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.versionId").value("version"));
        mvc.perform(as(post("/api/customer-self-service/catalogue/offer/deployments/deployment/versions")
            .contentType("application/json").content(body), PlatformRole.PLATFORM_OPERATOR))
            .andExpect(status().isForbidden());
        verify(versions, times(1)).publish(eq("offer"), eq("deployment"), any());
    }

    @Test void stagingApplyIsCustomerOnlyAndRejectsCallerSelectedTargetOrArtifact() throws Exception {
        String route = "/api/customer-self-service/catalogue/offer/deployments/deployment/staging-releases";
        String body = "{\"versionId\":\"version\",\"expectedEntitlementRevision\":2,\"operationKey\":\"apply-key\"}";
        mvc.perform(as(post(route).contentType("application/json").content(body), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isOk());
        verify(applies).apply(eq("offer"), eq("deployment"), any());
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR,
            PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(as(post(route).contentType("application/json").content(body), role)).andExpect(status().isForbidden());
        }
        clearInvocations(applies);
        mvc.perform(as(post(route).contentType("application/json").content(
            "{\"versionId\":\"version\",\"expectedEntitlementRevision\":2,\"operationKey\":\"apply-key\","
                + "\"targetProfileId\":\"foreign\",\"sourceArtifactId\":\"private\"}"), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(applies);
    }

    @Test void grantRevokeRoutesAreAdminOnlyAndCannotBeReachedThroughCustomerOrOperatorRoles() throws Exception {
        for (PlatformRole role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PLATFORM_OPERATOR)) {
            mvc.perform(as(post("/api/customer-self-service/staff/entitlements/grant/revoke").contentType("application/json")
                .content("{\"expectedRevision\":2,\"operationKey\":\"key\",\"reason\":\"revoke\"}"), role))
                .andExpect(status().isForbidden());
            mvc.perform(as(post("/api/customer-self-service/staff/targets/staging-target/placement-evidence").contentType("application/json")
                .content("{\"intent\":\"CUSTOMER_STAGING\"}"), role)).andExpect(status().isForbidden());
        }
        mvc.perform(as(get("/api/customer-self-service/staff/customers/customer/entitlements"), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk());
        verify(entitlements, never()).revoke(anyString(), any());
    }

    @Test void browserScopeSecretAndUnknownFieldsAreRejectedBeforeTheService() throws Exception {
        mvc.perform(as(post("/api/customer-self-service/invitations/grant/accept").contentType("application/json")
            .content("{\"expectedRevision\":1,\"operationKey\":\"key\",\"reason\":\"accept\",\"customerId\":\"foreign\",\"vault\":\"GLOBAL_SECRET\"}"),
            PlatformRole.CUSTOMER_ADMIN)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_CONTRACT"));
        verify(entitlements, never()).accept(anyString(), any());
        mvc.perform(as(post("/api/customer-self-service/staff/offers").contentType("application/json").content("null"),
            PlatformRole.PLATFORM_ADMIN)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_CONTRACT"));
        verify(entitlements, never()).publish(any());
    }

    @Test void typedPrerequisiteAndEntitlementErrorsArePreservedWithoutProviderDetail() throws Exception {
        when(catalogue.offer("offer")).thenThrow(CustomerSelfServiceException.conflict(STORAGE_REQUIRED));
        mvc.perform(as(get("/api/customer-self-service/catalogue/offer"), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("STORAGE_REQUIRED"));
        when(catalogue.offer("guessed")).thenThrow(CustomerSelfServiceException.deny(ENTITLEMENT_REQUIRED));
        mvc.perform(as(get("/api/customer-self-service/catalogue/guessed"), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ENTITLEMENT_REQUIRED"));
    }

    @Test void configurationBootstrapRejectsRawProviderAndCustomerFields() throws Exception {
        mvc.perform(as(post("/api/customer-self-service/catalogue/offer/deployments").contentType("application/json")
            .content("{\"targetProfileId\":\"staging-target\",\"name\":\"My deployment\",\"expectedEntitlementRevision\":2,\"operationKey\":\"create\"}"),
            PlatformRole.CUSTOMER_ADMIN)).andExpect(status().isOk());
        verify(bootstrap).bootstrap(eq("offer"), any());
        clearInvocations(bootstrap);
        mvc.perform(as(post("/api/customer-self-service/catalogue/offer/deployments").contentType("application/json")
            .content("{\"targetProfileId\":\"staging-target\",\"name\":\"My deployment\",\"expectedEntitlementRevision\":2,\"operationKey\":\"create\",\"customerId\":\"other\",\"sourceRepository\":\"unapproved\"}"),
            PlatformRole.CUSTOMER_ADMIN)).andExpect(status().isBadRequest());
        verifyNoInteractions(bootstrap);
        mvc.perform(as(post("/api/customer-self-service/catalogue/offer/deployments").contentType("application/json")
            .content("{}"), PlatformRole.PUBLIC_API_CLIENT)).andExpect(status().isForbidden());
    }

    @Test void customerAdmissionReservationRoutesAreOwnerOnlyAndStrictlyTyped() throws Exception {
        String route = "/api/customer-self-service/catalogue/offer/deployments/deployment/admission-reservations";
        String request = "{\"targetProfileId\":\"staging-target\",\"versionId\":\"version\","
            + "\"operation\":\"APPLY\",\"operationKey\":\"operation\"}";
        mvc.perform(as(post(route).contentType("application/json").content(request), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isOk());
        verify(reservations).reserve(eq("offer"), eq("deployment"), any());
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR, PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(as(post(route).contentType("application/json").content(request), role)).andExpect(status().isForbidden());
        }
        clearInvocations(reservations);
        mvc.perform(as(post(route).contentType("application/json")
            .content("{\"targetProfileId\":\"staging-target\",\"versionId\":\"version\","
                + "\"operation\":\"APPLY\",\"operationKey\":\"operation\",\"cpuMillis\":999999}"),
            PlatformRole.CUSTOMER_ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(as(post(route).contentType("application/json")
            .content("{\"targetProfileId\":\"staging-target\",\"manifestId\":\"caller-selected\",\"versionId\":\"version\","
                + "\"operation\":\"APPLY\",\"operationKey\":\"operation\"}"),
            PlatformRole.CUSTOMER_ADMIN)).andExpect(status().isBadRequest());
        verifyNoInteractions(reservations);
        mvc.perform(as(get(route + "/reservation"), PlatformRole.CUSTOMER_ADMIN)).andExpect(status().isOk());
        verify(reservations).status("offer", "deployment", "reservation");
        mvc.perform(as(get(route).queryParam("targetProfileId", "staging-target"), PlatformRole.CUSTOMER_ADMIN))
            .andExpect(status().isOk());
        verify(reservations).list("offer", "deployment", "staging-target");
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, PlatformRole role) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(new PlatformPrincipal("test-actor", role, "Test", "SESSION"),
            null, List.of(new SimpleGrantedAuthority(role.authority())))));
    }

    @org.springframework.boot.test.context.TestConfiguration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean CustomerOfferEntitlementService entitlements() { return mock(CustomerOfferEntitlementService.class); }
        @Bean CustomerSelfServiceCatalogueService catalogue() { return mock(CustomerSelfServiceCatalogueService.class); }
        @Bean CustomerOfferCodec codec() { return CustomerOfferTestFixtures.codec(); }
        @Bean CustomerPlacementProbeService placementProbes() { return mock(CustomerPlacementProbeService.class); }
        @Bean CustomerDeploymentBootstrapService bootstrap() { return mock(CustomerDeploymentBootstrapService.class); }
        @Bean CustomerDeploymentVersionService versions() { return mock(CustomerDeploymentVersionService.class); }
        @Bean CustomerDeploymentApplyService applies() { return mock(CustomerDeploymentApplyService.class); }
        @Bean CustomerDocumentStorageBindingService documentStorageBindings() {
            return mock(CustomerDocumentStorageBindingService.class);
        }
        @Bean CustomerAdmissionReservationService reservations() { return mock(CustomerAdmissionReservationService.class); }
        @Bean CustomerDeploymentPortfolioService deploymentPortfolio() { return mock(CustomerDeploymentPortfolioService.class); }
        @Bean CustomerSelfServiceController controller(CustomerSelfServiceCatalogueService catalogue,
            CustomerOfferEntitlementService entitlements, CustomerOfferCodec codec, CustomerPlacementProbeService placementProbes,
            CustomerDeploymentBootstrapService bootstrap, CustomerDeploymentVersionService versions,
            CustomerDeploymentApplyService applies,
            CustomerDocumentStorageBindingService documentStorageBindings,
            CustomerDeploymentPortfolioService deploymentPortfolio) {
            return new CustomerSelfServiceController(catalogue, entitlements, codec, placementProbes, bootstrap, versions,
                applies, documentStorageBindings, deploymentPortfolio);
        }
        @Bean CustomerSelfServiceExceptionHandler errors() { return new CustomerSelfServiceExceptionHandler(); }
        @Bean CustomerAdmissionReservationController admissionReservationController(CustomerAdmissionReservationService reservations,
                                                                                      CustomerOfferCodec codec) {
            return new CustomerAdmissionReservationController(reservations, codec);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
