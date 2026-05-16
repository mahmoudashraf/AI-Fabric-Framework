package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSourcePreflightSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSyncSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;

import java.util.ArrayList;
import java.util.List;

final class ShopifyStorefrontInteractionReadinessSupport {

    private ShopifyStorefrontInteractionReadinessSupport() {
    }

    static boolean isReady(ShopifyBridgeStoreSummary store) {
        ShopifyBridgeStoreReadinessSummary readiness = store == null ? null : store.readiness();
        if (readiness != null && readiness.storefrontReady()) {
            return true;
        }
        return derivedBaseStorefrontReady(store);
    }

    static List<String> blockingReasons(ShopifyBridgeStoreSummary store) {
        if (isReady(store)) {
            return List.of();
        }
        ShopifyBridgeStoreReadinessSummary readiness = store == null ? null : store.readiness();
        if (readiness != null
            && readiness.storefrontBlockingReasons() != null
            && !readiness.storefrontBlockingReasons().isEmpty()) {
            return List.copyOf(readiness.storefrontBlockingReasons());
        }
        return List.copyOf(derivedBaseStorefrontBlockingReasons(store));
    }

    static String firstBlockingReason(ShopifyBridgeStoreSummary store, String fallback) {
        List<String> reasons = blockingReasons(store);
        return reasons.isEmpty() ? fallback : reasons.getFirst();
    }

    private static boolean derivedBaseStorefrontReady(ShopifyBridgeStoreSummary store) {
        if (store == null) {
            return false;
        }
        boolean installed = equalsIgnoreCase(store.installStatus(), "INSTALLED");
        boolean credentialsReady = credentialsReady(store.credentials());
        boolean mappingReady = hasText(store.customerId())
            && hasText(store.deploymentId())
            && hasText(store.consumerId());
        boolean deploymentArchived = equalsIgnoreCase(store.deploymentStatus(), "ARCHIVED");
        boolean sourceReady = equalsIgnoreCase(store.sourceReadinessStatus(), "READY");
        boolean latestReleaseVerified = latestReleaseVerified(store.latestRelease());
        boolean widgetFailed = widgetFailed(store.widgetStatus(), store.widgetDetail());
        return installed
            && credentialsReady
            && mappingReady
            && !deploymentArchived
            && sourceReady
            && latestReleaseVerified
            && !widgetFailed;
    }

    private static List<String> derivedBaseStorefrontBlockingReasons(ShopifyBridgeStoreSummary store) {
        if (store == null) {
            return List.of("Store assistant is not live yet.");
        }

        List<String> blockers = new ArrayList<>();
        if (!equalsIgnoreCase(store.installStatus(), "INSTALLED")) {
            blockers.add("Shopify app installation is not active for this store.");
        }

        if (!credentialsReady(store.credentials())) {
            blockers.add("Shopify access credentials are missing or not ready.");
        }

        boolean mappingReady = hasText(store.customerId())
            && hasText(store.deploymentId())
            && hasText(store.consumerId());
        if (!mappingReady) {
            blockers.add("Platform bootstrap is incomplete. Customer, deployment, or consumer binding is missing.");
        }

        if (equalsIgnoreCase(store.deploymentStatus(), "ARCHIVED")) {
            blockers.add("The bound deployment is archived and cannot serve the Shopify storefront safely.");
        }

        if (!equalsIgnoreCase(store.sourceReadinessStatus(), "READY")) {
            ShopifyBridgeStoreSourcePreflightSummary sourcePreflight = store.sourcePreflight();
            blockers.add(sourcePreflight != null && equalsIgnoreCase(sourcePreflight.overallStatus(), "FAILED")
                ? "Shopify source preflight has failed for one or more enabled source categories."
                : "Shopify source readiness is not READY yet.");
        }

        ShopifyBridgeStoreDeploymentReleaseSummary latestRelease = store.latestRelease();
        boolean releaseInProgress = latestRelease != null && isReleaseInProgress(latestRelease.status());
        if (releaseInProgress) {
            blockers.add("Publish, apply, and verification are still running for the latest release.");
        }
        if (!latestReleaseVerified(latestRelease)) {
            blockers.add(latestRelease == null
                ? "No verified deployment release exists yet."
                : "The latest deployment release is not verified successfully yet.");
        }

        if (store.syncDetail() != null && equalsIgnoreCase(store.syncDetail().status(), "FAILED")) {
            ShopifyBridgeStoreSyncSummary syncDetail = store.syncDetail();
            blockers.add(syncDetail != null && syncDetail.message() != null && !syncDetail.message().isBlank()
                ? syncDetail.message()
                : "Apply-time Shopify derived index verification has failed.");
        }

        if (widgetFailed(store.widgetStatus(), store.widgetDetail())) {
            blockers.add("Storefront widget activation has failed.");
        }

        return blockers;
    }

    private static boolean credentialsReady(ShopifyBridgeStoreCredentialSummary credentials) {
        return credentials != null
            && equalsIgnoreCase(credentials.status(), "READY")
            && credentials.accessTokenPresent();
    }

    private static boolean latestReleaseVerified(ShopifyBridgeStoreDeploymentReleaseSummary latestRelease) {
        return latestRelease != null
            && equalsIgnoreCase(latestRelease.status(), "APPLIED_VERIFIED")
            && equalsIgnoreCase(latestRelease.verificationStatus(), "PASSED");
    }

    private static boolean widgetFailed(String widgetStatus, ShopifyBridgeStoreWidgetSummary widgetDetail) {
        return equalsIgnoreCase(widgetStatus, "FAILED")
            || (widgetDetail != null && equalsIgnoreCase(widgetDetail.status(), "FAILED"));
    }

    private static boolean isReleaseInProgress(String status) {
        return equalsIgnoreCase(status, "APPLY_REQUESTED")
            || equalsIgnoreCase(status, "PRE_APPLY_VERIFYING")
            || equalsIgnoreCase(status, "PROVISIONING")
            || equalsIgnoreCase(status, "VERIFYING");
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
