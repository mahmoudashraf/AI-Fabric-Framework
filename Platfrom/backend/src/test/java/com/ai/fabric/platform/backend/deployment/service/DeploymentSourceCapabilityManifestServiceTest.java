package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSpecialistBundleSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentSourceCapabilityManifestServiceTest {

    private static final String AGENTIC_BUNDLE_HASH =
        "sha256:ab1a1185dbe5f8ba5dc6c67c10c196bd9a569f211c537a39efb2d47fef05a025";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeploymentSourceCapabilityManifestService service =
        new DeploymentSourceCapabilityManifestService(objectMapper);

    @Test
    void normalizationIsDeterministicAcrossInputOrdering() throws Exception {
        JsonNode first = manifest(AGENTIC_BUNDLE_HASH);
        JsonNode second = objectMapper.readTree(objectMapper.writeValueAsString(first));
        ((com.fasterxml.jackson.databind.node.ArrayNode) second.path("capabilities"))
            .insert(0, ((com.fasterxml.jackson.databind.node.ArrayNode) second.path("capabilities")).remove(1));

        var firstNormalized = service.normalize(first);
        var secondNormalized = service.normalize(second);

        assertThat(firstNormalized.hash()).isEqualTo(secondNormalized.hash());
        assertThat(firstNormalized.manifest()).isEqualTo(secondNormalized.manifest());
        assertThat(firstNormalized.manifest().path("capabilities").get(0).asText())
            .isEqualTo("ai-fabric-execution");
    }

    @Test
    void exactSpecialistBundleSatisfiesReleaseRequirements() throws Exception {
        var normalized = service.normalize(manifest(AGENTIC_BUNDLE_HASH));

        service.requireSupports(normalized.manifest(), requirements());

        assertThat(normalized.hash()).hasSize(64);
    }

    @Test
    void modifiedSpecialistBundleHashFailsClosed() throws Exception {
        String differentHash = "sha256:" + "a".repeat(64);
        var normalized = service.normalize(manifest(differentHash));

        assertThatThrownBy(() -> service.requireSupports(normalized.manifest(), requirements()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("missing exact specialist bundles: deployment-intelligence-team@1");
    }

    @Test
    void undeclaredManifestAndBundleFieldsAreRejected() throws Exception {
        JsonNode topLevel = manifest(AGENTIC_BUNDLE_HASH);
        ((com.fasterxml.jackson.databind.node.ObjectNode) topLevel).put("inlineSpecialistYaml", "not-reviewed");
        assertThatThrownBy(() -> service.normalize(topLevel))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported capabilityManifest field: inlineSpecialistYaml");

        JsonNode bundleLevel = manifest(AGENTIC_BUNDLE_HASH);
        ((com.fasterxml.jackson.databind.node.ObjectNode) bundleLevel.path("specialistBundles").get(0))
            .put("inlinePrompt", "not-reviewed");
        assertThatThrownBy(() -> service.normalize(bundleLevel))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported specialist bundle field: inlinePrompt");
    }

    private JsonNode manifest(String bundleHash) throws Exception {
        return objectMapper.readTree("""
            {
              "schemaVersion": "loomai-runtime-capabilities-v1",
              "aiFabricVersion": "0.7.1",
              "supportedBehaviorTypes": ["AGENTIC_SPECIALIST_TEAM"],
              "supportedActivationSources": ["TRUSTED_APPLICATION"],
              "supportedChannelBindings": ["MAX_MODE"],
              "supportedExecutionExtensions": [],
              "capabilities": ["specialist-chains", "ai-fabric-execution"],
              "endpointClasses": ["chain-submit"],
              "migrationIds": ["ai-specialist-chain-execution-v1"],
              "verificationPackIds": ["agentic-specialist-team-v1"],
              "specialistBundles": [{
                "bundleId": "deployment-intelligence-team@1",
                "contractVersion": "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
                "contentHash": "%s",
                "behaviorTypes": ["AGENTIC_SPECIALIST_TEAM"],
                "specialistRefs": [
                  "deployment-runtime-state-specialist@1",
                  "deployment-knowledge-specialist@1",
                  "deployment-intelligence-manager@1"
                ],
                "chainRefs": ["deployment-intelligence-team@1"],
                "resourceLocations": [
                  "classpath:ai-specialists/deployment-knowledge-specialist.yml",
                  "classpath:ai-specialists/deployment-intelligence-team.yml",
                  "classpath:ai-chains/deployment-intelligence-team.yml"
                ]
              }]
            }
            """.formatted(bundleHash));
    }

    private DeploymentBehaviorCatalogService.RuntimeRequirements requirements() {
        return new DeploymentBehaviorCatalogService.RuntimeRequirements(
            "AGENTIC_SPECIALIST_TEAM",
            List.of("TRUSTED_APPLICATION"),
            List.of("MAX_MODE"),
            List.of(),
            List.of("ai-fabric-execution", "specialist-chains"),
            List.of("chain-submit"),
            List.of("ai-specialist-chain-execution-v1"),
            List.of("agentic-specialist-team-v1"),
            List.of(new DeploymentSpecialistBundleSummary(
                "deployment-intelligence-team@1",
                "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
                AGENTIC_BUNDLE_HASH,
                List.of("AGENTIC_SPECIALIST_TEAM"),
                List.of(
                    "deployment-intelligence-manager@1",
                    "deployment-knowledge-specialist@1",
                    "deployment-runtime-state-specialist@1"
                ),
                List.of("deployment-intelligence-team@1"),
                List.of(
                    "classpath:ai-chains/deployment-intelligence-team.yml",
                    "classpath:ai-specialists/deployment-intelligence-team.yml",
                    "classpath:ai-specialists/deployment-knowledge-specialist.yml"
                )
            )),
            true
        );
    }
}
