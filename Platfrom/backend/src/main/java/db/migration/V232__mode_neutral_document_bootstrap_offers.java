package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static db.migration.V231__customer_scoped_document_contract_revisions.*;

/** New native offer snapshots only. Public selection and customer grants remain explicit. */
public class V232__mode_neutral_document_bootstrap_offers extends BaseJavaMigration {
    private static final Map<String, String> PUBLIC_SUCCESSORS = Map.of(
        "public-knowledge-workspace-v1", "public-knowledge-workspace-v2",
        "public-dealership-simulator-v2", "public-dealership-simulator-v3");
    private static final Set<String> KIT_TEMPLATES = Set.of("mkp-template-document-knowledge-assistant",
        "mkp-template-simulated-dealership-experience-v1", "mkp-template-dealership-external-systems-v1",
        "mkp-template-dealership-external-systems-managed-inbox-v1",
        "mkp-template-ai-support-workspace", "mkp-template-loomai-support-workspace");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<String, Version> documents = new LinkedHashMap<>();
        for (String plugin : DOCUMENT_PLUGINS) {
            Version document = latest(connection, plugin);
            if (document != null && neutral(document.manifest())) documents.put(plugin, document);
        }
        Map<String, Offer> sources = new LinkedHashMap<>();
        Map<String, Offer> publicSources = new LinkedHashMap<>();
        try (var query = connection.prepareStatement("select id, definition_json, fingerprint from platform_customer_offers order by reviewed_at desc, id desc");
             var rows = query.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString(1);
                if (id.startsWith("offer-doc232-") || PUBLIC_SUCCESSORS.containsValue(id)) continue;
                ObjectNode definition = object(rows.getString(2));
                String template = definition.at("/template/pluginId").asText();
                if (!KIT_TEMPLATES.contains(template)) continue;
                if (!PUBLIC_SUCCESSORS.containsKey(id) && !publishedPins(connection, definition)) continue;
                Offer source = new Offer(id, definition, rows.getString(3));
                sources.putIfAbsent(template, source);
                if (PUBLIC_SUCCESSORS.containsKey(id)) publicSources.put(template, source);
            }
        }
        // The operator-selected public starters take precedence over private/historical offers of the same family.
        sources.putAll(publicSources);
        for (Offer source : sources.values()) publish(connection, source, documents);
    }

    private boolean publishedPins(Connection connection, ObjectNode definition) throws Exception {
        List<JsonNode> pins = new ArrayList<>();
        pins.add(definition.path("template"));
        definition.path("plugins").forEach(pins::add);
        try (var query = connection.prepareStatement("select plugin_id, status, manifest_json from platform_marketplace_plugin_versions where id = ?")) {
            for (JsonNode pin : pins) {
                query.setString(1, pin.path("versionId").asText());
                try (var rows = query.executeQuery()) {
                    if (!rows.next() || !"PUBLISHED".equals(rows.getString(2))
                        || !pin.path("pluginId").asText().equals(rows.getString(1))
                        || !hash(object(rows.getString(3))).equals(pin.path("manifestSha256").asText())) return false;
                }
            }
        }
        return true;
    }

    private void publish(Connection connection, Offer source, Map<String, Version> documents) throws Exception {
        if (!hash(source.definition()).equals(source.fingerprint())) {
            throw new IllegalStateException("Published bootstrap offer fingerprint changed: " + source.id());
        }
        ObjectNode definition = source.definition().deepCopy();
        Version oldTemplate = requirePin(connection, definition.path("template"));
        ObjectNode revised = reviseTemplate(oldTemplate.manifest(), documents);
        boolean changed = !revised.equals(oldTemplate.manifest());
        for (JsonNode entry : definition.path("plugins")) {
            Version oldVersion = requirePin(connection, entry);
            Version replacement = documents.get(oldVersion.plugin());
            if (replacement != null && !replacement.id().equals(oldVersion.id())) {
                setPin((ObjectNode) entry, replacement);
                changed = true;
            }
        }
        if (!changed) return;
        Version template = matchingTemplate(connection, oldTemplate, revised);
        setPin((ObjectNode) definition.path("template"), template);
        String id = PUBLIC_SUCCESSORS.getOrDefault(source.id(),
            "offer-doc232-" + hash(JSON.getNodeFactory().textNode(source.id())).substring(0, 32));
        String fingerprint = hash(definition);
        try (var query = connection.prepareStatement("select fingerprint from platform_customer_offers where id = ?")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    if (!fingerprint.equals(rows.getString(1))) throw new IllegalStateException("Bootstrap offer successor ID is already occupied: " + id);
                    return;
                }
            }
        }
        try (var query = connection.prepareStatement("""
            insert into platform_customer_offers (id, definition_json, fingerprint, review_reference, reviewed_by, reviewed_at)
            values (?, ?, ?, ?, 'system', ?)
            """)) {
            query.setString(1, id);
            query.setString(2, JSON.writeValueAsString(definition));
            query.setString(3, fingerprint);
            query.setString(4, "010.59:mode-neutral-document-bootstrap:source-offer:" + source.id());
            query.setTimestamp(5, Timestamp.from(Instant.now()));
            query.executeUpdate();
        }
    }

    private Version matchingTemplate(Connection connection, Version source, ObjectNode revised) throws Exception {
        for (Version candidate : published(connection, source.plugin())) {
            ObjectNode comparable = candidate.manifest().deepCopy();
            comparable.set("version", revised.path("version"));
            if (comparable.equals(revised)) return candidate;
        }
        Version current = latest(connection, source.plugin());
        return insert(connection, source.plugin(), nextMinor(connection, source.plugin(), current.version()),
            source.channel(), source.publisher(), revised, "doc232");
    }

    private Version requirePin(Connection connection, JsonNode pin) throws Exception {
        Version version = byId(connection, pin.path("versionId").asText());
        if (!version.plugin().equals(pin.path("pluginId").asText())
            || !hash(version.manifest()).equals(pin.path("manifestSha256").asText())) {
            throw new IllegalStateException("Published bootstrap composition pin changed: " + version.id());
        }
        return version;
    }

    private void setPin(ObjectNode pin, Version version) throws Exception {
        pin.put("versionId", version.id());
        pin.put("manifestSha256", hash(version.manifest()));
    }

    private record Offer(String id, ObjectNode definition, String fingerprint) { }
}
