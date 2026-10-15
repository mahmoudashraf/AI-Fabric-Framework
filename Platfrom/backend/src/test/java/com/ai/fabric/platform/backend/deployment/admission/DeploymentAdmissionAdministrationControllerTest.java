package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(DeploymentAdmissionAdministrationControllerTest.Config.class)
@WebAppConfiguration
class DeploymentAdmissionAdministrationControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired DeploymentAdmissionService admissions;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        reset(target());
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
        @Bean DeploymentAdmissionAdministrationController controller(DeploymentAdmissionService admissions) {
            return new DeploymentAdmissionAdministrationController(admissions);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
