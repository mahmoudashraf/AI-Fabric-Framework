package com.loomai.desk.service;

import static org.assertj.core.api.Assertions.*;

import com.loomai.desk.config.DeskProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ConversationContextEndpointPolicyTest {
  @Test
  void onlyExactReviewedHttpsOriginsAndNormalizedPathsAreAccepted() {
    DeskProperties properties = new DeskProperties();
    var policy = new ConversationContextEndpointPolicy(properties);
    assertThatThrownBy(() -> policy.requireEndpoint("https://api.example.com/context"))
        .isInstanceOf(ResponseStatusException.class);
    properties.setConversationContextAllowedOrigins(List.of("https://api.example.com"));
    assertThat(policy.requireEndpoint("https://api.example.com/context/v1").getHost())
        .isEqualTo("api.example.com");
    for (String url :
        List.of(
            "http://api.example.com/context", "https://api.example.com.evil.test/context",
            "https://other.example.com/context", "https://api.example.com:8443/context",
            "https://key@api.example.com/context",
                "https://api.example.com/context?url=https://evil.test",
            "https://api.example.com/context#fragment", "https://api.example.com/../context",
            "https://api.example.com//context", "https://api.example.com/%2e%2e/context",
            "http://127.0.0.1/context", "https://api.example.com/\n"))
      assertThatThrownBy(() -> policy.requireEndpoint(url))
          .as(url)
          .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void connectionResolverRejectsPrivateMixedMetadataAndNonGlobalAddresses() throws Exception {
    var policy = new ConversationContextEndpointPolicy(new DeskProperties());
    InetAddress publicAddress = InetAddress.getByName("8.8.8.8");
    policy.requireAddresses("approved.example.com", new InetAddress[] {publicAddress});
    for (String forbidden :
        List.of(
            "127.0.0.1",
            "0.0.0.0",
            "10.0.0.1",
            "172.16.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "224.0.0.1",
            "::1",
            "fc00::1",
            "fe80::1",
            "2001:db8::1",
            "::ffff:127.0.0.1")) {
      InetAddress address = InetAddress.getByName(forbidden);
      assertThatThrownBy(
              () -> policy.requireAddresses("approved.example.com", new InetAddress[] {address}))
          .as(forbidden)
          .isInstanceOf(UnknownHostException.class);
      assertThatThrownBy(
              () ->
                  policy.requireAddresses(
                      "approved.example.com", new InetAddress[] {publicAddress, address}))
          .as("mixed " + forbidden)
          .isInstanceOf(UnknownHostException.class);
    }
  }

  @Test
  void loopbackNeedsBothExplicitTestSwitchAndExactOrigin() throws Exception {
    DeskProperties properties = new DeskProperties();
    properties.setConversationContextAllowedOrigins(List.of("http://127.0.0.1:12345"));
    var policy = new ConversationContextEndpointPolicy(properties);
    assertThatThrownBy(() -> policy.requireEndpoint("http://127.0.0.1:12345/context"))
        .isInstanceOf(ResponseStatusException.class);
    properties.setConversationContextAllowInsecureLoopback(true);
    policy.requireEndpoint("http://127.0.0.1:12345/context");
    policy.requireAddresses("127.0.0.1", new InetAddress[] {InetAddress.getByName("127.0.0.1")});
    assertThatThrownBy(() -> policy.requireEndpoint("http://127.0.0.1:12346/context"))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                policy.requireAddresses(
                    "approved.example.com", new InetAddress[] {InetAddress.getByName("127.0.0.1")}))
        .isInstanceOf(UnknownHostException.class);
  }
}
