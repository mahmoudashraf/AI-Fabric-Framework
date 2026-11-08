package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.CustomerPluginLibraryContracts.*;
import com.ai.fabric.platform.backend.marketplace.repository.*;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class CustomerPluginLibraryService {
    private final MarketplacePluginRepository plugins;
    private final MarketplacePluginVersionRepository versions;
    private final DeploymentMarketplacePluginInstallRepository installs;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository deploymentVersions;
    private final MarketplaceManifestService manifests;
    private final CustomerOfferEntitlementService access;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;

    public CustomerPluginLibraryService(MarketplacePluginRepository plugins, MarketplacePluginVersionRepository versions,
        DeploymentMarketplacePluginInstallRepository installs, DeploymentRepository deployments,
        DeploymentReleaseRepository releases, DeploymentVersionRepository deploymentVersions,
        MarketplaceManifestService manifests, CustomerOfferEntitlementService access, PlatformAuditService audit,
        ObjectMapper mapper) {
        this.plugins = plugins;
        this.versions = versions;
        this.installs = installs;
        this.deployments = deployments;
        this.releases = releases;
        this.deploymentVersions = deploymentVersions;
        this.manifests = manifests;
        this.access = access;
        this.audit = audit;
        this.mapper = mapper;
    }

    public String requireWorkspace(String requestedCustomerId) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN) {
            String customerId = access.requireCustomerActor().customerId();
            if (requestedCustomerId != null && !customerId.equals(requestedCustomerId)) {
                throw new ResponseStatusException(FORBIDDEN, "Customer workspace mismatch.");
            }
            return customerId;
        }
        if (requestedCustomerId == null || requestedCustomerId.isBlank()) {
            throw new ResponseStatusException(FORBIDDEN, "An explicit customer workspace is required.");
        }
        access.requireStaffCustomerAccess(requestedCustomerId);
        return requestedCustomerId;
    }

    public Library list(String customerId) {
        String owner = requireWorkspace(customerId);
        return new Library(owner, true, plugins.findByOwnerCustomerIdOrderByUpdatedAtDesc(owner).stream()
            .filter(plugin -> "DATA".equals(plugin.getPluginType())).map(this::summary).toList());
    }

    public Summary get(String id, String customerId) {
        return summary(requireOwned(id, requireWorkspace(customerId), false));
    }

    public Summary create(String customerId, Create request) {
        String owner = requireWorkspace(customerId);
        JsonNode manifest = requireDataDocument(request == null ? null : request.manifest());
        var plugin = new MarketplacePluginEntity();
        plugin.setId("mkp-private-" + UUID.randomUUID());
        plugin.setSlug(plugin.getId());
        plugin.setPluginType("DATA");
        plugin.setOwnerCustomerId(owner);
        plugin.setVisibility("CUSTOMER_PRIVATE");
        plugin.setPublisherSlug("customer-" + owner);
        plugin.setPublisherDisplayName("Private workspace");
        plugin.setStatus("DRAFT");
        plugin.setCreatedAt(Instant.now());
        storeDraft(plugin, manifest);
        plugins.saveAndFlush(plugin);
        record("PLUGIN_LIBRARY_CREATED", plugin);
        return summary(plugin);
    }

    public Summary update(String id, String customerId, Save request) {
        var plugin = requireOwned(id, requireWorkspace(customerId), true);
        requireRevision(plugin, request == null ? null : request.expectedRevision());
        requireEditable(plugin);
        JsonNode manifest = requireDataDocument(request.manifest());
        requireTechnicalIdentity(plugin, manifest);
        storeDraft(plugin, manifest);
        plugins.saveAndFlush(plugin);
        record("PLUGIN_LIBRARY_DRAFT_SAVED", plugin);
        return summary(plugin);
    }

    private void requireTechnicalIdentity(MarketplacePluginEntity plugin, JsonNode manifest) {
        var existing = versions.findByPluginIdOrderByPublishedAtDesc(plugin.getId());
        if (!existing.isEmpty() && !Objects.equals(read(existing.get(0).getManifestJson()).path("pluginId"), manifest.path("pluginId"))) {
            throw new ResponseStatusException(CONFLICT, "The technical pluginId is fixed after the first reusable version.");
        }
    }

    public Check check(String id, String customerId, Save request) {
        var plugin = requireOwned(id, requireWorkspace(customerId), true);
        requireRevision(plugin, request == null ? null : request.expectedRevision());
        return checkDocument(plugin, request.manifest());
    }

    public List<Version> versions(String id, String customerId) {
        var plugin = requireOwned(id, requireWorkspace(customerId), false);
        return versionSummaries(plugin);
    }

    public Version version(String id, String label, String customerId) {
        requireOwned(id, requireWorkspace(customerId), false);
        return toVersion(versions.findByPluginIdAndVersion(id, label)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Private plugin version not found.")));
    }

    public Summary createVersion(String id, String customerId, Save request) {
        var plugin = requireOwned(id, requireWorkspace(customerId), true);
        requireRevision(plugin, request == null ? null : request.expectedRevision());
        requireEditable(plugin);
        JsonNode manifest = requireDataDocument(request.manifest());
        requireTechnicalIdentity(plugin, manifest);
        Check check = checkDocument(plugin, manifest);
        if (!check.valid()) throw new ResponseStatusException(BAD_REQUEST, check.issues().get(0).message());
        String label = manifest.path("version").textValue();
        if (versions.findByPluginIdAndVersion(id, label).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "This immutable version already exists; choose a new manifest.version.");
        }
        storeDraft(plugin, manifest);
        var version = documentVersion(plugin, manifest);
        version.setId("mpv-" + UUID.randomUUID());
        version.setReleaseChannel("PRIVATE");
        version.setStatus("PUBLISHED");
        version.setCreatedAt(Instant.now());
        version.setPublishedAt(version.getCreatedAt());
        versions.saveAndFlush(version);
        plugin.setStatus("ACTIVE");
        plugins.saveAndFlush(plugin);
        record("PLUGIN_LIBRARY_VERSION_CREATED", plugin);
        return summary(plugin);
    }

    public Summary archive(String id, String customerId, Revision request) {
        var plugin = requireOwned(id, requireWorkspace(customerId), true);
        requireRevision(plugin, request == null ? null : request.expectedRevision());
        plugin.setStatus("ARCHIVED");
        touch(plugin);
        plugins.saveAndFlush(plugin);
        record("PLUGIN_LIBRARY_ARCHIVED", plugin);
        return summary(plugin);
    }

    private Check checkDocument(MarketplacePluginEntity plugin, JsonNode manifest) {
        List<Issue> issues = new ArrayList<>();
        if (manifest == null || !manifest.isObject()) {
            issues.add(new Issue("/", "MANIFEST_OBJECT_REQUIRED", "Manifest must be a JSON object."));
        } else {
            if (manifest.path("schemaVersion").asInt(-1) != 1) issues.add(new Issue("/schemaVersion", "UNSUPPORTED_SCHEMA", "schemaVersion must be 1."));
            if (!manifest.path("contributions").isObject()) issues.add(new Issue("/contributions", "OBJECT_REQUIRED", "contributions must be an object."));
            for (String field : List.of("pluginId", "version", "displayName")) {
                if (!manifest.path(field).isTextual() || manifest.path(field).asText().isBlank()) {
                    issues.add(new Issue("/" + field, "REQUIRED", field + " is required for a reusable version."));
                }
            }
            issues.addAll(MarketplacePrivateDataContract.issues(manifest));
            try {
                requireDataDocument(manifest);
                manifests.parseAndValidate(plugin, documentVersion(plugin, manifest));
            } catch (ResponseStatusException ex) {
                issues.add(new Issue("/contributions", "MANIFEST_CONTRACT_INVALID", ex.getReason()));
            }
        }
        return new Check(issues.isEmpty(), plugin.getDraftRevision(), List.copyOf(issues), false);
    }

    private MarketplacePluginVersionEntity documentVersion(MarketplacePluginEntity plugin, JsonNode manifest) {
        var version = new MarketplacePluginVersionEntity();
        version.setPluginId(plugin.getId());
        version.setVersion(manifest.path("version").asText("draft"));
        version.setManifestJson(write(manifest));
        return version;
    }

    private MarketplacePluginEntity requireOwned(String id, String owner, boolean lock) {
        var plugin = (lock ? plugins.findByIdForUpdate(id) : plugins.findById(id))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Private plugin not found."));
        if (!"CUSTOMER_PRIVATE".equals(plugin.getVisibility()) || !owner.equals(plugin.getOwnerCustomerId())
            || !"DATA".equals(plugin.getPluginType())) {
            throw new ResponseStatusException(NOT_FOUND, "Private plugin not found.");
        }
        return plugin;
    }

    private void requireRevision(MarketplacePluginEntity plugin, Long expectedRevision) {
        if (expectedRevision == null) throw new ResponseStatusException(BAD_REQUEST, "expectedRevision is required.");
        if (expectedRevision != plugin.getDraftRevision()) throw new ResponseStatusException(CONFLICT, "Plugin draft revision changed; reload before saving.");
    }

    private void requireEditable(MarketplacePluginEntity plugin) {
        if ("ARCHIVED".equals(plugin.getStatus())) throw new ResponseStatusException(CONFLICT, "Archived plugin definitions are read-only.");
    }

    private JsonNode requireDataDocument(JsonNode manifest) {
        if (manifest == null || !manifest.isObject()) throw new ResponseStatusException(BAD_REQUEST, "Manifest must be a JSON object.");
        String type = manifest.path("pluginType").asText(manifest.path("type").asText("DATA"));
        if (!"DATA".equals(type)) throw new ResponseStatusException(BAD_REQUEST, "Private authoring supports the existing DATA contract only.");
        return manifest;
    }

    private void storeDraft(MarketplacePluginEntity plugin, JsonNode manifest) {
        plugin.setDraftManifestJson(write(manifest));
        plugin.setDisplayName(manifest.path("displayName").asText("Untitled data plugin"));
        plugin.setShortDescription(manifest.path("description").asText(""));
        JsonNode presentation = manifest.path("presentation");
        plugin.setCatalogueFamily(presentation.path("catalogueFamily").textValue());
        plugin.setCatalogueAudience(presentation.path("catalogueAudience").asText("CUSTOMER"));
        plugin.setAdapterContract(presentation.path("adapterContract").textValue());
        touch(plugin);
    }

    private void touch(MarketplacePluginEntity plugin) {
        plugin.setDraftRevision(plugin.getDraftRevision() + 1);
        plugin.setUpdatedAt(Instant.now());
    }

    private List<Version> versionSummaries(MarketplacePluginEntity plugin) {
        return versions.findByPluginIdOrderByPublishedAtDesc(plugin.getId()).stream().map(this::toVersion).toList();
    }

    private Version toVersion(MarketplacePluginVersionEntity version) {
        return new Version(version.getId(), version.getVersion(), version.getStatus(), read(version.getManifestJson()), version.getCreatedAt());
    }

    private Summary summary(MarketplacePluginEntity plugin) {
        return new Summary(plugin.getId(), plugin.getOwnerCustomerId(), plugin.getVisibility(), plugin.getDisplayName(),
            plugin.getPluginType(), plugin.getStatus(), read(plugin.getDraftManifestJson()), plugin.getDraftRevision(),
            versionSummaries(plugin), usage(plugin), true, plugin.getCatalogueFamily(), plugin.getCatalogueAudience(),
            plugin.getAdapterContract(), plugin.getUpdatedAt());
    }

    private Usage usage(MarketplacePluginEntity plugin) {
        List<Install> saved = new ArrayList<>();
        List<AppliedUse> applied = new ArrayList<>();
        for (DeploymentEntity deployment : deployments.findByCustomerIdOrderByUpdatedAtDesc(plugin.getOwnerCustomerId())) {
            for (var install : installs.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())) {
                if (!plugin.getId().equals(install.getPluginId())) continue;
                var version = versions.findById(install.getPluginVersionId()).orElseThrow();
                MarketplacePluginOwnership.requireVersion(plugin, version, deployment.getCustomerId());
                saved.add(new Install(install.getId(), deployment.getId(), deployment.getName(), version.getId(),
                    version.getVersion(), install.getStatus(), install.getUpdatedAt()));
            }
            Set<String> targets = new HashSet<>();
            var appliedReleases = releases.findByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).stream()
                .filter(r -> r.getAppliedAt() != null && Set.of("APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED").contains(r.getStatus()))
                .sorted(Comparator.comparing(com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity::getAppliedAt).reversed())
                .toList();
            for (var release : appliedReleases) {
                if (!targets.add(release.getTargetProfileId())) continue;
                var deploymentVersion = deploymentVersions.findById(release.getDeploymentVersionId()).orElseThrow();
                for (JsonNode pin : read(deploymentVersion.getCompositionProvenanceJson()).path("marketplaceInstalls")) {
                    if (!plugin.getId().equals(pin.path("pluginId").asText())) continue;
                    applied.add(new AppliedUse(deployment.getId(), release.getTargetProfileId(), release.getId(),
                        deploymentVersion.getId(), pin.path("pluginVersionId").asText(), pin.path("pluginVersion").asText(), release.getAppliedAt()));
                }
            }
        }
        return new Usage(saved.size(), applied.size(), List.copyOf(saved), List.copyOf(applied));
    }

    private JsonNode read(String json) {
        try { return json == null ? mapper.createObjectNode() : mapper.readTree(json); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Invalid stored plugin document.", ex); }
    }
    private String write(JsonNode node) {
        try { return mapper.writeValueAsString(node); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Cannot serialize plugin document.", ex); }
    }
    private void record(String event, MarketplacePluginEntity plugin) {
        audit.record(event, "MARKETPLACE_PLUGIN", plugin.getId(), Map.of("customerId", plugin.getOwnerCustomerId(), "revision", plugin.getDraftRevision()));
    }
}
