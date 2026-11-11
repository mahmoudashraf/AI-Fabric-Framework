package com.ai.fabric.product.mcp.gateway.client;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.net.Proxy;
import java.net.Socket;

/** Shared connection construction for SDK traffic and production token/broker requests. */
public final class McpApacheHttp {
    private McpApacheHttp() { }

    public static CloseableHttpClient create(McpGatewayProperties properties, PublicMcpAddressPolicy policy) {
        return create(properties, policy, SSLContexts.createSystemDefault());
    }

    static CloseableHttpClient create(McpGatewayProperties properties, PublicMcpAddressPolicy policy, SSLContext ssl) {
        var manager = new DirectManager().setDnsResolver(policy).setMaxConnTotal(32).setMaxConnPerRoute(8)
            .setConnectionFactory(ManagedHttpClientConnectionFactory.builder().http1Config(Http1Config.custom()
                .setMaxHeaderCount(64).setMaxLineLength(8192).build()).build())
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.of(properties.connectTimeout()))
                .setSocketTimeout(Timeout.of(properties.readTimeout())).build())
            .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.of(properties.connectTimeout())).build())
            .setTlsSocketStrategy(ClientTlsStrategyBuilder.create().setSslContext(ssl)
                .setTlsVersions("TLSv1.3", "TLSv1.2").setHostVerificationPolicy(HostnameVerificationPolicy.BOTH)
                .setHostnameVerifier(new DefaultHostnameVerifier()).buildClassic()).build();
        // Minimal client has no redirect, cookie, authentication negotiation or retry execution chain.
        return HttpClients.createMinimal(manager);
    }

    private static final class DirectManager extends PoolingHttpClientConnectionManagerBuilder {
        @Override
        protected HttpClientConnectionOperator createConnectionOperator(SchemePortResolver ports, DnsResolver dns, TlsSocketStrategy tls) {
            return new DefaultHttpClientConnectionOperator(ignored -> new Socket(Proxy.NO_PROXY), ports, dns,
                RegistryBuilder.<TlsSocketStrategy>create().register("https", tls).build());
        }
    }
}
