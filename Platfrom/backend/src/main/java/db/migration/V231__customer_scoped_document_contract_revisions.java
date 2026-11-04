package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Adds snapshots only; historical payloads and deployment/offer associations remain immutable. */
public class V231__customer_scoped_document_contract_revisions extends BaseJavaMigration {
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<String> DOCUMENT_PLUGINS = List.of("mkp-data-document-knowledge-s3",
        "mkp-data-dealership-knowledge-v1", "mkp-data-ai-support-documents");
    private static final List<String> TEMPLATES = List.of("mkp-template-document-knowledge-assistant",
        "mkp-template-simulated-dealership-experience-v1", "mkp-template-dealership-external-systems-v1",
        "mkp-template-dealership-external-systems-managed-inbox-v1",
        "mkp-template-ai-support-workspace", "mkp-template-loomai-support-workspace");
    private static final String SUPPORT_MANIFEST = """
        {"schemaVersion":1,"pluginId":"mkp-data-ai-support-documents","version":"1.1.0","pluginType":"DATA","displayName":"Approved support documents","description":"Deployment-scoped, explicitly approved support guides in customer-scoped object storage with a read-only bound connection.","compatibility":{"requiredCapabilities":["knowledgeSources"]},"pricing":{"pricingModel":"FREE"},"permissions":{"contributesKnowledgeSources":true,"requiresSharedDatasetAccess":true},"installForm":[{"id":"documentStorageBindingRef","label":"Document storage connection","type":"text","required":true,"description":"Customer-scoped S3-compatible connection, hosted by LoomAI or supplied by the customer. Do not enter access keys or a bucket URL here."}],"contributions":{"entityConfig":{"ai-entities":{"document":{"indexing":{"enabled":true,"max-characters":8000},"searchable-fields":[{"name":"content","destinations":["SEMANTIC_SEARCH","RAG_CONTEXT"],"required":true,"priority":100}],"metadata-fields":[{"name":"originalFilename","data-type":"STRING","destinations":["VECTOR_METADATA","LLM_CONTEXT","API_RESPONSE"],"required":true},{"name":"sourceCategory","data-type":"STRING","destinations":["VECTOR_METADATA","LLM_CONTEXT","API_RESPONSE"],"required":true},{"name":"publicationStatus","data-type":"STRING","destinations":["VECTOR_METADATA","LLM_CONTEXT"],"required":true},{"name":"tenantId","data-type":"ID","destinations":["VECTOR_METADATA"],"required":true},{"name":"customerId","data-type":"ID","destinations":["VECTOR_METADATA"],"required":true},{"name":"deploymentId","data-type":"ID","destinations":["VECTOR_METADATA"],"required":true},{"name":"datasetId","data-type":"ID","destinations":["VECTOR_METADATA"],"required":true},{"name":"knowledgeSourceHandleRef","data-type":"ID","destinations":["VECTOR_METADATA"],"required":true}]}}},"datasets":[{"datasetId":"support-documents","entityType":"document","storageScope":"CUSTOMER_SCOPED","sharingScope":"DEPLOYMENT_ONLY","ingestionMode":"EXTERNAL_DOCUMENT_STORAGE","updateStrategy":"VERSIONED_REPLACE","vectorizationProfile":"ai-support-documents","handleTemplate":"documents/{deploymentId}/approved-support","sourceConnector":{"connectorType":"S3_COMPATIBLE_OBJECT_STORAGE","storageOwnership":"BOUND_CONNECTION","bindingRefField":"documentStorageBindingRef","allowedOperations":["LIST","READ","HEAD"],"deleteSourceOnRemoval":false},"documentPolicy":{"allowedMediaTypes":["text/plain","application/json"],"allowedExtensions":[".txt",".json"],"jsonContentKeys":["content","text","body"],"maxSourceBytes":1048576,"maxSources":100,"maxTotalIndexedBytes":104857600,"maxChunksPerSource":100,"maxChunkCharacters":8000,"maxTotalCharacters":250000,"previewMaxChunks":10,"previewMaxCharactersPerChunk":500,"evidenceRetentionDays":30,"commandRetentionDays":30,"retentionBatchSize":100,"allowedMetadataKeys":["originalFilename","locale","sourceCategory","publicationStatus"],"initialIndexRequiresConfirmation":true,"trustedAutoIndexingAllowed":false}}],"knowledgeSources":[{"sourceKey":"approved-support-documents","sourceType":"deployment-private-vector","adapterType":"deployment-private-vector","datasetRef":"support-documents","entityType":"document","attributionLabel":"Approved support guides and policies","filters":{"datasetId":"support-documents","publicationStatus":"PUBLIC_APPROVED"},"authModes":["PRIVATE_RUNTIME_BACKEND_MEDIATED","PUBLIC_RUNTIME_AUTHENTICATED","PUBLIC_RUNTIME_ANONYMOUS"]}]}}
        """;

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<String, Version> documents = new LinkedHashMap<>();
        for (String plugin : DOCUMENT_PLUGINS) {
            Version source = latest(connection, plugin);
            if (source == null && plugin.equals("mkp-data-ai-support-documents")) {
                seedSupportPlugin(connection);
                documents.put(plugin, insert(connection, plugin, "1.1.0", "GA", "mpub-loom",
                    object(SUPPORT_MANIFEST), "doc231"));
            } else if (source != null) {
                if (neutral(source.manifest())) {
                    documents.put(plugin, source);
                } else {
                    ObjectNode manifest = source.manifest().deepCopy();
                    for (JsonNode entry : manifest.path("contributions").path("datasets")) {
                        if (!"EXTERNAL_DOCUMENT_STORAGE".equals(entry.path("ingestionMode").asText())) continue;
                        ObjectNode dataset = (ObjectNode) entry;
                        dataset.put("storageScope", "CUSTOMER_SCOPED");
                        ((ObjectNode) dataset.path("sourceConnector")).put("storageOwnership", "BOUND_CONNECTION");
                    }
                    if (!neutral(manifest)) throw new IllegalStateException("Document DATA lacks the expected source contract: " + plugin);
                    String name = manifest.path("displayName").asText();
                    if (name.equals("Document Knowledge - Customer Storage")) manifest.put("displayName", "Document Knowledge - Bound Storage");
                    if (name.equals("Dealership Knowledge - Customer Storage")) manifest.put("displayName", "Dealership Knowledge - Bound Storage");
                    if (manifest.has("description")) manifest.put("description",
                        "Approved documents in customer-scoped object storage with a read-only bound connection.");
                    for (JsonNode field : manifest.path("installForm")) {
                        if ("documentStorageBindingRef".equals(field.path("id").asText())) {
                            ((ObjectNode) field).put("description",
                                "Customer-scoped read-only storage connection, hosted by LoomAI or supplied by the customer.");
                        }
                    }
                    documents.put(plugin, insert(connection, plugin, nextMinor(connection, plugin, source.version()),
                        source.channel(), source.publisher(), manifest, "doc231"));
                }
            }
        }
        for (String plugin : TEMPLATES) {
            Version source = latest(connection, plugin);
            if (source == null) continue;
            ObjectNode manifest = reviseTemplate(source.manifest(), documents);
            if (!manifest.equals(source.manifest())) {
                insert(connection, plugin, nextMinor(connection, plugin, source.version()),
                    source.channel(), source.publisher(), manifest, "doc231");
            }
        }
        label(connection, DOCUMENT_PLUGINS.get(0), "Document Knowledge - Bound Storage");
        label(connection, DOCUMENT_PLUGINS.get(1), "Dealership Knowledge - Bound Storage");
    }

    static ObjectNode reviseTemplate(ObjectNode original, Map<String, Version> documents) {
        ObjectNode manifest = original.deepCopy();
        JsonNode node = manifest.at("/contributions/template");
        if (!(node instanceof ObjectNode template)) return manifest;
        JsonNode refs = template.path("requiredPluginRefs");
        if (refs instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                String reference = array.get(i).asText();
                String replacement = revisedReference(reference, documents);
                if (!reference.equals(replacement)) array.set(i, JSON.getNodeFactory().textNode(replacement));
            }
        }
        if (template.path("requiredPluginInstallDefaults") instanceof ObjectNode defaults) {
            List<String> keys = new ArrayList<>();
            defaults.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                String replacement = revisedReference(key, documents);
                if (!key.equals(replacement)) {
                    if (defaults.has(replacement)) throw new IllegalStateException("Duplicate revised document install defaults.");
                    defaults.set(replacement, defaults.remove(key));
                }
            }
        }
        return manifest;
    }

    private static String revisedReference(String reference, Map<String, Version> documents) {
        int separator = reference.lastIndexOf('@');
        Version document = separator < 0 ? null : documents.get(reference.substring(0, separator));
        return document == null ? reference : document.plugin() + "@" + document.version();
    }

    static boolean neutral(ObjectNode manifest) {
        boolean found = false;
        for (JsonNode dataset : manifest.path("contributions").path("datasets")) {
            if (!"EXTERNAL_DOCUMENT_STORAGE".equals(dataset.path("ingestionMode").asText())) continue;
            found = true;
            if (!"CUSTOMER_SCOPED".equals(dataset.path("storageScope").asText())
                || !"BOUND_CONNECTION".equals(dataset.at("/sourceConnector/storageOwnership").asText())) return false;
        }
        return found;
    }

    static Version latest(Connection connection, String plugin) throws Exception {
        return published(connection, plugin).stream().max(Comparator.comparing(Version::publishedAt)
            .thenComparing(version -> org.flywaydb.core.api.MigrationVersion.fromVersion(version.version()))).orElse(null);
    }

    static List<Version> published(Connection connection, String plugin) throws Exception {
        List<Version> result = new ArrayList<>();
        try (var query = connection.prepareStatement("""
            select id, plugin_id, version, release_channel, submitted_by_publisher_id, manifest_json, published_at
            from platform_marketplace_plugin_versions where plugin_id = ? and status = 'PUBLISHED'
            """)) {
            query.setString(1, plugin);
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(new Version(rows.getString(1), rows.getString(2), rows.getString(3),
                    rows.getString(4), rows.getString(5), object(rows.getString(6)), rows.getTimestamp(7).toInstant()));
            }
        }
        return result;
    }

    static Version byId(Connection connection, String id) throws Exception {
        try (var query = connection.prepareStatement("select plugin_id from platform_marketplace_plugin_versions where id = ? and status = 'PUBLISHED'")) {
            query.setString(1, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Published bootstrap pin is missing: " + id);
                return published(connection, rows.getString(1)).stream().filter(version -> version.id().equals(id)).findFirst().orElseThrow();
            }
        }
    }

    static String nextMinor(Connection connection, String plugin, String source) throws Exception {
        String[] parts = source.split("\\.");
        int minor = Integer.parseInt(parts[1]) + 1;
        try (var query = connection.prepareStatement("select count(*) from platform_marketplace_plugin_versions where plugin_id = ? and version = ?")) {
            while (true) {
                String version = parts[0] + "." + minor++ + ".0";
                query.setString(1, plugin);
                query.setString(2, version);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) == 0) return version;
                }
            }
        }
    }

    static Version insert(Connection connection, String plugin, String number, String channel,
                          String publisher, ObjectNode manifest, String revision) throws Exception {
        manifest.put("version", number);
        String id = "mkv-" + revision + "-" + hash(JSON.getNodeFactory().textNode(plugin + "@" + number)).substring(0, 32);
        Instant now = Instant.now();
        try (var query = connection.prepareStatement("""
            insert into platform_marketplace_plugin_versions
              (id, plugin_id, version, release_channel, status, manifest_json, submitted_by_publisher_id,
               submitted_by_actor_id, reviewed_by_actor_id, reviewed_at, review_notes, bundle_sha256, created_at, published_at)
            values (?, ?, ?, ?, 'PUBLISHED', ?, ?, 'system', 'system', ?, ?, ?, ?, ?)
            """)) {
            query.setString(1, id);
            query.setString(2, plugin);
            query.setString(3, number);
            query.setString(4, channel);
            query.setString(5, JSON.writeValueAsString(manifest));
            query.setString(6, publisher);
            query.setTimestamp(7, Timestamp.from(now));
            query.setString(8, "010.59: additive customer-scoped document bootstrap revision; bound ownership, read-only source access. Historical versions and deployment associations are unchanged.");
            query.setString(9, hash(manifest));
            query.setTimestamp(10, Timestamp.from(now));
            query.setTimestamp(11, Timestamp.from(now));
            query.executeUpdate();
        }
        return new Version(id, plugin, number, channel, publisher, manifest, now);
    }

    static ObjectNode object(String json) throws Exception {
        JsonNode node = JSON.readTree(json);
        if (!(node instanceof ObjectNode object)) throw new IllegalStateException("Expected an object in published bootstrap JSON.");
        return object;
    }

    // Same recursive key ordering as CustomerOfferCodec, for native immutable pin fingerprints.
    static String hash(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(JSON.writeValueAsString(canonical(node)).getBytes(StandardCharsets.UTF_8)));
    }

    private static JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), canonical(entry.getValue())));
            ObjectNode object = JSON.createObjectNode();
            fields.forEach(object::set);
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JSON.createArrayNode();
            node.forEach(child -> array.add(canonical(child)));
            return array;
        }
        return node;
    }

    private static void seedSupportPlugin(Connection connection) throws Exception {
        try (var query = connection.prepareStatement("""
            insert into platform_marketplace_plugins
              (id, slug, display_name, plugin_type, publisher_slug, publisher_display_name, short_description, status, created_at, updated_at)
            select 'mkp-data-ai-support-documents', 'ai-support-documents', 'Approved support documents', 'DATA',
              'loom', 'Loom AI', 'Approved support documents in customer-scoped bound storage.', 'ACTIVE', current_timestamp, current_timestamp
            where not exists (select 1 from platform_marketplace_plugins where id = 'mkp-data-ai-support-documents')
            """)) {
            query.executeUpdate();
        }
    }

    private static void label(Connection connection, String plugin, String name) throws Exception {
        try (var query = connection.prepareStatement("""
            update platform_marketplace_plugins set display_name = ?, short_description = ?, updated_at = current_timestamp where id = ?
            """)) {
            query.setString(1, name);
            query.setString(2, "Approved documents in customer-scoped S3-compatible storage, hosted or BYO.");
            query.setString(3, plugin);
            query.executeUpdate();
        }
    }

    record Version(String id, String plugin, String version, String channel, String publisher, ObjectNode manifest, Instant publishedAt) { }
}
