package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceDeploymentUpdateService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AIWorkspaceDeploymentUpdateControllerTest {
    private static final String PATH = "/api/platform/customers/customer/ai-workspace-installations/app/deployment-update";
    private static final String FINGERPRINT = "a".repeat(64);
    private AIWorkspaceDeploymentUpdateService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(AIWorkspaceDeploymentUpdateService.class);
        var proxy = new ProxyFactory(new AIWorkspaceDeploymentUpdateController(service));
        proxy.setProxyTargetClass(true);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        mvc = MockMvcBuilders.standaloneSetup(proxy.getProxy()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @EnumSource(value = PlatformRole.class, names = {"CUSTOMER_ADMIN", "PLATFORM_ADMIN"})
    void authorizedRolesGetReviewForTheExactCustomerAndApp(PlatformRole role) throws Exception {
        authenticate(role);
        when(service.review("customer", "app")).thenReturn(new AIWorkspaceDeploymentUpdateService.Review(
            "customer", "app", "connection", "dep", "Deployment", "production", "draft", FINGERPRINT, 7,
            "release", "target", "artifact", List.of("Security"), List.of("corsAllowedOrigins"),
            List.of("https://ai.customer.example"), List.of(), List.of("Customer workspace"), false));
        mvc.perform(post(PATH + "/review")).andExpect(status().isOk())
            .andExpect(jsonPath("draftId").value("draft"))
            .andExpect(jsonPath("fingerprint").value(FINGERPRINT))
            .andExpect(jsonPath("expectedBindingRevision").value(7))
            .andExpect(jsonPath("targetProfileId").value("target"))
            .andExpect(jsonPath("sourceArtifactId").value("artifact"))
            .andExpect(jsonPath("unrelatedChanges").value(false));
        verify(service).review("customer", "app");
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = PlatformRole.class, names = {"CUSTOMER_ADMIN", "PLATFORM_ADMIN"})
    void publishForwardsExactReviewedContractWithoutInventingApplyOrAssignment(PlatformRole role) throws Exception {
        authenticate(role);
        when(service.publish(eq("customer"), eq("app"), any())).thenReturn(new DeploymentVersionSummary(
            "version", "dep", "draft", "v2", "PUBLISHED", "hash", false, Instant.parse("2026-10-30T00:00:00Z")));
        mvc.perform(post(PATH + "/publish").contentType("application/json").content("""
            {"draftId":"draft","fingerprint":"%s","expectedBindingRevision":7}
            """.formatted(FINGERPRINT)))
            .andExpect(status().isOk()).andExpect(jsonPath("id").value("version"))
            .andExpect(jsonPath("sourceDraftId").value("draft"));
        verify(service).publish("customer", "app",
            new AIWorkspaceDeploymentUpdateService.ReviewedPublish("draft", FINGERPRINT, 7));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = PlatformRole.class, names = {"CUSTOMER_ADMIN", "PLATFORM_ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotReviewOrPublish(PlatformRole role) {
        authenticate(role);
        assertThatThrownBy(() -> mvc.perform(post(PATH + "/review")))
            .hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> mvc.perform(post(PATH + "/publish").contentType("application/json").content("""
            {"draftId":"draft","fingerprint":"%s","expectedBindingRevision":7}
            """.formatted(FINGERPRINT))))
            .hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test
    void missingOrMalformedPublishBodyNeverCallsService() throws Exception {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        mvc.perform(post(PATH + "/publish")).andExpect(status().isBadRequest());
        mvc.perform(post(PATH + "/publish").contentType("application/json").content("null"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(PATH + "/publish").contentType("application/json").content("not-json"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 409})
    void publicationBoundaryDenialsArePreserved(int code) throws Exception {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        when(service.publish(eq("customer"), eq("app"), any())).thenThrow(
            new ResponseStatusException(HttpStatus.valueOf(code), "Reviewed update denied"));
        mvc.perform(post(PATH + "/publish").contentType("application/json").content("""
            {"draftId":"draft","fingerprint":"%s","expectedBindingRevision":7}
            """.formatted(FINGERPRINT))).andExpect(status().is(code));
    }

    private void authenticate(PlatformRole role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new PlatformPrincipal("actor", role, "actor", "SESSION"), "unused",
            List.of(new SimpleGrantedAuthority(role.authority()))));
    }
}
