package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;

@Service
public class ExternalIntegrationPolicy {
    private final MarketplacePluginRepository plugins;
    private final MarketplacePluginVersionRepository versions;
    private final MarketplaceManifestService manifests;
    private final CustomerOfferCodec codec;
    private final ExternalIntegrationEndpointPolicy endpoints;
    public ExternalIntegrationPolicy(MarketplacePluginRepository plugins, MarketplacePluginVersionRepository versions,
        MarketplaceManifestService manifests, CustomerOfferCodec codec, ExternalIntegrationEndpointPolicy endpoints) {
        this.plugins = plugins; this.versions = versions; this.manifests = manifests; this.codec = codec; this.endpoints = endpoints;
    }

    public void validateDefinition(Definition definition) {
        Set<String> ids = new HashSet<>(), mappings = new HashSet<>();
        for (Profile profile : definition.externalIntegrationProfiles()) {
            if (profile == null || !ids.add(profile.id()) || !mappings.add(profile.bindingSlotId())) throw conflict("PROFILE_DUPLICATE");
            validateProfile(definition, profile);
        }
        for (var slot : definition.bindingSlots()) if ("EXTERNAL_SERVICE".equals(slot.kind().name())) {
            if (definition.externalIntegrationProfiles().stream().noneMatch(profile -> profile.bindingSlotId().equals(slot.id()))) {
                throw conflict("PROFILE_REQUIRED");
            }
            Set<String> actual = new HashSet<>();
            definition.externalIntegrationProfiles().stream().filter(profile -> slot.id().equals(profile.bindingSlotId()))
                .forEach(profile -> actual.add(profile.id()));
            if (!actual.equals(slot.supportedProfileIds()) || slot.installConfigField() != null) throw conflict("SLOT_PROFILE_MISMATCH");
        }
    }

    public Profile requireProfile(Definition definition, String slotId, String profileId, String targetId) {
        validateDefinition(definition);
        var matches = definition.externalIntegrationProfiles().stream()
            .filter(profile -> slotId.equals(profile.bindingSlotId()) && profileId.equals(profile.id())).toList();
        if (matches.size() != 1 || !matches.getFirst().targetProfileIds().contains(targetId)) throw conflict("PROFILE_SCOPE_MISMATCH");
        return matches.getFirst();
    }

    public void requireCompilerEvidence(Definition definition, JsonNode provenance) {
        if (definition == null || provenance == null || !provenance.isObject()) throw conflict("COMPILER_EVIDENCE_INVALID");
        validateDefinition(definition);
        JsonNode declared = provenance.path("externalIntegrationProfiles");
        if (definition.externalIntegrationProfiles().isEmpty()) {
            if (!declared.isMissingNode() && (!declared.isArray() || !declared.isEmpty())) throw conflict("COMPILER_PROFILE_MISMATCH");
            return;
        }
        if (!declared.isArray() || declared.size() != definition.externalIntegrationProfiles().size()) throw conflict("COMPILER_EVIDENCE_REQUIRED");
        for (Profile profile : definition.externalIntegrationProfiles()) {
            JsonNode expected = codec.tree(codec.write(ExternalIntegrationCompilerResolver.evidenceFields(profile, codec)));
            long matches = java.util.stream.StreamSupport.stream(declared.spliterator(), false).filter(expected::equals).count();
            if (matches != 1) throw conflict("COMPILER_PROFILE_MISMATCH");
        }
    }

