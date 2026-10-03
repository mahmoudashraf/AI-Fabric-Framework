package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalVehicleProviderVerificationManifestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarketplaceManifestService service = new MarketplaceManifestService(objectMapper);

    @Test
    void internalHostedCanaryManifestsPassCurrentMarketplaceContract() throws Exception {
        Path fixtureRoot = repositoryRoot().resolve(
            "verification-support/external-vehicle-provider-simulator/fixtures/marketplace"
        );
        for (String file : List.of(
            "profile-a-data.json",
            "profile-b-data.json",
            "profile-a-template.json",
            "profile-b-template.json",
            "autotrader-contract-data.json",
            "autotrader-contract-actions.json",
            "autotrader-contract-template.json"
        )) {
            String manifestJson = Files.readString(fixtureRoot.resolve(file))
                .replace("__SIMULATOR_BASE_URL__", "https://simulator.example.test")
                .replace("__SIMULATOR_HOST__", "simulator.example.test");
            JsonNode manifest = objectMapper.readTree(manifestJson);
            String pluginId = manifest.path("pluginId").asText();
            String pluginType = manifest.path("pluginType").asText();
            MarketplaceManifestService.ParsedMarketplaceManifest parsed = service.parseAndValidate(
                plugin(pluginId, pluginType),
                version(pluginId, manifest.path("version").asText(), manifestJson)
            );
            assertThat(parsed.pluginType()).isEqualTo(pluginType);
        }
    }

    private MarketplacePluginEntity plugin(String pluginId, String pluginType) {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId(pluginId);
        plugin.setSlug(pluginId);
        plugin.setDisplayName(pluginId);
        plugin.setPluginType(pluginType);
        plugin.setPublisherSlug("loom-internal-verification");
        plugin.setPublisherDisplayName("Loom Internal Verification");
        plugin.setShortDescription("Internal hosted verification fixture.");
        plugin.setStatus("DRAFT");
        plugin.setCreatedAt(Instant.parse("2026-09-28T00:00:00Z"));
        plugin.setUpdatedAt(Instant.parse("2026-09-28T00:00:00Z"));
        return plugin;
    }

    private MarketplacePluginVersionEntity version(String pluginId, String versionLabel, String manifestJson) {
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId("mkv-verification-" + Math.abs(pluginId.hashCode()));
        version.setPluginId(pluginId);
        version.setVersion(versionLabel);
        version.setReleaseChannel("INTERNAL_VERIFICATION");
        version.setStatus("SUBMITTED");
        version.setManifestJson(manifestJson);
        version.setCreatedAt(Instant.parse("2026-09-28T00:00:00Z"));
        version.setPublishedAt(Instant.parse("2026-09-28T00:00:00Z"));
        return version;
    }

    private Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve("Platfrom/backend/pom.xml"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Repository root could not be located.");
    }
}
