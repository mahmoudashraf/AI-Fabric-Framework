package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Declared credential detector, not an intent classifier. Exact protocol credential names,
 * apiKey declarations, and schema format=password/writeOnly/x-sensitive/x-secret declarations
 * identify values. Ordinary business fields such as tokenCount or passwordPolicy are untouched.
 * It cannot certify arbitrary prose as secret-free; the caller still controls provider disclosure.
 */
final class AuthoringDocumentCredentials {
    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> STANDARD_FIELDS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-api-key", "api-key", "api_key", "apikey", "access_token", "refresh_token", "id_token", "client_secret",
            "client_assertion", "password", "passwd", "secret", "token");
    private static final Set<String> VALUE_FIELDS = Set.of("example", "examples", "default", "enum", "value", "x-example", "x-examples");
    private static final Set<String> HEADER_FIELDS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key");
    private static final Set<String> ID_MAPS = Set.of("security", "securitySchemes", "securityDefinitions", "schemas", "definitions");
    private static final Set<String> DECLARATION_MAPS = Set.of("properties", "headers", "parameters");
    private static final Pattern CURL_CREDENTIAL = Pattern.compile("(?<!\\S)((?:--user|--proxy-user|--oauth2-bearer|--cookie)(?:\\h+|=)|-[uUb]\\h+)(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s]+)");
    private static final Pattern URL_CREDENTIAL = Pattern.compile("(?i)(https?://)([^\\s/@:\"']+):([^\\s/@\"']+)@");
    private final Set<String> credentialNames = new HashSet<>(STANDARD_FIELDS);
    private final Set<String> headerNames = new HashSet<>(HEADER_FIELDS);
    private final Set<String> values = new LinkedHashSet<>();
    private Pattern textCredential = credentialPattern(STANDARD_FIELDS, false);
    private Pattern textHeader = credentialPattern(HEADER_FIELDS, true);
    private boolean formal;
    private int redactions;

    JsonNode redact(JsonNode input, List<String> warnings) {
        discover(input);
        formal = input.has("openapi") || input.has("swagger");
        textCredential = credentialPattern(credentialNames, false);
        textHeader = credentialPattern(headerNames, true);
        JsonNode safe = structured(input, false, "", !formal);
        safe = scrubStrings(safe);
        report(warnings);
        return safe;
    }

    String redactText(String text, List<String> warnings) {
        String safe = detectText(text);
        safe = replaceKnownValues(safe);
        report(warnings);
        return safe;
    }

    private void discover(JsonNode node) {
        if (node.path("type").asText().equals("apiKey") && node.path("name").isTextual()) {
            declare(node.path("name").asText());
            if (node.path("in").asText().equals("header")) {
                headerNames.add(normalize(node.path("name").asText()));
            }
        }
        node.path("properties").fields().forEachRemaining(field -> {
            if (declaredSecret(field.getValue())) {
                declare(field.getKey());
            }
        });
        node.elements().forEachRemaining(this::discover);
        if (credentialNames.size() > 256) {
            throw new IllegalArgumentException("Credential declaration limit exceeded; upload sanitized input.");
        }
    }

    private void declare(String name) {
        if (name.isBlank() || name.length() > 256) {
            throw new IllegalArgumentException("Credential declaration name limit exceeded; upload sanitized input.");
        }
        credentialNames.add(normalize(name));
    }

    private JsonNode structured(JsonNode node, boolean sensitive, String context, boolean data) {
        if (node.isObject()) {
            sensitive |= !data && (declaredSecret(node) || (node.has("in") && credentialNames.contains(normalize(node.path("name").asText()))));
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                if (sensitive && VALUE_FIELDS.contains(key)) {
                    copy.set(key, hide(value));
                } else if (formal && !data && (ID_MAPS.contains(context) || context.equals("flows"))) {
                    // Scheme/schema IDs and OAuth scope names are declarations, never credential values.
                    copy.set(key, structured(value, false, key, false));
                } else if (STANDARD_FIELDS.contains(normalize(key))
                        || ((data || DECLARATION_MAPS.contains(context)) && credentialNames.contains(normalize(key)))) {
                    boolean declaration = formal && !data && DECLARATION_MAPS.contains(context) && value.isObject();
                    copy.set(key, declaration ? structured(value, true, key, false) : hide(value));
                } else {
                    copy.set(key, structured(value, sensitive && key.equals("schema"), key, data || VALUE_FIELDS.contains(key)));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) {
                copy.add(structured(child, sensitive, context, data));
            }
            return copy;
        }
        return node;
    }

    private static boolean declaredSecret(JsonNode node) {
        return node.path("format").asText().equals("password") || node.path("writeOnly").asBoolean()
                || node.path("x-sensitive").asBoolean() || node.path("x-secret").asBoolean();
    }

    private JsonNode hide(JsonNode value) {
        // Preserve the container shape required by formal examples/enums, but never their values.
        if (value.isContainerNode()) {
            if (value.isArray()) {
                ArrayNode result = JsonNodeFactory.instance.arrayNode();
                value.forEach(child -> result.add(hide(child)));
                return result;
            }
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            value.fields().forEachRemaining(field -> result.set(field.getKey(), hide(field.getValue())));
            return result;
        }
        if (!value.isNull()) {
            remember(value.asText());
            redactions++;
        }
        return TextNode.valueOf(REDACTED);
    }

    private JsonNode scrubStrings(JsonNode node) {
        if (node.isTextual()) {
            return TextNode.valueOf(replaceKnownValues(detectText(node.asText())));
        }
        if (node instanceof ObjectNode object) {
            List<String> keys = new ArrayList<>();
            object.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                object.set(key, scrubStrings(object.get(key)));
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, scrubStrings(array.get(i)));
            }
        }
        return node;
    }

    private String detectText(String text) {
        String safe = detectText(detectText(detectText(text, textHeader), textCredential), CURL_CREDENTIAL);
        Matcher urls = URL_CREDENTIAL.matcher(safe);
        StringBuilder result = new StringBuilder();
        while (urls.find()) {
            remember(urls.group(2) + ":" + urls.group(3));
            redactions++;
            urls.appendReplacement(result, Matcher.quoteReplacement(urls.group(1) + REDACTED + "@"));
        }
        urls.appendTail(result);
        return result.toString();
    }

    private String detectText(String text, Pattern detector) {
        Matcher matcher = detector.matcher(text);
        StringBuilder safe = new StringBuilder();
        while (matcher.find()) {
            String value = matcher.group(2);
            boolean quoted = value.startsWith("\"") || value.startsWith("'");
            String unquoted = quoted ? value.substring(1, value.length() - 1) : value.stripTrailing();
            if (!unquoted.equals(REDACTED)) {
                remember(unquoted);
                redactions++;
            }
            matcher.appendReplacement(safe, Matcher.quoteReplacement(matcher.group(1)
                    + (quoted ? value.substring(0, 1) + REDACTED + value.substring(0, 1) : REDACTED)));
        }
        matcher.appendTail(safe);
        return safe.toString();
    }

    private void remember(String value) {
        if (!value.isBlank() && !value.equals(REDACTED)) {
            values.add(value);
            if (values.size() > 256 || values.stream().mapToInt(String::length).sum() > 65_536) {
                throw new IllegalArgumentException("Credential value isolation limit exceeded; upload sanitized input.");
            }
        }
    }

    private String replaceKnownValues(String text) {
        List<String> sorted = values.stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        for (String value : sorted) {
            if (text.contains(value)) {
                text = text.replace(value, REDACTED);
            }
        }
        return text;
    }

    private void report(List<String> warnings) {
        if (redactions > 0) {
            String warning = "Credential values redacted using explicit credential-field/header and security-schema declarations. Review selected material before sharing with a provider.";
            if (!warnings.contains(warning)) {
                warnings.add(warning);
            }
        }
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static Pattern credentialPattern(Set<String> names, boolean header) {
        String alternatives = names.stream().sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"));
        return Pattern.compile("(?im)(?<![a-z0-9_-])([\"']?(?:" + alternatives + ")[\"']?\\h*" + (header ? ":" : "[:=]") + "\\h*)"
                + "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|" + (header ? "[^\\r\\n\"'`,}]+" : "[^\\s&;,\\\"'`{}]+") + ")");
    }
}
