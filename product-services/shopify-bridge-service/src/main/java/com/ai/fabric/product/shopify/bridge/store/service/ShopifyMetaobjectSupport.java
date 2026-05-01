package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

public final class ShopifyMetaobjectSupport {

    private static final int DEFINITIONS_PAGE_SIZE = 50;

    private static final String DEFINITIONS_QUERY = """
        query ShopifyCompanionMetaobjectDefinitions($cursor: String) {
          metaobjectDefinitions(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                type
                name
                description
                displayNameKey
                metaobjectsCount
                access {
                  admin
                  storefront
                }
                fieldDefinitions {
                  key
                  name
                }
              }
            }
          }
        }
        """;

    private static final String METAOBJECTS_BY_TYPE_QUERY = """
        query ShopifyCompanionMetaobjectsByType($type: String!, $cursor: String, $limit: Int!) {
          metaobjects(type: $type, first: $limit, after: $cursor, sortKey: "updated_at") {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                type
                handle
                displayName
                updatedAt
                onlineStoreUrl
                fields {
                  key
                  type
                  value
                }
              }
            }
          }
        }
        """;

    private static final String METAOBJECT_NODE_QUERY = """
        query ShopifyCompanionMetaobjectRecord($id: ID!) {
          node(id: $id) {
            __typename
            ... on Metaobject {
              id
              type
              handle
              displayName
              updatedAt
              onlineStoreUrl
              fields {
                key
                type
                value
              }
              definition {
                id
                type
                name
                description
                displayNameKey
                fieldDefinitions {
                  key
                  name
                }
              }
            }
          }
        }
        """;

    private ShopifyMetaobjectSupport() {
    }

    public static List<MetaobjectDefinitionSummary> loadDefinitions(String shopDomain,
                                                                    String accessToken,
                                                                    ShopifyAdminGraphqlClient shopifyAdminGraphqlClient) {
        List<MetaobjectDefinitionSummary> definitions = new ArrayList<>();
        String cursor = null;
        while (true) {
            Map<String, Object> response = shopifyAdminGraphqlClient.execute(
                shopDomain,
                accessToken,
                DEFINITIONS_QUERY,
                cursorVariables(cursor)
            );
            List<String> errors = errorMessages(response);
            if (!errors.isEmpty()) {
                throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
            }
            Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
            Map<String, Object> connection = requireMap(
                data.get("metaobjectDefinitions"),
                "Shopify Admin API response is missing metaobjectDefinitions."
            );
            List<?> edges = requireList(connection.get("edges"), "Shopify Admin API response is missing metaobject definition edges.");
            for (Object edge : edges) {
                Map<String, Object> edgeMap = requireMapFromListItem(edge);
                Map<String, Object> node = requireMap(edgeMap.get("node"), "Shopify Admin API response is missing metaobject definition node.");
                int metaobjectsCount = integer(node.get("metaobjectsCount"));
                if (metaobjectsCount <= 0) {
                    continue;
                }
                definitions.add(definition(node, metaobjectsCount));
            }
            Map<String, Object> pageInfo = requireMap(connection.get("pageInfo"), "Shopify Admin API response is missing metaobject definition pageInfo.");
            boolean hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
            String endCursor = text(pageInfo, "endCursor");
            if (!hasNextPage || endCursor == null || endCursor.isBlank()) {
                return List.copyOf(definitions);
            }
            cursor = endCursor;
        }
    }

    static int totalCount(String shopDomain,
                          String accessToken,
                          ShopifyAdminGraphqlClient shopifyAdminGraphqlClient) {
        return loadDefinitions(shopDomain, accessToken, shopifyAdminGraphqlClient).stream()
            .mapToInt(MetaobjectDefinitionSummary::metaobjectsCount)
            .sum();
    }

    static MetaobjectPage pageEntries(String shopDomain,
                                      String accessToken,
                                      ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                      String cursor,
                                      int limit) {
        List<MetaobjectDefinitionSummary> definitions = loadDefinitions(shopDomain, accessToken, shopifyAdminGraphqlClient);
        if (definitions.isEmpty()) {
            return new MetaobjectPage(List.of(), null, false, 0);
        }
        ParsedCursor parsedCursor = parseCursor(cursor, definitions.getFirst().type());
        int startIndex = definitionIndex(definitions, parsedCursor.definitionType());
        int remaining = Math.max(limit, 1);
        List<MetaobjectEntrySummary> items = new ArrayList<>();

        for (int index = startIndex; index < definitions.size(); index++) {
            MetaobjectDefinitionSummary definition = definitions.get(index);
            String nativeCursor = index == startIndex ? parsedCursor.nativeCursor() : null;
            EntryPage page = loadEntriesByType(
                shopDomain,
                accessToken,
                shopifyAdminGraphqlClient,
                definition,
                nativeCursor,
                remaining
            );
            items.addAll(page.items());
            remaining -= page.items().size();
            if (remaining == 0) {
                String nextCursor = page.hasMore()
                    ? encodeCursor(definition.type(), page.nextCursor())
                    : index + 1 < definitions.size()
                        ? encodeCursor(definitions.get(index + 1).type(), null)
                        : null;
                return new MetaobjectPage(items, nextCursor, nextCursor != null, total(definitions));
            }
            if (page.hasMore()) {
                String nextCursor = encodeCursor(definition.type(), page.nextCursor());
                return new MetaobjectPage(items, nextCursor, true, total(definitions));
            }
        }
        return new MetaobjectPage(items, null, false, total(definitions));
    }

