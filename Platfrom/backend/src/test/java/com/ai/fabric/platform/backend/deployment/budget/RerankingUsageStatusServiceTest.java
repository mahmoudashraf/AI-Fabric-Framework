package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageRuntimeClient.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RerankingUsageStatusServiceTest {
    final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    final ProviderUsageRuntimeArtifactGate artifacts = mock(ProviderUsageRuntimeArtifactGate.class);
    final ProviderUsageRuntimeClient runtime = mock(ProviderUsageRuntimeClient.class);
    final RerankingUsageStatusService service = new RerankingUsageStatusService(targets, authority, artifacts, runtime, new ObjectMapper());
    final DeploymentVersionEntity version = new DeploymentVersionEntity();
    final Instant expiry = Instant.now().plusSeconds(3600);
    final Limits limits = new Limits(0, 100_000, 100_000_000);

    RerankingUsageStatusServiceTest() {
        var principal = new PlatformPrincipal("owner", PlatformRole.CUSTOMER_ADMIN, "Owner", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
        var deployment = new DeploymentEntity(); deployment.setId("brain"); deployment.setCustomerId("customer");
        var target = new DeploymentTargetProfileEntity(); target.setId("staging-target"); target.setEnvironmentName("staging");
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setRuntimeAuthorityFingerprint("fingerprint");
        version.setProviderConfigJson("{\"knowledgeRetrieval\":{\"mode\":\"DENSE\",\"reranking\":{\"enabled\":true}}}");
        when(targets.resolveCurrent("brain", "staging-target", false)).thenReturn(new DeploymentTargetContextService.Context(
            deployment, target, release, version, "https://exact-target.example", "https://connector.example"));
        when(authority.issuePlatformProxyHeaders(eq("brain"), eq("staging-target"), eq("release"), eq("fingerprint"), anyList(), any()))
            .thenReturn(Map.of("X-Private", "private-test-header"));
        when(runtime.read(eq("https://exact-target.example"), anyMap(), eq(ProviderUsagePurpose.RERANKING)))
            .thenReturn(readback("customer", "staging-target", expiry, new Usage(20, 0, 400), false));
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void readsOnlyTheAuthorizedExactTargetAndDoesNotInventUsage() throws Exception {
        var status = service.read("brain", "staging-target");
        assertThat(status.status()).isEqualTo("ACTIVE");
        assertThat(status.providerAttempts()).isEqualTo(1);
        assertThat(status.accountedUsage().costNanoUsd()).isEqualTo(400);
        assertThat(status.releaseId()).isEqualTo("release");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(status))
            .doesNotContain("private-test-header", "fingerprint", "credential");
        verify(runtime).read(eq("https://exact-target.example"), anyMap(), eq(ProviderUsagePurpose.RERANKING));
        verifyNoMoreInteractions(runtime);
    }

    @Test void offReadsTheAppliedVersionWithoutAnyRuntimeCall() {
        version.setProviderConfigJson("{}");
        var status = service.read("brain", "staging-target");
        assertThat(status.status()).isEqualTo("OFF");
        assertThat(status.accountedUsage()).isNull();
        assertThat(status.limits()).isNull();
        verifyNoInteractions(runtime, authority, artifacts);
    }

    @Test void anonymousAndUnauthorizedTargetsStopBeforeThePrivateRequest() {
        when(targets.resolveCurrent("brain", "staging-target", false)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.read("brain", "staging-target")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(runtime, authority, artifacts);
        SecurityContextHolder.clearContext();
        clearInvocations(targets);
        assertThatThrownBy(() -> service.read("brain", "staging-target")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(targets);
    }

    @Test void mismatchedCustomerOrTargetReadbackIsNeverDisplayed() {
        for (var pair : java.util.List.of(java.util.List.of("foreign", "staging-target"), java.util.List.of("customer", "production-target"))) {
            when(runtime.read(anyString(), anyMap(), eq(ProviderUsagePurpose.RERANKING)))
                .thenReturn(readback(pair.getFirst(), pair.getLast(), expiry, new Usage(0, 0, 0), false));
            assertThatThrownBy(() -> service.read("brain", "staging-target")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match");
        }
    }

    @Test void expiredStoppedAndExhaustedAreNotPresentedAsActive() {
        when(runtime.read(anyString(), anyMap(), eq(ProviderUsagePurpose.RERANKING)))
            .thenReturn(readback("customer", "staging-target", Instant.now().minusSeconds(1), new Usage(0, 0, 0), false));
        assertThat(service.read("brain", "staging-target").status()).isEqualTo("EXPIRED");
        when(runtime.read(anyString(), anyMap(), eq(ProviderUsagePurpose.RERANKING)))
            .thenReturn(readback("customer", "staging-target", expiry, new Usage(0, 0, 0), true));
        assertThat(service.read("brain", "staging-target").status()).isEqualTo("STOPPED");
        when(runtime.read(anyString(), anyMap(), eq(ProviderUsagePurpose.RERANKING)))
            .thenReturn(readback("customer", "staging-target", expiry, new Usage(0, 0, limits.costNanoUsd()), false));
        assertThat(service.read("brain", "staging-target").status()).isEqualTo("EXHAUSTED");
    }

    private Readback readback(String customer, String target, Instant until, Usage usage, boolean stopped) {
        return new Readback("ACTIVE", java.util.UUID.randomUUID(), "approval", "runtime-fingerprint", customer,
            "brain", target, "staging", ProviderUsageRuntimeProfile.RERANKING_RATE_CARD, limits, until, 0L, 1L, usage, stopped);
    }
}