    public void validateProfile(Definition definition, Profile profile) {
        validateProfile(profile);
        if (profile.targetProfileIds().stream().anyMatch(id -> definition.targets().stream().noneMatch(target -> id.equals(target.targetProfileId())))) {
            throw conflict("PROFILE_TARGET_NOT_REVIEWED");
        }
        var slot = definition.bindingSlots().stream().filter(value -> profile.bindingSlotId().equals(value.id())).findFirst()
            .orElseThrow(() -> conflict("SLOT_REQUIRED"));
        if (!"EXTERNAL_SERVICE".equals(slot.kind().name()) || !profile.pluginId().equals(slot.pluginId())
            || slot.installConfigField() != null || !slot.supportedProfileIds().contains(profile.id())) throw conflict("SLOT_PROFILE_MISMATCH");
        if (definition.plugins().stream().noneMatch(pin -> profile.pluginId().equals(pin.pluginId())
            && profile.pluginVersionId().equals(pin.versionId()) && profile.manifestSha256().equals(pin.manifestSha256()))) throw conflict("PLUGIN_PIN_MISMATCH");
    }

    /** Validate the selected integration itself, not the template's unrelated bootstrap plugins. */
    public void validateProfile(Profile profile) {
        if (profile == null || !PROFILE_SCHEMA.equals(profile.schemaVersion()) || !identifier(profile.id())
            || !identifier(profile.bindingSlotId()) || !identifier(profile.pluginId()) || !identifier(profile.pluginVersionId())
            || profile.manifestSha256() == null || !profile.manifestSha256().matches("[a-f0-9]{64}")
            || profile.targetProfileIds().isEmpty() || profile.targetProfileIds().size() > 16
            || profile.configFields().isEmpty() || profile.configFields().size() > 32
            || profile.secretFields().isEmpty() || profile.secretFields().size() > 16
            || profile.endpointFields().isEmpty() || profile.allowedHosts().isEmpty() || profile.allowedHosts().size() > 16
            || !profile.configFields().containsAll(profile.endpointFields()) || !profile.configFields().containsAll(profile.accountScopeFields())
            || profile.configFields().stream().anyMatch(field -> !identifier(field) || profile.secretFields().contains(field))
            || profile.secretFields().stream().anyMatch(field -> !identifier(field))) throw conflict("PROFILE_INVALID");
        var parsed = manifest(profile);
        Set<String> config = new HashSet<>(), secrets = new HashSet<>(), urls = new HashSet<>();
        for (var field : parsed.installForm()) {
            switch (field.type()) {
                case "secretRef" -> secrets.add(field.id());
                case "url" -> { config.add(field.id()); urls.add(field.id()); }
                case "text" -> config.add(field.id());
                default -> throw conflict("FIELD_TYPE_UNSUPPORTED");
            }
        }
        if (!config.equals(profile.configFields()) || !secrets.equals(profile.secretFields()) || !urls.equals(profile.endpointFields())) {
            throw conflict("FIELD_CONTRACT_MISMATCH");
        }
        profile.allowedHosts().forEach(endpoints::requireHost);
        for (JsonNode action : parsed.manifest().path("contributions").path("actions")) {
            JsonNode route = action.path("route");
            if (route.has("sourceProjection")) continue;
            if (!"connector-http".equals(action.path("adapterType").asText()) || route.has("url") || route.has("urlTemplate")
                || route.has("headers") || !profile.endpointFields().contains(route.path("managedUrlField").asText())) {
                throw conflict("UNREVIEWED_EXECUTION_ENDPOINT");
            }
            for (JsonNode field : route.path("managedHeaderSecretRefFields")) {
                if (!field.isTextual() || !profile.secretFields().contains(field.textValue())) throw conflict("UNREVIEWED_EXECUTION_CREDENTIAL");
            }
        }
        for (JsonNode dataset : parsed.manifest().path("contributions").path("datasets")) {
            if (!"EXTERNAL_SYNC_HTTP".equals(dataset.path("ingestionMode").asText())) throw conflict("DATA_PROTOCOL_UNSUPPORTED");
            JsonNode connection = dataset.at("/syncConnector/connectionProfile");
            if (connection.has("baseUrl") || connection.has("allowedHosts")
                || !profile.endpointFields().contains(connection.path("managedBaseUrlField").asText())
                || !profile.configFields().contains(connection.path("managedAllowedHostField").asText())) throw conflict("UNREVIEWED_EXECUTION_ENDPOINT");
            JsonNode auth = connection.path("auth");
            if (auth.has("tokenBaseUrl") || auth.has("apiKeyValue") || auth.has("token")
                || (auth.has("managedTokenBaseUrlField") && !profile.endpointFields().contains(auth.path("managedTokenBaseUrlField").asText()))) {
                throw conflict("UNREVIEWED_EXECUTION_ENDPOINT");
            }
            for (JsonNode credential : auth.path("credentialFields")) {
                if (!profile.secretFields().contains(credential.path("secretRefField").asText()) || credential.has("value")) throw conflict("UNREVIEWED_EXECUTION_CREDENTIAL");
            }
            String apiKeyField = auth.path("apiKeySecretRefField").asText();
            if (!apiKeyField.isBlank() && !profile.secretFields().contains(apiKeyField)) throw conflict("UNREVIEWED_EXECUTION_CREDENTIAL");
            String webhookField = dataset.at("/syncConnector/webhook/verification/secretRefField").asText();
            if (!webhookField.isBlank() && !profile.secretFields().contains(webhookField)) throw conflict("UNREVIEWED_EXECUTION_CREDENTIAL");
            String field = dataset.at("/syncConnector/protectedResource/resourceIdField").asText();
            if (!field.isBlank() && !profile.accountScopeFields().contains(field)) throw conflict("ACCOUNT_SCOPE_FIELD_REQUIRED");
        }
    }

