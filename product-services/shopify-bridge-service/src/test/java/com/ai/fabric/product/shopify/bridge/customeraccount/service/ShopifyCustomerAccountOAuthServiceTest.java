package com.ai.fabric.product.shopify.bridge.customeraccount.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.config.ShopifyMcpExternalAuthProperties;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.customeraccount.entity.ShopifyCustomerAccountAuthClaimEntity;
import com.ai.fabric.product.shopify.bridge.customeraccount.entity.ShopifyCustomerAccountSessionEntity;
import com.ai.fabric.product.shopify.bridge.customeraccount.model.ShopifyCustomerAccountAuthStatus;
import com.ai.fabric.product.shopify.bridge.customeraccount.repository.ShopifyCustomerAccountAuthClaimRepository;
import com.ai.fabric.product.shopify.bridge.customeraccount.repository.ShopifyCustomerAccountSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShopifyCustomerAccountOAuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void beginsPkceAuthorizationAndBindsEncryptedTokenToShopperSession() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyCustomerAccountSessionRepository repository = mock(ShopifyCustomerAccountSessionRepository.class);
        ShopifyCustomerAccountAuthClaimRepository claimRepository = mock(ShopifyCustomerAccountAuthClaimRepository.class);
        ShopifyCustomerAccountOAuthService service = service(repository, claimRepository, builder);
        when(repository.findByShopDomainIgnoreCaseAndShopperSessionIdHash(
            eq("alpha.myshopify.com"),
            anyString()
        )).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(ShopifyCustomerAccountSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://alpha.myshopify.com/.well-known/openid-configuration"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "authorization_endpoint": "https://shopify.com/authentication/1/oauth/authorize",
                  "token_endpoint": "https://shopify.com/authentication/1/oauth/token"
                }
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://shopify.com/authentication/1/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, basic("customer-client-id", "customer-client-secret")))
            .andExpect(content().string(containsString("grant_type=authorization_code")))
            .andExpect(content().string(containsString("code=oauth-code")))
            .andExpect(content().string(containsString("code_verifier=")))
            .andRespond(withSuccess("""
                {
                  "access_token": "customer-access-token",
                  "refresh_token": "customer-refresh-token",
                  "id_token": "customer-id-token",
                  "token_type": "Bearer",
                  "scope": "customer-account-mcp-api:full",
                  "expires_in": 3600
                }
                """, MediaType.APPLICATION_JSON));

        URI authorization = service.beginAuthorization(
            "Alpha.MyShopify.Com",
            "shopper-session-1",
            "https://alpha.myshopify.com/products/red-bag"
        );
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");

        assertThat(authorization.toString())
            .startsWith("https://shopify.com/authentication/1/oauth/authorize")
            .contains("client_id=customer-client-id")
            .contains("code_challenge_method=S256")
            .contains("customer-account-mcp-api:full");
        assertThat(state).isNotBlank();

        URI returnTo = service.completeAuthorization("oauth-code", state);

        var returnQuery = UriComponentsBuilder.fromUri(returnTo).build().getQueryParams();
        assertThat(returnTo.toString()).startsWith("https://alpha.myshopify.com/products/red-bag?");
        assertThat(returnQuery.getFirst("loomCustomerAuth")).isEqualTo("connected");
        assertThat(returnQuery.getFirst("loomCustomerAuthClaim")).matches("^scac-[A-Za-z0-9]{32}$");
        ArgumentCaptor<ShopifyCustomerAccountSessionEntity> captor =
            ArgumentCaptor.forClass(ShopifyCustomerAccountSessionEntity.class);
        verify(repository).save(captor.capture());
        ShopifyCustomerAccountSessionEntity saved = captor.getValue();
        assertThat(saved.getAccessTokenCiphertext()).doesNotContain("customer-access-token");
        assertThat(saved.getRefreshTokenCiphertext()).doesNotContain("customer-refresh-token");

        when(repository.findByShopDomainIgnoreCaseAndShopperSessionIdHashAndRevokedAtIsNull(
            eq("alpha.myshopify.com"),
            anyString()
        )).thenReturn(Optional.of(saved));

        assertThat(service.resolveAccessToken("alpha.myshopify.com", "shopper-session-1"))
            .contains("customer-access-token");
        ShopifyCustomerAccountAuthStatus status = service.status("alpha.myshopify.com", "shopper-session-1");
        assertThat(status.authenticated()).isTrue();
        assertThat(status.scopes()).isEqualTo("customer-account-mcp-api:full");
        verify(claimRepository).save(org.mockito.ArgumentMatchers.any(ShopifyCustomerAccountAuthClaimEntity.class));
        server.verify();
    }

    @Test
    void completeAuthorizationReactivatesRevokedSessionRow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyCustomerAccountSessionRepository repository = mock(ShopifyCustomerAccountSessionRepository.class);
        ShopifyCustomerAccountOAuthService service = service(repository, builder);
        ShopifyCustomerAccountSessionEntity revoked = new ShopifyCustomerAccountSessionEntity();
        revoked.setId("scas-existing");
        revoked.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        revoked.setRevokedAt(Instant.parse("2026-05-02T00:00:00Z"));
        when(repository.findByShopDomainIgnoreCaseAndShopperSessionIdHash(
            eq("alpha.myshopify.com"),
            anyString()
        )).thenReturn(Optional.of(revoked));
        when(repository.save(org.mockito.ArgumentMatchers.any(ShopifyCustomerAccountSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://alpha.myshopify.com/.well-known/openid-configuration"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "authorization_endpoint": "https://shopify.com/authentication/1/oauth/authorize",
                  "token_endpoint": "https://shopify.com/authentication/1/oauth/token"
                }
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://shopify.com/authentication/1/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "access_token": "new-customer-access-token",
                  "refresh_token": "new-customer-refresh-token",
                  "token_type": "Bearer",
                  "scope": "customer-account-mcp-api:full",
                  "expires_in": 3600
                }
                """, MediaType.APPLICATION_JSON));

        URI authorization = service.beginAuthorization(
            "alpha.myshopify.com",
            "shopper-session-1",
            "https://alpha.myshopify.com/account"
        );
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");

        service.completeAuthorization("oauth-code", state);

        ArgumentCaptor<ShopifyCustomerAccountSessionEntity> captor =
            ArgumentCaptor.forClass(ShopifyCustomerAccountSessionEntity.class);
        verify(repository).save(captor.capture());
        ShopifyCustomerAccountSessionEntity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo("scas-existing");
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.getAccessTokenCiphertext()).doesNotContain("new-customer-access-token");
        server.verify();
    }

    @Test
    void unsafeReturnToFallsBackToShopAccountPage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyCustomerAccountOAuthService service = service(mock(ShopifyCustomerAccountSessionRepository.class), builder);

        server.expect(requestTo("https://alpha.myshopify.com/.well-known/openid-configuration"))
            .andRespond(withSuccess("""
                {
                  "authorization_endpoint": "https://shopify.com/authentication/1/oauth/authorize",
                  "token_endpoint": "https://shopify.com/authentication/1/oauth/token"
                }
                """, MediaType.APPLICATION_JSON));

        URI authorization = service.beginAuthorization(
            "alpha.myshopify.com",
            "shopper-session-1",
            "https://evil.example/capture"
        );
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        URI target = service.failedAuthorizationReturn(state);

        assertThat(target.toString()).isEqualTo("https://alpha.myshopify.com/account?loomCustomerAuth=failed");
        server.verify();
    }

    @Test
    void configuredStorefrontDomainIsUsedForDiscoveryAndReturnToWhileSessionStaysCanonical() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyCustomerAccountSessionRepository repository = mock(ShopifyCustomerAccountSessionRepository.class);
        ShopifyCustomerAccountOAuthService service = service(repository, builder, "shop-staging.loomai.pro");
        when(repository.findByShopDomainIgnoreCaseAndShopperSessionIdHash(
            eq("alpha.myshopify.com"),
            anyString()
        )).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(ShopifyCustomerAccountSessionEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("https://shop-staging.loomai.pro/.well-known/openid-configuration"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "authorization_endpoint": "https://shopify.com/authentication/1/oauth/authorize",
                  "token_endpoint": "https://shopify.com/authentication/1/oauth/token"
                }
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://shopify.com/authentication/1/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {
                  "access_token": "customer-access-token",
                  "refresh_token": "customer-refresh-token",
                  "token_type": "Bearer",
                  "scope": "customer-account-mcp-api:full",
                  "expires_in": 3600
                }
                """, MediaType.APPLICATION_JSON));

        URI authorization = service.beginAuthorization(
            "alpha.myshopify.com",
            "shopper-session-1",
            "https://shop-staging.loomai.pro/account/orders"
        );
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");

        URI returnTo = service.completeAuthorization("oauth-code", state);

        var returnQuery = UriComponentsBuilder.fromUri(returnTo).build().getQueryParams();
        assertThat(returnTo.toString()).startsWith("https://shop-staging.loomai.pro/account/orders?");
        assertThat(returnQuery.getFirst("loomCustomerAuth")).isEqualTo("connected");
        assertThat(returnQuery.getFirst("loomCustomerAuthClaim")).matches("^scac-[A-Za-z0-9]{32}$");
        ArgumentCaptor<ShopifyCustomerAccountSessionEntity> captor =
            ArgumentCaptor.forClass(ShopifyCustomerAccountSessionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getShopDomain()).isEqualTo("alpha.myshopify.com");
        server.verify();
    }

    @Test
    void claimBrowserSessionRebindsCompletedAuthToCurrentStorefrontSession() {
        RestClient.Builder builder = RestClient.builder();
        ShopifyCustomerAccountSessionRepository repository = mock(ShopifyCustomerAccountSessionRepository.class);
        ShopifyCustomerAccountAuthClaimRepository claimRepository = mock(ShopifyCustomerAccountAuthClaimRepository.class);
        ShopifyCustomerAccountOAuthService service = service(repository, claimRepository, builder);
        String sourceHash = serviceTestSessionHash(service, "alpha.myshopify.com", "shopper-session-before-redirect");
        String targetHash = serviceTestSessionHash(service, "alpha.myshopify.com", "shopper-session-after-redirect");

        ShopifyCustomerAccountSessionEntity source = new ShopifyCustomerAccountSessionEntity();
        source.setId("scas-source");
        source.setShopDomain("alpha.myshopify.com");
        source.setShopperSessionIdHash(sourceHash);
        source.setTokenEndpoint("https://shopify.com/authentication/1/oauth/token");
        source.setAccessTokenCiphertext("encrypted-access");
        source.setRefreshTokenCiphertext("encrypted-refresh");
        source.setTokenType("Bearer");
        source.setScopesText("customer-account-mcp-api:full");
        source.setAccessTokenExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        source.setSessionExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        source.setCreatedAt(Instant.now().minus(Duration.ofMinutes(1)));
        source.setUpdatedAt(Instant.now().minus(Duration.ofMinutes(1)));

        ShopifyCustomerAccountAuthClaimEntity claim = new ShopifyCustomerAccountAuthClaimEntity();
        claim.setId("scac-12345678901234567890123456789012");
        claim.setShopDomain("alpha.myshopify.com");
        claim.setSourceShopperSessionIdHash(sourceHash);
        claim.setCreatedAt(Instant.now().minus(Duration.ofSeconds(10)));
        claim.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));

        when(claimRepository.findById("scac-12345678901234567890123456789012")).thenReturn(Optional.of(claim));
        when(repository.findByShopDomainIgnoreCaseAndShopperSessionIdHashAndRevokedAtIsNull(
            eq("alpha.myshopify.com"),
            eq(sourceHash)
        )).thenReturn(Optional.of(source));
        when(repository.findByShopDomainIgnoreCaseAndShopperSessionIdHash(
            eq("alpha.myshopify.com"),
            eq(targetHash)
        )).thenReturn(Optional.empty());
        when(repository.findByShopDomainIgnoreCaseAndShopperSessionIdHashAndRevokedAtIsNull(
            eq("alpha.myshopify.com"),
            eq(targetHash)
        )).thenAnswer(invocation -> {
            ShopifyCustomerAccountSessionEntity rebound = new ShopifyCustomerAccountSessionEntity();
            rebound.setId("scas-target");
            rebound.setShopDomain("alpha.myshopify.com");
            rebound.setShopperSessionIdHash(targetHash);
            rebound.setAccessTokenCiphertext("encrypted-access");
            rebound.setSessionExpiresAt(Instant.now().plus(Duration.ofDays(1)));
            rebound.setCreatedAt(Instant.now());
            rebound.setUpdatedAt(Instant.now());
            return Optional.of(rebound);
        });

        ShopifyCustomerAccountAuthStatus status = service.claimBrowserSession(
            "alpha.myshopify.com",
            "shopper-session-after-redirect",
            "scac-12345678901234567890123456789012"
        );

        ArgumentCaptor<ShopifyCustomerAccountSessionEntity> sessionCaptor =
            ArgumentCaptor.forClass(ShopifyCustomerAccountSessionEntity.class);
        verify(repository).save(sessionCaptor.capture());
        ShopifyCustomerAccountSessionEntity rebound = sessionCaptor.getValue();
        assertThat(rebound.getShopperSessionIdHash()).isEqualTo(targetHash);
        assertThat(rebound.getAccessTokenCiphertext()).isEqualTo("encrypted-access");
        assertThat(status.authenticated()).isTrue();
        assertThat(claim.getConsumedAt()).isNotNull();
        verify(claimRepository).save(claim);
    }

    @Test
    void perStorePlatformStorefrontDomainOverridesGlobalFallback() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ShopifyBridgeProperties bridgeProperties = bridgeProperties();
        PlatformShopifyStoreClient platformClient = new PlatformShopifyStoreClient(builder, bridgeProperties);
        ShopifyCustomerAccountOAuthService service = service(
            mock(ShopifyCustomerAccountSessionRepository.class),
            mock(ShopifyCustomerAccountAuthClaimRepository.class),
            builder,
            "global-staging.loomai.pro",
            platformClient,
            bridgeProperties
        );

        server.expect(requestTo("https://platform.example/api/shopify/stores/alpha.myshopify.com/customer-account-config"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess("""
                {
                  "shopDomain": "alpha.myshopify.com",
                  "storefrontDomain": "store-alpha.example",
                  "storefrontDomainConfigured": true,
                  "effectiveStorefrontDomain": "store-alpha.example",
                  "source": "STORE_CONFIG",
                  "updatedAt": "2026-05-09T10:00:00Z"
                }
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://store-alpha.example/.well-known/openid-configuration"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {
                  "authorization_endpoint": "https://shopify.com/authentication/1/oauth/authorize",
                  "token_endpoint": "https://shopify.com/authentication/1/oauth/token"
                }
                """, MediaType.APPLICATION_JSON));

        URI authorization = service.beginAuthorization(
            "alpha.myshopify.com",
            "shopper-session-1",
            "https://store-alpha.example/account"
        );

        assertThat(authorization.toString())
            .startsWith("https://shopify.com/authentication/1/oauth/authorize")
            .contains("client_id=customer-client-id");
        server.verify();
    }

    private ShopifyCustomerAccountOAuthService service(ShopifyCustomerAccountSessionRepository repository,
                                                       RestClient.Builder builder) {
        return service(repository, mock(ShopifyCustomerAccountAuthClaimRepository.class), builder, "");
    }

    private ShopifyCustomerAccountOAuthService service(ShopifyCustomerAccountSessionRepository repository,
                                                       ShopifyCustomerAccountAuthClaimRepository claimRepository,
                                                       RestClient.Builder builder) {
        return service(repository, claimRepository, builder, "");
    }

    private ShopifyCustomerAccountOAuthService service(ShopifyCustomerAccountSessionRepository repository,
                                                       RestClient.Builder builder,
                                                       String storefrontDomain) {
        return service(repository, mock(ShopifyCustomerAccountAuthClaimRepository.class), builder, storefrontDomain, null, bridgeProperties());
    }

    private ShopifyCustomerAccountOAuthService service(ShopifyCustomerAccountSessionRepository repository,
                                                       ShopifyCustomerAccountAuthClaimRepository claimRepository,
                                                       RestClient.Builder builder,
                                                       String storefrontDomain) {
        return service(repository, claimRepository, builder, storefrontDomain, null, bridgeProperties());
    }

    private ShopifyCustomerAccountOAuthService service(ShopifyCustomerAccountSessionRepository repository,
                                                       ShopifyCustomerAccountAuthClaimRepository claimRepository,
                                                       RestClient.Builder builder,
                                                       String storefrontDomain,
                                                       PlatformShopifyStoreClient platformClient,
                                                       ShopifyBridgeProperties bridgeProperties) {
        ShopifyMcpExternalAuthProperties authProperties = new ShopifyMcpExternalAuthProperties(
            true,
            true,
            "customer-client-id",
            "customer-client-secret",
            "https://bridge.example/api/customer-auth/callback",
            storefrontDomain,
            List.of("customer-account-mcp-api:full"),
            Duration.ofMinutes(10),
            Duration.ofDays(30),
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            false,
            false
        );
        return new ShopifyCustomerAccountOAuthService(
            bridgeProperties,
            authProperties,
            new ShopifyCustomerAccountSecurityService(bridgeProperties, objectMapper),
            repository,
            claimRepository,
            platformClient,
            builder,
            Clock.systemUTC()
        );
    }

    private String serviceTestSessionHash(ShopifyCustomerAccountOAuthService service, String shopDomain, String sessionId) {
        return new ShopifyCustomerAccountSecurityService(bridgeProperties(), objectMapper)
            .hmacHex(service.normalizeShopDomain(shopDomain) + "|" + service.normalizeShopperSessionId(sessionId));
    }

    private ShopifyBridgeProperties bridgeProperties() {
        return new ShopifyBridgeProperties(
            "Shopify Bridge Service",
            "shopify-bridge-test",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "test",
            "https://bridge.example",
            "2026-04",
            "shopify-app-key",
            "shopify-app-secret",
            "https://platform.example",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }

    private String basic(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
