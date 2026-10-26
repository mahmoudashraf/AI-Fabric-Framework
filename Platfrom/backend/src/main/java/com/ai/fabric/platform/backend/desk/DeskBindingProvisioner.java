package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Target-local Desk registration, shared by explicit activation and normal release provisioning. */
@Service
public class DeskBindingProvisioner {
    private final DeskBindingRepository bindings;
    private final PlatformSecretService secrets;
    private final DeskServiceClient desk;
    private final PlatformUserRepository users;
    private final PlatformCustomerIdentityLinkRepository identities;
    private final Environment environment;
    private final ObjectMapper mapper;

    public DeskBindingProvisioner(DeskBindingRepository bindings, PlatformSecretService secrets, DeskServiceClient desk,
            PlatformUserRepository users, PlatformCustomerIdentityLinkRepository identities, Environment environment, ObjectMapper mapper) {
        this.bindings = bindings; this.secrets = secrets; this.desk = desk; this.users = users;
        this.identities = identities; this.environment = environment; this.mapper = mapper;
    }

    /** Read-only projection: names are deterministic, but no credentials or registrations are created. */
    public static DeskBindingEntity projection(DeploymentEntity deployment, String targetId) {
        var binding = new DeskBindingEntity();
        binding.setId(bindingId(deployment.getId(), targetId));
        binding.setCustomerId(deployment.getCustomerId()); binding.setTenantId(deployment.getTenantId());
        binding.setDeploymentId(deployment.getId()); binding.setTargetProfileId(targetId);
        binding.setProviderKeyRef(secretName(binding.getId(), "PROVIDER"));
        binding.setCallbackKeyRef(secretName(binding.getId(), "CALLBACK"));
        binding.setRuntimeCallbackKeyRef(secretName(binding.getId(), "RUNTIME_CALLBACK"));
        binding.setBridgeKeyRef(secretName(binding.getId(), "REVIEW_BRIDGE"));
        return binding;
    }

    @Transactional
    public DeskBindingEntity prepareBinding(DeploymentEntity deployment, DeploymentTargetProfileEntity target,
                                            String sourceReleaseId, String operationKey) {
        if (!target.isActive()) throw new ResponseStatusException(CONFLICT, "Desk target is unavailable.");
        desk.connection(target.getEnvironmentName());
        var existing = bindings.findByDeploymentIdAndTargetProfileId(deployment.getId(), target.getId());
        var binding = existing.orElseGet(() -> projection(deployment, target.getId()));
        requireOwnership(binding, deployment, target);
        if (existing.isEmpty()) {
            binding.setSourceReleaseId(sourceReleaseId); binding.setOperationKey(operationKey);
            binding.setCreatedAt(Instant.now());
            store(binding, "PROVIDER", binding.getProviderKeyRef());
            store(binding, "CALLBACK", binding.getCallbackKeyRef());
            store(binding, "RUNTIME_CALLBACK", binding.getRuntimeCallbackKeyRef());
            store(binding, "REVIEW_BRIDGE", binding.getBridgeKeyRef());
        }
        binding.setStatus("PREPARING"); binding.setErrorMessage(null); binding.setUpdatedAt(Instant.now());
        return bindings.saveAndFlush(binding);
    }

    @Transactional
    public void prepareForApply(DeploymentEntity deployment, DeploymentVersionEntity version,
                                DeploymentReleaseEntity release, DeploymentTargetProfileEntity target) {
        try {
            var behavior = mapper.readTree(version.getBehaviorConfigJson());
            if (!DeskTargetConfigurationResolver.usesDesk(behavior)) return;
            if (!DeskTargetConfigurationResolver.isTargetIndependent(behavior))
                throw new ResponseStatusException(CONFLICT, "Desk requires target-independent published configuration.");
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new ResponseStatusException(CONFLICT, "Desk configuration is unavailable.");
        }
        if (!Objects.equals(deployment.getId(), version.getDeploymentId())
            || !Objects.equals(deployment.getId(), release.getDeploymentId())
            || !Objects.equals(version.getId(), release.getDeploymentVersionId())
            || !Objects.equals(target.getId(), release.getTargetProfileId())) {
            throw new ResponseStatusException(CONFLICT, "Desk release does not match its deployment target.");
        }
        var binding = prepareBinding(deployment, target, release.getId(), "apply:" + release.getId());
        binding.setReleaseId(release.getId()); binding.setVersionId(version.getId());
        bindings.save(binding);
    }