    public Profile forPluginVersion(Profile defaults, String versionId) {
        var version = versions.findById(versionId).filter(value -> defaults.pluginId().equals(value.getPluginId()))
            .orElseThrow(() -> conflict("PLUGIN_REQUIRED"));
        Profile profile = new Profile(defaults.schemaVersion(), defaults.id(), defaults.bindingSlotId(), defaults.pluginId(),
            versionId, codec.hash(codec.tree(version.getManifestJson())), defaults.targetProfileIds(), defaults.configFields(),
            defaults.secretFields(), defaults.endpointFields(), defaults.accountScopeFields(), defaults.allowedHosts());
        validateProfile(profile);
        return profile;
    }

    public List<Profile> profilesForVersion(Definition defaults, JsonNode provenance) {
        if (!ExternalIntegrationRequirement.required(provenance)) return List.of();
        List<Profile> result = new ArrayList<>();
        Set<String> slots = new HashSet<>();
        JsonNode installs = provenance.path("marketplaceInstalls");
        if (!installs.isArray()) throw conflict("COMPILER_EVIDENCE_REQUIRED");
        for (JsonNode evidence : provenance.path("externalIntegrationProfiles")) {
            var candidates = defaults.externalIntegrationProfiles().stream().filter(profile ->
                profile.id().equals(evidence.path("profileId").asText())
                    && profile.bindingSlotId().equals(evidence.path("bindingSlotId").asText())
                    && profile.pluginId().equals(evidence.path("pluginId").asText())).toList();
            if (candidates.size() != 1 || !slots.add(evidence.path("bindingSlotId").asText())) throw conflict("COMPILER_PROFILE_MISMATCH");
            Profile profile = forPluginVersion(candidates.getFirst(), evidence.path("pluginVersionId").asText());
            String pluginType = plugins.findById(profile.pluginId()).orElseThrow(() -> conflict("PLUGIN_REQUIRED")).getPluginType();
            JsonNode expected = codec.tree(codec.write(ExternalIntegrationCompilerResolver.evidenceFields(profile, codec)));
            if (!expected.equals(evidence)) throw conflict("COMPILER_PROFILE_MISMATCH");
            long matchingInstalls = java.util.stream.StreamSupport.stream(installs.spliterator(), false).filter(install ->
                profile.pluginId().equals(install.path("pluginId").asText())
                    && profile.pluginVersionId().equals(install.path("pluginVersionId").asText())
                    && ("sha256:" + profile.manifestSha256()).equals(install.path("manifestSha256").asText())
                    && pluginType.equals(install.path("pluginType").asText())).count();
            if (matchingInstalls != 1) throw conflict("COMPILER_PROFILE_MISMATCH");
            result.add(profile);
        }
        return List.copyOf(result);
    }

