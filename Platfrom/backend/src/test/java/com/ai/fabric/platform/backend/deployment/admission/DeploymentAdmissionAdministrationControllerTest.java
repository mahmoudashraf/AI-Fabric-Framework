package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(DeploymentAdmissionAdministrationControllerTest.Config.class)
@WebAppConfiguration
class DeploymentAdmissionAdministrationControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired DeploymentAdmissionService admissions;
    @Autowired DeploymentService deployments;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        reset(target());
        reset(deployments);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void onlyPlatformAdministratorsCanPublishReviewedAdmissionRecords() throws Exception {
        String body = "{\"id\":\"manifest\",\"deploymentId\":\"deployment\",\"versionId\":\"version\","
            + "\"versionConfigHash\":\"hash\",\"targetProfileId\":\"target\",\"bindingFingerprint\":\"binding\","
            + "\"topologyReference\":\"topology\",\"demand\":{\"amounts\":{}},"
            + "\"prerequisiteReferences\":[\"reviewed\"],\"customerPrerequisiteReferences\":{}}";
        String route = "/api/platform/admission/reviews/manifests";

        mvc.perform(as(post(route).contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk());
        verify(target()).approveManifest(any());
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN,
            PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(as(post(route).contentType("application/json").content(body), role))
                .andExpect(status().isForbidden());
        }
    }

    @Test void continuityUsesTheNormalStaffPublicationRouteAndPreservesExplicitRevisionZero() throws Exception {
        String body = "{\"id\":\"policy-new\",\"continuations\":[{\"originalPolicyId\":\"policy-old\",\"originalPolicyFingerprint\":\""
            + "a".repeat(64) + "\",\"reservations\":[{\"reservationId\":\"reservation\",\"revision\":0,\"generation\":\"generation\"}]}]}";
        String route = "/api/platform/admission/reviews/fleet-policies";
        mvc.perform(as(post(route).contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.policyId").value("policy-new"));
        verify(target()).approvePolicy(argThat(policy -> policy.continuations().size() == 1
            && policy.continuations().getFirst().reservations().equals(List.of(new AdmissionContracts.Fence("reservation", 0, "generation")))));
        for (var role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT))
            mvc.perform(as(post(route).contentType("application/json").content(body), role)).andExpect(status().isForbidden());
        mvc.perform(post(route).contentType("application/json").content(body)).andExpect(status().isForbidden());
        verify(target(), times(1)).approvePolicy(any());
    }

    @ParameterizedTest @ValueSource(strings={"",",\"revision\":null",",\"revision\":\"0\"",",\"revision\":0.5"})
    void continuityFenceRequiresExplicitIntegralRevision(String revision) throws Exception {
        String body = "{\"id\":\"policy-new\",\"continuations\":[{\"originalPolicyId\":\"policy-old\",\"originalPolicyFingerprint\":\""
            + "a".repeat(64) + "\",\"reservations\":[{\"reservationId\":\"reservation\",\"generation\":\"generation\"" + revision + "}]}]}";
        mvc.perform(as(post("/api/platform/admission/reviews/fleet-policies").contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isBadRequest());
        verify(target(), never()).approvePolicy(any());
    }

    @Test
    void onlyPlatformAdministratorsCanPrepareRuntimeAuthorityCandidates() throws Exception {
        String body = "{\"deploymentId\":\"deployment\",\"targetProfileId\":\"target\",\"versionId\":\"version\"}";
        String route = "/api/platform/admission/reviews/runtime-authority-candidates";
        when(deployments.prepareReviewedRuntimeAuthorityCandidate(any())).thenReturn(
            new AdmissionContracts.RuntimeAuthorityCandidateEvidence(
                "deployment", "target", "version", "snapshot", "fingerprint", 1,
                List.of("https://workspace.example.com")
            )
        );

        mvc.perform(as(post(route).contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fingerprint").value("fingerprint"))
            .andExpect(jsonPath("$.allowedOrigins[0]").value("https://workspace.example.com"))
            .andExpect(jsonPath("$.binding").doesNotExist())
            .andExpect(jsonPath("$.secrets").doesNotExist());
        verify(deployments).prepareReviewedRuntimeAuthorityCandidate(any());
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN,
            PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(as(post(route).contentType("application/json").content(body), role))
                .andExpect(status().isForbidden());
        }
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, PlatformRole role) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("test-actor", role, "Test", "SESSION"), null,
            List.of(new SimpleGrantedAuthority(role.authority())))));
    }

    private DeploymentAdmissionService target() throws Exception {
        return AopTestUtils.getTargetObject(admissions);
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config {
        @Bean DeploymentAdmissionService admissions() { return mock(DeploymentAdmissionService.class); }
        @Bean InitialAdmissionRenewalAuthorizer initialRenewalAuthorizer() { return mock(InitialAdmissionRenewalAuthorizer.class); }
        @Bean AdmissionPolicyPlacementVerifier policyPlacementVerifier() { return mock(AdmissionPolicyPlacementVerifier.class); }
        @Bean jakarta.persistence.EntityManagerFactory entityManagerFactory() { return mock(jakarta.persistence.EntityManagerFactory.class); }
        @Bean DeploymentService deployments() { return mock(DeploymentService.class); }
        @Bean com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver externalIntegrations() {
            return mock(com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver.class);
        }
        @Bean AdmissionReleaseCoordinator admissionReleaseCoordinator() { return mock(AdmissionReleaseCoordinator.class); }
        @Bean com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver documentBindingResolver() {
            return mock(com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver.class);
        }
        @Bean com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary offerBoundary() {
            return mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary.class);
        }
        @Bean com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService retirementGates() {
            return mock(com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService.class);
        }
        @Bean DeploymentAdmissionAdministrationController controller(DeploymentAdmissionService admissions,
                                                                       DeploymentService deployments) {
            return new DeploymentAdmissionAdministrationController(admissions, deployments);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
