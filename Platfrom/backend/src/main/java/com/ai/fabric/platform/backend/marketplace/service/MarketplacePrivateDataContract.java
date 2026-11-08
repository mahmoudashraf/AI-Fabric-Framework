package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.model.CustomerPluginLibraryContracts.Issue;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Private definitions cannot use Platform SQL, classpath resources or server filesystem authority. */
public final class MarketplacePrivateDataContract {
    private MarketplacePrivateDataContract() { }

    public static List<Issue> issues(JsonNode manifest) {
        List<Issue> issues = new ArrayList<>();
        JsonNode datasets = manifest.path("contributions").path("datasets");
        if (!datasets.isArray() || datasets.isEmpty()) {
            return List.of(new Issue("/contributions/datasets", "PRIVATE_DATASET_REQUIRED",
                "Private sources require a declared HTTP_JSON or bound S3 dataset."));
        }
        for (int index = 0; index < datasets.size(); index++) {
            issues.addAll(datasetIssues(datasets.path(index), "/contributions/datasets/" + index));
        }
        var datasetIds = new java.util.HashSet<String>();
        datasets.forEach(dataset -> datasetIds.add(dataset.path("datasetId").asText()));
        JsonNode sources = manifest.path("contributions").path("knowledgeSources");
        for (int index = 0; index < sources.size(); index++) {
            JsonNode handle = sources.path(index).path("handleRef");
            if (!handle.isMissingNode() && !handle.isNull() && (!handle.isTextual() || !handle.asText().isBlank())) {
                issues.add(new Issue("/contributions/knowledgeSources/" + index + "/handleRef", "PRIVATE_DATASET_HANDLE_DERIVED",
                    "Private source handles are derived from the declared dataset and authorized installation; remove the explicit handleRef."));
            }
            if (!datasetIds.contains(sources.path(index).path("datasetRef").asText())) {
                issues.add(new Issue("/contributions/knowledgeSources/" + index + "/datasetRef", "PRIVATE_DATASET_BINDING_REQUIRED",
                    "Private knowledge sources must bind to a declared supported dataset, not an arbitrary source handle."));
            }
        }
        return List.copyOf(issues);
    }

    public static void requireSupported(MarketplacePluginEntity plugin, JsonNode manifest) {
        if (!MarketplacePluginOwnership.isPrivate(plugin)) return;
        List<Issue> issues = issues(manifest);
        if (!issues.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, issues.get(0).path() + ": " + issues.get(0).message());
    }

    public static void requireCompiledDataset(MarketplacePluginEntity plugin, JsonNode dataset) {
        if (!MarketplacePluginOwnership.isPrivate(plugin)) return;
        List<Issue> issues = datasetIssues(dataset, "/marketplaceDatasetConfig/datasets");
        if (!issues.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, issues.get(0).message());
    }

    private static List<Issue> datasetIssues(JsonNode dataset, String path) {
        String mode = dataset.path("ingestionMode").asText();
        if ("EXTERNAL_SYNC_HTTP".equals(mode) && "HTTP_JSON".equals(dataset.path("syncConnector").path("connectorType").asText())) {
            return List.of();
        }
        if ("EXTERNAL_DOCUMENT_STORAGE".equals(mode)) {
            JsonNode connector = dataset.path("sourceConnector");
            if ("S3_COMPATIBLE_OBJECT_STORAGE".equals(connector.path("connectorType").asText())
                && "BOUND_CONNECTION".equals(connector.path("storageOwnership").asText())) return List.of();
            return List.of(new Issue(path + "/sourceConnector", "PRIVATE_BOUND_STORAGE_REQUIRED",
                "Private document sources require S3_COMPATIBLE_OBJECT_STORAGE with BOUND_CONNECTION; server filesystem and unowned storage access are unavailable."));
        }
        return List.of(new Issue(path + "/ingestionMode", "PRIVATE_ADAPTER_UNAVAILABLE",
            "Private reusable sources support HTTP_JSON or bound S3 document storage only. SQL has no customer-owned executable binding; folder and packaged sources use Platform filesystem/classpath authority."));
    }
}
