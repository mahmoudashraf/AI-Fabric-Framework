package com.ai.fabric.platform.backend.aiworkspace.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIWorkspaceAuthoringContractTest {
    private final AIWorkspaceExperiencePackRegistry packs = new AIWorkspaceExperiencePackRegistry(
        mock(AIWorkspaceAssetCatalogService.class), mock(AIWorkspaceConfigurationValidator.class));

    @Test void eachPackOwnsItsExactFieldMapping() {
        var generic = packs.authoringContract("generic-workspace", "1.0.0");
        var support = packs.authoringContract("support", "1.0.0");
        var dealer = packs.authoringContract("dealership", "1.1.0");
        assertThat(generic.fieldPaths().get("assistantLabel")).isEqualTo("/configuration/app/assistantLabel");
        assertThat(support.fieldPaths().get("assistantLabel")).isEqualTo("/configuration/app/assistantLabel");
        assertThat(dealer.fieldPaths().get("assistantLabel")).isEqualTo("/configuration/dealer/assistantLabel");
        assertThat(generic.toString()).doesNotContain("dealership", "/dealer/");
        assertThat(dealer.fieldPaths().values()).doesNotContain("/configuration/app/assistantLabel");
        assertThat(dealer.configurationSchemaVersion()).isEqualTo("loomai-dealership-experience-config-v1");
    }

    @Test void unknownPackCannotBorrowAnotherPacksMapping() {
        assertThatThrownBy(() -> packs.authoringContract("unknown", "1.0.0"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
