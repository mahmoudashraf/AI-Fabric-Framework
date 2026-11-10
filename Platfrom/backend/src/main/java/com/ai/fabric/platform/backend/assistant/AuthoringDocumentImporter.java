package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.yaml.snakeyaml.LoaderOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Inert, untrusted authoring input, never an executable API definition or trusted model instruction.
 * Byte/depth/node/expansion limits protect parser resources; redaction protects provider disclosure.
 * No parser location APIs, service-loaded parser extensions, or external reference resolvers are used.
 * Qualified source: https://github.com/swagger-api/swagger-parser/tree/v2.1.48
 */
@Component
public final class AuthoringDocumentImporter {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_CONTENT_CHARS = 200_000;
    public static final int MAX_PDF_PAGES = 50;
    public static final int MAX_OPERATIONS = 500;
    public static final int MAX_QUERY_CHARS = 8_000;
    private static final int MAX_NODES = 100_000;
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final Set<String> YAML_TYPES = Set.of("application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml",
            "application/vnd.oai.openapi", "application/vnd.oai.openapi+yaml");
    private static final Set<String> TEXT_TYPES = Set.of("text/plain", "text/markdown", "text/x-markdown",
            "application/x-curl", "text/x-shellscript");
    private final ObjectMapper json;
    private final ObjectMapper yaml;

    public AuthoringDocumentImporter(ObjectMapper objectMapper) {
        // Copies isolate importer limits from the application's shared mapper configuration.
        json = Objects.requireNonNull(objectMapper, "objectMapper").copy();
        configure(json);
        LoaderOptions loader = new LoaderOptions();
        loader.setCodePointLimit(MAX_FILE_BYTES);
        loader.setNestingDepthLimit(64);
        loader.setMaxAliasesForCollections(0);
        yaml = new ObjectMapper(YAMLFactory.builder().loaderOptions(loader).build());
        configure(yaml);
    }

