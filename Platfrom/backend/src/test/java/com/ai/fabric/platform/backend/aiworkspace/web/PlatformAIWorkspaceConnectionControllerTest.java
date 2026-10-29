package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceConnectionRequest;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformAIWorkspaceConnectionControllerTest {
    private static final String PATH = "/api/platform/customers/customer/ai-workspaces/connections";
    private static final String PAYLOAD = """
        {"deploymentId":"deployment","targetProfileId":"target","displayName":"Engage app","operationKey":"connect-1"}
        """;
    private AIWorkspaceConnectionService connections;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        connections = mock(AIWorkspaceConnectionService.class);
        var controller = new PlatformAIWorkspaceController(mock(AIWorkspaceInstallationService.class), connections);
        var proxy = new ProxyFactory(controller); proxy.setProxyTargetClass(true);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        mvc = MockMvcBuilders.standaloneSetup(proxy.getProxy()).build();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @EnumSource(value = PlatformRole.class, names = {"CUSTOMER_ADMIN", "PLATFORM_ADMIN"})
    void authorizedRolesReceiveTheExistingConsumerSummaryWithOptionalRelease(PlatformRole role) throws Exception {
        authenticate(role);
        var result = new PlatformConsumerSummary("engage-server-id", "customer", "Engage app", null, "ACTIVE",
            "deployment", "Deployment", "staging", "DRAFT", null, null, "target", null, null, null, 1);
        when(connections.create(eq("customer"), any())).thenReturn(result);
        mvc.perform(post(PATH).contentType("application/json").content(PAYLOAD))
            .andExpect(status().isCreated()).andExpect(jsonPath("consumerId").value("engage-server-id"))
            .andExpect(jsonPath("boundDeploymentId").value("deployment"))
            .andExpect(jsonPath("boundReleaseId").doesNotExist()).andExpect(jsonPath("bindingRevision").value(1));
        verify(connections).create("customer", new CreateAIWorkspaceConnectionRequest(
            "deployment", "target", null, "Engage app", "connect-1"));
    }

    @Test
    void exactReleaseIsForwardedWithoutAcceptingAnAssignmentIdentifier() throws Exception {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        mvc.perform(post(PATH).contentType("application/json").content("""
            {"deploymentId":"deployment","targetProfileId":"target","releaseId":"release",
             "displayName":"Engage app","operationKey":"connect-1"}
            """)).andExpect(status().isCreated());
        verify(connections).create("customer", new CreateAIWorkspaceConnectionRequest(
            "deployment", "target", "release", "Engage app", "connect-1"));
    }

    @ParameterizedTest
    @EnumSource(value = PlatformRole.class, names = {"CUSTOMER_ADMIN", "PLATFORM_ADMIN"})
    void previewCapabilitiesUsesTheExactScopedPathAndSafeContract(PlatformRole role) throws Exception {
        authenticate(role);
        var intake = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"anonymousAllowed":true,"fields":[{"id":"email","type":"email","required":true}]}
            """);
        when(connections.previewCapabilities("customer", "engage-server-id"))
            .thenReturn(new AIWorkspaceConnectionService.PreviewCapabilities(true,
                new AIWorkspaceConnectionService.PreviewHandover(true, true, false, intake)));
        mvc.perform(get(PATH + "/engage-server-id/preview-capabilities"))
            .andExpect(status().isOk()).andExpect(jsonPath("runtimeConfigured").value(true))
            .andExpect(jsonPath("handover.enabled").value(true))
            .andExpect(jsonPath("handover.userCanCancelPending").value(true))
            .andExpect(jsonPath("handover.userCanResumeAi").value(false))
            .andExpect(jsonPath("handover.intake.fields[0].required").value(true))
            .andExpect(jsonPath("$").value(org.hamcrest.Matchers.aMapWithSize(2)))
            .andExpect(jsonPath("handover").value(org.hamcrest.Matchers.aMapWithSize(4)));
        verify(connections).previewCapabilities("customer", "engage-server-id");
        verifyNoMoreInteractions(connections);
    }

    @Test
    void noReleasePreviewSerializesExplicitNullAndDisabledControlsWithoutIntake() throws Exception {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        when(connections.previewCapabilities("customer", "draft-connection"))
            .thenReturn(new AIWorkspaceConnectionService.PreviewCapabilities(null,
                new AIWorkspaceConnectionService.PreviewHandover(false, false, false, null)));
        mvc.perform(get(PATH + "/draft-connection/preview-capabilities"))
            .andExpect(status().isOk()).andExpect(content().json("""
                {"runtimeConfigured":null,"handover":{"enabled":false,"userCanCancelPending":false,"userCanResumeAi":false}}
                """, true));
    }

    @ParameterizedTest
    @EnumSource(value = PlatformRole.class, names = {"CUSTOMER_ADMIN", "PLATFORM_ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void previewCapabilitiesRejectsOtherRoles(PlatformRole role) {
        authenticate(role);
        assertThatThrownBy(() -> mvc.perform(get(PATH + "/engage-server-id/preview-capabilities")))
            .hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(connections);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"deploymentId\":\"deployment\"}",
        "{\"deploymentId\":\"deployment\",\"targetProfileId\":\"target\",\"displayName\":\"App\",\"operationKey\":\"bad/key\"}"})
    void malformedContractsAreRejectedBeforeComposition(String payload) throws Exception {
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        mvc.perform(post(PATH).contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        verifyNoInteractions(connections);
    }

    @ParameterizedTest
    @EnumSource(value = PlatformRole.class, names = {"CUSTOMER_ADMIN", "PLATFORM_ADMIN"}, mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotEnterTheComposition(PlatformRole role) {
        authenticate(role);
        assertThatThrownBy(() -> mvc.perform(post(PATH).contentType("application/json").content(PAYLOAD)))
            .hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(connections);
    }

    private void authenticate(PlatformRole role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new PlatformPrincipal("actor", role, "actor", "SESSION"), "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }
}
