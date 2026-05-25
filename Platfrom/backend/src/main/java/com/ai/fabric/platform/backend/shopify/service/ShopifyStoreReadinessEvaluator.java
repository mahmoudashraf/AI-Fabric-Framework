package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreCredentialSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreReadinessSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSyncSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWidgetSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class ShopifyStoreReadinessEvaluator {

    public ShopifyStoreReadinessSummary evaluate(ShopifyStoreConnectionEntity store,
                                                 ShopifyStoreCredentialSummary credentials,
                                                 ShopifyStoreSourcePreflightSummary sourcePreflight,
                                                 ShopifyStoreSyncSummary syncDetail,
                                                 ShopifyStoreWidgetSummary widgetDetail,
                                                 DeploymentReleaseSummary latestRelease,
                                                 boolean deploymentArchived) {
        List<String> goLiveBlockers = new ArrayList<>();
        List<String> storefrontBlockers = new ArrayList<>();
        Set<String> nextActions = new LinkedHashSet<>();

        boolean installed = equalsIgnoreCase(store.getInstallStatus(), "INSTALLED");
        if (!installed) {
            goLiveBlockers.add("Shopify app installation is not active for this store.");
            storefrontBlockers.add("Shopify app installation is not active for this store.");
            nextActions.add("Reconnect or reinstall the Shopify app before continuing.");
        }

        boolean credentialsReady = credentials != null
            && equalsIgnoreCase(credentials.status(), "READY")
            && credentials.accessTokenPresent();
        if (!credentialsReady) {
            goLiveBlockers.add("Shopify access credentials are missing or not ready.");
            storefrontBlockers.add("Shopify access credentials are missing or not ready.");
            nextActions.add("Reconnect the store to persist fresh Shopify access credentials.");
        }

        boolean mappingReady = hasText(store.getCustomerId()) && hasText(store.getDeploymentId()) && hasText(store.getConsumerId());
        if (!mappingReady) {
            goLiveBlockers.add("Platform bootstrap is incomplete. Customer, deployment, or consumer binding is missing.");
            storefrontBlockers.add("Platform bootstrap is incomplete. Customer, deployment, or consumer binding is missing.");
            nextActions.add("Run platform bootstrap to create and bind the customer, deployment, and consumer.");
        }

        if (deploymentArchived) {
            goLiveBlockers.add("The bound deployment is archived and cannot be reused for Shopify Companion go-live.");
            storefrontBlockers.add("The bound deployment is archived and cannot serve the Shopify storefront safely.");
            nextActions.add("Run platform bootstrap again to create a fresh deployment and rebind the storefront consumer.");
        }

        boolean sourceReady = equalsIgnoreCase(store.getSourceReadinessStatus(), "READY");
        if (!sourceReady) {
            String message = sourcePreflight != null && equalsIgnoreCase(sourcePreflight.overallStatus(), "FAILED")
                ? "Shopify source preflight has failed for one or more enabled source categories."
                : "Shopify source readiness is not READY yet.";
            goLiveBlockers.add(message);
            storefrontBlockers.add(message);
            nextActions.add("Run source preflight and resolve any blocked Shopify source categories.");
        }

        boolean onboardingBlocked = equalsIgnoreCase(store.getOnboardingStatus(), "BLOCKED");
        if (onboardingBlocked) {
            goLiveBlockers.add("Store onboarding is currently blocked.");
            storefrontBlockers.add("Store onboarding is currently blocked.");
            nextActions.add("Resolve the current onboarding blocker before requesting go-live again.");
        }

        boolean releaseInProgress = latestRelease != null && isReleaseInProgress(latestRelease.status());
        if (releaseInProgress) {
            goLiveBlockers.add("Publish, apply, and verification are still running for the latest release.");
            storefrontBlockers.add("Publish, apply, and verification are still running for the latest release.");
            nextActions.add("Wait for the current publish/apply/verify run to finish.");
        }

        boolean latestReleaseVerified = latestRelease != null
            && equalsIgnoreCase(latestRelease.status(), "APPLIED_VERIFIED")
            && equalsIgnoreCase(latestRelease.verificationStatus(), "PASSED");
        if (!latestReleaseVerified) {
            storefrontBlockers.add(latestRelease == null
                ? "No verified deployment release exists yet."
                : "The latest deployment release is not verified successfully yet.");
            if (latestRelease == null) {
                nextActions.add("Request go-live to publish, apply, and verify the deployment.");
            } else if (!releaseInProgress) {
                nextActions.add("Inspect the latest deployment release failure and rerun go-live after fixing the deployment.");
            }
        }

        boolean syncFailed = syncDetail != null && equalsIgnoreCase(syncDetail.status(), "FAILED");
        if (syncFailed) {
            storefrontBlockers.add("Apply-time Shopify derived index verification has failed.");
            nextActions.add("Inspect indexing diagnostics and reindex after fixing the failure.");
        }

        boolean widgetFailed = equalsIgnoreCase(store.getWidgetStatus(), "FAILED")
            || (widgetDetail != null && equalsIgnoreCase(widgetDetail.status(), "FAILED"));
        if (widgetFailed) {
            storefrontBlockers.add("Storefront widget activation has failed.");
            nextActions.add("Fix the storefront widget configuration before enabling shopper traffic.");
        }

        boolean goLiveEligible = goLiveBlockers.isEmpty();
        boolean storefrontReady = installed
            && credentialsReady
            && mappingReady
            && !deploymentArchived
            && sourceReady
            && latestReleaseVerified
            && !syncFailed
            && !widgetFailed;

        if (storefrontReady && !equalsIgnoreCase(store.getWidgetStatus(), "ENABLED")) {
            nextActions.add("Enable the Shopify theme app extension and load the storefront once to finish widget activation.");
        }

        String overallStatus;
        if (storefrontReady) {
            overallStatus = "STOREFRONT_READY";
        } else if (releaseInProgress) {
            overallStatus = "GO_LIVE_IN_PROGRESS";
        } else if (goLiveEligible) {
            overallStatus = "READY_FOR_GO_LIVE";
        } else {
            overallStatus = "BLOCKED";
        }

        return new ShopifyStoreReadinessSummary(
            overallStatus,
            goLiveEligible,
            storefrontReady,
            List.copyOf(goLiveBlockers),
            List.copyOf(storefrontBlockers),
            List.copyOf(nextActions)
        );
    }

    private boolean isReleaseInProgress(String status) {
        return equalsIgnoreCase(status, "APPLY_REQUESTED")
            || equalsIgnoreCase(status, "PRE_APPLY_VERIFYING")
            || equalsIgnoreCase(status, "PROVISIONING")
            || equalsIgnoreCase(status, "VERIFYING");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
