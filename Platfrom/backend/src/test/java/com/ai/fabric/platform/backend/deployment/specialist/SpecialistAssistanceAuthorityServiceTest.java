package com.ai.fabric.platform.backend.deployment.specialist;

import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecialistAssistanceAuthorityServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentMarketplacePluginInstallRepository installs = mock(DeploymentMarketplacePluginInstallRepository.class);
    private final MarketplacePluginVersionRepository versions = mock(MarketplacePluginVersionRepository.class);
    private final SpecialistAssistanceAuthorityService service = new SpecialistAssistanceAuthorityService(installs, versions, mapper);

    @Test
    void resolvesOnlyOwnedEnabledInstalledReadActionsAndDeclaredSourceSpaces() throws Exception {
        var install = install("ENABLED");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("dep-1")).thenReturn(List.of(install));
        when(versions.findById("version-1")).thenReturn(Optional.of(version()));
        var authority = service.resolve("dep-1", actions(), sources());
        assertThat(authority.readActionScopes()).containsOnlyKeys("get_account_status");
        assertThat(authority.readActionScopes().get("get_account_status")).containsExactly("account:read");
        assertThat(authority.vectorSpaces()).containsExactly("product-guide");
    }

    @Test
    void disabledInstallAndCopiedOrForeignProvenanceCannotGrantAuthority() throws Exception {
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("dep-1")).thenReturn(List.of(install("DISABLED")));
        assertThat(service.resolve("dep-1", actions(), sources()).readActionScopes()).isEmpty();
        var enabled = install("ENABLED");
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("dep-1")).thenReturn(List.of(enabled));
        when(versions.findById("version-1")).thenReturn(Optional.of(version()));
        JsonNode forged = actions();
        ((ObjectNode) forged.path("actions").get(0)).put("marketplaceInstallId", "foreign-install");
        assertThat(service.resolve("dep-1", forged, sources()).readActionScopes()).isEmpty();
        enabled.setDeploymentId("foreign-deployment");
        assertThat(service.resolve("dep-1", actions(), sources()).vectorSpaces()).isEmpty();
    }

    @Test
    void draftCannotRelabelADeclaredWriteOrInventSourceSpace() throws Exception {
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("dep-1")).thenReturn(List.of(install("ENABLED")));
        when(versions.findById("version-1")).thenReturn(Optional.of(version()));
        JsonNode actions = actions();
        ((ObjectNode) actions.path("actions").get(1)).put("accessMode", "READ");
        JsonNode sources = sources();
        ((ObjectNode) sources.path("sources").get(0)).put("entityType", "private-record");
        var authority = service.resolve("dep-1", actions, sources);
        assertThat(authority.readActionScopes()).containsOnlyKeys("get_account_status");
        assertThat(authority.vectorSpaces()).isEmpty();
    }

    @Test
    void rejectsInstallWhoseVersionBelongsToAnotherPlugin() throws Exception {
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("dep-1")).thenReturn(List.of(install("ENABLED")));
        var version = version();
        version.setPluginId("foreign-plugin");
        when(versions.findById("version-1")).thenReturn(Optional.of(version));
        assertThatThrownBy(() -> service.resolve("dep-1", actions(), sources())).hasMessageContaining("ownership mismatch");
    }

    @Test
    void disabledSourceAndStaleVersionDoNotRemainAvailable() throws Exception {
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc("dep-1")).thenReturn(List.of(install("ENABLED")));
        when(versions.findById("version-1")).thenReturn(Optional.of(version()));
        JsonNode sources = sources();
        ((ObjectNode) sources.path("sources").get(0)).put("enabled", false);
        JsonNode actions = actions();
        ((ObjectNode) actions.path("actions").get(0)).put("marketplacePluginVersion", "0.9.0");
        var authority = service.resolve("dep-1", actions, sources);
        assertThat(authority.vectorSpaces()).isEmpty();
        assertThat(authority.readActionScopes()).isEmpty();
    }

    private DeploymentMarketplacePluginInstallEntity install(String status) {
        var install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("install-1"); install.setDeploymentId("dep-1"); install.setPluginId("plugin-1");
        install.setPluginVersionId("version-1"); install.setStatus(status);
        return install;
    }

    private MarketplacePluginVersionEntity version() {
        var version = new MarketplacePluginVersionEntity();
        version.setId("version-1"); version.setPluginId("plugin-1"); version.setVersion("1.0.0");
        version.setManifestJson("""
            {"contributions":{
              "actions":[{"actionId":"get_account_status","readOnly":true,"requiredScopes":["account:read"]},
                         {"actionId":"close_account","readOnly":false}],
              "knowledgeSources":[{"id":"guides","entityType":"product-guide"}]
            }}
            """);
        return version;
    }

    private JsonNode actions() throws Exception {
        JsonNode actions = mapper.readTree("""
            {"actions":[{"name":"get_account_status","accessMode":"READ"},
                        {"name":"close_account","accessMode":"WRITE_ONLY"}]}
            """);
        actions.path("actions").forEach(value -> provenance((ObjectNode) value));
        return actions;
    }

    private JsonNode sources() throws Exception {
        JsonNode sources = mapper.readTree("""
            {"sources":[{"id":"guides","entityType":"product-guide","enabled":true}]}
            """);
        provenance((ObjectNode) sources.path("sources").get(0));
        return sources;
    }

    private void provenance(ObjectNode value) {
        value.put("marketplaceManaged", true).put("marketplaceInstallId", "install-1")
            .put("marketplacePluginId", "plugin-1").put("marketplacePluginVersion", "1.0.0");
    }
}
