package com.ai.fabric.product.shopify.bridge.action.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class ShopifyCustomerAccountMcpDiscoveryService {

    private final RestClient restClient;

    public ShopifyCustomerAccountMcpDiscoveryService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public CustomerAccountMcpDiscovery discover(String shopDomain) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        URI discoveryUri = URI.create("https://" + normalizedShop + "/.well-known/customer-account-api");
        try {
            JsonNode response = restClient.get()
                .uri(discoveryUri)
                .retrieve()
                .body(JsonNode.class);
            URI mcpEndpoint = firstUri(
                response,
                normalizedShop,
                "mcp_api",
                "customer_account_mcp_endpoint",
                "customerAccountMcpEndpoint",
                "mcp_endpoint",
                "mcpEndpoint"
            );
            if (mcpEndpoint == null) {
                mcpEndpoint = URI.create("https://" + normalizedShop + "/customer/api/mcp");
            }
            URI authorizationEndpoint = firstUri(
                response,
                normalizedShop,
                "authorization_endpoint",
                "authorizationEndpoint"
            );
            URI tokenEndpoint = firstUri(response, normalizedShop, "token_endpoint", "tokenEndpoint");
            return new CustomerAccountMcpDiscovery(discoveryUri, mcpEndpoint, authorizationEndpoint, tokenEndpoint, response);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                BAD_GATEWAY,
                "Shopify Customer Accounts MCP discovery returned HTTP " + ex.getStatusCode().value() + ".",
                ex
            );
        }
    }

    private URI firstUri(JsonNode node, String shopDomain, String... fields) {
        if (node != null && fields != null) {
            for (String field : fields) {
                String value = node.path(field).asText(null);
                if (StringUtils.hasText(value)) {
                    return URI.create(value.trim());
                }
            }
        }
        return null;
    }

    private String normalizeShopDomain(String shopDomain) {
        if (!StringUtils.hasText(shopDomain)) {
            throw new ResponseStatusException(BAD_GATEWAY, "shopDomain is required for Customer Accounts MCP discovery.");
        }
        return shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    public record CustomerAccountMcpDiscovery(
        URI discoveryUri,
        URI mcpEndpoint,
        URI authorizationEndpoint,
        URI tokenEndpoint,
        JsonNode metadata
    ) {
    }
}
