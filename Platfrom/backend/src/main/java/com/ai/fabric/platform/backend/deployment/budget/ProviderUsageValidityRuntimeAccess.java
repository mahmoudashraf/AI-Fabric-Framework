package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

/** Exact target keys, issuer and release endpoint. Never falls back to global signing material. */
@Component
public class ProviderUsageValidityRuntimeAccess {
    private final DeploymentRuntimeAuthorityService authority;
    private final DeploymentReleaseRepository releases;
    private final DeploymentProviderResourceHandleRepository handles;
    private final DeploymentReleaseEndpointService endpoints;
    private final PlatformSecretRepository secrets;
    private final RuntimePrivateAssertionSigningService signer;

    public ProviderUsageValidityRuntimeAccess(DeploymentRuntimeAuthorityService authority, DeploymentReleaseRepository releases,
        DeploymentProviderResourceHandleRepository handles, DeploymentReleaseEndpointService endpoints,
        PlatformSecretRepository secrets, RuntimePrivateAssertionSigningService signer) {
        this.authority = authority; this.releases = releases; this.handles = handles;
        this.endpoints = endpoints; this.secrets = secrets; this.signer = signer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Access resolve(AllocationScope scope, String releaseId, boolean write) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) throw denied("PROVIDER_USAGE_ADMIN_REVIEW_REQUIRED");
        var release = releases.findById(releaseId).orElseThrow(() -> missing("PROVIDER_USAGE_RELEASE_REQUIRED"));
        if (!scope.deploymentId().equals(release.getDeploymentId()) || !scope.targetProfileId().equals(release.getTargetProfileId())
            || !Set.of("FAILED", "PROVISIONING", "VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED").contains(release.getStatus()))
            throw conflict("PROVIDER_USAGE_EXTENSION_RELEASE_CHANGED");
        var snapshot = authority.requireReleaseUnchanged(scope.deploymentId(), scope.targetProfileId(), releaseId,
            release.getRuntimeAuthorityFingerprint());
        var binding = snapshot.binding();
        if (!scope.customerId().equals(binding.customerId()) || !scope.deploymentId().equals(binding.deploymentId())
            || !scope.targetProfileId().equals(binding.targetProfileId())) throw denied("PROVIDER_USAGE_SCOPE_MISMATCH");
        var handle = handles.findById(text(release.getProviderResourceHandleId(), 64))
            .orElseThrow(() -> conflict("PROVIDER_USAGE_EXTENSION_RUNTIME_HANDLE_REQUIRED"));
        if (!scope.deploymentId().equals(handle.getDeploymentId()) || !scope.targetProfileId().equals(handle.getTargetProfileId())
            || !releaseId.equals(handle.getReleaseId()) || !"APPLICATION".equals(handle.getResourceKind())
            || !Set.of("ACTIVE", "CREATED").contains(handle.getStatus()) || handle.getProviderType() != release.getProviderType())
            throw conflict("PROVIDER_USAGE_EXTENSION_RUNTIME_HANDLE_CHANGED");
        String trusted = secret(binding, RuntimeAuthorityPurpose.TRUSTED_BACKEND_API_KEY);
        String signing = secret(binding, RuntimeAuthorityPurpose.PRIVATE_ASSERTION_SIGNING_KEY);
        var claims = new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(principal.actorId(),
            "INTERNAL_PLATFORM_USER", "PLATFORM_PROXY_SESSION", "PLATFORM_PROXY", "budget-validity-" + releaseId,
            binding.deploymentId(), binding.customerId(), binding.tenantId(), binding.privateIssuer(), Instant.now().plusSeconds(120),
            List.of(binding.privateAudience()), write ? List.of("runtime:admin:provider-budget:write") : List.of("runtime:admin:overview"));
        return new Access(endpoints.runtimeBaseUrl(release), snapshot.fingerprint(), Map.of(
            RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, trusted,
            RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER,
            RuntimePrivateAssertionSigningService.TOKEN_SCHEME + " " + signer.issueAssertionWithSigningKey(claims, signing)));
    }

    private String secret(RuntimeAuthorityBinding binding, RuntimeAuthorityPurpose purpose) {
        var revision = binding.secrets().get(purpose);
        if (revision == null) throw conflict("PROVIDER_USAGE_EXTENSION_AUTHORITY_REQUIRED");
        // requireReleaseUnchanged has just verified ownership and byte integrity in this same persistence context.
        var secret = secrets.findById(revision.reference()).orElseThrow(() -> conflict("PROVIDER_USAGE_EXTENSION_AUTHORITY_REQUIRED"));
        if (secret.getUpdatedAt() == null || !revision.revision().equals(secret.getUpdatedAt().toString())
            || !binding.deploymentId().equals(secret.getDeploymentId()) || secret.getSecretValue() == null || secret.getSecretValue().isBlank())
            throw conflict("PROVIDER_USAGE_EXTENSION_AUTHORITY_CHANGED");
        return secret.getSecretValue();
    }

    public record Access(String baseUrl, String authorityFingerprint, Map<String, String> headers) {
        @Override public String toString() { return "Access[authorityFingerprint=" + authorityFingerprint + ", headers=REDACTED]"; }
    }
}
