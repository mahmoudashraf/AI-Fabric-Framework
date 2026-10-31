package com.loomai.desk.config;

import com.loomai.desk.channel.service.InboxSecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
public class DeskSecurityConfiguration {
  @Bean
  JwtDecoderFactory<ClientRegistration> deskOidcIdTokenDecoderFactory(DeskProperties properties) {
    SignatureAlgorithm algorithm = SignatureAlgorithm.from(properties.getOidcSigningAlgorithm());
    if (algorithm != SignatureAlgorithm.ES256 && algorithm != SignatureAlgorithm.RS256) {
      throw new IllegalStateException("Desk OIDC requires ES256 or RS256 signing.");
    }
    OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
    factory.setJwsAlgorithmResolver(registration -> algorithm);
    return factory;
  }

  @Bean
  JwtDecoder deskJwtDecoder(DeskProperties p) {
    requireSecret(p.getServiceKey(), "LOOMAI_DESK_SERVICE_KEY");
    requireSecret(p.getDataEncryptionSecret(), "DESK_DATA_ENCRYPTION_SECRET");
    if (p.getServiceKey().equals(p.getDataEncryptionSecret()))
      throw new IllegalStateException("Service and encryption keys must be distinct.");
    requireHttps(p.getIssuer());
    requireHttps(p.getJwksUri());
    requireHttps(p.getSupabaseUrl());
    requireIdentityPortal(p.getIdentityPortalUrl());
    if (p.getAudiences() == null || p.getAudiences().isEmpty())
      throw new IllegalStateException("Supabase token audience is required.");
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(p.getJwksUri())
            .jwsAlgorithms(
                a -> {
                  a.add(SignatureAlgorithm.RS256);
                  a.add(SignatureAlgorithm.ES256);
                })
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(p.getIssuer()),
            jwt ->
                jwt.getSubject() != null
                        && !jwt.getSubject().isBlank()
                        && jwt.getExpiresAt() != null
                        && jwt.getAudience().stream().anyMatch(p.getAudiences()::contains)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                            "invalid_token",
                            "Token subject, expiry or audience is invalid.",
                            null))));
    return decoder;
  }

  @Bean
  ClientRegistrationRepository deskClients(DeskProperties p) {
    // An absent OAuth client disables only interactive OIDC login, never API identity validation.
    if (p.getClientId() == null || p.getClientId().isBlank()) return id -> null;
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("supabase")
            .clientId(p.getClientId())
            .clientSecret(p.getClientSecret() == null ? "" : p.getClientSecret())
            .clientAuthenticationMethod(
                p.getClientSecret() == null || p.getClientSecret().isBlank()
                    ? ClientAuthenticationMethod.NONE
                    : ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "email", "profile")
            .authorizationUri(p.getIssuer() + "/oauth/authorize")
            .tokenUri(p.getIssuer() + "/oauth/token")
            .userInfoUri(p.getIssuer() + "/oauth/userinfo")
            .jwkSetUri(p.getJwksUri())
            .issuerUri(p.getIssuer())
            .userNameAttributeName("sub")
            .clientName("LoomAI")
            .build();
    return new InMemoryClientRegistrationRepository(registration);
  }

  @Bean
  OAuth2AuthorizedClientRepository deskAuthorizedClients(OAuth2AuthorizedClientService service) {
    return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(service);
  }

  @Bean
  OAuth2AuthorizedClientManager deskAuthorizedClientManager(
      ClientRegistrationRepository clients, OAuth2AuthorizedClientRepository saved) {
    DefaultOAuth2AuthorizedClientManager manager =
        new DefaultOAuth2AuthorizedClientManager(clients, saved);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build());
    return manager;
  }

  @Bean
  SecurityFilterChain deskSecurity(
      HttpSecurity http,
      DeskProperties p,
      JwtDecoder decoder,
      ClientRegistrationRepository clients,
      OAuth2AuthorizedClientRepository saved,
      OAuth2AuthorizedClientService clientService,
      DeskLoginNavigation navigation)
      throws Exception {
    RequestMatcher serviceRequest =
        request ->
            request.getServletPath().startsWith("/api/desk/admin/v1/")
                || request.getServletPath().equals("/api/conversation-channel/v1/dispatches");
    RequestMatcher bearerRequest =
        request -> {
          String value = request.getHeader("Authorization");
          return value != null && value.startsWith("Bearer ");
        };
    http.csrf(
        csrf ->
            csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(serviceRequest, bearerRequest));
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    HttpMethod.GET,
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/favicon.ico",
                    "/api/desk/v1/config",
                    "/api/desk/v1/session",
                    "/actuator/health",
                    "/actuator/health/**")
                .permitAll()
                .requestMatchers(
                    "/api/desk/admin/v1/**",
                    "/api/conversation-channel/v1/dispatches",
                    "/oauth2/**",
                    "/login/**",
                    "/error")
                .permitAll()
                .requestMatchers("/api/desk/v1/**")
                .authenticated()
                .anyRequest()
                .denyAll());
    http.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder)));
    if (clients.findByRegistrationId("supabase") != null) {
      DefaultOAuth2AuthorizationRequestResolver resolver =
          new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
      resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
      http.oauth2Login(
          login ->
              login
                  .withObjectPostProcessor(
                      new ObjectPostProcessor<OAuth2AuthorizationRequestRedirectFilter>() {
                        @Override
                        public <O extends OAuth2AuthorizationRequestRedirectFilter> O postProcess(
                            O filter) {
                          filter.setAuthenticationFailureHandler(
                              (request, response, error) ->
                                  response.sendRedirect("/?login=cancelled"));
                          return filter;
                        }
                      })
                  .clientRegistrationRepository(clients)
                  .authorizedClientRepository(saved)
                  .authorizationEndpoint(
                      endpoint ->
                          endpoint.authorizationRequestResolver(
                              navigation.preservingBinding(resolver)))
                  .successHandler(navigation)
                  .failureUrl("/?login=cancelled"));
    }
    http.logout(
        logout ->
            logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .addLogoutHandler(
                    (request, response, authentication) -> {
                      if (authentication != null)
                        clientService.removeAuthorizedClient("supabase", authentication.getName());
                    })
                .deleteCookies("__Host-LOOMAI_DESK_SESSION")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
    http.exceptionHandling(
        errors ->
            errors.authenticationEntryPoint(
                (request, response, error) -> {
                  response.setStatus(401);
                  response.setContentType("application/json");
                  response
                      .getWriter()
                      .write(
                          "{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Sign in to LoomAI Desk.\"}");
                }));
    http.addFilterBefore(
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            if (request.getServletPath().startsWith("/api/desk/admin/v1/")
                && !InboxSecurityService.constantTime(
                    p.getServiceKey(), request.getHeader("X-LoomAI-Desk-Service-Key"))) {
              response.setStatus(401);
              response.setContentType("application/json");
              response.getWriter().write("{\"code\":\"SERVICE_AUTH_INVALID\"}");
              return;
            }
            chain.doFilter(request, response);
          }
        },
        UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private void requireSecret(String value, String name) {
    if (value == null || value.length() < 32)
      throw new IllegalStateException(name + " must have at least 32 characters.");
  }

  private void requireHttps(String value) {
    try {
      URI uri = URI.create(value);
      if (uri.getHost() == null
          || !("https".equals(uri.getScheme())
              || ("http".equals(uri.getScheme())
                  && Set.of("localhost", "127.0.0.1").contains(uri.getHost()))))
        throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new IllegalStateException("Supabase endpoints must use HTTPS outside loopback.");
    }
  }

  private void requireIdentityPortal(String value) {
    if (value.isBlank()) return;
    try {
      requireHttps(value);
      URI uri = URI.create(value);
      if (uri.getRawUserInfo() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null) {
        throw new IllegalArgumentException();
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "DESK_IDENTITY_PORTAL_URL must use HTTPS outside loopback, without credentials, query or fragment.");
    }
  }
}
