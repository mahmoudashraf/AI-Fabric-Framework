package com.loomai.desk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.config.DeskProperties;
import com.loomai.desk.model.DeskContracts.VerifiedIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeskIdentityService {
  private final DeskProperties p;
  private final OAuth2AuthorizedClientManager clients;
  private final JwtDecoder decoder;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public DeskIdentityService(
      DeskProperties p,
      OAuth2AuthorizedClientManager clients,
      JwtDecoder decoder,
      ObjectMapper mapper) {
    this.p = p;
    this.clients = clients;
    this.decoder = decoder;
    this.mapper = mapper;
    http =
        HttpClient.newBuilder()
            .connectTimeout(p.getRequestTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public VerifiedIdentity require(Authentication auth) {
    if (auth instanceof JwtAuthenticationToken token) return fromJwt(token.getToken());
    if (auth instanceof OAuth2AuthenticationToken token
        && token.getPrincipal() instanceof OidcUser user) {
      OAuth2AuthorizedClient client;
      try {
        client =
            clients.authorize(
                OAuth2AuthorizeRequest.withClientRegistrationId(
                        token.getAuthorizedClientRegistrationId())
                    .principal(auth)
                    .build());
      } catch (OAuth2AuthorizationException e) {
        throw unauthorized();
      }
      if (client == null
          || client.getAccessToken() == null
          || client.getAccessToken().getExpiresAt() == null
          || !client.getAccessToken().getExpiresAt().isAfter(Instant.now())) throw unauthorized();
      Jwt jwt;
      try {
        jwt = decoder.decode(client.getAccessToken().getTokenValue());
      } catch (JwtException e) {
        throw unauthorized();
      }
      if (!user.getSubject().equals(jwt.getSubject())
          || !p.getIssuer().equals(user.getIssuer().toString())) throw unauthorized();
      String email = user.getEmail();
      if (!Boolean.TRUE.equals(user.getEmailVerified()) || email == null)
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email to use Desk.");
      return new VerifiedIdentity(
          p.getIssuer(),
          user.getSubject(),
          email,
          name(user.getFullName(), email),
          true,
          client.getAccessToken().getTokenValue());
    }
    throw unauthorized();
  }

  private VerifiedIdentity fromJwt(Jwt jwt) {
    String email = jwt.getClaimAsString("email");
    boolean verified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
    // Supabase user_metadata is writable by the user; never use its email_verified flag as proof.
    if (!verified) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder(URI.create(p.getSupabaseUrl() + "/auth/v1/user"))
                .timeout(p.getRequestTimeout())
                .header("Authorization", "Bearer " + jwt.getTokenValue())
                .header("apikey", p.getSupabasePublishableKey())
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw unauthorized();
        JsonNode user = mapper.readTree(response.body());
        if (!jwt.getSubject().equals(user.path("id").asText())
            || user.path("email_confirmed_at").isNull()
            || user.path("email_confirmed_at").asText().isBlank())
          throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email to use Desk.");
        email = user.path("email").asText(null);
        verified = email != null;
      } catch (ResponseStatusException e) {
        throw e;
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "Identity verification is temporarily unavailable.");
      }
    }
    if (!verified || email == null)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verified email is required.");
    return new VerifiedIdentity(
        p.getIssuer(),
        jwt.getSubject(),
        email,
        name(jwt.getClaimAsString("name"), email),
        true,
        jwt.getTokenValue());
  }

  private String name(String name, String email) {
    String value = name == null || name.isBlank() ? email : name;
    return value.length() > 160 ? value.substring(0, 160) : value;
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to LoomAI Desk again.");
  }
}
