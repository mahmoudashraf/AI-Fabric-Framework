package com.loomai.desk.ticket;

import static com.loomai.desk.ticket.TicketContracts.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.loomai.desk.channel.service.InboxSecurityService;
import com.loomai.desk.config.DeskProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Set;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class RequesterIdentityService {
  private final SupportSpaces spaces;
  private final TicketRuntimeClient runtime;
  private final DeskProperties properties;
  private final LinkedHashMap<String, JwtDecoder> decoders = new LinkedHashMap<>(16, .75f, true);

  public RequesterIdentityService(
      SupportSpaces spaces, TicketRuntimeClient runtime, DeskProperties properties) {
    this.spaces = spaces;
    this.runtime = runtime;
    this.properties = properties;
  }

  public boolean emailVerificationAvailable(Space space) {
    return space.identity().mode() == IdentityMode.TRUSTED_PRIVATE_HOST
        || space.identity().issuer().equals(properties.getIssuer())
            && properties.getSupabasePublishableKey() != null
            && !properties.getSupabasePublishableKey().isBlank()
            && (properties.getSupabaseUrl() + "/auth/v1").equals(properties.getIssuer());
  }

  // Called only after the requester filter admits this JWT and its NOTIFICATIONS operation.
  public String verifiedEmail(Space space, String authorization) {
    if (!emailVerificationAvailable(space))
      throw new TicketException(
          422,
          "NOTIFICATION_IDENTITY_UNSUPPORTED",
          "Email verification is not configured for this identity provider.");
    try {
      var jwt = decoder(space).decode(authorization.substring(7));
      String email;
      if (space.identity().mode() == IdentityMode.TRUSTED_PRIVATE_HOST) {
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) throw emailUnverified();
        email = jwt.getClaimAsString("email");
      } else {
        String endpoint = properties.getSupabaseUrl() + "/auth/v1/user";
        var headers = new HttpHeaders();
        headers.setBearerAuth(jwt.getTokenValue());
        headers.set("apikey", properties.getSupabasePublishableKey());
        String payload =
            runtime
                .jwks(endpoint)
                .exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getBody();
        JsonNode user;
        try {
          // Desk uses its existing Jackson 2 mapper, independently of Boot's HTTP converters.
          user = spaces.readJson(payload, JsonNode.class);
        } catch (RuntimeException invalid) {
          throw new TicketException(
              503,
              "NOTIFICATION_IDENTITY_UNAVAILABLE",
              "Email verification is temporarily unavailable.");
        }
        if (user == null
            || !jwt.getSubject().equals(user.path("id").asText())
            || !user.path("email_confirmed_at").isTextual()
            || user.path("email_confirmed_at").asText().isBlank()
            || !user.path("is_anonymous").isBoolean()
            || user.path("is_anonymous").asBoolean()) throw emailUnverified();
        email = user.path("email").asText(null);
      }
      // This is mailbox/header safety, not a semantic validator of user content.
      if (email == null || email.length() > 320 || email.contains("\r") || email.contains("\n"))
        throw emailUnverified();
      var address = new jakarta.mail.internet.InternetAddress(email, true);
      address.validate();
      if (!email.equals(address.getAddress())
          || address.getPersonal() != null
          || !email.contains("@")) throw emailUnverified();
      return email;
    } catch (TicketException e) {
      throw e;
    } catch (org.springframework.web.client.RestClientException e) {
      throw new TicketException(
          503,
          "NOTIFICATION_IDENTITY_UNAVAILABLE",
          "Email verification is temporarily unavailable.");
    } catch (Exception e) {
      throw emailUnverified();
    }
  }

  private TicketException emailUnverified() {
    return new TicketException(
        403,
        "NOTIFICATION_EMAIL_UNVERIFIED",
        "Verify your account email before enabling notifications.");
  }

  public Requester authenticate(
      Space space, String authorization, String serverKey, String operation) {
    if (authorization == null
        || !authorization.startsWith("Bearer ")
        || authorization.length() > 32768) throw unauthorized();
    IdentityInput p = space.identity();
    if (p.mode() == IdentityMode.TRUSTED_PRIVATE_HOST
        && !InboxSecurityService.constantTime(p.privateServerKey(), serverKey))
      throw unauthorized();
    try {
      var jwt = decoder(space).decode(authorization.substring(7));
      Instant now = Instant.now();
      if (jwt.getIssuedAt() == null
          || jwt.getIssuedAt().isAfter(now.plusSeconds(30))
          || jwt.getExpiresAt() == null
          || !jwt.getExpiresAt().isAfter(now)
          || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
          || jwt.getSubject() == null
          || jwt.getSubject().isBlank()
          || Boolean.TRUE.equals(jwt.getClaim("is_anonymous"))) throw unauthorized();
      Set<String> operations = Set.of(operation);
      if (p.mode() == IdentityMode.TRUSTED_PRIVATE_HOST) {
        if (jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plusSeconds(300))
            || !space.view().spaceId().equals(jwt.getClaimAsString("spaceId"))
            || jwt.getId() == null
            || jwt.getId().isBlank()) throw unauthorized();
        var allowed = jwt.getClaimAsStringList("operations");
        if (allowed == null || !allowed.contains(operation))
          throw new TicketException(
              403,
              "REQUESTER_OPERATION_DENIED",
              "Requester assertion does not permit this operation.");
        operations = Set.copyOf(allowed);
      }
      Requester requester = spaces.requester(space, jwt.getSubject(), operations);
      if (p.mode() == IdentityMode.TRUSTED_PRIVATE_HOST) {
        String principal = jwt.getClaimAsString("runtimePrincipal");
        String conversation = jwt.getClaimAsString("runtimeConversationId");
        if (principal != null) SupportSpaces.text(principal, 256, "signed runtimePrincipal");
        if (conversation != null)
          SupportSpaces.text(conversation, 160, "signed runtimeConversationId");
        return new Requester(
            requester.id(), requester.subject(), operations, principal, conversation);
      }
      return requester;
    } catch (TicketException e) {
      throw e;
    } catch (Exception e) {
      for (Throwable cause = e; cause != null; cause = cause.getCause())
        if (cause instanceof org.springframework.web.client.RestClientException)
          throw new TicketException(
              503,
              "REQUESTER_IDENTITY_UNAVAILABLE",
              "Identity verification is temporarily unavailable.");
      throw unauthorized();
    }
  }

  public Requester authenticateServerConnector(Space space, HttpServletRequest request) {
    var profile = space.serverConnector();
    if (profile == null
        || space.identity().mode() != IdentityMode.SUPABASE
        || !InboxSecurityService.constantTime(
            space.connectorKey(), single(request, "X-LoomAI-Ticket-Connector-Key")))
      throw unauthorized();
    var binding = spaces.binding(space);
    match(request, "X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER");
    match(request, "X-AIFABRIC-AUTH-MODE", profile.authMode());
    match(request, "X-AIFABRIC-AUTH-CALLER-TYPE", profile.callerType());
    match(request, "X-AIFABRIC-AUTH-ISSUER", profile.issuer());
    match(request, "X-AIFABRIC-AUTH-CUSTOMER-ID", binding.customerId());
    match(request, "X-AIFABRIC-AUTH-TENANT-ID", binding.tenantId());
    match(request, "X-AIFABRIC-AUTH-DEPLOYMENT-ID", binding.deploymentId());
    var audiences =
        java.util.Arrays.asList(single(request, "X-AIFABRIC-AUTH-AUDIENCES").split(","));
    var scopes = java.util.Arrays.asList(single(request, "X-AIFABRIC-AUTH-SCOPES").split(","));
    if (!audiences.contains(profile.audience()) || !scopes.containsAll(profile.requiredScopes()))
      throw unauthorized();
    try {
      if (!Instant.parse(single(request, "X-AIFABRIC-AUTH-EXPIRES-AT")).isAfter(Instant.now()))
        throw unauthorized();
    } catch (java.time.format.DateTimeParseException invalid) {
      throw unauthorized();
    }
    String subject = single(request, "X-AIFABRIC-AUTH-SUBJECT-ID");
    String conversation =
        request.getHeader("X-AIFABRIC-CONVERSATION-ID") == null
            ? null
            : single(request, "X-AIFABRIC-CONVERSATION-ID");
    if (conversation != null && !conversation.matches("[A-Za-z0-9_-]{1,160}")) throw unauthorized();
    Set<String> operations = Set.of("CREATE", "GET", "LIST", "LINK_CONVERSATION");
    var owner = spaces.requester(space, subject, operations);
    return new Requester(owner.id(), owner.subject(), operations, subject, conversation);
  }

  private void match(HttpServletRequest request, String header, String expected) {
    if (!expected.equals(single(request, header))) throw unauthorized();
  }

  private String single(HttpServletRequest request, String header) {
    var values = java.util.Collections.list(request.getHeaders(header));
    if (values.size() != 1 || values.getFirst().isBlank() || values.getFirst().length() > 8192)
      throw unauthorized();
    return values.getFirst();
  }

  private synchronized JwtDecoder decoder(Space s) {
    String key = s.view().spaceId() + ":" + s.view().version();
    JwtDecoder found = decoders.get(key);
    if (found != null) return found;
    IdentityInput p = s.identity();
    NimbusJwtDecoder created =
        NimbusJwtDecoder.withJwkSetUri(p.jwksUri())
            .restOperations(runtime.jwks(p.jwksUri()))
            .jwsAlgorithms(
                a -> {
                  a.add(SignatureAlgorithm.RS256);
                  a.add(SignatureAlgorithm.ES256);
                })
            .build();
    created.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(p.issuer()),
            jwt ->
                jwt.getAudience().contains(p.audience())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
    if (decoders.size() >= 128) decoders.remove(decoders.keySet().iterator().next());
    decoders.put(key, created);
    return created;
  }

  private TicketException unauthorized() {
    return new TicketException(
        401, "REQUESTER_AUTH_INVALID", "Sign in again to access your requests.");
  }
}
