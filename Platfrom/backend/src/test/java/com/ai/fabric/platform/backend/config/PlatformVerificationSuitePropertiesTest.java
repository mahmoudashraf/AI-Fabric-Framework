package com.ai.fabric.platform.backend.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformVerificationSuitePropertiesTest {

    @Test
    void doesNotApplyLiveTargetDefaultsInJava() {
        PlatformVerificationSuiteProperties properties = new PlatformVerificationSuiteProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            0,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        assertThat(properties.timeout()).isEqualTo(Duration.ofMinutes(180));
        assertThat(properties.hostedStageTimeout()).isEqualTo(Duration.ofMinutes(12));
        assertThat(properties.scriptStageTimeout()).isEqualTo(Duration.ofMinutes(20));
        assertThat(properties.releaseGateFreshness()).isEqualTo(Duration.ofHours(12));
        assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.maxRecentRuns()).isEqualTo(20);
        assertThat(properties.maxStageLogCharacters()).isEqualTo(12_000);
        assertThat(properties.codeRegressionMaxLogCharacters()).isEqualTo(80_000);

        assertThat(properties.platformUiBaseUrl()).isNull();
        assertThat(properties.weaviateHost()).isNull();
        assertThat(properties.shopifyBridgeBaseUrl()).isNull();
        assertThat(properties.shopifyShopDomain()).isNull();
        assertThat(properties.shopifyProductServiceRef()).isNull();
        assertThat(properties.shopifyEmbeddedHost()).isNull();
        assertThat(properties.partnerUiBaseUrl()).isNull();
    }

    @Test
    void normalizesConfiguredVerificationTargets() {
        PlatformVerificationSuiteProperties properties = new PlatformVerificationSuiteProperties(
            Duration.ofMinutes(10),
            Duration.ofMinutes(11),
            Duration.ofMinutes(12),
            Duration.ofMinutes(13),
            Duration.ofHours(1),
            Duration.ofSeconds(5),
            7,
            8_000,
            9_000,
            " https://platform-ui.example.test/ ",
            " weaviate.example.test ",
            " https://bridge.example.test/ ",
            " shop.example.test ",
            " shopify-bridge-prod ",
            " embedded-host ",
            " https://partner-ui.example.test/ "
        );

        assertThat(properties.platformUiBaseUrl()).isEqualTo("https://platform-ui.example.test");
        assertThat(properties.weaviateHost()).isEqualTo("weaviate.example.test");
        assertThat(properties.shopifyBridgeBaseUrl()).isEqualTo("https://bridge.example.test");
        assertThat(properties.shopifyShopDomain()).isEqualTo("shop.example.test");
        assertThat(properties.shopifyProductServiceRef()).isEqualTo("shopify-bridge-prod");
        assertThat(properties.shopifyEmbeddedHost()).isEqualTo("embedded-host");
        assertThat(properties.partnerUiBaseUrl()).isEqualTo("https://partner-ui.example.test");
    }
}
