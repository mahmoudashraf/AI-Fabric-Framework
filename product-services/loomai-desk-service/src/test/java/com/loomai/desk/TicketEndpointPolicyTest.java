package com.loomai.desk;

import static org.assertj.core.api.Assertions.*;

import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.ticket.TicketEndpointPolicy;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class TicketEndpointPolicyTest {
  @Test
  void adminRegisteredRuntimeAndSupportedIdentityProjectsNeedNoPerProjectOpsAllowlist()
      throws Exception {
    var properties = new DeskProperties();
    var policy = new TicketEndpointPolicy(properties);
    assertThat(properties.getConversationContextAllowedOrigins()).isEmpty();
    for (String endpoint :
        java.util.List.of(
            "https://new-runtime.customer.example",
            "https://new-project.supabase.co/auth/v1",
            "https://new-project.supabase.co/auth/v1/.well-known/jwks.json",
            "https://console.customer.example"))
      assertThat(policy.requireEndpoint(endpoint).toString()).isEqualTo(endpoint);
    assertThat(policy.resolve("8.8.8.8")).hasSize(1);
    assertThat(policy.resolve("2001:4860:4860::8888")).hasSize(1);
    assertThat(properties.getConversationContextAllowedOrigins()).isEmpty();
  }

  @Test
  void nonCanonicalAndCredentialBearingUrlsAreRejectedBeforeAnyConnection() {
    var policy = new TicketEndpointPolicy(new DeskProperties());
    for (String endpoint :
        java.util.List.of(
            "http://runtime.example",
            "https://user:password@runtime.example",
            "https://runtime.example?target=other",
            "https://runtime.example#secret",
            "https://runtime.example/a/../b",
            "https://runtime.example//b",
            "https://runtime.example/%2e%2e/b",
            "https://runtime.example:8443",
            "https://RUNTIME.example"))
      assertThatThrownBy(() -> policy.requireEndpoint(endpoint))
          .isInstanceOfSatisfying(
              com.loomai.desk.ticket.TicketException.class,
              error -> assertThat(error.code()).isEqualTo("TICKET_INPUT_INVALID"));
  }

  @Test
  void connectionTimeDnsRejectsLocalMetadataMappedIpv6AndReservedRanges() {
    var policy = new TicketEndpointPolicy(new DeskProperties());
    for (String host :
        java.util.List.of(
            "0.0.0.0",
            "127.0.0.1",
            "10.0.0.1",
            "172.16.1.2",
            "192.168.1.2",
            "169.254.169.254",
            "100.100.100.200",
            "100.64.0.1",
            "198.18.0.1",
            "224.0.0.1",
            "::",
            "::1",
            "fc00::1",
            "fe80::1",
            "ff02::1",
            "::ffff:127.0.0.1",
            "::ffff:10.0.0.1",
            "2001:db8::1"))
      assertThatThrownBy(() -> policy.resolve(host))
          .as(host)
          .isInstanceOf(UnknownHostException.class);
  }
}
