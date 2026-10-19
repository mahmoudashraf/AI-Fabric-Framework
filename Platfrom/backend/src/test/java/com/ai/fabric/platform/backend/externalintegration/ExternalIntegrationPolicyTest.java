package com.ai.fabric.platform.backend.externalintegration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ExternalIntegrationPolicyTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void realManifestTypesAndImmutableOfferPinsConstrainEveryConfigAndSecretField() throws Exception {
        var f = new ExternalIntegrationFixture();
        f.policy.validateDefinition(f.definition);
        assertThat(f.policy.requireProfile(f.definition, f.profile.bindingSlotId(), f.profile.id(), f.target.getId())).isEqualTo(f.profile);
        assertThatThrownBy(() -> f.policy.requireProfile(f.definition, f.profile.bindingSlotId(), f.profile.id(), "foreign-target"))
            .hasMessageContaining("PROFILE_SCOPE_MISMATCH");
        var old = f.pluginVersions.findById(f.profile.pluginVersionId()).orElseThrow(); old.setManifestJson(old.getManifestJson().replace("Generic action", "Changed action"));
        assertThatThrownBy(() -> f.policy.validateDefinition(f.definition)).hasMessageContaining("PLUGIN_PIN_MISMATCH");
    }

    @Test void exactReviewRejectsArbitraryConfigHostsCredentialsQuerySecretsAndUnsafeUrlsWithoutNetwork() throws Exception {
        var f = new ExternalIntegrationFixture();
        for (String url : new String[]{"http://provider.example.com/actions", "https://evil.example.com/actions", "https://127.0.0.1/actions",
            "https://localhost/actions", "https://provider.example.com:8443/actions", "https://user:secret@provider.example.com/actions",
            "https://provider.example.com/actions?apiKey=raw", "https://provider.example.com/actions#secret", "https://provider.example.com/a/../actions",
            "https://provider.example.com/%2e%2e/actions", "${MANAGED_SIMULATOR_BASE_URL}"}) {
            assertThatThrownBy(() -> f.policy.validateConfig(f.profile, Map.of("endpointUrl", url), Map.of("externalAccount", "reviewed-account")));
        }
        assertThatThrownBy(() -> f.policy.validateConfig(f.profile, Map.of("endpointUrl", "https://provider.example.com/actions", "apiKey", "arbitrary-ref"), Map.of("externalAccount", "reviewed-account")));
        assertThatThrownBy(() -> f.policy.validateConfig(f.profile, Map.of("endpointUrl", "https://provider.example.com/actions"), Map.of()));
        assertThat(f.dnsCalls).hasValue(0);
    }

    @Test void publicDnsRejectsEveryPrivateAddressEvenInMixedAnswersAndNeverUsesUnknownResults() throws Exception {
        for (String address : new String[]{"127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.0.1", "169.254.169.254", "100.64.0.1", "::1", "fc00::1", "fe80::1"}) {
            var policy = new ExternalIntegrationEndpointPolicy(host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8}), InetAddress.getByName(address)});
            assertThatThrownBy(() -> policy.requirePublicDns(Set.of("provider.example.com"))).hasMessageContaining("ADDRESS_FORBIDDEN");
        }
        assertThatThrownBy(() -> new ExternalIntegrationEndpointPolicy(host -> { throw new UnknownHostException(); })
            .requirePublicDns(Set.of("provider.example.com"))).hasMessageContaining("DNS_UNPROVEN");
        new ExternalIntegrationEndpointPolicy(host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})})
            .requirePublicDns(Set.of("provider.example.com"));
    }

    @Test void compilerOverlayIsDeterministicPureAndRefusesMutableValuesOrSharedSecretInjection() throws Exception {
        var f = new ExternalIntegrationFixture();
        var original = f.mapper.createObjectNode().put("unrelated", "retained");
        var inputs = f.compiler.installInputs(f.profile, original, f.mapper.createObjectNode());
        assertThat(original.size()).isEqualTo(1);
        assertThat(inputs.config().path("endpointUrl").asText()).matches("\\$\\{MANAGED_EXTERNAL_INTEGRATION_[A-F0-9]{40}}");
        assertThat(inputs.secretRefs().path("apiKey").asText()).matches("MANAGED_EXTERNAL_INTEGRATION_[A-F0-9]{40}");
        assertThat(f.compiler.installInputs(f.profile, inputs.config(), inputs.secretRefs())).isEqualTo(inputs);
        assertThatThrownBy(() -> f.compiler.installInputs(f.profile, f.mapper.createObjectNode().put("endpointUrl", "https://unreviewed.example.com"), f.mapper.createObjectNode()))
            .hasMessageContaining("MUTABLE_INSTALL_BINDING_FORBIDDEN");
        assertThatThrownBy(() -> f.compiler.installInputs(f.profile, f.mapper.createObjectNode(), f.mapper.createObjectNode().put("apiKey", "ZILLIZ_CLOUD_API_KEY")))
            .hasMessageContaining("MUTABLE_INSTALL_BINDING_FORBIDDEN");
        var evidence = f.compiler.compilerEvidence(f.profile);
        assertThat(f.compiler.compilerEvidence(f.codec.read(f.codec.write(f.profile), ExternalIntegrationContracts.Profile.class))).isEqualTo(evidence);
    }

    @Test void compilerEvidenceIsRequiredExactlyOnceForEveryReviewedProfile() throws Exception {
        var f = new ExternalIntegrationFixture();
        var evidence = f.compiler.compilerEvidence(f.profile);
        var provenance = f.mapper.createObjectNode();
        provenance.putArray("externalIntegrationProfiles").add(evidence);
        f.policy.requireCompilerEvidence(f.definition, provenance);
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, f.mapper.createObjectNode()))
            .hasMessageContaining("COMPILER_EVIDENCE_REQUIRED");
        for (String marker : new String[]{"null", "{}", "[]", "[null]", "[true]", "[\"unexpected\"]"}) {
            var malformed = f.mapper.createObjectNode();
            malformed.set("externalIntegrationProfiles", f.mapper.readTree(marker));
            assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, malformed))
                .hasMessageContaining("COMPILER_");
        }
        provenance.withArray("externalIntegrationProfiles").add(evidence.deepCopy());
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, provenance))
            .hasMessageContaining("COMPILER_EVIDENCE_REQUIRED");
        assertThat(f.dnsCalls).hasValue(0);
    }

    @Test void compilerEvidenceRejectsChangedFieldsAliasesHashesAndUnexpectedFields() throws Exception {
        var f = new ExternalIntegrationFixture();
        var original = f.compiler.compilerEvidence(f.profile);
        for (String field : new String[]{"schemaVersion", "profileId", "bindingSlotId", "pluginId", "pluginVersionId", "profileFingerprint"}) {
            var changed = original.deepCopy().put(field, "unexpected");
            var provenance = f.mapper.createObjectNode();
            provenance.putArray("externalIntegrationProfiles").add(changed);
            assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, provenance))
                .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
            changed.remove(field);
            assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, provenance))
                .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
        }
        for (String aliasField : new String[]{"configAliases", "secretAliases"}) {
            var changed = original.deepCopy();
            changed.withObject("/" + aliasField).put("unexpectedField", "MANAGED_EXTERNAL_INTEGRATION_UNREVIEWED");
            var provenance = f.mapper.createObjectNode();
            provenance.putArray("externalIntegrationProfiles").add(changed);
            assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, provenance))
                .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
            changed.set(aliasField, f.mapper.createObjectNode());
            assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, provenance))
                .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
        }
        var provenance = f.mapper.createObjectNode();
        provenance.putArray("externalIntegrationProfiles").add(original.deepCopy().put("unreviewed", true));
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(f.definition, provenance))
            .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
    }

    @Test void compilerEvidenceMatchesAllProfilesWithoutDependingOnArrayOrderOrAcceptingSubstitution() throws Exception {
        var f = new ExternalIntegrationFixture();
        var definitionTree = (com.fasterxml.jackson.databind.node.ObjectNode) f.mapper.valueToTree(f.definition);
        var second = (com.fasterxml.jackson.databind.node.ObjectNode) f.mapper.valueToTree(f.profile);
        second.put("id", "second-profile").put("bindingSlotId", "second-slot");
        definitionTree.withArray("externalIntegrationProfiles").add(second);
        var secondSlot = definitionTree.withArray("bindingSlots").get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) secondSlot).put("id", "second-slot")
            .putArray("supportedProfileIds").add("second-profile");
        definitionTree.withArray("bindingSlots").add(secondSlot);
        var definition = f.mapper.treeToValue(definitionTree,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition.class);
        var provenance = f.mapper.createObjectNode();
        var evidence = provenance.putArray("externalIntegrationProfiles");
        evidence.add(f.compiler.compilerEvidence(definition.externalIntegrationProfiles().get(1)))
            .add(f.compiler.compilerEvidence(f.profile));
        f.policy.requireCompilerEvidence(definition, provenance);
        evidence.set(0, f.compiler.compilerEvidence(f.profile));
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(definition, provenance))
            .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
        evidence.set(0, f.compiler.compilerEvidence(definition.externalIntegrationProfiles().get(1)).put("profileId", "foreign-profile"));
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(definition, provenance))
            .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
    }

    @Test void noProfileDefinitionRejectsUnexpectedOrMalformedCompilerMarkers() throws Exception {
        var f = new ExternalIntegrationFixture();
        var definitionTree = (com.fasterxml.jackson.databind.node.ObjectNode) f.mapper.valueToTree(f.definition);
        definitionTree.putArray("externalIntegrationProfiles");
        definitionTree.putArray("bindingSlots");
        var definition = f.mapper.treeToValue(definitionTree,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition.class);
        f.policy.requireCompilerEvidence(definition, f.mapper.createObjectNode());
        var empty = f.mapper.createObjectNode();
        empty.putArray("externalIntegrationProfiles");
        f.policy.requireCompilerEvidence(definition, empty);
        for (String marker : new String[]{"null", "{}", "\"unexpected\"", "[null]"}) {
            var provenance = f.mapper.createObjectNode();
            provenance.set("externalIntegrationProfiles", f.mapper.readTree(marker));
            assertThatThrownBy(() -> f.policy.requireCompilerEvidence(definition, provenance))
                .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
        }
        var unexpected = f.mapper.createObjectNode();
        unexpected.putArray("externalIntegrationProfiles").add(f.compiler.compilerEvidence(f.profile));
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(definition, unexpected))
            .hasMessageContaining("COMPILER_PROFILE_MISMATCH");
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(definition, null))
            .hasMessageContaining("COMPILER_EVIDENCE_INVALID");
        assertThatThrownBy(() -> f.policy.requireCompilerEvidence(definition, f.mapper.createArrayNode()))
            .hasMessageContaining("COMPILER_EVIDENCE_INVALID");
        assertThat(f.dnsCalls).hasValue(0);
    }

    @Test void unexpectedDatabaseFailuresAreNotDisguisedAsMissingCredentialsOrBusyState() throws Exception {
        var f = new ExternalIntegrationFixture();
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var failure = new org.springframework.jdbc.UncategorizedSQLException("synthetic", "synthetic", new java.sql.SQLException("synthetic database failure", "XX000"));
        org.mockito.Mockito.when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(String.class), org.mockito.ArgumentMatchers.eq("dep"))).thenThrow(failure);
        assertThatThrownBy(() -> new ExternalIntegrationStore(jdbc, f.codec).fenceDeployment("dep")).isSameAs(failure);
    }
}
