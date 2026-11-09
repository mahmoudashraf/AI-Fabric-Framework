package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adds evidence fields to new immutable plugin revisions, without upgrading installed deployments. */
public class V239__dealership_inventory_evidence_contracts extends BaseJavaMigration {
    private static final Map<String, String> PROVIDER_FIELDS = Map.of(
        "electricRangeMiles", "/vehicle/batteryRangeMiles",
        "seats", "/vehicle/seats",
        "location", "/advertiser/location/town",
        "reservationStatus", "/adverts/reservationStatus",
        "sourceUpdatedAt", "/metadata/lastUpdated",
        "sourceUpdatedByAdvertiserAt", "/metadata/lastUpdatedByAdvertiser",
        "dateOnForecourt", "/metadata/dateOnForecourt");
    private static final List<String> FIELDS = List.of("features", "electricRangeMiles", "seats", "location",
        "reservationStatus", "sourceUpdatedAt", "sourceUpdatedByAdvertiserAt", "dateOnForecourt");
    private static final List<String> TEMPLATES = List.of("mkp-template-simulated-dealership-experience-v1",
        "mkp-template-dealership-external-systems-v1", "mkp-template-dealership-external-systems-managed-inbox-v1");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Map<String, V231__customer_scoped_document_contract_revisions.Version> revised = new LinkedHashMap<>();
        for (String plugin : List.of("mkp-data-dealership-external-stock-v1", "mkp-data-simulated-dealership-stock-v1",
            "mkp-action-dealership-external-discovery-v1", "mkp-action-simulated-dealership-discovery-v1")) {
            var source = V231__customer_scoped_document_contract_revisions.latest(connection, plugin);
            if (source == null) continue;
            ObjectNode manifest = source.manifest().deepCopy();
            if (plugin.startsWith("mkp-data-")) {
                for (JsonNode dataset : manifest.at("/contributions/datasets")) {
                    ObjectNode mapping = (ObjectNode) dataset.at("/syncConnector/httpSource/mapping");
                    for (String section : List.of("contentFields", "entityFields")) {
                        ObjectNode fields = mapping.with(section);
                        PROVIDER_FIELDS.forEach(fields::put);
                    }
                    ObjectNode metadata = mapping.with("metadataFields");
                    metadata.put("sourceUpdatedByAdvertiserAt", PROVIDER_FIELDS.get("sourceUpdatedByAdvertiserAt"));
                    metadata.put("dateOnForecourt", PROVIDER_FIELDS.get("dateOnForecourt"));
                }
            } else {
                for (JsonNode action : manifest.at("/contributions/actions")) {
                    append((ArrayNode) action.at("/route/sourceProjection/outputFields"));
                    for (JsonNode collection : action.at("/llmFacts/lists")) {
                        append((ArrayNode) collection.path("includeFields"));
                    }
                    for (JsonNode object : action.at("/llmFacts/objects")) {
                        append((ArrayNode) object.path("includeFields"));
                    }
                    JsonNode entityFields = action.at("/route/response/pinnedTargetsFromCollection/contentFields");
                    if (entityFields instanceof ObjectNode fields) FIELDS.forEach(field -> fields.put(field, "/" + field));
                }
            }
            if (!manifest.equals(source.manifest())) revised.put(plugin, insert(connection, source, manifest));
        }
        for (String plugin : TEMPLATES) {
            var source = V231__customer_scoped_document_contract_revisions.latest(connection, plugin);
            if (source == null) continue;
            ObjectNode manifest = V231__customer_scoped_document_contract_revisions.reviseTemplate(source.manifest(), revised);
            if (!manifest.equals(source.manifest())) insert(connection, source, manifest);
        }
    }

    private static void append(ArrayNode fields) {
        for (String field : FIELDS) {
            boolean present = false;
            for (JsonNode value : fields) if (field.equals(value.asText())) present = true;
            if (!present) fields.add(field);
        }
    }

    private static V231__customer_scoped_document_contract_revisions.Version insert(Connection connection,
        V231__customer_scoped_document_contract_revisions.Version source, ObjectNode manifest) throws Exception {
        String version = V231__customer_scoped_document_contract_revisions.nextMinor(connection, source.plugin(), source.version());
        manifest.put("version", version);
        String id = "mkv-evidence239-" + V231__customer_scoped_document_contract_revisions.hash(
            V231__customer_scoped_document_contract_revisions.JSON.getNodeFactory().textNode(source.plugin() + "@" + version)).substring(0, 28);
        Instant now = Instant.now();
        try (var query = connection.prepareStatement("""
            insert into platform_marketplace_plugin_versions
              (id, plugin_id, version, release_channel, status, manifest_json, submitted_by_publisher_id,
               submitted_by_actor_id, reviewed_by_actor_id, reviewed_at, review_notes, bundle_sha256, created_at, published_at)
            values (?, ?, ?, ?, 'PUBLISHED', ?, ?, 'system', 'system', ?, ?, ?, ?, ?)
            """)) {
            query.setString(1, id);
            query.setString(2, source.plugin());
            query.setString(3, version);
            query.setString(4, source.channel());
            query.setString(5, manifest.toString());
            query.setString(6, source.publisher());
            query.setTimestamp(7, Timestamp.from(now));
            query.setString(8, "010.63: preserve provider-supplied inventory evidence through indexed content, read projections and presentation. Optional facts remain optional; no inferred range test basis, new authority or infrastructure. Existing installations and historical versions are unchanged.");
            query.setString(9, V231__customer_scoped_document_contract_revisions.hash(manifest));
            query.setTimestamp(10, Timestamp.from(now));
            query.setTimestamp(11, Timestamp.from(now));
            query.executeUpdate();
        }
        return new V231__customer_scoped_document_contract_revisions.Version(id, source.plugin(), version,
            source.channel(), source.publisher(), manifest, now);
    }
}
