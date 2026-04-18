package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShopifyInstallFlowServiceTest {

    private MockRestServiceServer server;
    private PlatformShopifyStoreClient platformShopifyStoreClient;
    private ShopifyInstallRecordService installRecordService;
    private ShopifyInstallStateService installStateService;
    private ShopifyInstallFlowService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        installRecordService = mock(ShopifyInstallRecordService.class);
        installStateService = new ShopifyInstallStateService(properties());
        service = new ShopifyInstallFlowService(
            properties(),
            installStateService,
            platformShopifyStoreClient,
            installRecordService,
            builder
        );
    }

    @Test
    void beginInstallBuildsAuthorizeRedirect() {
        URI redirect = service.beginInstall("alpha.myshopify.com");

        assertThat(redirect.toString()).startsWith("https://alpha.myshopify.com/admin/oauth/authorize?");
        assertThat(redirect.toString()).contains("client_id=shopify-api-key");
        assertThat(redirect.toString()).contains("scope=read_products,read_content");
        assertThat(redirect.toString()).contains("redirect_uri=https://bridge.example.com/auth/shopify/callback");
        assertThat(redirect.toString()).contains("state=");
    }

    @Test
    void completeInstallExchangesPermanentTokenAndRedirectsBackToEmbeddedApp() {
        String state = installStateService.issueState("alpha.myshopify.com");
        String host = "admin.shopify.com/store/alpha";
        String rawQueryWithoutHmac = "code=temp-code&host=" + host + "&shop=alpha.myshopify.com&state=" + state;
        String hmac = hmacHex(rawQueryWithoutHmac);

        server.expect(requestTo("https://alpha.myshopify.com/admin/oauth/access_token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(containsString("code=temp-code")))
            .andRespond(withSuccess("""
                {
                  "access_token":"shopify-offline-token",
                  "scope":"read_products,read_content"
                }
                """, MediaType.APPLICATION_JSON));

        when(platformShopifyStoreClient.getStore("alpha.myshopify.com"))
            .thenThrow(HttpClientErrorException.create(NOT_FOUND, "Not Found", null, new byte[0], StandardCharsets.UTF_8));
        when(platformShopifyStoreClient.upsertStore(any())).thenReturn(storeSummary());
        when(platformShopifyStoreClient.upsertCredentials(eq("alpha.myshopify.com"), any())).thenReturn(storeSummary());

        URI redirect = service.completeInstall(
            "alpha.myshopify.com",
            "temp-code",
            host,
            state,
            rawQueryWithoutHmac + "&hmac=" + hmac
        );

        assertThat(redirect.toString()).isEqualTo("https://bridge.example.com?shop=alpha.myshopify.com&host=admin.shopify.com/store/alpha");
        verify(platformShopifyStoreClient).upsertStore(any());
        verify(platformShopifyStoreClient).upsertCredentials(eq("alpha.myshopify.com"), any());
        verify(installRecordService).recordInstall(
            eq("alpha.myshopify.com"),
            eq("https://alpha.myshopify.com"),
            eq(host),
            eq("read_products,read_content"),
            eq("MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA"),
            eq(null),
            eq(null),
            eq(null)
        );
        server.verify();
    }

    private ShopifyBridgeStoreSummary storeSummary() {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "DRAFT",
            "consumer-1",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "CONNECTED",
            true,
            true,
            true,
            true,
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                false,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                null,
                null,
                null,
                null,
                "read_products,read_content",
                false
            ),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }

    private String hmacHex(String rawQueryWithoutHmac) {
        List<String> pairs = java.util.Arrays.stream(rawQueryWithoutHmac.split("&"))
            .sorted()
            .toList();
        String message = String.join("&", pairs);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("shopify-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", current));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
            "shopify-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }
}
