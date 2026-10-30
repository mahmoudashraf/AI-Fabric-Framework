package com.loomai.desk.service;

import static com.loomai.desk.model.ConversationContextContracts.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.config.DeskProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ConversationContextClient {
  private static final int MAX_RESPONSE_BYTES = 32768;
  private final ConversationContextEndpointPolicy endpoints;
  private final ObjectMapper mapper;
  private final CloseableHttpClient client;

  public ConversationContextClient(
      ConversationContextEndpointPolicy endpoints, DeskProperties properties, ObjectMapper mapper) {
    this.endpoints = endpoints;
    this.mapper = mapper;
    Timeout timeout = Timeout.ofMilliseconds(properties.getRequestTimeout().toMillis());
    var pool =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(12)
            .setMaxConnPerRoute(4)
            .setDnsResolver(
                new DnsResolver() {
                  @Override
                  public InetAddress[] resolve(String host) throws UnknownHostException {
                    return endpoints.resolve(host);
                  }

                  @Override
                  public String resolveCanonicalHostname(String host) {
                    return host;
                  }
                })
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(timeout)
                    .setSocketTimeout(timeout)
                    .build())
            .build();
    client =
        HttpClients.custom()
            .setConnectionManager(pool)
            .setDefaultRequestConfig(
                RequestConfig.custom()
                    .setConnectionRequestTimeout(timeout)
                    .setResponseTimeout(timeout)
                    .build())
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableContentCompression()
            .build();
  }

  public ProviderResponse request(Provider provider, String bearer, ProviderRequest payload) {
    var endpoint = endpoints.requireEndpoint(provider.providerUrl());
    try {
      // Recheck DNS even for a pooled connection; the resolver also checks the actual dial
      // addresses.
      endpoints.resolve(endpoint.getHost());
      HttpPost request = new HttpPost(endpoint);
      request.setHeader("Authorization", "Bearer " + bearer);
      request.setHeader("X-LoomAI-Desk-Service-Key", provider.serviceKey());
      request.setHeader("X-LoomAI-Desk-Binding-Id", payload.scope().bindingId());
      request.setHeader("Accept", "application/json");
      request.setEntity(
          new StringEntity(mapper.writeValueAsString(payload), ContentType.APPLICATION_JSON));
      return client.execute(
          request,
          response -> {
            try {
              if (response.getCode() != 200) {
                request.cancel();
                int status = response.getCode();
                if (status == 401
                    || status == 403
                    || status == 404
                    || status == 409
                    || status == 422)
                  throw new ResponseStatusException(
                      HttpStatus.valueOf(status),
                      "The context provider rejected this operation; refresh context and"
                          + " permissions.");
                throw unavailable();
              }
              var entity = response.getEntity();
              if (entity == null || entity.getContentLength() > MAX_RESPONSE_BYTES) {
                request.cancel();
                throw invalid();
              }
              ContentType type = ContentType.parse(entity.getContentType());
              if (type == null || !"application/json".equalsIgnoreCase(type.getMimeType()))
                throw invalid();
              byte[] bytes;
              try (var stream = entity.getContent()) {
                bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                  request.cancel();
                  throw invalid();
                }
              }
              return mapper
                  .readerFor(ProviderResponse.class)
                  .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                  .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                  .readValue(bytes);
            } catch (IOException | RuntimeException error) {
              request.cancel();
              throw error;
            }
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw unavailable();
    }
  }

  private ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.BAD_GATEWAY, "The context provider returned an invalid projection.");
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.BAD_GATEWAY, "External conversation context is unavailable.");
  }

  @PreDestroy
  public void close() throws IOException {
    client.close();
  }
}
