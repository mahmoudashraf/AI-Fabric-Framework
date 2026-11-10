package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretCleanupPolicy;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretContracts;
import com.ai.fabric.platform.backend.secret.model.UpsertDeploymentProviderSecretBindingRequest;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reuses the existing secret store/materializer; caller JSON never supplies ownership. */
@Service
public class CustomerDeploymentSecretService {
    public static final String INTEGRATION = "INTEGRATION_CREDENTIAL";
    private final PlatformSecretRepository secrets;
    private final DeploymentProviderSecretBindingRepository bindings;
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;
    private final PlatformCustomerAccessService customers;
    private final DeploymentSecretPurposeCatalog purposes;
    private final DeploymentProviderSecretOverrideService overrides;
    private final PlatformAuditService audit;

    public CustomerDeploymentSecretService(PlatformSecretRepository secrets, DeploymentProviderSecretBindingRepository bindings,
        DeploymentRepository deployments, DeploymentAccessService access, PlatformCustomerAccessService customers,
        DeploymentSecretPurposeCatalog purposes, DeploymentProviderSecretOverrideService overrides, PlatformAuditService audit) {
        this.secrets = secrets; this.bindings = bindings; this.deployments = deployments; this.access = access;
        this.customers = customers; this.purposes = purposes; this.overrides = overrides; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DeploymentSecretContracts.Catalogue list(String deploymentId) {
        requireOwner(deploymentId);
        var options = new ArrayList<DeploymentSecretContracts.Purpose>();
        options.add(new DeploymentSecretContracts.Purpose(INTEGRATION, "Integration or security key", false));
        purposes.supportedOverridePurposes().stream().sorted().map(purposes::requireDefinition)
            .forEach(p -> options.add(new DeploymentSecretContracts.Purpose(p.purpose(), p.displayName(), p.paired())));
        return new DeploymentSecretContracts.Catalogue(deploymentId, List.copyOf(options),
            secrets.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE, deploymentId)
                .stream().filter(s -> customerEditable(s, deploymentId)).map(this::summary).toList());
    }

    @Transactional
    public List<DeploymentSecretContracts.Secret> create(String deploymentId, DeploymentSecretContracts.Create request) {
        requireOwner(deploymentId);
        if (request == null || request.name() == null || !request.name().matches("[A-Z][A-Z0-9_]{0,63}"))
            throw bad("Use a name of 1 to 64 uppercase letters, numbers or underscores, starting with a letter.");
        var definition = INTEGRATION.equals(request.purpose()) ? null : purposes.requireDefinition(request.purpose());
        String purpose = definition == null ? INTEGRATION : definition.purpose();
        boolean paired = definition != null && definition.paired();
        String primaryName = prefix(deploymentId) + request.name() + (paired ? "_USERNAME" : "");
        String secondaryName = paired ? prefix(deploymentId) + request.name() + "_PASSWORD" : null;
        // Validate every write before touching managed state; paired storage/binding is atomic.
        requireNew(primaryName);
        if (paired) requireNew(secondaryName);
        String primaryValue = value(request.value());
        String secondaryValue = paired ? value(request.secondaryValue()) : null;
        if (!paired && request.secondaryValue() != null && !request.secondaryValue().isBlank()) throw bad("This key has no secondary value.");
        var primary = store(primaryName, deploymentId, purpose, primaryValue);
        var result = new ArrayList<PlatformSecretEntity>(); result.add(primary);
        if (paired) result.add(store(secondaryName, deploymentId, purpose, secondaryValue));
        if (!INTEGRATION.equals(purpose)) overrides.upsertBinding(deploymentId,
            new UpsertDeploymentProviderSecretBindingRequest(purpose, "REQUIRE_OVERRIDE", primaryName, secondaryName));
        return result.stream().map(this::summary).toList();
    }

