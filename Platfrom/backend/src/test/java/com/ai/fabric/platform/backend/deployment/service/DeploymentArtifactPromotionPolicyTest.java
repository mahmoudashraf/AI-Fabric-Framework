package com.ai.fabric.platform.backend.deployment.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactPromotionPolicyTest {
    @ParameterizedTest
    @CsvSource({
        "staging,staging,true",
        "production,production,true",
        "production,staging,true",
        "PRODUCTION,STAGING,true",
        "staging,production,false",
        "development,staging,false",
        "development,production,false",
        "custom,custom,true",
        "production,custom,false"
    })
    void qualificationIsDirectional(String channel, String expected, boolean allowed) {
        assertThat(DeploymentArtifactPromotionPolicy.allows(channel, expected)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingQualificationIsAlwaysRejected(String channel) {
        assertThat(DeploymentArtifactPromotionPolicy.allows(channel, "staging")).isFalse();
        assertThat(DeploymentArtifactPromotionPolicy.allows(channel, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production", "custom"})
    void unclassifiedDeveloperTargetsKeepExistingExplicitPromotionBehavior(String channel) {
        assertThat(DeploymentArtifactPromotionPolicy.allows(channel, null)).isTrue();
    }
}
