package com.ai.fabric.platform.backend.deployment.tasks;

import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceAuthorityService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedTaskResourceServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final DeploymentService deployments = mock(DeploymentService.class);
    final SpecialistAssistanceAuthorityService authority = mock(SpecialistAssistanceAuthorityService.class);
    final ManagedTaskResourceService service = new ManagedTaskResourceService(deployments, authority, mapper);

    @Test void returnsOwnedResourcesAndInheritedSpecialistContractWithoutConnectionsOrSecrets() throws Exception {
        var actions = mapper.readTree("""
            {"actions":[{"name":"get_account_status","displayName":"Account status","accessMode":"READ"},
             {"name":"save_preference","accessMode":"WRITE_ONLY","confirmationRequired":false,"credential":"never-return-this"}]}
            """);
        var knowledge = mapper.readTree("{\"sources\":[{\"entityType\":\"product-guide\"}]}");
        var behavior = SpecialistAssistanceFixtures.behavior(mapper);
        behavior.withArray("executionExtensions").add("HUMAN_REVIEW");
        var empty = mapper.createObjectNode();
        var draft = new DeploymentDraftResponse("draft", "brain", 1, "ACTIVE", actions, empty, empty, empty, empty, empty,
            knowledge, empty, empty, Instant.now(), Instant.now(), "AI_ENTITY_CONFIG_V0_4", behavior);
        when(deployments.getActiveDraftForDeployment("brain")).thenReturn(draft);
        when(authority.resolveTasks("brain", actions, knowledge)).thenReturn(new SpecialistAssistanceCompiler.Authority(
            Map.of("get_account_status", Set.of()), Set.of("product-guide"), Map.of("save_preference", Set.of())));
        when(authority.resolve("brain", actions, knowledge)).thenReturn(SpecialistAssistanceFixtures.authority());
        var result = service.resources("brain");
        assertThat(result.path("actions")).hasSize(2);
        assertThat(result.at("/actions/1/confirmationRequired").asBoolean()).isFalse();
        assertThat(result.path("reviewAvailable").asBoolean()).isTrue();
        assertThat(result.path("specialists")).hasSize(2);
        assertThat(result.at("/specialists/0/inputSchema/properties/question/type").asText()).isEqualTo("string");
        assertThat(result.toString()).doesNotContain("never-return-this", "credential", "instructions");
        verify(deployments).getActiveDraftForDeployment("brain");
    }

    @Test void deniedBrainIsRejectedBeforeReadingAnyCapabilityAuthority() {
        when(deployments.getActiveDraftForDeployment("foreign")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.resources("foreign")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(authority);
    }
}