    private static void configure(ObjectMapper mapper) {
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(64).maxStringLength(MAX_FILE_BYTES).maxNumberLength(1000).build());
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.getFactory().disable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);
    }

    /**
     * Invalid/unsupported input throws IllegalArgumentException with a payload-free message.
     * Useful partial imports return warnings. sourcePointer is an original-document fragment;
     * prose/PDF content contains [line:N] / [page:N] anchors. Truncation is never silent.
     */
    public ImportResult importDocument(String sourceId, String title, String mediaType, byte[] bytes) {
        requireMetadata(sourceId, "sourceId");
        requireMetadata(title, "title");
        requireMetadata(mediaType, "mediaType");
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Document is empty.");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Document exceeds the 2097152-byte (2 MiB) import limit.");
        }
        String type = mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();
        if (type.equals("application/pdf")) {
            String text = AuthoringDocumentPdf.extract(bytes, warnings);
            text = new AuthoringDocumentCredentials().redactText(text, warnings);
            return result(sourceId, title, type, text, json.createArrayNode(), warnings);
        }
        String text = decode(bytes);
        if (TEXT_TYPES.contains(type)) {
            text = new AuthoringDocumentCredentials().redactText(text, warnings);
            warnings.add("Prose/examples only; no formal API operations inferred or executed.");
            return result(sourceId, title, type, lineAnchors(text), json.createArrayNode(), warnings);
        }
        boolean isYaml = YAML_TYPES.contains(type);
        if (!supportedMediaType(type)) {
            throw new IllegalArgumentException("Unsupported document media type; upload JSON, YAML, text, Markdown or text PDF.");
        }
        JsonNode input;
        try {
            preflight(text, isYaml ? yaml : json);
            input = (isYaml ? yaml : json).readTree(text);
            checkNodes(input, new int[1]);
        } catch (IOException | RuntimeException ex) {
            // Parser exception messages/causes can contain source snippets or credentials.
            throw new IllegalArgumentException("Malformed JSON/YAML or parser resource limit exceeded; aliases, duplicate keys and extra documents are unsupported.");
        }
        AuthoringDocumentCredentials credentials = new AuthoringDocumentCredentials();
        JsonNode safe = credentials.redact(input, warnings);
        boolean formal = input.isObject() && (input.has("openapi") || input.has("swagger"));
        if (!formal) {
            warnings.add("JSON/YAML sample only; no formal API operations inferred or executed.");
            return result(sourceId, title, type, serialize(safe), json.createArrayNode(), warnings);
        }
        String version = input.path("openapi").asText();
        boolean swagger2 = input.path("swagger").asText().equals("2.0");
        if (!swagger2 && !version.matches("3\\.[01]\\.[0-9]+")) {
            throw new IllegalArgumentException("Unsupported API version; expected Swagger 2.0 or OpenAPI 3.0.x/3.1.x.");
        }
        SwaggerParseResult parsed;
        try {
            ParseOptions options = offlineOptions();
            // parseJsonNode bypasses the parser's content/location readers and ALL resolution code.
            parsed = swagger2 ? new SwaggerConverter().readContents(serialize(safe), List.of(), options)
                    : new OpenAPIV3Parser().parseJsonNode(null, safe, options);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("API document could not be parsed; provide a valid sanitized specification.");
        }
        if (parsed == null || parsed.getOpenAPI() == null) {
            throw new IllegalArgumentException("API document could not be parsed; provide a valid sanitized specification.");
        }
        if (parsed.getMessages() != null && !parsed.getMessages().isEmpty()) {
            warnings.add("Formal parser reported " + parsed.getMessages().size()
                    + " diagnostic(s); raw diagnostics withheld because they may contain document values. Import is not API validation.");
        }
        if (swagger2) {
            warnings.add("Swagger 2.0 converted to OpenAPI 3.0 by Swagger Parser; source pointers refer to the original Swagger document.");
        }
        ObjectMapper modelMapper = version.startsWith("3.1.") ? Json31.mapper() : Json.mapper();
        JsonNode model = modelMapper.valueToTree(parsed.getOpenAPI());
        model = credentials.redact(model, warnings);
        AuthoringDocumentReferences references = new AuthoringDocumentReferences(model, warnings);
        references.inspect(safe);
        ArrayNode operations = operations(sourceId, safe, model, references, warnings);
        return result(sourceId, title, type, serialize(safe), operations, warnings);
    }

    /** Explicit opt-in only: caller must authorize the source and gate this method with its fetch flag. */
    public ImportResult fetchPublicDocument(String sourceId, String url) {
        requireMetadata(sourceId, "sourceId");
        var document = new AuthoringDocumentPublicFetcher().fetch(url);
        return importDocument(sourceId, "Public documentation", document.mediaType(), document.bytes());
    }

    static boolean supportedMediaType(String type) {
        return type.equals("application/pdf") || TEXT_TYPES.contains(type) || YAML_TYPES.contains(type)
                || type.equals("application/json") || type.endsWith("+json");
    }

    /** Redacts query/turn text before persistence/sharing; rejects oversize input without silent truncation. */
    public String redactText(String text) {
        if (text == null || text.length() > MAX_QUERY_CHARS) {
            throw new IllegalArgumentException("Query text must be non-null and at most 8000 characters.");
        }
        String safe = new AuthoringDocumentCredentials().redactText(text, new ArrayList<>());
        if (safe.length() > MAX_QUERY_CHARS) {
            throw new IllegalArgumentException("Redacted query exceeds 8000 characters; provide a shorter sanitized query.");
        }
        return safe;
    }

    private static ParseOptions offlineOptions() {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        options.setFlatten(false);
        options.setResolveCombinators(false);
        options.setResolveRequestBody(false);
        options.setResolveResponses(false);
        options.setValidateExternalRefs(false);
        options.setValidateInternalRefs(false);
        return options;
    }

    private ArrayNode operations(String sourceId, JsonNode original, JsonNode model,
                                 AuthoringDocumentReferences refs, List<String> warnings) {
        ArrayNode result = json.createArrayNode();
        Map<String, Integer> ids = new HashMap<>();
        Set<String> canonicalIds = new java.util.HashSet<>();
        var countedPaths = model.path("paths").fields();
        while (countedPaths.hasNext()) {
            var countedPath = countedPaths.next();
            var methods = refs.expand(countedPath.getValue()).fields();
            while (methods.hasNext()) {
                var entry = methods.next();
                if (METHODS.contains(entry.getKey()) && entry.getValue().isObject()) {
                    canonicalIds.add(sourceId + "#" + entry.getKey().toUpperCase(Locale.ROOT) + " " + countedPath.getKey());
                    if (entry.getValue().hasNonNull("operationId")) {
                        ids.merge(entry.getValue().path("operationId").asText(), 1, Integer::sum);
                    }
                }
            }
        }
        int total = 0;
        int chars = 2;
        var paths = model.path("paths").fields();
        while (paths.hasNext()) {
            var pathEntry = paths.next();
            JsonNode path = refs.expand(pathEntry.getValue());
            for (String method : List.of("get", "put", "post", "delete", "options", "head", "patch", "trace")) {
                JsonNode operation = path.path(method);
                if (!operation.isObject()) {
                    continue;
                }
                total++;
                String operationId = operation.path("operationId").asText();
                String canonicalId = sourceId + "#" + method.toUpperCase(Locale.ROOT) + " " + pathEntry.getKey();
                String id = !operationId.isBlank() && ids.getOrDefault(operationId, 0) == 1
                        && (!canonicalIds.contains(operationId) || operationId.equals(canonicalId)) ? operationId : canonicalId;
                ObjectNode item = json.createObjectNode();
                item.put("id", id);
                item.put("method", method.toUpperCase(Locale.ROOT));
                item.put("path", pathEntry.getKey());
                item.put("summary", operation.path("summary").asText(""));
                item.put("description", operation.path("description").asText(""));
                item.set("parameters", mergedParameters(path.path("parameters"), operation.path("parameters")));
                item.set("requestBody", operation.has("requestBody") ? operation.get("requestBody") : json.nullNode());
                item.set("responses", operation.has("responses") ? operation.get("responses") : json.createObjectNode());
                item.set("security", operation.has("security") ? operation.get("security")
                        : model.has("security") ? model.get("security").deepCopy() : json.createArrayNode());
                item.put("sourcePointer", sourcePointer(original, pathEntry.getKey(), method));
                int size = serialize(item).length() + 1;
                if (result.size() < MAX_OPERATIONS && chars + size <= MAX_CONTENT_CHARS) {
                    result.add(item);
                    chars += size;
                }
            }
        }
        if (total > result.size()) {
            warnings.add("Operation catalogue truncated: retained " + result.size() + " of " + total + "; omitted "
                    + (total - result.size()) + " operation(s) at the 500-operation/200000-character catalogue limit.");
        }
        if (ids.values().stream().anyMatch(count -> count > 1)) {
            warnings.add("Duplicate operation IDs use sourceId + HTTP method + path identities.");
        }
        return result;
    }

    private ArrayNode mergedParameters(JsonNode inherited, JsonNode own) {
        Map<String, JsonNode> parameters = new LinkedHashMap<>();
        for (JsonNode list : List.of(inherited, own)) {
            if (list.isArray()) {
                for (JsonNode parameter : list) {
                    String key = parameter.has("name") ? parameter.path("in").asText() + ":" + parameter.path("name").asText()
                            : serialize(parameter);
                    parameters.put(key, parameter);
                }
            }
        }
        return json.createArrayNode().addAll(new ArrayList<>(parameters.values()));
    }

    private static String sourcePointer(JsonNode original, String path, String method) {
        String pointer = "#/paths/" + path.replace("~", "~0").replace("/", "~1");
        Set<String> visited = new java.util.HashSet<>();
        JsonNode item = original.path("paths").path(path);
        while (item.path("$ref").isTextual() && visited.size() < 32) {
            String ref = item.path("$ref").asText();
            if (!ref.startsWith("#/") || !visited.add(ref)) {
                break;
            }
            try {
                item = AuthoringDocumentReferences.localTarget(original, ref);
                pointer = ref;
            } catch (IllegalArgumentException ex) {
                break;
            }
        }
        return pointer + "/" + method;
    }

    private static void checkNodes(JsonNode node, int[] count) {
        if (node == null || ++count[0] > MAX_NODES) {
            throw new IllegalArgumentException("Document node limit exceeded.");
        }
        node.elements().forEachRemaining(child -> checkNodes(child, count));
    }

    private static void preflight(String text, ObjectMapper mapper) throws IOException {
        try (JsonParser parser = mapper.createParser(text)) {
            int tokens = 0;
            while (parser.nextToken() != null) {
                if (++tokens > MAX_NODES * 2) {
                    throw new IllegalArgumentException("Document token limit exceeded.");
                }
                if (parser instanceof YAMLParser yamlParser) {
                    String tag = yamlParser.getTypeId();
                    if (yamlParser.isCurrentAlias() || (tag != null && !Set.of("tag:yaml.org,2002:str", "tag:yaml.org,2002:map",
                            "tag:yaml.org,2002:seq", "tag:yaml.org,2002:int", "tag:yaml.org,2002:float", "tag:yaml.org,2002:bool",
                            "tag:yaml.org,2002:null").contains(tag))) {
                        throw new IllegalArgumentException("YAML aliases and custom tags are unsupported.");
                    }
                }
            }
        }
    }

    private static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("Text documents must be UTF-8.");
        }
    }

    private static void requireMetadata(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException(name + " must be nonblank and at most 512 characters.");
        }
    }

    private String serialize(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Document cannot be serialized safely.");
        }
    }

    private static String lineAnchors(String text) {
        StringBuilder result = new StringBuilder();
        int line = 1;
        for (String value : text.split("\\R", -1)) {
            result.append("[line:").append(line++).append("] ").append(value).append('\n');
        }
        return result.toString();
    }

    private static ImportResult result(String sourceId, String title, String type, String content,
                                       ArrayNode operations, List<String> warnings) {
        if (content.length() > MAX_CONTENT_CHARS) {
            int end = MAX_CONTENT_CHARS;
            if (Character.isHighSurrogate(content.charAt(end - 1))) {
                end--;
            }
            warnings.add("Content truncated: retained " + end + " of " + content.length() + " characters; omitted "
                    + (content.length() - end) + " characters at the 200000-character content limit (including source anchors).");
            content = content.substring(0, end);
        }
        return new ImportResult(sourceId, title, type, content, operations, List.copyOf(new java.util.LinkedHashSet<>(warnings)));
    }

    public record ImportResult(String sourceId, String title, String mediaType, String content,
                               JsonNode operations, List<String> warnings) { }
}