    static List<MetaobjectEntrySummary> loadAllEntries(String shopDomain,
                                                       String accessToken,
                                                       ShopifyAdminGraphqlClient shopifyAdminGraphqlClient) {
        List<MetaobjectEntrySummary> entries = new ArrayList<>();
        for (MetaobjectDefinitionSummary definition : loadDefinitions(shopDomain, accessToken, shopifyAdminGraphqlClient)) {
            String cursor = null;
            while (true) {
                EntryPage page = loadEntriesByType(
                    shopDomain,
                    accessToken,
                    shopifyAdminGraphqlClient,
                    definition,
                    cursor,
                    DEFINITIONS_PAGE_SIZE
                );
                entries.addAll(page.items());
                if (!page.hasMore()) {
                    break;
                }
                cursor = page.nextCursor();
            }
        }
        return List.copyOf(entries);
    }

    static MetaobjectEntrySummary loadRecord(String shopDomain,
                                             String accessToken,
                                             ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                             String sourceObjectId) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            METAOBJECT_NODE_QUERY,
            Map.of("id", sourceObjectId)
        );
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> node = requireMap(data.get("node"), "Shopify Admin API response is missing node.");
        String typename = text(node, "__typename");
        if (!"Metaobject".equalsIgnoreCase(typename)) {
            throw new ResponseStatusException(CONFLICT, "Shopify source record type mismatch. Expected Metaobject but found " + typename + ".");
        }
        MetaobjectDefinitionSummary definition = definition(
            requireMap(node.get("definition"), "Shopify Admin API response is missing metaobject definition."),
            -1
        );
        return entry(node, definition);
    }

    private static EntryPage loadEntriesByType(String shopDomain,
                                               String accessToken,
                                               ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                               MetaobjectDefinitionSummary definition,
                                               String cursor,
                                               int limit) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            METAOBJECTS_BY_TYPE_QUERY,
            Map.of(
                "type", definition.type(),
                "cursor", cursor,
                "limit", Math.max(limit, 1)
            )
        );
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> connection = requireMap(
            data.get("metaobjects"),
            "Shopify Admin API response is missing metaobjects."
        );
        List<?> edges = requireList(connection.get("edges"), "Shopify Admin API response is missing metaobject edges.");
        List<MetaobjectEntrySummary> items = new ArrayList<>();
        for (Object edge : edges) {
            Map<String, Object> edgeMap = requireMapFromListItem(edge);
            Map<String, Object> node = requireMap(edgeMap.get("node"), "Shopify Admin API response is missing metaobject node.");
            items.add(entry(node, definition));
        }
        Map<String, Object> pageInfo = requireMap(connection.get("pageInfo"), "Shopify Admin API response is missing metaobject pageInfo.");
        boolean hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
        String endCursor = text(pageInfo, "endCursor");
        return new EntryPage(items, hasNextPage && endCursor != null ? endCursor : null, hasNextPage && endCursor != null);
    }

    private static MetaobjectDefinitionSummary definition(Map<String, Object> node, int metaobjectsCountOverride) {
        Map<String, Object> access = node.get("access") instanceof Map<?, ?> rawAccess
            ? requireMap(rawAccess, "Shopify Admin API response is missing metaobject access.")
            : Map.of();
        LinkedHashMap<String, String> fieldLabels = new LinkedHashMap<>();
        if (node.get("fieldDefinitions") instanceof List<?> fieldDefinitions) {
            for (Object fieldDefinition : fieldDefinitions) {
                Map<String, Object> field = requireMapFromListItem(fieldDefinition);
                String key = text(field, "key");
                if (key == null || key.isBlank()) {
                    continue;
                }
                fieldLabels.put(key.trim(), labeled(text(field, "name"), key));
            }
        }
        return new MetaobjectDefinitionSummary(
            requiredText(node, "id"),
            requiredText(node, "type"),
            labeled(text(node, "name"), text(node, "type")),
            text(node, "description"),
            text(node, "displayNameKey"),
            metaobjectsCountOverride >= 0 ? metaobjectsCountOverride : integer(node.get("metaobjectsCount")),
            text(access, "admin"),
            text(access, "storefront"),
            Map.copyOf(fieldLabels)
        );
    }

    private static MetaobjectEntrySummary entry(Map<String, Object> node, MetaobjectDefinitionSummary definition) {
        List<String> fieldLines = new ArrayList<>();
        LinkedHashMap<String, String> fieldValues = new LinkedHashMap<>();
        if (node.get("fields") instanceof List<?> fields) {
            for (Object fieldValue : fields) {
                Map<String, Object> field = requireMapFromListItem(fieldValue);
                String key = text(field, "key");
                String value = text(field, "value");
                if (key == null || key.isBlank() || value == null || value.isBlank()) {
                    continue;
                }
                String normalizedKey = key.trim();
                String label = definition.fieldLabels().getOrDefault(normalizedKey, humanizeKey(normalizedKey));
                String normalizedValue = sanitizeFieldValue(value);
                if (normalizedValue.isBlank()) {
                    continue;
                }
                fieldValues.put(normalizedKey, normalizedValue);
                fieldLines.add(label + ": " + normalizedValue);
            }
        }
        String title = labeled(text(node, "displayName"), text(node, "handle"));
        if (title == null) {
            title = definition.name();
        }
        List<String> contentParts = new ArrayList<>();
        contentParts.add(title);
        if (definition.name() != null && !definition.name().equalsIgnoreCase(title)) {
            contentParts.add("Metaobject definition: " + definition.name());
        }
        if (definition.description() != null && !definition.description().isBlank()) {
            contentParts.add(definition.description().trim());
        }
        if (!fieldLines.isEmpty()) {
            contentParts.add(String.join("\n", fieldLines));
        }
        return new MetaobjectEntrySummary(
            requiredText(node, "id"),
            requiredText(node, "type"),
            text(node, "handle"),
            title,
            text(node, "updatedAt"),
            text(node, "onlineStoreUrl"),
            definition.name(),
            definition.description(),
            Map.copyOf(fieldValues),
            String.join("\n\n", contentParts)
        );
    }

    private static ParsedCursor parseCursor(String cursor, String defaultType) {
        if (cursor == null || cursor.isBlank()) {
            return new ParsedCursor(defaultType, null);
        }
        String[] parts = cursor.split("~", 2);
        String definitionType = parts[0].trim();
        String nativeCursor = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
        return new ParsedCursor(definitionType.isBlank() ? defaultType : definitionType, nativeCursor);
    }

    private static int definitionIndex(List<MetaobjectDefinitionSummary> definitions, String definitionType) {
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).type().equalsIgnoreCase(definitionType)) {
                return i;
            }
        }
        return 0;
    }

    private static String encodeCursor(String definitionType, String nativeCursor) {
        return definitionType + "~" + (nativeCursor == null ? "" : nativeCursor);
    }

    private static int total(List<MetaobjectDefinitionSummary> definitions) {
        return definitions.stream()
            .mapToInt(MetaobjectDefinitionSummary::metaobjectsCount)
            .sum();
    }

    private static Map<String, Object> cursorVariables(String cursor) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("cursor", cursor);
        return variables;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Object value, String message) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    private static List<?> requireList(Object value, String message) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMapFromListItem(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API returned an invalid metaobject payload.");
    }

    private static List<String> errorMessages(Map<String, Object> response) {
        Object value = response.get("errors");
        if (!(value instanceof List<?> errors)) {
            return List.of();
        }
        return errors.stream()
            .map(ShopifyMetaobjectSupport::requireMapFromListItem)
            .map(error -> text(error, "message"))
            .filter(message -> message != null && !message.isBlank())
            .toList();
    }

    private static String requiredText(Map<String, Object> source, String fieldName) {
        String value = text(source, fieldName);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API response is missing " + fieldName + ".");
        }
        return value;
    }

    private static String text(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        return value == null ? null : value.toString();
    }

    private static int integer(Object value) {
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(value.toString()), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String labeled(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static String sanitizeFieldValue(String value) {
        return value
            .replaceAll("<[^>]+>", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String humanizeKey(String key) {
        String normalized = key == null ? "" : key.trim().replace('_', ' ').replace('-', ' ');
        if (normalized.isBlank()) {
            return "Field";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public record MetaobjectDefinitionSummary(
        String id,
        String type,
        String name,
        String description,
        String displayNameKey,
        int metaobjectsCount,
        String adminAccess,
        String storefrontAccess,
        Map<String, String> fieldLabels
    ) {
    }

    record MetaobjectEntrySummary(
        String id,
        String type,
        String handle,
        String title,
        String updatedAt,
        String storefrontUrl,
        String definitionName,
        String definitionDescription,
        Map<String, String> fieldValues,
        String content
    ) {
    }

    record MetaobjectPage(
        List<MetaobjectEntrySummary> items,
        String nextCursor,
        boolean hasMore,
        int totalCount
    ) {
    }

    private record EntryPage(
        List<MetaobjectEntrySummary> items,
        String nextCursor,
        boolean hasMore
    ) {
    }

    private record ParsedCursor(String definitionType, String nativeCursor) {
    }
}
