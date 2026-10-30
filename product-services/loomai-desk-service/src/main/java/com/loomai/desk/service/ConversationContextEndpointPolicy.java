package com.loomai.desk.service;

import com.loomai.desk.config.DeskProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** The reviewed server origin and actual connection DNS are both trust boundaries. */
@Component
public class ConversationContextEndpointPolicy {
  private final DeskProperties properties;

  public ConversationContextEndpointPolicy(DeskProperties properties) {
    this.properties = properties;
  }

  public URI requireEndpoint(String value) {
    try {
      if (value == null || value.length() > 2048) throw new IllegalArgumentException();
      URI uri = URI.create(value);
      String host = uri.getHost();
      boolean testLoopback =
          properties.isConversationContextAllowInsecureLoopback()
              && Set.of("localhost", "127.0.0.1", "[::1]").contains(host == null ? "" : host);
      if (host == null
          || !host.equals(host.toLowerCase(Locale.ROOT))
          || uri.getUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !uri.normalize().equals(uri)
          || !uri.getRawPath().matches("[A-Za-z0-9/_~.-]*")
          || uri.getRawPath().contains("//")
          || java.util.Arrays.stream(uri.getRawPath().split("/"))
              .anyMatch(segment -> segment.equals(".") || segment.equals(".."))
          || !("https".equals(uri.getScheme()) && (uri.getPort() == -1 || uri.getPort() == 443)
              || testLoopback && "http".equals(uri.getScheme())))
        throw new IllegalArgumentException();
      String origin =
          uri.getScheme()
              + "://"
              + host
              + (uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort());
      if (!properties.getConversationContextAllowedOrigins().contains(origin))
        throw new IllegalArgumentException();
      return uri;
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Conversation context requires a reviewed, allowlisted HTTPS endpoint.");
    }
  }

  public InetAddress[] resolve(String host) throws UnknownHostException {
    InetAddress[] addresses = InetAddress.getAllByName(host);
    requireAddresses(host, addresses);
    return addresses;
  }

  void requireAddresses(String host, InetAddress[] addresses) throws UnknownHostException {
    if (addresses == null || addresses.length == 0 || addresses.length > 32)
      throw new UnknownHostException("Context endpoint DNS is not available.");
    boolean loopback =
        properties.isConversationContextAllowInsecureLoopback()
            && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host);
    for (InetAddress address : addresses) {
      if (loopback && address != null && address.isLoopbackAddress()) continue;
      if (!publicAddress(address))
        throw new UnknownHostException("Context endpoint address is forbidden.");
    }
  }

  private boolean publicAddress(InetAddress address) {
    if (address == null
        || address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return false;
    byte[] bytes = address.getAddress();
    int a = Byte.toUnsignedInt(bytes[0]), b = Byte.toUnsignedInt(bytes[1]);
    if (bytes.length == 4)
      return a != 0
          && a != 127
          && a < 224
          && !(a == 100 && b >= 64 && b <= 127)
          && !(a == 192 && b == 0)
          && !(a == 198 && (b == 18 || b == 19 || b == 51))
          && !(a == 203 && b == 0);
    return bytes.length == 16
        && (a & 0xe0) == 0x20
        && !(a == 0x20 && b == 0x01 && bytes[2] == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8);
  }
}
