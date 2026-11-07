package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.service.DeploymentCuratedModuleCatalogService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentConfigCompiler;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeploymentAiBehaviourControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final DeploymentCuratedModuleCatalogService curated = new DeploymentCuratedModuleCatalogService(mapper, new DefaultResourceLoader());
    private final DeploymentAiBehaviourController controller = new DeploymentAiBehaviourController(deployments, curated, new DeploymentConfigCompiler(mapper));

    @Test
    void compositionRequiresTheExistingDeploymentAccessCheckBeforeReturningAnything() {
        when(deployments.getActiveDraftForDeployment("other-customer"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> controller.compose("other-customer",
            new DeploymentAiBehaviourController.ComposeRequest(mapper.createObjectNode(), mapper.createObjectNode())))
            .isInstanceOf(ResponseStatusException.class);
        verify(deployments).getActiveDraftForDeployment("other-customer");
        verifyNoMoreInteractions(deployments);
    }

    @Test
    void composingTheEditorDoesNotSaveDraftsOrPublishReleases() throws Exception {
        var draft = mock(DeploymentDraftResponse.class);
        when(draft.providerConfig()).thenReturn(mapper.createObjectNode().put("curatedModuleId", "default"));
        when(draft.knowledgeSourceConfig()).thenReturn(mapper.createObjectNode());
        when(deployments.getActiveDraftForDeployment("own-brain")).thenReturn(draft);
        var result = controller.compose("own-brain", new DeploymentAiBehaviourController.ComposeRequest(
            curated.promptPreset("default"), mapper.createObjectNode().put("tone", "warm")));
        assertThat(result).containsKeys("promptConfig", "effectivePromptConfig", "changes");
        verify(deployments).getActiveDraftForDeployment("own-brain");
        verifyNoMoreInteractions(deployments);
    }
}
