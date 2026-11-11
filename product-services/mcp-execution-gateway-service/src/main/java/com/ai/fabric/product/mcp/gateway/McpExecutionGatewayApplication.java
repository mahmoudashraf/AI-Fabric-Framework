package com.ai.fabric.product.mcp.gateway;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import com.ai.fabric.product.mcp.gateway.client.McpApacheHttp;
import com.ai.fabric.product.mcp.gateway.client.PublicMcpAddressPolicy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.http.HttpMethod;
import org.apache.hc.core5.http.ClassicHttpRequest;
import java.net.URI;

@SpringBootApplication
@EnableConfigurationProperties(McpGatewayProperties.class)
public class McpExecutionGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpExecutionGatewayApplication.class, args);
    }

    @Bean
    CloseableHttpClient credentialHttpClient(McpGatewayProperties properties) {
        return McpApacheHttp.create(properties, new PublicMcpAddressPolicy());
    }

    @Bean
    RestClientCustomizer tokenRequestTimeouts(McpGatewayProperties properties, CloseableHttpClient credentialHttpClient) {
        return builder -> {
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(credentialHttpClient) {
                @Override protected ClassicHttpRequest createHttpUriRequest(HttpMethod method, URI uri) {
                    try { new PublicMcpAddressPolicy().validateUri(uri); }
                    catch (PublicMcpAddressPolicy.DestinationRejectedException denied) {
                        throw new IllegalArgumentException("MCP credential destination must be public HTTPS.", denied);
                    }
                    return super.createHttpUriRequest(method, uri);
                }
            };
            factory.setConnectionRequestTimeout((int) properties.connectTimeout().toMillis());
            // Apache's connection socket timeout also bounds token/broker response reads on this Spring 6.1 baseline.
            builder.requestFactory(factory);
        };
    }
}
