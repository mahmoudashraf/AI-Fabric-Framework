package com.ai.fabric.platform.backend.deployment.budget;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderUsageRuntimeAllocationIdentityTest {
    private static final String APPROVAL = "pua-fixture";
    private static final AllocationScope SCOPE = new AllocationScope(APPROVAL, "customer", "deployment", "target", "staging");
    private static final Limits CAPS = new Limits(50, 100_000, 2_000_000_000L);
    private static final Instant EXPIRY = Instant.parse("2030-01-01T00:00:00.123456789Z");
    private static final String CREDENTIAL = "a".repeat(64);
    private static final String RATE = "fixture-rate-card";
    private static final String GOLDEN_HASH = "bb50b722cd30aeb4de14d10a88696ed733468ce8c2ee23e9b5a014d9e4c25999";

    @Test void matchesExactRuntimeWireGoldenIncludingCredentialPrefixAndNanosecondExpiry() {
        String json = "{\"approvalId\":\"pua-fixture\",\"expiresAt\":{\"epochSecond\":1893456000,\"nano\":123456789},"
            + "\"limits\":{\"chatAttempts\":50,\"costNanoUsd\":2000000000,\"totalTokens\":100000},"
            + "\"schemaVersion\":\"runtime-provider-budget-allocation-v1\",\"scope\":{\"credentialIdentity\":\"sha256:"
            + CREDENTIAL + "\",\"customerId\":\"customer\",\"deploymentId\":\"deployment\",\"environment\":\"staging\","
            + "\"rateCardReference\":\"fixture-rate-card\",\"targetProfileId\":\"target\"}}";
        assertThat(ProviderUsageAllowanceCodec.sha256(json)).isEqualTo(GOLDEN_HASH);
        assertThat(hash(APPROVAL, SCOPE, CAPS, EXPIRY, CREDENTIAL, RATE)).isEqualTo(GOLDEN_HASH);
    }

    @Test void everyRuntimeScopeLimitCredentialPriceAndExpiryFieldBindsTheHash() {
        for (AllocationScope scope : List.of(new AllocationScope(APPROVAL, "other", "deployment", "target", "staging"),
            new AllocationScope(APPROVAL, "customer", "other", "target", "staging"),
            new AllocationScope(APPROVAL, "customer", "deployment", "other", "staging"),
            new AllocationScope(APPROVAL, "customer", "deployment", "target", "production")))
            assertThat(hash(APPROVAL, scope, CAPS, EXPIRY, CREDENTIAL, RATE)).isNotEqualTo(GOLDEN_HASH);
        for (Limits caps : List.of(new Limits(51, 100_000, 2_000_000_000L), new Limits(50, 100_001, 2_000_000_000L),
            new Limits(50, 100_000, 2_000_000_001L)))
            assertThat(hash(APPROVAL, SCOPE, caps, EXPIRY, CREDENTIAL, RATE)).isNotEqualTo(GOLDEN_HASH);
        assertThat(hash("other", SCOPE, CAPS, EXPIRY, CREDENTIAL, RATE)).isNotEqualTo(GOLDEN_HASH);
        assertThat(hash(APPROVAL, SCOPE, CAPS, EXPIRY.plusSeconds(1), CREDENTIAL, RATE)).isNotEqualTo(GOLDEN_HASH);
        assertThat(hash(APPROVAL, SCOPE, CAPS, EXPIRY.plusNanos(1), CREDENTIAL, RATE)).isNotEqualTo(GOLDEN_HASH);
        assertThat(hash(APPROVAL, SCOPE, CAPS, EXPIRY, "b".repeat(64), RATE)).isNotEqualTo(GOLDEN_HASH);
        assertThat(hash(APPROVAL, SCOPE, CAPS, EXPIRY, CREDENTIAL, "other")).isNotEqualTo(GOLDEN_HASH);
    }

    @Test void cpOnlyReviewTermsDoNotChangeRuntimeIdentityButRemainSeparateAuditTerms() {
        var first = new AllocationTerms(APPROVAL, "grant", SCOPE, CAPS, EXPIRY, RATE,
            new ProviderBinding("provider", List.of("model"), "secret-purpose", "secret-ref"), CREDENTIAL,
            new ReviewedR7Context("reservation", "generation", "version"));
        var other = new AllocationTerms(APPROVAL, "other-grant", SCOPE, CAPS, EXPIRY, RATE,
            new ProviderBinding("provider", List.of("other-model"), "other-purpose", "other-reference"), CREDENTIAL,
            new ReviewedR7Context("other-reservation", "other-generation", "other-version"));
        assertThat(ProviderUsageRuntimeAllocationIdentity.fingerprint(first)).isEqualTo(GOLDEN_HASH);
        assertThat(ProviderUsageRuntimeAllocationIdentity.fingerprint(other)).isEqualTo(GOLDEN_HASH);
    }

    private static String hash(String approval, AllocationScope scope, Limits caps, Instant expiry, String credential, String rate) {
        return ProviderUsageRuntimeAllocationIdentity.fingerprint(approval, scope, caps, expiry, credential, rate);
    }
}
