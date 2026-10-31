package com.loomai.desk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loomai.desk.web.DeskController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeskIdentityPortalConfigTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://console.loomai.pro/auth/desk/sign-in",
        "https://console.staging.example.test/auth/desk/sign-in",
        "http://localhost:5173/auth/desk/sign-in",
        "http://127.0.0.1:5173/auth/desk/sign-in"
      })
  void admitsOnlyConfiguredSecureOrLoopbackPortal(String url) {
    DeskProperties properties = properties();
    properties.setIdentityPortalUrl(url);
    assertThatCode(() -> new DeskSecurityConfiguration().deskJwtDecoder(properties))
        .doesNotThrowAnyException();
    var config = new DeskController(properties, null, null, null, null).config();
    assertThat(config)
        .containsEntry("identityPortalUrl", url)
        .containsEntry("registrationEnabled", true);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://console.loomai.pro/auth/desk/sign-in",
        "//console.loomai.pro/auth/desk/sign-in",
        "/auth/desk/sign-in",
        "javascript:alert(1)",
        "https://user:secret@console.example.test/auth/desk/sign-in",
        "https://console.example.test/auth/desk/sign-in?next=https://untrusted.example.test",
        "https://console.example.test/auth/desk/sign-in#redirect",
        "http://localhost.untrusted.example.test/auth/desk/sign-in"
      })
  void rejectsUnsafePortalAtStartup(String url) {
    DeskProperties properties = properties();
    properties.setIdentityPortalUrl(url);
    assertThatThrownBy(() -> new DeskSecurityConfiguration().deskJwtDecoder(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DESK_IDENTITY_PORTAL_URL");
  }

  @Test
  void missingPortalDisablesOnlyAccountLinksAndRegistration() {
    DeskProperties properties = properties();
    assertThatCode(() -> new DeskSecurityConfiguration().deskJwtDecoder(properties))
        .doesNotThrowAnyException();
    var config = new DeskController(properties, null, null, null, null).config();
    assertThat(config)
        .containsEntry("identityPortalUrl", "")
        .containsEntry("registrationEnabled", false)
        .containsEntry("oidcEnabled", true)
        .containsEntry("loginUrl", "/oauth2/authorization/supabase");
  }

  private DeskProperties properties() {
    DeskProperties properties = new DeskProperties();
    properties.setServiceKey("service-key-separate-12345678901234567890");
    properties.setDataEncryptionSecret("encryption-key-separate-12345678901234567890");
    properties.setSupabaseUrl("https://identity.example.test");
    properties.setSupabasePublishableKey("sb_publishable_test");
    properties.setIssuer("https://identity.example.test/auth/v1");
    properties.setJwksUri("https://identity.example.test/auth/v1/.well-known/jwks.json");
    properties.setClientId("desk-test-client");
    return properties;
  }
}
