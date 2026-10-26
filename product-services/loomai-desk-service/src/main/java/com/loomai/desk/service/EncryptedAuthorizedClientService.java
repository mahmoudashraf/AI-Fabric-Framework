package com.loomai.desk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.desk.channel.config.BasicInboxProperties;
import com.loomai.desk.channel.service.InboxSecurityService;
import com.loomai.desk.config.DeskProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncryptedAuthorizedClientService implements OAuth2AuthorizedClientService {
  private final JdbcTemplate jdbc;
  private final ClientRegistrationRepository registrations;
  private final ObjectMapper mapper;
  private final InboxSecurityService security;

  public EncryptedAuthorizedClientService(
      JdbcTemplate jdbc,
      ClientRegistrationRepository registrations,
      ObjectMapper mapper,
      DeskProperties properties) {
    this.jdbc = jdbc;
    this.registrations = registrations;
    this.mapper = mapper;
    BasicInboxProperties p = new BasicInboxProperties();
    p.setDataEncryptionSecret(properties.getDataEncryptionSecret());
    security = new InboxSecurityService(p);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
      String registration, String principal) {
    if (registrations.findByRegistrationId(registration) == null) return null;
    return (T)
        jdbc
            .query(
                "SELECT protected_tokens FROM desk_oauth_client WHERE registration_id=? AND principal_name=?",
                (rs, n) -> {
                  try {
                    Tokens tokens =
                        mapper.readValue(security.decrypt(rs.getString(1)), Tokens.class);
                    OAuth2AccessToken access =
                        new OAuth2AccessToken(
                            OAuth2AccessToken.TokenType.BEARER,
                            tokens.access(),
                            tokens.issuedAt(),
                            tokens.expiresAt(),
                            tokens.scopes());
                    OAuth2RefreshToken refresh =
                        tokens.refresh() == null
                            ? null
                            : new OAuth2RefreshToken(tokens.refresh(), tokens.refreshIssuedAt());
                    return new OAuth2AuthorizedClient(
                        registrations.findByRegistrationId(registration),
                        principal,
                        access,
                        refresh);
                  } catch (Exception e) {
                    throw new IllegalStateException("Stored OAuth client cannot be read.", e);
                  }
                },
                registration,
                principal)
            .stream()
            .findFirst()
            .orElse(null);
  }

  @Override
  @Transactional
  public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) {
    try {
      OAuth2AccessToken access = client.getAccessToken();
      OAuth2RefreshToken refresh = client.getRefreshToken();
      Tokens tokens =
          new Tokens(
              access.getTokenValue(),
              access.getIssuedAt(),
              access.getExpiresAt(),
              access.getScopes(),
              refresh == null ? null : refresh.getTokenValue(),
              refresh == null ? null : refresh.getIssuedAt());
      String protectedTokens = security.encrypt(mapper.writeValueAsString(tokens));
      int updated =
          jdbc.update(
              "UPDATE desk_oauth_client SET protected_tokens=?,updated_at=? WHERE registration_id=? AND principal_name=?",
              protectedTokens,
              Timestamp.from(Instant.now()),
              client.getClientRegistration().getRegistrationId(),
              principal.getName());
      if (updated == 0)
        jdbc.update(
            "INSERT INTO desk_oauth_client(registration_id,principal_name,protected_tokens,updated_at) VALUES(?,?,?,?)",
            client.getClientRegistration().getRegistrationId(),
            principal.getName(),
            protectedTokens,
            Timestamp.from(Instant.now()));
    } catch (org.springframework.dao.DataAccessException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("OAuth credentials cannot be stored.", e);
    }
  }

  @Override
  public void removeAuthorizedClient(String registration, String principal) {
    jdbc.update(
        "DELETE FROM desk_oauth_client WHERE registration_id=? AND principal_name=?",
        registration,
        principal);
  }

  private record Tokens(
      String access,
      Instant issuedAt,
      Instant expiresAt,
      Set<String> scopes,
      String refresh,
      Instant refreshIssuedAt) {}
}
