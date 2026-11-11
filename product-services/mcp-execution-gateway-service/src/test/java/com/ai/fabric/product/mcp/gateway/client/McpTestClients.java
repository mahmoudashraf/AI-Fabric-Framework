package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.ssl.SSLContexts;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** Test-only loopback receiver wiring. Production has no switch to disable the destination boundary. */
public class McpTestClients extends McpStreamableHttpClient {
    public McpTestClients(ObjectMapper mapper, McpGatewayProperties properties) {
        super(mapper, properties, new PinnedDnsHttpClient(properties, loopbackPolicy(), SSLContexts.createSystemDefault()));
    }

    static PublicMcpAddressPolicy loopbackPolicy() {
        return new PublicMcpAddressPolicy() {
            @Override public void validateUri(URI uri) throws DestinationRejectedException {
                if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()))
                    throw new DestinationRejectedException("Test receiver must use explicit loopback HTTP.");
            }
            @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                if (!"127.0.0.1".equals(host)) throw new UnknownHostException("Unexpected test destination.");
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }
        };
    }
}
