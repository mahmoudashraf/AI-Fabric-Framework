package com.ai.fabric.vectorization.adapter.source;

import com.ai.fabric.integration.discovery.DiscoveryCountMethod;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationSourcePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FileVectorizationSourceAdapter implements VectorizationSourceAdapter {

    private final ObjectMapper objectMapper;

    public FileVectorizationSourceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String adapterType) {
        return "FILE".equalsIgnoreCase(adapterType);
    }

    @Override
    public VectorizationDiscoveryResult discover(VectorizationExecutionBundle bundle, List<String> entityTypes) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, com.ai.fabric.integration.discovery.DiscoveryCountMethod> methods = new LinkedHashMap<>();
        for (String entityType : entityTypes) {
            counts.put(entityType, (long) loadRecords(bundle, entityType).size());
            methods.put(entityType, DiscoveryCountMethod.EXACT);
        }
        return new VectorizationDiscoveryResult(counts, methods);
    }

    @Override
    public VectorizationSourcePage fetchPage(VectorizationExecutionBundle bundle,
                                             String entityType,
                                             String cursor,
                                             int pageSize) throws Exception {
        List<JsonNode> records = loadRecords(bundle, entityType);
        int offset = StringUtils.hasText(cursor) ? Integer.parseInt(cursor) : 0;
        int limit = Math.max(pageSize, 1);
        int end = Math.min(offset + limit, records.size());
        List<JsonNode> page = offset >= records.size() ? List.of() : records.subList(offset, end);
        boolean hasMore = end < records.size();
        return new VectorizationSourcePage(List.copyOf(page), hasMore ? Integer.toString(end) : null, hasMore);
    }

    private List<JsonNode> loadRecords(VectorizationExecutionBundle bundle, String entityType) throws IOException {
        JsonNode datasetConfig = VectorizationSourceAdapterSupport.datasetConfig(bundle, entityType);
        String filePath = VectorizationSourceAdapterSupport.text(datasetConfig, "filePath", null);
        if (!StringUtils.hasText(filePath)) {
            filePath = VectorizationSourceAdapterSupport.text(datasetConfig, "path", null);
        }
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("FILE vectorization source requires filePath or path.");
        }
        String format = VectorizationSourceAdapterSupport.text(datasetConfig, "format", "JSON_ARRAY").toUpperCase();
        Path path = Path.of(filePath.trim());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Vectorization source file does not exist: " + path);
        }
        return switch (format) {
            case "CSV" -> readCsv(path);
            case "JSON_LINES", "JSONL" -> readJsonLines(path);
            default -> readJsonArray(path);
        };
    }

    private List<JsonNode> readJsonArray(Path path) throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(path));
        if (!root.isArray()) {
            throw new IllegalArgumentException("JSON_ARRAY file must contain a top-level array.");
        }
        ArrayNode array = (ArrayNode) root;
        List<JsonNode> records = new ArrayList<>(array.size());
        array.forEach(records::add);
        return records;
    }

    private List<JsonNode> readJsonLines(Path path) throws IOException {
        List<JsonNode> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path);
             MappingIterator<JsonNode> iterator = objectMapper.readerFor(JsonNode.class).readValues(reader)) {
            while (iterator.hasNext()) {
                records.add(iterator.next());
            }
        }
        return records;
    }

    private List<JsonNode> readCsv(Path path) throws IOException {
        List<JsonNode> records = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                ObjectNode object = objectMapper.createObjectNode();
                for (Map.Entry<String, Integer> header : parser.getHeaderMap().entrySet()) {
                    object.put(header.getKey(), row.get(header.getValue()));
                }
                records.add(object);
            }
        }
        return records;
    }
}
