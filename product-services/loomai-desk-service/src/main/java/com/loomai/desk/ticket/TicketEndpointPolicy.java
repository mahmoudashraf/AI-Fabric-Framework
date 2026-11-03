package com.loomai.desk.ticket;

import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.service.ConversationContextEndpointPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Admin registration selects exact destinations; the existing public-DNS boundary still applies.
 */
@Component
public class TicketEndpointPolicy {
  private final DeskProperties properties;
  private final ConversationContextEndpointPolicy network;

  public TicketEndpointPolicy(DeskProperties properties) {
    this.properties = properties;
    network = new ConversationContextEndpointPolicy(properties);
  }

  public URI requireEndpoint(String value) {
    try {
      URI uri = URI.create(value);
      if (uri.getHost() == null) throw new IllegalArgumentException();
      String origin =
          uri.getScheme()
              + "://"
              + uri.getHost()
              + (uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort());
      var registration = new DeskProperties();
      registration.setConversationContextAllowInsecureLoopback(
          properties.isConversationContextAllowInsecureLoopback());
      // This per-call origin comes only from admin-registered configuration, never action/browser
      // fields.
      registration.setConversationContextAllowedOrigins(List.of(origin));
      return new ConversationContextEndpointPolicy(registration).requireEndpoint(value);
    } catch (RuntimeException invalid) {
      throw TicketException.invalid("canonical registered HTTPS endpoint");
    }
  }

  public InetAddress[] resolve(String host) throws UnknownHostException {
    return network.resolve(host);
  }
}