    public void validateConfig(Profile profile, Map<String, String> config, Map<String, String> accountScope) {
        if (!profile.configFields().equals(config.keySet()) || accountScope.isEmpty() || accountScope.size() > 8
            || accountScope.entrySet().stream().anyMatch(entry -> !identifier(entry.getKey()) || !literal(entry.getValue()))) {
            throw conflict("APPROVED_CONFIG_INVALID");
        }
        for (var entry : config.entrySet()) if (!literal(entry.getValue())) throw conflict("APPROVED_CONFIG_INVALID");
        for (String field : profile.endpointFields()) endpoints.requireUrl(config.get(field), profile.allowedHosts());
        for (String field : profile.accountScopeFields()) {
            if (!config.get(field).equals(accountScope.get(field))) throw conflict("ACCOUNT_SCOPE_MISMATCH");
        }
        for (JsonNode dataset : manifest(profile).manifest().path("contributions").path("datasets")) {
            JsonNode connection = dataset.at("/syncConnector/connectionProfile");
            String urlField = connection.path("managedBaseUrlField").asText(), hostField = connection.path("managedAllowedHostField").asText();
            if (!urlField.isBlank() && !hostField.isBlank()
                && !endpoints.requireUrl(config.get(urlField), profile.allowedHosts()).getHost().equals(config.get(hostField))) {
                throw conflict("ENDPOINT_HOST_SCOPE_MISMATCH");
            }
        }
    }

    public Map<String, String> textualObject(JsonNode input, int maxValueLength) {
        if (input == null || !input.isObject() || input.size() > 32) throw conflict("TEXT_OBJECT_REQUIRED");
        Map<String, String> result = new LinkedHashMap<>();
        input.fields().forEachRemaining(entry -> {
            if (!identifier(entry.getKey()) || !entry.getValue().isTextual() || entry.getValue().textValue().isBlank()
                || entry.getValue().textValue().length() > maxValueLength) throw conflict("TEXT_VALUE_REQUIRED");
            result.put(entry.getKey(), entry.getValue().textValue());
        });
        return result;
    }
    private MarketplaceManifestService.ParsedMarketplaceManifest manifest(Profile profile) {
        var plugin = plugins.findById(profile.pluginId()).orElseThrow(() -> conflict("PLUGIN_REQUIRED"));
        var version = versions.findById(profile.pluginVersionId()).orElseThrow(() -> conflict("PLUGIN_REQUIRED"));
        if (!Set.of("DATA", "ACTION").contains(plugin.getPluginType()) || !"ACTIVE".equals(plugin.getStatus())
            || !"PUBLISHED".equals(version.getStatus()) || !profile.pluginId().equals(version.getPluginId())
            || !profile.manifestSha256().equals(codec.hash(codec.tree(version.getManifestJson())))) throw conflict("PLUGIN_PIN_MISMATCH");
        try { return manifests.parseAndValidate(plugin, version); }
        catch (RuntimeException invalid) { throw conflict("PLUGIN_PIN_MISMATCH"); }
    }
    public static boolean identifier(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"); }
    private boolean literal(String value) { return value != null && !value.isBlank() && value.length() <= 2048
        && value.equals(value.trim()) && value.chars().noneMatch(Character::isISOControl) && !value.contains("${") && !value.contains("#{"); }
    public static ResponseStatusException conflict(String code) { return new ResponseStatusException(HttpStatus.CONFLICT, "EXTERNAL_INTEGRATION_" + code); }
}