    @Transactional
    public DeploymentSecretContracts.Secret rotate(String deploymentId, String name, DeploymentSecretContracts.Rotate request) {
        requireOwner(deploymentId);
        var entity = owned(deploymentId, name);
        entity.setSecretValue(value(request == null ? null : request.value())); entity.setUpdatedAt(Instant.now());
        secrets.save(entity); record("CUSTOMER_SECRET_ROTATED", entity);
        return summary(entity);
    }

    @Transactional
    public void delete(String deploymentId, String name) {
        requireOwner(deploymentId);
        var entity = owned(deploymentId, name);
        if (bindingCount(name) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "This key is used by a provider. Rotate it or save a replacement provider key before deleting it.");
        secrets.delete(entity); record("CUSTOMER_SECRET_DELETED", entity);
    }

    private DeploymentEntity requireOwner(String id) {
        if (!PlatformSecurityContext.isAuthenticated()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var deployment = deployments.findById(id).orElseThrow(() -> missing());
        var principal = PlatformSecurityContext.currentPrincipal();
        // Staff delegation is rechecked for every operation, including after revocation.
        if (principal.role() == PlatformRole.PLATFORM_OPERATOR) customers.requireStaffCustomerAccess(deployment.getCustomerId());
        access.requireDeploymentAdminAccess(deployment);
        if ("ARCHIVED".equals(deployment.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Restore this Smart Brain before managing its keys.");
        return deployment;
    }

    private PlatformSecretEntity owned(String deploymentId, String name) {
        var entity = secrets.findById(name).orElseThrow(() -> missing());
        if (!customerEditable(entity, deploymentId)) throw missing();
        return entity;
    }

    private boolean customerEditable(PlatformSecretEntity entity, String deploymentId) {
        return deploymentId.equals(entity.getDeploymentId()) && entity.getTargetProfileId() == null
            && entity.getScopeType() == PlatformSecretScopeType.DEPLOYMENT_OVERRIDE
            && entity.getOwnerType() == PlatformSecretOwnerType.DEPLOYMENT && !entity.isManagedByPlatform();
    }

    private PlatformSecretEntity store(String name, String deploymentId, String purpose, String value) {
        var entity = new PlatformSecretEntity(); entity.setName(name); entity.setSecretValue(value);
        entity.setDeploymentId(deploymentId); entity.setScopeType(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE);
        entity.setSecretPurpose(purpose); entity.setOwnerType(PlatformSecretOwnerType.DEPLOYMENT);
        entity.setManagedByPlatform(false); entity.setCleanupPolicy(PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE);
        entity.setUpdatedAt(Instant.now()); secrets.save(entity); record("CUSTOMER_SECRET_CREATED", entity);
        return entity;
    }

    private void record(String action, PlatformSecretEntity entity) {
        audit.record(action, "DEPLOYMENT", entity.getDeploymentId(), Map.of("secretName", entity.getName(), "purpose", entity.getSecretPurpose()));
    }

    private DeploymentSecretContracts.Secret summary(PlatformSecretEntity entity) {
        String prefix = prefix(entity.getDeploymentId());
        String label = entity.getName().startsWith(prefix) ? entity.getName().substring(prefix.length()) : entity.getName();
        return new DeploymentSecretContracts.Secret(entity.getName(), label, entity.getSecretPurpose(),
            entity.getSecretValue() != null && !entity.getSecretValue().isBlank(), bindingCount(entity.getName()),
            entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString());
    }

    private int bindingCount(String name) {
        return Math.toIntExact(bindings.countBySecretName(name) + bindings.countBySecondarySecretName(name));
    }

    private void requireNew(String name) {
        if (secrets.existsById(name)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That key name already exists. Choose another name or rotate the existing key.");
    }

    private static String prefix(String id) {
        return "CUSTOMER_" + HexFormat.of().withUpperCase().formatHex(id.getBytes(StandardCharsets.UTF_8)) + "_";
    }
    private static String value(String value) {
        if (value == null || value.isBlank() || value.length() > 16_384) throw bad("Provide a key value of at most 16,384 characters.");
        return value.trim();
    }
    private static ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Key not found in this Smart Brain."); }
}
