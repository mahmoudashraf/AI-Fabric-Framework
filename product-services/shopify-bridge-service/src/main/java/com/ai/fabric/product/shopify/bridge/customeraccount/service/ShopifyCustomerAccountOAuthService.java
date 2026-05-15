package com.ai.fabric.product.shopify.bridge.customeraccount.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.config.ShopifyMcpExternalAuthProperties;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.customeraccount.entity.ShopifyCustomerAccountAuthClaimEntity;
import com.ai.fabric.product.shopify.bridge.customeraccount.entity.ShopifyCustomerAccountSessionEntity;
import com.ai.fabric.product.shopify.bridge.customeraccount.model.ShopifyCustomerAccountAuthStatus;
import com.ai.fabric.product.shopify.bridge.customeraccount.repository.ShopifyCustomerAccountAuthClaimRepository;
import com.ai.fabric.product.shopify.bridge.customeraccount.repository.ShopifyCustomerAccountSessionRepository;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeCustomerAccountConfigSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyCustomerAccountOAuthService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyCustomerAccountOAuthService.class);

    private static final Pattern SHOP_DOMAIN_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");
    private static final Pattern CUSTOMER_ACCOUNT_DOMAIN_PATTERN =
        Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,120}$");
    private static final Pattern SAFE_AUTH_CLAIM_ID = Pattern.compile("^scac-[A-Za-z0-9]{32}$");
    private static final Duration TOKEN_REFRESH_SKEW = Duration.ofMinutes(2);
    private static final Duration AUTH_CLAIM_TTL = Duration.ofMinutes(5);

    private final ShopifyBridgeProperties bridgeProperties;
    private final ShopifyMcpExternalAuthProperties authProperties;
    private final ShopifyCustomerAccountSecurityService securityService;
    private final ShopifyCustomerAccountSessionRepository repository;
    private final ShopifyCustomerAccountAuthClaimRepository authClaimRepository;
    private final PlatformShopifyStoreClient platformStoreClient;
    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    public ShopifyCustomerAccountOAuthService(ShopifyBridgeProperties bridgeProperties,
                                              ShopifyMcpExternalAuthProperties authProperties,
                                              ShopifyCustomerAccountSecurityService securityService,
                                              ShopifyCustomerAccountSessionRepository repository,
                                              ShopifyCustomerAccountAuthClaimRepository authClaimRepository,
                                              RestClient.Builder restClientBuilder,
                                              PlatformShopifyStoreClient platformStoreClient) {
        this(
            bridgeProperties,
            authProperties,
            securityService,
            repository,
            authClaimRepository,
            platformStoreClient,
            customerAccountRestClient(restClientBuilder, authProperties),
            Clock.systemUTC()
        );
    }

    ShopifyCustomerAccountOAuthService(ShopifyBridgeProperties bridgeProperties,
                                       ShopifyMcpExternalAuthProperties authProperties,
                                       ShopifyCustomerAccountSecurityService securityService,
                                       ShopifyCustomerAccountSessionRepository repository,
                                       ShopifyCustomerAccountAuthClaimRepository authClaimRepository,
                                       RestClient.Builder restClientBuilder,
                                       Clock clock) {
        this(
            bridgeProperties,
            authProperties,
            securityService,
            repository,
            authClaimRepository,
            null,
            restClientBuilder.build(),
            clock
        );
    }

    ShopifyCustomerAccountOAuthService(ShopifyBridgeProperties bridgeProperties,
                                       ShopifyMcpExternalAuthProperties authProperties,
                                       ShopifyCustomerAccountSecurityService securityService,
                                       ShopifyCustomerAccountSessionRepository repository,
                                       ShopifyCustomerAccountAuthClaimRepository authClaimRepository,
                                       PlatformShopifyStoreClient platformStoreClient,
                                       RestClient.Builder restClientBuilder,
                                       Clock clock) {
        this(
            bridgeProperties,
            authProperties,
            securityService,
            repository,
            authClaimRepository,
            platformStoreClient,
            restClientBuilder.build(),
            clock
        );
    }

    private ShopifyCustomerAccountOAuthService(ShopifyBridgeProperties bridgeProperties,
                                               ShopifyMcpExternalAuthProperties authProperties,
                                               ShopifyCustomerAccountSecurityService securityService,
                                               ShopifyCustomerAccountSessionRepository repository,
                                               ShopifyCustomerAccountAuthClaimRepository authClaimRepository,
                                               PlatformShopifyStoreClient platformStoreClient,
                                               RestClient restClient,
                                               Clock clock) {
        this.bridgeProperties = bridgeProperties;
        this.authProperties = authProperties;
        this.securityService = securityService;
        this.repository = repository;
        this.authClaimRepository = authClaimRepository;
        this.platformStoreClient = platformStoreClient;
        this.restClient = restClient;
        this.clock = clock;
    }

    public URI beginAuthorization(String shopDomain,
                                  String shopperSessionId,
                                  String returnTo) {
        requireCustomerAuthConfigured();
        String normalizedShop = normalizeShopDomain(shopDomain);
        String normalizedSession = normalizeShopperSessionId(shopperSessionId);
        String customerAccountDomain = customerAccountDomainForShop(normalizedShop);
        JsonNode discovery = discoverCustomerAccounts(customerAccountDomain);
        String authorizationEndpoint = requiredDiscoveryField(discovery, "authorization_endpoint");
        String tokenEndpoint = requiredDiscoveryField(discovery, "token_endpoint");
        String verifier = securityService.randomVerifier();
        Instant now = clock.instant();
        AuthStateClaims state = new AuthStateClaims(
            normalizedShop,
            normalizedSession,
            safeReturnTo(normalizedShop, customerAccountDomain, returnTo),
            tokenEndpoint,
            verifier,
            securityService.randomNonce(),
            now.getEpochSecond(),
            now.plus(authProperties.customerAccountMcpStateTtl()).getEpochSecond()
        );
        String encryptedState = securityService.encryptJson(state);
        return UriComponentsBuilder.fromUriString(authorizationEndpoint)
            .queryParam("client_id", authProperties.customerAccountMcpClientId())
            .queryParam("redirect_uri", authProperties.customerAccountMcpRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", String.join(" ", authProperties.customerAccountMcpScopes()))
            .queryParam("state", encryptedState)
            .queryParam("nonce", state.nonce())
            .queryParam("code_challenge", securityService.codeChallenge(verifier))
            .queryParam("code_challenge_method", "S256")
            .build()
            .toUri();
    }

    @Transactional
    public URI completeAuthorization(String code, String state) {
        requireCustomerAuthConfigured();
        String normalizedCode = requiredText(code, "Missing Shopify Customer Account authorization code.");
        AuthStateClaims claims = decodeState(state);
        Instant now = clock.instant();
        if (claims.expiresAtEpochSecond() <= now.getEpochSecond() || claims.issuedAtEpochSecond() > now.plusSeconds(60).getEpochSecond()) {
            throw new ResponseStatusException(CONFLICT, "Expired Shopify Customer Account auth state.");
        }
        TokenResponse tokenResponse = exchangeAuthorizationCode(claims, normalizedCode);
        persistSession(claims, tokenResponse, now);
        String claimId = createAuthClaim(claims, now);
        return callbackReturnTo(claims.returnTo(), true, claimId);
    }

    public URI failedAuthorizationReturn(String state) {
        AuthStateClaims claims = decodeState(state);
        return callbackReturnTo(claims.returnTo(), false, null);
    }

    @Transactional(readOnly = true)
    public ShopifyCustomerAccountAuthStatus status(String shopDomain, String shopperSessionId) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        String normalizedSession = normalizeShopperSessionId(shopperSessionId);
        Optional<ShopifyCustomerAccountSessionEntity> session = findSession(normalizedShop, normalizedSession);
        Instant now = clock.instant();
        boolean authenticated = session
            .filter(entity -> entity.getSessionExpiresAt() != null && entity.getSessionExpiresAt().isAfter(now))
            .filter(entity -> entity.getAccessTokenCiphertext() != null && !entity.getAccessTokenCiphertext().isBlank())
            .isPresent();
        return new ShopifyCustomerAccountAuthStatus(
            authProperties.customerAccountConfigured(),
            authenticated,
            normalizedShop,
            securityService.shortRef(normalizedSession),
            session.map(ShopifyCustomerAccountSessionEntity::getScopesText).orElse(null),
            session.map(ShopifyCustomerAccountSessionEntity::getAccessTokenExpiresAt).orElse(null),
            session.map(ShopifyCustomerAccountSessionEntity::getSessionExpiresAt).orElse(null),
            localStartUrl(normalizedShop, normalizedSession, null),
            authenticated
                ? "Customer Account MCP session is bound to this shopper session."
                : "Customer Account MCP session is not bound for this shopper session."
        );
    }

    @Transactional
    public void revoke(String shopDomain, String shopperSessionId) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        String normalizedSession = normalizeShopperSessionId(shopperSessionId);
        findSession(normalizedShop, normalizedSession).ifPresent(entity -> {
            Instant now = clock.instant();
            entity.setRevokedAt(now);
            entity.setUpdatedAt(now);
            repository.save(entity);
        });
    }

    @Transactional
    public ShopifyCustomerAccountAuthStatus claimBrowserSession(String shopDomain,
                                                               String shopperSessionId,
                                                               String claimId) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        String normalizedSession = normalizeShopperSessionId(shopperSessionId);
        String normalizedClaim = normalizeAuthClaimId(claimId);
        Instant now = clock.instant();
        ShopifyCustomerAccountAuthClaimEntity claim = authClaimRepository.findById(normalizedClaim)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Shopify Customer Account auth claim is not available."));
        if (!normalizedShop.equalsIgnoreCase(claim.getShopDomain())
            || claim.getConsumedAt() != null
            || claim.getExpiresAt() == null
            || !claim.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(CONFLICT, "Shopify Customer Account auth claim is expired or already used.");
        }

        ShopifyCustomerAccountSessionEntity source = repository
            .findByShopDomainIgnoreCaseAndShopperSessionIdHashAndRevokedAtIsNull(
                normalizedShop,
                claim.getSourceShopperSessionIdHash()
            )
            .filter(entity -> entity.getSessionExpiresAt() != null && entity.getSessionExpiresAt().isAfter(now))
            .filter(entity -> StringUtils.hasText(entity.getAccessTokenCiphertext()))
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Shopify Customer Account auth session is not available."));

        String targetHash = sessionHash(normalizedShop, normalizedSession);
        if (!targetHash.equals(claim.getSourceShopperSessionIdHash())) {
            ShopifyCustomerAccountSessionEntity target = repository
                .findByShopDomainIgnoreCaseAndShopperSessionIdHash(normalizedShop, targetHash)
                .orElseGet(ShopifyCustomerAccountSessionEntity::new);
            if (target.getId() == null) {
                target.setId("scas-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                target.setCreatedAt(now);
            }
            copySessionBinding(source, target, normalizedShop, targetHash, now);
            repository.save(target);
        }

        claim.setConsumedAt(now);
        authClaimRepository.save(claim);
        return status(normalizedShop, normalizedSession);
    }

    @Transactional
    public Optional<String> resolveAccessToken(String shopDomain, String shopperSessionId) {
        String normalizedShop = normalizeShopDomain(shopDomain);
        String normalizedSession = normalizeShopperSessionId(shopperSessionId);
        Optional<ShopifyCustomerAccountSessionEntity> session = findSession(normalizedShop, normalizedSession);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        ShopifyCustomerAccountSessionEntity entity = session.get();
        Instant now = clock.instant();
        if (entity.getSessionExpiresAt() == null || !entity.getSessionExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (entity.getAccessTokenExpiresAt() == null || entity.getAccessTokenExpiresAt().isAfter(now.plus(TOKEN_REFRESH_SKEW))) {
            return Optional.ofNullable(securityService.decryptText(entity.getAccessTokenCiphertext()));
        }
        return refreshAccessToken(entity, now);
    }

    public String normalizeShopDomain(String shopDomain) {
        String normalized = shopDomain == null ? "" : shopDomain.trim().toLowerCase(Locale.ROOT);
        if (!SHOP_DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid Shopify shop domain.");
        }
        return normalized;
    }

    public String normalizeShopperSessionId(String shopperSessionId) {
        String normalized = shopperSessionId == null ? "" : shopperSessionId.trim();
        if (!SAFE_SESSION_ID.matcher(normalized).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing or invalid shopper session identifier.");
        }
        return normalized;
    }

    private Optional<String> refreshAccessToken(ShopifyCustomerAccountSessionEntity entity, Instant now) {
        String refreshToken = securityService.decryptText(entity.getRefreshTokenCiphertext());
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }
        try {
            String tokenEndpoint = StringUtils.hasText(entity.getTokenEndpoint())
                ? entity.getTokenEndpoint().trim()
                : requiredDiscoveryField(discoverCustomerAccounts(customerAccountDomainForShop(entity.getShopDomain())), "token_endpoint");
            TokenResponse response = exchangeRefreshToken(tokenEndpoint, refreshToken);
            applyTokenResponse(entity, response, now);
            repository.save(entity);
            return Optional.ofNullable(securityService.decryptText(entity.getAccessTokenCiphertext()));
        } catch (RuntimeException ex) {
            entity.setRevokedAt(now);
            entity.setUpdatedAt(now);
            repository.save(entity);
            return Optional.empty();
        }
    }

    private void persistSession(AuthStateClaims claims, TokenResponse response, Instant now) {
        String sessionHash = sessionHash(claims.shopDomain(), claims.shopperSessionId());
        ShopifyCustomerAccountSessionEntity entity = repository
            .findByShopDomainIgnoreCaseAndShopperSessionIdHash(claims.shopDomain(), sessionHash)
            .orElseGet(ShopifyCustomerAccountSessionEntity::new);
        if (entity.getId() == null) {
            entity.setId("scas-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            entity.setCreatedAt(now);
        }
        entity.setShopDomain(claims.shopDomain());
        entity.setShopperSessionIdHash(sessionHash);
        entity.setTokenEndpoint(claims.tokenEndpoint());
        entity.setSessionExpiresAt(now.plus(authProperties.customerAccountMcpSessionTtl()));
        entity.setRevokedAt(null);
        applyTokenResponse(entity, response, now);
        repository.save(entity);
    }

    private String createAuthClaim(AuthStateClaims claims, Instant now) {
        ShopifyCustomerAccountAuthClaimEntity claim = new ShopifyCustomerAccountAuthClaimEntity();
        claim.setId("scac-" + UUID.randomUUID().toString().replace("-", ""));
        claim.setShopDomain(claims.shopDomain());
        claim.setSourceShopperSessionIdHash(sessionHash(claims.shopDomain(), claims.shopperSessionId()));
        claim.setCreatedAt(now);
        claim.setExpiresAt(now.plus(AUTH_CLAIM_TTL));
        authClaimRepository.save(claim);
        return claim.getId();
    }

    private void copySessionBinding(ShopifyCustomerAccountSessionEntity source,
                                    ShopifyCustomerAccountSessionEntity target,
                                    String shopDomain,
                                    String shopperSessionIdHash,
                                    Instant now) {
        target.setShopDomain(shopDomain);
        target.setShopperSessionIdHash(shopperSessionIdHash);
        target.setTokenEndpoint(source.getTokenEndpoint());
        target.setAccessTokenCiphertext(source.getAccessTokenCiphertext());
        target.setRefreshTokenCiphertext(source.getRefreshTokenCiphertext());
        target.setIdTokenCiphertext(source.getIdTokenCiphertext());
        target.setTokenType(source.getTokenType());
        target.setScopesText(source.getScopesText());
        target.setAccessTokenExpiresAt(source.getAccessTokenExpiresAt());
        target.setRefreshTokenExpiresAt(source.getRefreshTokenExpiresAt());
        target.setSessionExpiresAt(source.getSessionExpiresAt());
        target.setRevokedAt(null);
        target.setUpdatedAt(now);
    }

    private void applyTokenResponse(ShopifyCustomerAccountSessionEntity entity, TokenResponse response, Instant now) {
        entity.setAccessTokenCiphertext(securityService.encryptText(response.accessToken()));
        if (StringUtils.hasText(response.refreshToken())) {
            entity.setRefreshTokenCiphertext(securityService.encryptText(response.refreshToken()));
        }
        if (StringUtils.hasText(response.idToken())) {
            entity.setIdTokenCiphertext(securityService.encryptText(response.idToken()));
        }
        entity.setTokenType(blankToNull(response.tokenType()));
        entity.setScopesText(blankToNull(response.scope()));
        entity.setAccessTokenExpiresAt(response.expiresInSeconds() == null
            ? now.plus(Duration.ofHours(1))
            : now.plusSeconds(Math.max(1, response.expiresInSeconds())));
        if (response.refreshTokenExpiresInSeconds() != null) {
            entity.setRefreshTokenExpiresAt(now.plusSeconds(Math.max(1, response.refreshTokenExpiresInSeconds())));
        }
        entity.setUpdatedAt(now);
    }

    private Optional<ShopifyCustomerAccountSessionEntity> findSession(String shopDomain, String shopperSessionId) {
        return repository.findByShopDomainIgnoreCaseAndShopperSessionIdHashAndRevokedAtIsNull(
            shopDomain,
            sessionHash(shopDomain, shopperSessionId)
        );
    }

    private String sessionHash(String shopDomain, String shopperSessionId) {
        return securityService.hmacHex(shopDomain + "|" + shopperSessionId);
    }

    private TokenResponse exchangeAuthorizationCode(AuthStateClaims claims, String code) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", authProperties.customerAccountMcpClientId());
        form.add("redirect_uri", authProperties.customerAccountMcpRedirectUri());
        form.add("code", code);
        form.add("code_verifier", claims.codeVerifier());
        return tokenRequest(claims.tokenEndpoint(), form);
    }

    private TokenResponse exchangeRefreshToken(String tokenEndpoint, String refreshToken) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", authProperties.customerAccountMcpClientId());
        form.add("refresh_token", refreshToken);
        return tokenRequest(tokenEndpoint, form);
    }

    private TokenResponse tokenRequest(String tokenEndpoint, LinkedMultiValueMap<String, String> form) {
        JsonNode response = restClient.post()
            .uri(tokenEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, basicClientAuthorization())
            .body(form)
            .retrieve()
            .body(JsonNode.class);
        if (response == null || !response.isObject()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Customer Account token endpoint returned an empty response.");
        }
        String accessToken = text(response, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Customer Account token endpoint response is missing access_token.");
        }
        return new TokenResponse(
            accessToken,
            text(response, "refresh_token"),
            text(response, "id_token"),
            text(response, "token_type"),
            text(response, "scope"),
            longValue(response, "expires_in"),
            longValue(response, "refresh_token_expires_in")
        );
    }

    private String basicClientAuthorization() {
        String raw = authProperties.customerAccountMcpClientId() + ":" + authProperties.customerAccountMcpClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode discoverCustomerAccounts(String shopDomain) {
        JsonNode response = restClient.get()
            .uri("https://" + shopDomain + "/.well-known/openid-configuration")
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(JsonNode.class);
        if (response == null || !response.isObject()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Customer Account discovery returned an empty response.");
        }
        return response;
    }

    private String requiredDiscoveryField(JsonNode discovery, String field) {
        String value = text(discovery, field);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Customer Account discovery is missing " + field + ".");
        }
        return value;
    }

    private AuthStateClaims decodeState(String state) {
        String normalized = requiredText(state, "Missing Shopify Customer Account auth state.");
        AuthStateClaims claims = securityService.decryptJson(normalized, AuthStateClaims.class);
        normalizeShopDomain(claims.shopDomain());
        normalizeShopperSessionId(claims.shopperSessionId());
        if (!StringUtils.hasText(claims.tokenEndpoint()) || !StringUtils.hasText(claims.codeVerifier())) {
            throw new ResponseStatusException(CONFLICT, "Malformed Shopify Customer Account auth state.");
        }
        return claims;
    }

    private URI callbackReturnTo(String returnTo, boolean success, String claimId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(URI.create(returnTo))
            .replaceQueryParam("loomCustomerAuth", success ? "connected" : "failed")
            .replaceQueryParam("loomCustomerAuthClaim");
        if (success && StringUtils.hasText(claimId)) {
            builder.queryParam("loomCustomerAuthClaim", claimId);
        }
        return builder.build(true).toUri();
    }

    private String safeReturnTo(String shopDomain, String customerAccountDomain, String returnTo) {
        String fallback = "https://" + customerAccountDomain + "/account";
        if (!StringUtils.hasText(returnTo)) {
            return fallback;
        }
        try {
            URI candidate = URI.create(returnTo.trim());
            if (!"https".equalsIgnoreCase(candidate.getScheme())) {
                return fallback;
            }
            String host = candidate.getHost();
            if (host == null
                || (!host.equalsIgnoreCase(customerAccountDomain) && !host.equalsIgnoreCase(shopDomain))) {
                return fallback;
            }
            return candidate.toString();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private String localStartUrl(String shopDomain, String shopperSessionId, String returnTo) {
        if (!StringUtils.hasText(bridgeProperties.publicBaseUrl())) {
            return "/api/customer-auth/start?shop=" + shopDomain;
        }
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromHttpUrl(normalizedPublicBaseUrl() + "/api/customer-auth/start")
            .queryParam("shop", shopDomain)
            .queryParam("shopperSessionId", shopperSessionId);
        if (StringUtils.hasText(returnTo)) {
            builder.queryParam("returnTo", returnTo.trim());
        }
        return builder.build().toUriString();
    }

    private String normalizedPublicBaseUrl() {
        if (bridgeProperties.publicBaseUrl().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge public base URL is not configured.");
        }
        return bridgeProperties.publicBaseUrl().endsWith("/")
            ? bridgeProperties.publicBaseUrl().substring(0, bridgeProperties.publicBaseUrl().length() - 1)
            : bridgeProperties.publicBaseUrl();
    }

    private void requireCustomerAuthConfigured() {
        if (!authProperties.customerAccountConfigured()) {
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Shopify Customer Account MCP OAuth/PKCE and protected customer data posture are not configured."
            );
        }
    }

    private String customerAccountDomainForShop(String shopDomain) {
        Optional<String> perStoreDomain = perStoreCustomerAccountDomain(shopDomain);
        if (perStoreDomain.isPresent()) {
            return perStoreDomain.get();
        }
        return globalCustomerAccountDomain().orElse(shopDomain);
    }

    private Optional<String> perStoreCustomerAccountDomain(String shopDomain) {
        if (platformStoreClient == null) {
            return Optional.empty();
        }
        ShopifyBridgeCustomerAccountConfigSummary config;
        try {
            config = platformStoreClient.getCustomerAccountConfig(shopDomain);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                log.debug("No per-store Customer Account MCP domain returned by Platform for {}: HTTP {}",
                    shopDomain, ex.getStatusCode().value());
            } else {
                log.warn("Platform Customer Account MCP domain lookup failed for {} with HTTP {}.",
                    shopDomain, ex.getStatusCode().value());
            }
            return Optional.empty();
        } catch (ResponseStatusException ex) {
            log.warn("Platform Customer Account MCP domain lookup is not available for {}: {}", shopDomain, ex.getReason());
            return Optional.empty();
        } catch (ResourceAccessException ex) {
            log.warn("Platform Customer Account MCP domain lookup transport failed for {}: {}", shopDomain, ex.getMessage());
            return Optional.empty();
        }
        if (config == null || !StringUtils.hasText(config.storefrontDomain())) {
            return Optional.empty();
        }
        return Optional.of(normalizeCustomerAccountDomain(
            config.storefrontDomain(),
            "Invalid per-store Shopify Customer Account storefront domain configuration."
        ));
    }

    private Optional<String> globalCustomerAccountDomain() {
        String configured = authProperties.customerAccountMcpStorefrontDomain();
        if (!StringUtils.hasText(configured)) {
            return Optional.empty();
        }
        return Optional.of(normalizeCustomerAccountDomain(
            configured,
            "Invalid Shopify Customer Account storefront domain configuration."
        ));
    }

    private String normalizeCustomerAccountDomain(String configured, String errorMessage) {
        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        if (!CUSTOMER_ACCOUNT_DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, errorMessage);
        }
        return normalized;
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(CONFLICT, message);
        }
        return value.trim();
    }

    private String normalizeAuthClaimId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!SAFE_AUTH_CLAIM_ID.matcher(normalized).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing or invalid Shopify Customer Account auth claim.");
        }
        return normalized;
    }

    private String text(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value != null && value.canConvertToLong() ? value.asLong() : null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static RestClient customerAccountRestClient(RestClient.Builder restClientBuilder,
                                                       ShopifyMcpExternalAuthProperties authProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(authProperties.customerAccountMcpConnectTimeout());
        requestFactory.setReadTimeout(authProperties.customerAccountMcpReadTimeout());
        return restClientBuilder
            .requestFactory(requestFactory)
            .build();
    }

    record AuthStateClaims(
        String shopDomain,
        String shopperSessionId,
        String returnTo,
        String tokenEndpoint,
        String codeVerifier,
        String nonce,
        long issuedAtEpochSecond,
        long expiresAtEpochSecond
    ) {
    }

    private record TokenResponse(
        String accessToken,
        String refreshToken,
        String idToken,
        String tokenType,
        String scope,
        Long expiresInSeconds,
        Long refreshTokenExpiresInSeconds
    ) {
    }
}