    @Transactional
    public void registerCallback(DeploymentEntity deployment, DeploymentTargetProfileEntity target, String connectorBaseUrl) {
        var binding = bindings.findByDeploymentIdAndTargetProfileId(deployment.getId(), target.getId())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Prepare the target Desk binding before registration."));
        requireOwnership(binding, deployment, target);
        if (connectorBaseUrl == null || !connectorBaseUrl.startsWith("https://")
            || java.net.URI.create(connectorBaseUrl).getHost() == null) {
            throw new ResponseStatusException(CONFLICT, "Desk requires the resolved HTTPS connector URL.");
        }
        var connection = desk.connection(target.getEnvironmentName());
        desk.put(connection, "/api/desk/admin/v1/workspaces/" + binding.getCustomerId(), Map.of(
            "name", deployment.getName(), "owners", workspaceOwners(binding.getCustomerId())));
        String bridge = environment.getProperty("LOOMAI_DESK_REVIEW_BRIDGE_BASE_URL", "");
        if (!bridge.startsWith("https://") || java.net.URI.create(bridge).getHost() == null) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Desk review bridge HTTPS origin is not configured.");
        }
        desk.put(connection, "/api/desk/admin/v1/bindings/" + binding.getId(), Map.ofEntries(
            Map.entry("customerId", binding.getCustomerId()), Map.entry("tenantId", binding.getTenantId()),
            Map.entry("deploymentId", binding.getDeploymentId()), Map.entry("targetProfileId", binding.getTargetProfileId()),
            Map.entry("deploymentName", deployment.getName()), Map.entry("environment", target.getEnvironmentName()),
            Map.entry("callbackUrl", connectorBaseUrl.replaceAll("/$", "") + "/integrations/conversation-channel/events"),
            Map.entry("providerApiKey", secrets.resolveSecret(binding.getProviderKeyRef())),
            Map.entry("callbackSigningSecret", secrets.resolveSecret(binding.getCallbackKeyRef())),
            Map.entry("reviewBridgeUrl", bridge.replaceAll("/$", "") + "/api/desk/bridge/v1/bindings/" + binding.getId() + "/reviews"),
            Map.entry("reviewBridgeKey", secrets.resolveSecret(binding.getBridgeKeyRef())), Map.entry("enabled", true)));
        binding.setStatus("APPLYING"); binding.setUpdatedAt(Instant.now()); bindings.save(binding);
    }

    private void requireOwnership(DeskBindingEntity binding, DeploymentEntity deployment, DeploymentTargetProfileEntity target) {
        if (!Objects.equals(binding.getCustomerId(), deployment.getCustomerId())
            || !Objects.equals(binding.getTenantId(), deployment.getTenantId())
            || !Objects.equals(binding.getDeploymentId(), deployment.getId())
            || !Objects.equals(binding.getTargetProfileId(), target.getId())) {
            throw new ResponseStatusException(FORBIDDEN, "Desk binding ownership does not match the selected target.");
        }
    }

    private List<Map<String, String>> workspaceOwners(String customerId) {
        var owners = new ArrayList<Map<String, String>>();
        for (var user : users.findByCustomerIdAndRoleIgnoreCaseAndStatusIgnoreCase(customerId, "CUSTOMER_ADMIN", "ACTIVE")) {
            identities.findByPlatformUserId(user.getId()).filter(value -> value.getRevokedAt() == null).ifPresent(identity -> {
                String name = user.getDisplayName() == null ? "Desk user" : user.getDisplayName();
                owners.add(Map.of("issuer", identity.getIssuer(), "subject", identity.getSubject(), "email", user.getEmail(),
                    "displayName", name.substring(0, Math.min(name.length(), 160))));
            });
        }
        if (owners.isEmpty()) throw new ResponseStatusException(FORBIDDEN, "No active linked customer owners are available for Desk.");
        return List.copyOf(owners);
    }

    private void store(DeskBindingEntity binding, String purpose, String name) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        secrets.upsertManagedSecret(name, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), Map.of(
            "deploymentId", binding.getDeploymentId(), "targetProfileId", binding.getTargetProfileId(), "bindingId", binding.getId(), "purpose", purpose));
    }

    private static String secretName(String bindingId, String purpose) {
        return "MANAGED_DESK_" + purpose + "_" + bindingId.replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static String bindingId(String deploymentId, String targetId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((deploymentId + ":" + targetId).getBytes(StandardCharsets.UTF_8));
            return "desk-" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
