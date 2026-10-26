package com.loomai.desk;

import static com.loomai.desk.model.DeskContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.config.DeskLoginNavigation;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class DeskHttpSecurityTest {
  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  static final String ADMIN = "desk-service-secret-1234567890123456789012345";
  static final String BRIDGE = "desk-bridge-secret-1234567890123456789012345";

  @ParameterizedTest
  @ValueSource(strings = {"h2", "postgres"})
  void realSignedJwtAdmissionAdminIsolationAssignmentsReviewIdentityAndPkce(String db)
      throws Exception {
    try (Harness h = new Harness(db)) {
      assertThat(h.request("GET", "/api/desk/v1/session", null, null).statusCode()).isEqualTo(200);
      assertThat(
              JSON.readTree(h.request("GET", "/api/desk/v1/session", null, null).body())
                  .path("authenticated")
                  .asBoolean())
          .isFalse();
      String config = h.request("GET", "/api/desk/v1/config", null, null).body();
      assertThat(
              h.request("GET", "/api/desk/v1/session", h.esToken("ec-agent", "authenticated"), null)
                  .statusCode())
          .isEqualTo(200);
      assertThat(config)
          .contains("sb_publishable_test")
          .doesNotContain(ADMIN, BRIDGE, "protected_");
      assertThat(h.request("POST", "/api/desk/v1/agents/register", null, "{}").statusCode())
          .isEqualTo(403);
      assertThat(h.request("GET", "/api/desk/v1/bindings/a/conversations", null, null).statusCode())
          .isEqualTo(401);
      assertThat(h.request("GET", "/api/desk/admin/v1/bindings/a", null, null).statusCode())
          .isEqualTo(401);
      assertThat(
              h.request("POST", "/api/conversation-channel/v1/dispatches", null, "{}").statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "/api/desk/v1/session",
                      h.token("agent", h.issuer, "wrong", true, 600),
                      null)
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "/api/desk/v1/session",
                      h.token("agent", "https://wrong.test", "authenticated", true, 600),
                      null)
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "/api/desk/v1/session",
                      h.token("agent", h.issuer, "authenticated", true, -300),
                      null)
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              h.request(
                      "GET",
                      "/api/desk/v1/session",
                      h.token("agent", h.issuer, "authenticated", false, 600),
                      null)
                  .statusCode())
          .isEqualTo(403);
      h.provision();
      String owner = h.token("owner", h.issuer, "authenticated", true, 600);
      String agent = h.token("agent", h.issuer, "authenticated", true, 600);
      String reviewer = h.token("reviewer", h.issuer, "authenticated", true, 600);
      JsonNode ownerSession =
          JSON.readTree(h.request("GET", "/api/desk/v1/session", owner, null).body());
      assertThat(ownerSession.path("bindings").size()).isEqualTo(1);
      assertThat(ownerSession.path("bindings").get(0).path("roles").toString())
          .contains("OWNER")
          .doesNotContain("SUPPORT");
      assertThat(
              h.request("GET", "/api/desk/v1/bindings/a/conversations", owner, null).statusCode())
          .isEqualTo(403);
      JsonNode registered =
          JSON.readTree(
              h.request(
                      "POST",
                      "/api/desk/v1/agents/register",
                      agent,
                      "{\"displayName\":\"Sam Support\"}")
                  .body());
      assertThat(registered.path("displayName").asText()).isEqualTo("Sam Support");
      assertThat(registered.path("email").asText()).isEqualTo("agent@example.test");
      JsonNode reviewed =
          JSON.readTree(h.request("POST", "/api/desk/v1/agents/register", reviewer, "{}").body());
      assertThat(
              h.request("GET", "/api/desk/v1/bindings/a/conversations", agent, null).statusCode())
          .isEqualTo(403);
      assertThat(
              h.request(
                      "POST",
                      "/api/desk/v1/bindings/a/agents",
                      owner,
                      JSON.writeValueAsString(
                          new AssignmentInput(
                              null, registered.path("id").asText(), Set.of(Role.SUPPORT))))
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              h.request(
                      "POST",
                      "/api/desk/v1/bindings/a/agents",
                      owner,
                      JSON.writeValueAsString(
                          new AssignmentInput(
                              null, reviewed.path("id").asText(), Set.of(Role.REVIEWER))))
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              h.request("GET", "/api/desk/v1/bindings/a/conversations", agent, null).statusCode())
          .isEqualTo(200);
      assertThat(
              h.request("GET", "/api/desk/v1/bindings/b/conversations", agent, null).statusCode())
          .isEqualTo(403);
      assertThat(h.request("GET", "/api/desk/v1/bindings/a/reviews", agent, null).statusCode())
          .isEqualTo(403);
      assertThat(h.bridgeRequests).isEmpty();
      assertThat(
              h.request(
                      "GET",
                      "/api/desk/v1/bindings/a/reviews?status=PENDING&limit=10",
                      reviewer,
                      null)
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              h.request("GET", "/api/desk/v1/bindings/a/reviews/task-1", reviewer, null)
                  .statusCode())
          .isEqualTo(200);
      assertThat(
              h.request(
                      "POST",
                      "/api/desk/v1/bindings/a/reviews/task-1/decisions",
                      reviewer,
                      "{\"decisionId\":\"decision-1\",\"decision\":\"APPROVE\",\"expectedVersion\":3}")
                  .statusCode())
          .isEqualTo(200);
      assertThat(h.bridgeRequests)
          .hasSize(3)
          .allSatisfy(
              call -> {
                assertThat(call.bearer()).isEqualTo("Bearer " + reviewer);
                assertThat(call.key()).isEqualTo(BRIDGE);
                assertThat(call.binding()).isEqualTo("a");
              });
      assertThat(h.bridgeRequests.get(2).body()).contains("\"expectedVersion\":3");
      assertThat(h.bridgeRequests.get(0).path())
          .isEqualTo("/bridge/a/reviews?limit=10&status=PENDING");
      assertThat(h.bridgeRequests.get(1).path()).isEqualTo("/bridge/a/reviews/task-1");
      assertThat(h.bridgeRequests.get(2).path()).isEqualTo("/bridge/a/reviews/task-1/decisions");
      assertThat(
              JSON.readTree(
                      h.adminGet(
                              "/bindings/a/authorizations?issuer="
                                  + java.net.URLEncoder.encode(h.issuer, StandardCharsets.UTF_8)
                                  + "&subject=reviewer&permission=REVIEWER")
                          .body())
                  .path("authorized")
                  .asBoolean())
          .isTrue();
      assertThat(
              JSON.readTree(
                      h.adminGet(
                              "/bindings/a/authorizations?issuer="
                                  + java.net.URLEncoder.encode(h.issuer, StandardCharsets.UTF_8)
                                  + "&subject=owner&permission=REVIEWER")
                          .body())
                  .path("authorized")
                  .asBoolean())
          .isFalse();
      assertThat(
              h.request(
                      "DELETE",
                      "/api/desk/v1/bindings/a/agents/" + registered.path("id").asText(),
                      owner,
                      null)
                  .statusCode())
          .isEqualTo(204);
      assertThat(
              h.request("GET", "/api/desk/v1/bindings/a/conversations", agent, null).statusCode())
          .isEqualTo(403);
      assertThat(
              h.request(
                      "DELETE",
                      "/api/desk/v1/bindings/a/agents/" + reviewed.path("id").asText(),
                      owner,
                      null)
                  .statusCode())
          .isEqualTo(204);
      assertThat(h.request("GET", "/api/desk/v1/bindings/a/reviews", reviewer, null).statusCode())
          .isEqualTo(403);
      assertThat(h.bridgeRequests).hasSize(3);
      assertThat(
              JSON.readTree(
                      h.adminGet(
                              "/bindings/a/authorizations?issuer="
                                  + java.net.URLEncoder.encode(h.issuer, StandardCharsets.UTF_8)
                                  + "&subject=reviewer&permission=REVIEWER")
                          .body())
                  .path("authorized")
                  .asBoolean())
          .isFalse();
      HttpResponse<String> redirect =
          h.request("GET", "/oauth2/authorization/supabase", null, null);
      assertThat(redirect.statusCode()).isEqualTo(302);
      assertThat(redirect.headers().firstValue("Location").orElseThrow())
          .contains(
              "code_challenge=",
              "code_challenge_method=S256",
              "state=",
              "nonce=",
              "client_id=desk-test-client",
              "/login/oauth2/code/supabase");
      assertThat(redirect.headers().allValues("Set-Cookie").toString())
          .contains("__Host-LOOMAI_DESK_SESSION", "Secure", "HttpOnly", "SameSite=Lax")
          .doesNotContain("Domain=");
    }
  }

  @Test
  void oidcBffStoresAccessAndRefreshCredentialsEncryptedAndEnforcesSessionCsrfAndLogout()
      throws Exception {
    try (Harness h = new Harness("h2")) {
      h.provision();
      String jwt = h.token("owner", h.issuer, "authenticated", true, 600);
      var claims =
          Map.<String, Object>of(
              "iss",
              h.issuer,
              "sub",
              "owner",
              "email",
              "owner@example.test",
              "email_verified",
              true,
              "aud",
              List.of("desk-test-client"));
      var user =
          new DefaultOidcUser(
              List.of(new SimpleGrantedAuthority("OIDC_USER")),
              new OidcIdToken(
                  "id-token-local", Instant.now(), Instant.now().plusSeconds(600), claims));
      var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "supabase");
      var registration =
          h.context.getBean(ClientRegistrationRepository.class).findByRegistrationId("supabase");
      var client =
          new OAuth2AuthorizedClient(
              registration,
              "owner",
              new OAuth2AccessToken(
                  OAuth2AccessToken.TokenType.BEARER,
                  jwt,
                  Instant.now(),
                  Instant.now().plusSeconds(600),
                  Set.of("openid", "email")),
              new org.springframework.security.oauth2.core.OAuth2RefreshToken(
                  "refresh-secret-local", Instant.now()));
      var store = h.context.getBean(OAuth2AuthorizedClientService.class);
      store.saveAuthorizedClient(client, authentication);
      String protectedTokens =
          h.context
              .getBean(JdbcTemplate.class)
              .queryForObject("SELECT protected_tokens FROM desk_oauth_client", String.class);
      assertThat(protectedTokens).doesNotContain(jwt, "refresh-secret-local");
      assertThat(store.loadAuthorizedClient("supabase", "owner").getAccessToken().getTokenValue())
          .isEqualTo(jwt);
      MockMvc mvc =
          MockMvcBuilders.webAppContextSetup((WebApplicationContext) h.context)
              .apply(
                  org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                      .springSecurity())
              .build();
      MockHttpSession session =
          new MockHttpSession(((WebApplicationContext) h.context).getServletContext());
      String response =
          mvc.perform(
                  get("/api/desk/v1/session").session(session).with(authentication(authentication)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode state = JSON.readTree(response);
      assertThat(state.path("operator").path("email").asText()).isEqualTo("owner@example.test");
      mvc.perform(
              post("/api/desk/v1/agents/register")
                  .session(session)
                  .with(authentication(authentication)))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/api/desk/v1/agents/register")
                  .session(session)
                  .with(authentication(authentication))
                  .header(state.path("csrfHeaderName").asText(), state.path("csrfToken").asText()))
          .andExpect(status().isOk());
      mvc.perform(post("/logout").session(session).with(authentication(authentication)))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/logout")
                  .session(session)
                  .with(authentication(authentication))
                  .header(state.path("csrfHeaderName").asText(), state.path("csrfToken").asText()))
          .andExpect(status().is3xxRedirection());
      OAuth2AuthorizedClient removed = store.loadAuthorizedClient("supabase", "owner");
      assertThat(removed).isNull();
      @SuppressWarnings("unchecked")
      JwtDecoderFactory<ClientRegistration> idDecoders =
          (JwtDecoderFactory<ClientRegistration>)
              h.context.getBean("deskOidcIdTokenDecoderFactory");
      assertThat(
              idDecoders
                  .createDecoder(registration)
                  .decode(h.esToken("owner", "desk-test-client"))
                  .getSubject())
          .isEqualTo("owner");
      MockHttpSession navigationSession =
          new MockHttpSession(((WebApplicationContext) h.context).getServletContext());
      var authorization =
          mvc.perform(
                  get("/oauth2/authorization/supabase")
                      .session(navigationSession)
                      .param("bindingId", "binding-a"))
              .andExpect(status().is3xxRedirection())
              .andReturn();
      MockHttpServletRequest callback = new MockHttpServletRequest();
      callback.setSession((MockHttpSession) authorization.getRequest().getSession());
      MockHttpServletResponse redirected = new MockHttpServletResponse();
      h.context
          .getBean(DeskLoginNavigation.class)
          .onAuthenticationSuccess(callback, redirected, authentication);
      assertThat(redirected.getRedirectedUrl()).isEqualTo("/?bindingId=binding-a");
      mvc.perform(get("/oauth2/authorization/supabase").param("bindingId", "https://evil.test/"))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/?login=cancelled"));
    }
  }

  record BridgeRequest(String path, String bearer, String key, String binding, String body) {}

  static class Harness implements AutoCloseable {
    final DeskTestDatabase db;
    final HttpServer identity;
    final String issuer;
    final RSAKey rsa;
    final ECKey ec;
    final ConfigurableApplicationContext context;
    final HttpClient http =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    final List<BridgeRequest> bridgeRequests = new CopyOnWriteArrayList<>();
    final String base;

    Harness(String engine) throws Exception {
      db = new DeskTestDatabase(engine);
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      rsa =
          new RSAKey.Builder((RSAPublicKey) pair.getPublic())
              .privateKey(pair.getPrivate())
              .keyID("desk-test")
              .build();
      identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      var ecGenerator = KeyPairGenerator.getInstance("EC");
      ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
      var ecPair = ecGenerator.generateKeyPair();
      ec =
          new ECKey.Builder(Curve.P_256, (ECPublicKey) ecPair.getPublic())
              .privateKey(ecPair.getPrivate())
              .keyID("desk-es256")
              .build();
      issuer = "http://127.0.0.1:" + identity.getAddress().getPort() + "/auth/v1";
      identity.createContext(
          "/auth/v1/.well-known/jwks.json",
          exchange -> {
            byte[] payload =
                new JWKSet(List.of(rsa.toPublicJWK(), ec.toPublicJWK()))
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
          });
      identity.createContext(
          "/auth/v1/user",
          exchange -> {
            byte[] payload =
                "{\"id\":\"agent\",\"email\":\"agent@example.test\",\"email_confirmed_at\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
          });
      identity.createContext(
          "/bridge/",
          exchange -> {
            bridgeRequests.add(
                new BridgeRequest(
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Service-Key"),
                    exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Binding-Id"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] payload =
                "{\"tasks\":[],\"outcome\":\"ACTUAL_BRIDGE_RESULT\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
          });
      identity.start();
      context =
          new SpringApplicationBuilder(LoomAiDeskApplication.class)
              .run(
                  "--server.port=0",
                  "--spring.datasource.url=" + db.url,
                  "--spring.datasource.username=" + db.username,
                  "--spring.datasource.password=" + db.password,
                  "--loomai.desk.service-key=" + ADMIN,
                  "--loomai.desk.data-encryption-secret=data-encryption-secret-separate-1234567890123456",
                  "--loomai.desk.supabase-url=http://127.0.0.1:" + identity.getAddress().getPort(),
                  "--loomai.desk.supabase-publishable-key=sb_publishable_test",
                  "--loomai.desk.issuer=" + issuer,
                  "--loomai.desk.jwks-uri=" + issuer + "/.well-known/jwks.json",
                  "--loomai.desk.client-id=desk-test-client",
                  "--DESK_CALLBACK_POLL_MS=3600000",
                  "--logging.level.root=WARN",
                  "--spring.main.banner-mode=off");
      base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
    }

    String token(String subject, String issuer, String audience, boolean verified, long duration)
        throws Exception {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .subject(subject)
              .audience(audience)
              .issueTime(new Date())
              .expirationTime(Date.from(Instant.now().plusSeconds(duration)))
              .claim("email", subject + "@example.test")
              .claim("email_verified", verified)
              .claim("user_metadata", Map.of("email_verified", true))
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .type(JOSEObjectType.JWT)
                  .keyID(rsa.getKeyID())
                  .build(),
              claims);
      jwt.sign(new RSASSASigner(rsa));
      return jwt.serialize();
    }

    HttpResponse<String> request(String method, String path, String bearer, String body)
        throws Exception {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
      if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
      if (body != null) builder.header("Content-Type", "application/json");
      return http.send(
          builder
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    String esToken(String subject, String audience) throws Exception {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .subject(subject)
              .audience(audience)
              .issueTime(new Date())
              .expirationTime(Date.from(Instant.now().plusSeconds(600)))
              .claim("email", subject + "@example.test")
              .claim("email_verified", true)
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ec.getKeyID()).build(), claims);
      jwt.sign(new ECDSASigner(ec));
      return jwt.serialize();
    }

    HttpResponse<String> adminGet(String path) throws Exception {
      return http.send(
          HttpRequest.newBuilder(URI.create(base + "/api/desk/admin/v1" + path))
              .header("X-LoomAI-Desk-Service-Key", ADMIN)
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    void provision() throws Exception {
      for (String customer : List.of("a", "b")) {
        String owner = customer.equals("a") ? "owner" : "other-owner";
        String workspace =
            JSON.writeValueAsString(
                new WorkspaceInput(
                    customer,
                    List.of(new OwnerInput(issuer, owner, owner + "@example.test", owner))));
        assertThat(admin("/workspaces/" + customer, workspace).statusCode()).isEqualTo(200);
        BindingInput b =
            new BindingInput(
                customer,
                "tenant-" + customer,
                "dep-" + customer,
                "production",
                customer,
                "PRODUCTION",
                "http://localhost/callback",
                "provider-" + customer + "-123456789012345678901234567890",
                "signing-" + customer + "-123456789012345678901234567890",
                "http://127.0.0.1:"
                    + identity.getAddress().getPort()
                    + "/bridge/"
                    + customer
                    + "/reviews",
                BRIDGE,
                true);
        HttpResponse<String> result = admin("/bindings/" + customer, JSON.writeValueAsString(b));
        assertThat(result.statusCode()).withFailMessage(result.body()).isEqualTo(200);
        assertThat(result.body())
            .doesNotContain(b.providerApiKey(), b.callbackSigningSecret(), BRIDGE);
      }
    }

    HttpResponse<String> admin(String path, String body) throws Exception {
      return http.send(
          HttpRequest.newBuilder(URI.create(base + "/api/desk/admin/v1" + path))
              .header("X-LoomAI-Desk-Service-Key", ADMIN)
              .header("Content-Type", "application/json")
              .PUT(HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    public void close() {
      context.close();
      identity.stop(0);
      db.close();
    }
  }
}
