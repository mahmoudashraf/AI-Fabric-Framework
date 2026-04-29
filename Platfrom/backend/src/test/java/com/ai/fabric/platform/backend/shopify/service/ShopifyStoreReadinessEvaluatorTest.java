package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreCredentialSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreReadinessSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShopifyStoreReadinessEvaluatorTest {

    @Test
    void evaluateBlocksGoLiveWhenBoundDeploymentIsArchived() {
        ShopifyStoreReadinessEvaluator evaluator = new ShopifyStoreReadinessEvaluator();

        ShopifyStoreConnectionEntity store = new ShopifyStoreConnectionEntity();
        store.setInstallStatus("INSTALLED");
        store.setCustomerId("cust-1");
        store.setDeploymentId("dep-1");
        store.setConsumerId("consumer-1");
        store.setSourceReadinessStatus("READY");
        store.setSyncStatus("SYNCED");
        store.setWidgetStatus("ENABLED");
        store.setOnboardingStatus("LIVE");

        ShopifyStoreCredentialSummary credentials = new ShopifyStoreCredentialSummary(
            "READY",
            true,
            true,
            "ACCESS_SECRET",
            "REFRESH_SECRET",
            Instant.parse("2026-04-22T10:00:00Z"),
            Instant.parse("2026-04-22T11:00:00Z"),
            Instant.parse("2026-07-22T10:00:00Z"),
            "read_products",
            true
        );

        DeploymentReleaseSummary latestRelease = new DeploymentReleaseSummary(
            "rel-1",
            "dep-1",
            "ver-1",
            "APPLIED_VERIFIED",
            "PASSED",
            "SUCCEEDED",
            "RAILWAY",
            "verify_release",
            "Verified.",
            null,
            null,
            null,
            Instant.parse("2026-04-22T10:05:00Z"),
            Instant.parse("2026-04-22T10:10:00Z"),
            Instant.parse("2026-04-22T10:11:00Z")
        );

        ShopifyStoreReadinessSummary readiness = evaluator.evaluate(
            store,
            credentials,
            null,
            null,
            null,
            latestRelease,
            true
        );

        assertThat(readiness.goLiveEligible()).isFalse();
        assertThat(readiness.storefrontReady()).isFalse();
        assertThat(readiness.goLiveBlockingReasons())
            .contains("The bound deployment is archived and cannot be reused for Shopify Companion go-live.");
        assertThat(readiness.nextActions())
            .contains("Run platform bootstrap again to create a fresh deployment and rebind the storefront consumer.");
        assertThat(readiness.storefrontBlockingReasons())
            .contains("The bound deployment is archived and cannot serve the Shopify storefront safely.");
    }
}
