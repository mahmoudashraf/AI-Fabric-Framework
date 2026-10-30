package com.ai.fabric.platform.backend.deployment.specialist;

import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Resolves authority from actual deployment installs, not client-supplied Marketplace labels. */
@Service
public class SpecialistAssistanceAuthorityService {
    private final DeploymentMarketplacePluginInstallRepository installs;
    private final MarketplacePluginVersionRepository versions;
    private final ObjectMapper mapper;

    public SpecialistAssistanceAuthorityService(DeploymentMarketplacePluginInstallRepository installs,
                                                MarketplacePluginVersionRepository versions, ObjectMapper mapper) {
        this.installs = installs;
        this.versions = versions;
        this.mapper = mapper;
    }

    public SpecialistAssistanceCompiler.Authority resolve(String deploymentId, JsonNode actions, JsonNode knowledge) {
        Map<String, Set<String>> reads = new LinkedHashMap<>();
        Set<String> spaces = new LinkedHashSet<>();
        for (JsonNode action : actions.path("actions")) {
            if (SpecialistAssistanceCompiler.ACTION.equals(action.path("name").asText())) {
                throw invalid("consult_specialist is a reserved native runtime action, not a connector action.");
            }
        }
        for (DeploymentMarketplacePluginInstallEntity install : installs.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId)) {
            if (!deploymentId.equals(install.getDeploymentId()) || !("ENABLED".equalsIgnoreCase(install.getStatus())
                || "BOOTSTRAPPED".equalsIgnoreCase(install.getStatus()))) continue;
            var version = versions.findById(install.getPluginVersionId())
                .orElseThrow(() -> invalid("An enabled Marketplace install has no immutable plugin version."));
            if (!install.getPluginId().equals(version.getPluginId())) throw invalid("Marketplace plugin version ownership mismatch.");
            JsonNode manifest;
            try { manifest = mapper.readTree(version.getManifestJson()); }
            catch (Exception ex) { throw invalid("Cannot read the installed plugin manifest."); }
            JsonNode contributions = manifest.path("contributions");
            for (JsonNode action : actions.path("actions")) {
                if (!belongsTo(action, install, version.getVersion()) || !"READ".equals(action.path("accessMode").asText())) continue;
                String name = action.path("name").asText();
                for (JsonNode declared : contributions.path("actions")) {
                    String declaredName = declared.path("actionId").asText(declared.path("id").asText());
                    boolean declaredRead = declared.path("readOnly").asBoolean(false)
                        || "READ".equals(declared.path("accessMode").asText());
                    if (!name.equals(declaredName) || !declaredRead) continue;
                    Set<String> requiredScopes = new LinkedHashSet<>();
                    addScopes(requiredScopes, declared.path("requiredScopes"));
                    addScopes(requiredScopes, action.path("requiredScopes"));
                    reads.put(name, Set.copyOf(requiredScopes));
                }
            }
            for (JsonNode source : knowledge.path("sources")) {
                if (!source.path("enabled").asBoolean(true) || !belongsTo(source, install, version.getVersion())) continue;
                String sourceId = source.path("id").asText();
                String space = source.path("entityType").asText();
                if (space.isBlank()) continue;
                for (JsonNode declared : contributions.path("knowledgeSources")) {
                    String declaredId = declared.path("id").asText(declared.path("sourceKey").asText());
                    if (!sourceId.equals(declaredId)) continue;
                    String declaredSpace = declared.path("entityType").asText();
                    if (declaredSpace.isBlank()) {
                        String datasetRef = declared.path("datasetRef").asText();
                        for (JsonNode dataset : contributions.path("datasets")) {
                            String datasetId = dataset.path("datasetId").asText(dataset.path("id").asText());
                            if (datasetRef.equals(datasetId)) declaredSpace = dataset.path("entityType").asText();
                        }
                    }
                    if (space.equals(declaredSpace)) spaces.add(space);
                }
            }
        }
        return new SpecialistAssistanceCompiler.Authority(reads, spaces);
    }

    private boolean belongsTo(JsonNode node, DeploymentMarketplacePluginInstallEntity install, String version) {
        return node.path("marketplaceManaged").asBoolean(false)
            && install.getId().equals(node.path("marketplaceInstallId").asText())
            && install.getPluginId().equals(node.path("marketplacePluginId").asText())
            && version.equals(node.path("marketplacePluginVersion").asText());
    }

    private void addScopes(Set<String> scopes, JsonNode values) {
        if (values.isMissingNode()) return;
        if (!values.isArray()) throw invalid("Declared READ action scopes must be an array.");
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 200) {
                throw invalid("Declared READ action scope is invalid.");
            }
            scopes.add(value.asText());
        }
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(BAD_REQUEST, "SPECIALIST_ASSISTANCE_AUTHORITY_INVALID: " + message);
    }
}
