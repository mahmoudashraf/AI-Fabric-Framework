package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.authority.*;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.secret.entity.*;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderUsageValidityRuntimeAccessTest {
    final DeploymentRuntimeAuthorityService authority = mock(DeploymentRuntimeAuthorityService.class);
    final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    final PlatformSecretRepository secrets = mock(PlatformSecretRepository.class);
    final RuntimePrivateAssertionSigningService signer = mock(RuntimePrivateAssertionSigningService.class);
    final ProviderUsageValidityRuntimeAccess adapter = new ProviderUsageValidityRuntimeAccess(authority, releases, handles,
        new DeploymentReleaseEndpointService(new ObjectMapper()), secrets, signer);
    final AllocationScope scope = new AllocationScope("approval", "customer", "deployment", "target", "staging");
    final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    final DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
    final Instant revision = Instant.parse("2026-10-20T12:00:00Z");
    RuntimeAuthorityBinding binding;

    @BeforeEach void setup() {
        ProviderUsageAllowanceContract.authenticate(PlatformRole.PLATFORM_ADMIN);
        release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId("target");
        release.setStatus("FAILED"); release.setRuntimeAuthorityFingerprint("a".repeat(64)); release.setProviderType(DeploymentProviderType.COOLIFY);
        release.setProviderResourceHandleId("handle"); release.setProvisioningDetailsJson("{\"runtimeBaseUrl\":\"https://exact.synthetic.invalid\"}");
        handle.setId("handle"); handle.setDeploymentId("deployment"); handle.setTargetProfileId("target"); handle.setReleaseId("release");
        handle.setResourceKind("APPLICATION"); handle.setStatus("ACTIVE"); handle.setProviderType(DeploymentProviderType.COOLIFY);
        binding = new RuntimeAuthorityBinding("customer", "tenant", "deployment", "target", "version", "config", 7,
            "exact-issuer", "exact-audience", "public-issuer", "public-audience", false, false, List.of(), Map.of(
                RuntimeAuthorityPurpose.TRUSTED_BACKEND_API_KEY, new RuntimeAuthorityBinding.SecretRevision("target-trusted", revision.toString()),
                RuntimeAuthorityPurpose.PRIVATE_ASSERTION_SIGNING_KEY, new RuntimeAuthorityBinding.SecretRevision("target-signing", revision.toString())));
        when(releases.findById("release")).thenReturn(Optional.of(release)); when(handles.findById("handle")).thenReturn(Optional.of(handle));
        when(authority.requireReleaseUnchanged("deployment", "target", "release", release.getRuntimeAuthorityFingerprint()))
            .thenAnswer(call -> new RuntimeAuthorityBinding.Snapshot("snapshot", "release", release.getRuntimeAuthorityFingerprint(), binding));
        for (String id : List.of("target-trusted", "target-signing")) {
            var secret = new PlatformSecretEntity(); secret.setName(id); secret.setDeploymentId("deployment"); secret.setUpdatedAt(revision);
            secret.setManagedByPlatform(true); secret.setScopeType(PlatformSecretScopeType.DEPLOYMENT_MANAGED);
            secret.setOwnerType(PlatformSecretOwnerType.PLATFORM_MANAGED); secret.setSecretValue("synthetic-" + id);
            when(secrets.findById(id)).thenReturn(Optional.of(secret));
        }
        when(signer.issueAssertionWithSigningKey(any(), eq("synthetic-target-signing"))).thenReturn("synthetic-signed-assertion");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void failedReleaseUsesExactFrozenTargetKeysIssuerAudienceAndMinimalStaffScopes() {
        var write = adapter.resolve(scope, "release", true);
        assertThat(write.baseUrl()).isEqualTo("https://exact.synthetic.invalid");
        assertThat(write.headers()).containsEntry(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, "synthetic-target-trusted");
        var claims = ArgumentCaptor.forClass(RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims.class);
        verify(signer).issueAssertionWithSigningKey(claims.capture(), eq("synthetic-target-signing"));
        assertThat(claims.getValue().issuer()).isEqualTo("exact-issuer"); assertThat(claims.getValue().audiences()).containsExactly("exact-audience");
        assertThat(claims.getValue().grantedScopes()).containsExactly("runtime:admin:provider-budget:write");
        assertThat(claims.getValue().authMode()).isEqualTo("PLATFORM_PROXY_SESSION");
        clearInvocations(signer); adapter.resolve(scope, "release", false);
        verify(signer).issueAssertionWithSigningKey(claims.capture(), anyString());
        assertThat(claims.getValue().grantedScopes()).containsExactly("runtime:admin:overview");
        assertThat(write.toString()).doesNotContain("synthetic-target-trusted", "synthetic-signed-assertion");
    }

    @Test void changedOwnerHandleOrAuthorityNeverSignsAndNeverUsesGlobalFallback() {
        assertThatThrownBy(() -> adapter.resolve(new AllocationScope("approval", "foreign", "deployment", "target", "staging"), "release", true))
            .hasMessageContaining("SCOPE_MISMATCH");
        handle.setReleaseId("other"); assertThatThrownBy(() -> adapter.resolve(scope, "release", true)).hasMessageContaining("HANDLE_CHANGED");
        handle.setReleaseId("release"); handle.setStatus("DELETED");
        assertThatThrownBy(() -> adapter.resolve(scope, "release", true)).hasMessageContaining("HANDLE_CHANGED");
        handle.setStatus("ACTIVE"); when(authority.requireReleaseUnchanged(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("synthetic frozen-authority drift"));
        assertThatThrownBy(() -> adapter.resolve(scope, "release", true)).hasMessageContaining("frozen-authority drift");
        verifyNoInteractions(signer);
    }

    @Test void replacedExactSecretRevisionIsDeniedRatherThanResolvedByEnvironment() {
        secrets.findById("target-signing").orElseThrow().setUpdatedAt(revision.plusSeconds(1));
        assertThatThrownBy(() -> adapter.resolve(scope, "release", true)).hasMessageContaining("AUTHORITY_CHANGED");
        verifyNoInteractions(signer);
    }
}
