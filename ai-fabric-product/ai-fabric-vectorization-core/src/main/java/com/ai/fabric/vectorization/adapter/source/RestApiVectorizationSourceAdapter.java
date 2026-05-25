package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.integration.discovery.DiscoveryCountMethod;
import com.ai.fabric.integration.json.JsonNavigation;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationSourcePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RestApiVectorizationSourceAdapter implements VectorizationSourceAdapter {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RestApiVectorizationSourceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public boolean supports(String adapterType) {
        return "REST_API".equalsIgnoreCase(adapterType);
    }

    @Override
    public VectorizationDiscoveryResult discover(VectorizationExecutionBundle bundle, List<String> entityTypes) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, DiscoveryCountMethod> methods = new LinkedHashMap<>();
        for (String entityType : entityTypes) {
            JsonNode datasetConfig = VectorizationSourceAdapterSupport.datasetConfig(bundle, entityType);
            String recordVectorSpacePath = recordVectorSpacePath(bundle, datasetConfig, entityType);
            if (StringUtils.hasText(recordVectorSpacePath)) {
                discoverRecordVectorSpaces(bundle, entityType, datasetConfig, recordVectorSpacePath, counts, methods);
                continue;
            }
            JsonNode response = fetchResponse(bundle, datasetConfig, null, VectorizationSourceAdapterSupport.pageSize(datasetConfig, bundle.executionConfig(), 100));
            String countPath = VectorizationSourceAdapterSupport.text(datasetConfig, "countPath", null);
            if (StringUtils.hasText(countPath)) {
                counts.put(entityType, JsonNavigation.asLong(response, countPath, 0));
                methods.put(entityType, DiscoveryCountMethod.SOURCE_REPORTED);
                continue;
            }
            JsonNode records = recordsNode(response, datasetConfig);
            counts.put(entityType, (long) records.size());
            methods.put(entityType, DiscoveryCountMethod.EXACT);
        }
        return new VectorizationDiscoveryResult(counts, methods);
    }

    @Override
    public VectorizationSourcePage fetchPage(VectorizationExecutionBundle bundle,
                                             String entityType,
                                             String cursor,
                                             int pageSize) throws Exception {
        JsonNode datasetConfig = VectorizationSourceAdapterSupport.datasetConfig(bundle, entityType);
        JsonNode response = fetchResponse(bundle, datasetConfig, cursor, pageSize);
        JsonNode recordsNode = recordsNode(response, datasetConfig);
        List<JsonNode> records = recordsNode.isArray()
            ? objectMapper.convertValue(recordsNode, objectMapper.getTypeFactory().constructCollectionType(List.class, JsonNode.class))
            : List.of();
        String paginationMode = VectorizationSourceAdapterSupport.text(datasetConfig, "paginationMode", "OFFSET_LIMIT").toUpperCase(Locale.ROOT);
        String nextCursor = switch (paginationMode) {
            case "CURSOR" -> VectorizationSourceAdapterSupport.nextCursor(response, datasetConfig);
            case "PAGE_NUMBER" -> Integer.toString(StringUtils.hasText(cursor) ? Integer.parseInt(cursor) + 1 : datasetConfig.path("startingPage").asInt(1) + 1);
            case "OFFSET_LIMIT" -> Integer.toString((StringUtils.hasText(cursor) ? Integer.parseInt(cursor) : 0) + records.size());
            default -> null;
        };
        boolean hasMore = VectorizationSourceAdapterSupport.hasMore(response, datasetConfig, records.size(), pageSize, paginationMode);
        return new VectorizationSourcePage(records, hasMore ? nextCursor : null, hasMore);
    }

    private JsonNode fetchResponse(VectorizationExecutionBundle bundle,
                                   JsonNode datasetConfig,
                                   String cursor,
                                   int pageSize) throws Exception {
        String baseUrl = VectorizationSourceAdapterSupport.text(datasetConfig, "baseUrl", bundle.connectionDescriptor().config().path("baseUrl").asText(""));
        String path = VectorizationSourceAdapterSupport.text(datasetConfig, "path", null);
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(path)) {
            throw new IllegalArgumentException("REST_API vectorization source requires baseUrl and path.");
        }

        String paginationMode = VectorizationSourceAdapterSupport.text(datasetConfig, "paginationMode", "OFFSET_LIMIT").toUpperCase(Locale.ROOT);
        Map<String, String> query = new LinkedHashMap<>();
        int effectivePageSize = pageSize > 0 ? pageSize : VectorizationSourceAdapterSupport.pageSize(datasetConfig, bundle.executionConfig(), 100);
        switch (paginationMode) {
            case "PAGE_NUMBER" -> {
                String pageParam = VectorizationSourceAdapterSupport.text(datasetConfig, "pageParam", "page");
                String pageSizeParam = VectorizationSourceAdapterSupport.text(datasetConfig, "pageSizeParam", "limit");
                String page = StringUtils.hasText(cursor) ? cursor : Integer.toString(datasetConfig.path("startingPage").asInt(1));
                query.put(pageParam, page);
                query.put(pageSizeParam, Integer.toString(effectivePageSize));
            }
            case "CURSOR" -> {
                String cursorParam = VectorizationSourceAdapterSupport.text(datasetConfig, "cursorParam", "cursor");
                String pageSizeParam = VectorizationSourceAdapterSupport.text(datasetConfig, "pageSizeParam", "limit");
                if (StringUtils.hasText(cursor)) {
                    query.put(cursorParam, cursor);
                }
                query.put(pageSizeParam, Integer.toString(effectivePageSize));
            }
            case "NONE" -> {
            }
            default -> {
                String offsetParam = VectorizationSourceAdapterSupport.text(datasetConfig, "offsetParam", "offset");
                String pageSizeParam = VectorizationSourceAdapterSupport.text(datasetConfig, "pageSizeParam", "limit");
                query.put(offsetParam, StringUtils.hasText(cursor) ? cursor : "0");
                query.put(pageSizeParam, Integer.toString(effectivePageSize));
            }
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(VectorizationSourceAdapterSupport.buildUri(baseUrl, path, query))
            .timeout(Duration.ofSeconds(45))
            .GET()
            .header("Accept", "application/json");
        VectorizationSourceAdapterSupport.authHeaders(bundle.connectionDescriptor(), bundle.sourceAuthMaterial(), datasetConfig)
            .forEach(requestBuilder::header);
        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("REST_API source returned HTTP " + response.statusCode() + ".");
        }
        return StringUtils.hasText(response.body()) ? objectMapper.readTree(response.body()) : MissingNode.getInstance();
    }

    private JsonNode recordsNode(JsonNode response, JsonNode datasetConfig) {
        String itemsPath = VectorizationSourceAdapterSupport.text(datasetConfig, "itemsPath", null);
        return JsonNavigation.firstArrayCandidate(response, itemsPath);
    }

    private void discoverRecordVectorSpaces(VectorizationExecutionBundle bundle,
                                            String sourceEntityType,
                                            JsonNode datasetConfig,
                                            String recordVectorSpacePath,
                                            Map<String, Long> counts,
                                            Map<String, DiscoveryCountMethod> methods) throws Exception {
        int pageSize = VectorizationSourceAdapterSupport.pageSize(datasetConfig, bundle.executionConfig(), 100);
        int maxPages = datasetConfig.path("discoveryMaxPages").asInt(1000);
        String cursor = null;
        boolean hasMore;
        int pages = 0;
        boolean sawRecord = false;
        do {
            if (pages >= maxPages) {
                throw new IllegalStateException("REST_API discovery exceeded maxPages for source entity '" + sourceEntityType + "'.");
            }
            VectorizationSourcePage page = fetchPage(bundle, sourceEntityType, cursor, pageSize);
            for (JsonNode record : page.records()) {
                sawRecord = true;
                String vectorSpace = JsonNavigation.asText(record, recordVectorSpacePath);
                if (!StringUtils.hasText(vectorSpace)) {
                    throw new IllegalArgumentException(
                        "REST_API discovery record is missing vector space field '" + recordVectorSpacePath
                            + "' for source entity '" + sourceEntityType + "'."
                    );
                }
                String key = vectorSpace.trim();
                counts.merge(key, 1L, Long::sum);
                methods.put(key, DiscoveryCountMethod.EXACT);
            }
            cursor = page.nextCursor();
            hasMore = page.hasMore();
            pages++;
        } while (hasMore);
        if (!sawRecord) {
            counts.put(sourceEntityType, 0L);
            methods.put(sourceEntityType, DiscoveryCountMethod.EXACT);
        }
    }

    private String recordVectorSpacePath(VectorizationExecutionBundle bundle, JsonNode datasetConfig, String entityType) {
        String configured = VectorizationSourceAdapterSupport.text(datasetConfig, "recordVectorSpacePath", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        JsonNode mapping = bundle.mappingConfig().path("entityMappings").path(entityType);
        configured = VectorizationSourceAdapterSupport.text(mapping, "targetEntityTypeField", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return VectorizationSourceAdapterSupport.text(mapping, "vectorSpaceField", null);
    }
}
