package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class DeploymentTargetProfileIdentityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String GOLDEN_HASH = "e5e3ee7463654d36663577109db633685027c5b0c51dae0c5bd7ce99c71b4d2a";

    // Golden counterpart for sorted-object JSON.stringify; all values are fictional and nonsecret.
    private DeploymentTargetProfileEntity golden() {
        var profile = new DeploymentTargetProfileEntity();
        profile.setId("target-golden");
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("customer-staging");
        profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setCredentialRefId("credential-golden");
        profile.setProviderConfigJson("""
            {"serverUuid":"server-golden","destinationUuid":"destination-golden",
             "options":{"tls":true,"labels":["alpha","beta"],"limits":{"retries":2,"timeout":30}},
             "callbackPath":"/api/hooks/a?x=1&y=2"}
            """);
        profile.setNetworkPolicyJson("""
            {"allowedOrigins":["https://workspace.example","https://dealer.example"],
             "policy":{"credentials":false,"methods":["GET","POST"]}}
            """);
        profile.setResourceDefaultsJson("""
            {"sourceStrategy":"IMAGE_SOURCE","limits":{"cpuMillis":1000,"memoryMiB":1024},"flags":{"inbox":true}}
            """);
        return profile;
    }

    @Test void nestedCanonicalPayloadMatchesTheSharedGoldenHash() {
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, golden())).isEqualTo(GOLDEN_HASH);
    }

    @Test void nestedOrderingAndExactIntegralNumericRepresentationsDoNotChangeTheGoldenIdentity() {
        var profile = golden();
        profile.setProviderConfigJson("""
            {"callbackPath":"/api/hooks/a?x=1&y=2","options":{"limits":{"timeout":3e1,"retries":2.0},
             "labels":["alpha","beta"],"tls":true},"destinationUuid":"destination-golden","serverUuid":"server-golden"}
            """);
        profile.setNetworkPolicyJson("""
            {"policy":{"methods":["GET","POST"],"credentials":false},
             "allowedOrigins":["https://workspace.example","https://dealer.example"]}
            """);
        profile.setResourceDefaultsJson("""
            {"flags":{"inbox":true},"limits":{"memoryMiB":1.024e3,"cpuMillis":1000.0},"sourceStrategy":"IMAGE_SOURCE"}
            """);
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, profile)).isEqualTo(GOLDEN_HASH);
    }

    @ParameterizedTest @ValueSource(strings = {"id", "provider", "environment", "sourceStrategy", "credentialReference",
        "providerConfig", "networkPolicy", "resourceDefaults"})
    void everySecurityFieldChangesTheIdentity(String field) {
        var profile = golden();
        switch (field) {
            case "id" -> profile.setId("other-target");
            case "provider" -> profile.setProviderType(DeploymentProviderType.RAILWAY_API);
            case "environment" -> profile.setEnvironmentName("customer-production");
            case "sourceStrategy" -> profile.setSourceStrategy("GIT_SOURCE");
            case "credentialReference" -> profile.setCredentialRefId("other-credential");
            case "providerConfig" -> profile.setProviderConfigJson("{\"serverUuid\":\"other-server\"}");
            case "networkPolicy" -> profile.setNetworkPolicyJson("{\"allowedOrigins\":[]}");
            case "resourceDefaults" -> profile.setResourceDefaultsJson("{\"limits\":{\"cpuMillis\":2000}}");
            default -> throw new AssertionError(field);
        }
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, profile)).isNotEqualTo(GOLDEN_HASH);
    }

    @Test void arrayOrderRemainsSignificant() {
        var profile = golden();
        profile.setNetworkPolicyJson("""
            {"allowedOrigins":["https://dealer.example","https://workspace.example"],
             "policy":{"credentials":false,"methods":["GET","POST"]}}
            """);
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, profile)).isNotEqualTo(GOLDEN_HASH);
    }

    @ParameterizedTest @ValueSource(strings = {"id", "environment", "sourceStrategy", "provider"})
    void requiredIdentityFieldsCannotBeMissingOrBlank(String field) {
        for (String value : new String[]{null, "", " \t"}) {
            var profile = golden();
            switch (field) {
                case "id" -> profile.setId(value);
                case "environment" -> profile.setEnvironmentName(value);
                case "sourceStrategy" -> profile.setSourceStrategy(value);
                case "provider" -> profile.setProviderType(null);
                default -> throw new AssertionError(field);
            }
            assertThatThrownBy(() -> DeploymentTargetProfileIdentity.fingerprint(mapper, profile))
                .hasMessage("TARGET_PROFILE_IDENTITY_INVALID");
        }
    }

    @Test void identityStringsAreNotTrimmedOrCaseFolded() {
        var profile = golden();
        profile.setEnvironmentName(" customer-staging");
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, profile)).isNotEqualTo(GOLDEN_HASH);
        profile.setEnvironmentName("CUSTOMER-STAGING");
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, profile)).isNotEqualTo(GOLDEN_HASH);
        profile.setEnvironmentName("customer-staging");
        profile.setCredentialRefId("credential-golden ");
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, profile)).isNotEqualTo(GOLDEN_HASH);
    }

    @ParameterizedTest @ValueSource(strings = {"providerConfig", "networkPolicy", "resourceDefaults"})
    void zeroRepresentationsNormalizeOnlyInsideTargetIdentity(String field) {
        var profile = golden();
        json(profile, field, "{\"nested\":[{\"value\":0}]}");
        String expected = DeploymentTargetProfileIdentity.fingerprint(mapper, profile);
        json(profile, field, "{\"nested\":[{\"value\":-0.0}]}");
        assertThat(DeploymentTargetProfileIdentity.fingerprint(mapper, profile)).isEqualTo(expected);
        assertThat(DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(1.0)))
            .isNotEqualTo(DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(1)));
    }

    @ParameterizedTest @CsvSource({"providerConfig,[]", "providerConfig,null", "providerConfig,broken",
        "networkPolicy,[]", "networkPolicy,null", "networkPolicy,broken",
        "resourceDefaults,[]", "resourceDefaults,null", "resourceDefaults,broken"})
    void nonObjectOrMalformedJsonFailsClosed(String field, String value) {
        var profile = golden();
        json(profile, field, value);
        assertThatThrownBy(() -> DeploymentTargetProfileIdentity.fingerprint(mapper, profile))
            .hasMessage("TARGET_PROFILE_IDENTITY_INVALID");
    }

    @ParameterizedTest @CsvSource({"providerConfig,0.5", "networkPolicy,1.25", "resourceDefaults,-0.1",
        "providerConfig,9007199254740992", "networkPolicy,-9007199254740992", "resourceDefaults,9007199254740991.1",
        "providerConfig,1e1000", "networkPolicy,1e-1000"})
    void unsupportedNumbersAreRejectedInsteadOfRoundedOrHashedDifferentlyFromJavaScript(String field, String number) {
        var profile = golden();
        json(profile, field, "{\"nested\":[{\"value\":" + number + "}]}");
        assertThatThrownBy(() -> DeploymentTargetProfileIdentity.fingerprint(mapper, profile))
            .hasMessage("TARGET_PROFILE_IDENTITY_NUMBER_UNSUPPORTED");
    }

    private void json(DeploymentTargetProfileEntity profile, String field, String value) {
        switch (field) {
            case "providerConfig" -> profile.setProviderConfigJson(value);
            case "networkPolicy" -> profile.setNetworkPolicyJson(value);
            case "resourceDefaults" -> profile.setResourceDefaultsJson(value);
            default -> throw new AssertionError(field);
        }
    }
}
