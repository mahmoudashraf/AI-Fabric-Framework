package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.integration.discovery.DiscoveryCountMethod;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationSourcePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SqlVectorizationSourceAdapter implements VectorizationSourceAdapter {

    private final ObjectMapper objectMapper;

    public SqlVectorizationSourceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String adapterType) {
        return "SQL".equalsIgnoreCase(adapterType);
    }

    @Override
    public VectorizationDiscoveryResult discover(VectorizationExecutionBundle bundle, List<String> entityTypes) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, DiscoveryCountMethod> methods = new LinkedHashMap<>();
        for (String entityType : entityTypes) {
            JsonNode datasetConfig = VectorizationSourceAdapterSupport.datasetConfig(bundle, entityType);
            String countQuery = VectorizationSourceAdapterSupport.text(datasetConfig, "countQuery", null);
            if (!StringUtils.hasText(countQuery)) {
                counts.put(entityType, 0L);
                methods.put(entityType, DiscoveryCountMethod.UNKNOWN);
                continue;
            }
            try (Connection connection = openConnection(bundle, datasetConfig);
                 PreparedStatement statement = connection.prepareStatement(countQuery);
                 ResultSet resultSet = statement.executeQuery()) {
                counts.put(entityType, resultSet.next() ? resultSet.getLong(1) : 0L);
                methods.put(entityType, DiscoveryCountMethod.EXACT);
            }
        }
        return new VectorizationDiscoveryResult(counts, methods);
    }

    @Override
    public VectorizationSourcePage fetchPage(VectorizationExecutionBundle bundle,
                                             String entityType,
                                             String cursor,
                                             int pageSize) throws Exception {
        JsonNode datasetConfig = VectorizationSourceAdapterSupport.datasetConfig(bundle, entityType);
        String pageQuery = VectorizationSourceAdapterSupport.text(datasetConfig, "pageQuery", null);
        String baseQuery = StringUtils.hasText(pageQuery)
            ? pageQuery
            : VectorizationSourceAdapterSupport.text(datasetConfig, "query", null);
        if (!StringUtils.hasText(baseQuery)) {
            throw new IllegalArgumentException("SQL vectorization source requires pageQuery or query.");
        }
        int offset = StringUtils.hasText(cursor) ? Integer.parseInt(cursor) : 0;
        String sql = baseQuery
            .replace(":limit", Integer.toString(pageSize))
            .replace(":offset", Integer.toString(offset));
        if (sql.equals(baseQuery) && !baseQuery.toLowerCase().contains(" limit ")) {
            sql = baseQuery + " LIMIT " + pageSize + " OFFSET " + offset;
        }
        List<JsonNode> rows = new ArrayList<>();
        try (Connection connection = openConnection(bundle, datasetConfig);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            int columnCount = metadata.getColumnCount();
            while (resultSet.next()) {
                ObjectNode row = objectMapper.createObjectNode();
                for (int index = 1; index <= columnCount; index++) {
                    String column = metadata.getColumnLabel(index);
                    Object value = resultSet.getObject(index);
                    row.set(column, objectMapper.valueToTree(value));
                }
                rows.add(row);
            }
        }
        boolean hasMore = rows.size() >= pageSize;
        return new VectorizationSourcePage(rows, hasMore ? Integer.toString(offset + rows.size()) : null, hasMore);
    }

    private Connection openConnection(VectorizationExecutionBundle bundle, JsonNode datasetConfig) throws Exception {
        String jdbcUrl = VectorizationSourceAdapterSupport.text(datasetConfig, "jdbcUrl", bundle.connectionDescriptor().config().path("jdbcUrl").asText(""));
        if (!StringUtils.hasText(jdbcUrl)) {
            throw new IllegalArgumentException("SQL vectorization source requires jdbcUrl.");
        }
        String driverClass = VectorizationSourceAdapterSupport.text(datasetConfig, "driverClass", null);
        if (StringUtils.hasText(driverClass)) {
            Class.forName(driverClass);
        }
        Properties properties = new Properties();
        String username = bundle.sourceAuthMaterial().secret("username");
        String password = bundle.sourceAuthMaterial().secret("password");
        if (StringUtils.hasText(username)) {
            properties.setProperty("user", username);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }
        JsonNode additionalProperties = datasetConfig.path("connectionProperties");
        if (additionalProperties.isObject()) {
            additionalProperties.fields().forEachRemaining(entry -> properties.setProperty(entry.getKey(), entry.getValue().asText("")));
        }
        return DriverManager.getConnection(jdbcUrl, properties);
    }
}
