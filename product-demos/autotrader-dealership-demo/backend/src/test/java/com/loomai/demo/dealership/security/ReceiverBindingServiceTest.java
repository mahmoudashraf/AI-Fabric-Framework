package com.loomai.demo.dealership.security;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.provider.ProviderStockClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiverBindingServiceTest {
    private static final String KEY = "synthetic-dedicated-receiver-key-123456";
    private DealershipDemoProperties properties;
    private ProviderStockClient stock;
    private ReceiverBindingService bindings;

    @BeforeEach
    void configure() {
        properties = new DealershipDemoProperties();
        properties.getInternal().setApiKey("old-global-key");
        properties.getRuntime().getPrivateAccess().setDeploymentId("old-deployment");
        var receiver = properties.getReceiver();
        receiver.setEnabled(true); receiver.setBindingId("fixture-1"); receiver.setCustomerId("customer");
        receiver.setTenantId("tenant"); receiver.setDeploymentId("deployment"); receiver.setAdvertiserId("advertiser");
        receiver.setApiKey(KEY);
        stock = mock(ProviderStockClient.class);
        when(stock.configuredAdvertiserId()).thenReturn("advertiser");
        bindings = new ReceiverBindingService(properties, stock);
    }

    @Test
    void aFreshApplicationHasNoReceiverOrRetentionCompatibilityFallback() {
        var defaults = new DealershipDemoProperties();
        assertThat(defaults.getReceiver().isEnabled()).isFalse();
        assertThat(defaults.getPrivacy().isLeadRetentionEnabled()).isFalse();
        assertThatThrownBy(() -> new ReceiverBindingService(defaults, stock).requireActive())
            .isInstanceOfSatisfying(ResponseStatusException.class, denied -> assertThat(denied.getStatusCode().value()).isEqualTo(503));
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "binding", "customer", "tenant", "deployment", "advertiser", "key", "short-key",
        "global-key", "runtime-key", "signing-key", "account-mismatch", "control-character"})
    void incompleteOrNonDedicatedBindingsFailClosedWithoutDisclosingConfiguration(String change) {
        var receiver = properties.getReceiver();
        switch (change) {
            case "disabled" -> receiver.setEnabled(false);
            case "binding" -> receiver.setBindingId(null);
            case "customer" -> receiver.setCustomerId(null);
            case "tenant" -> receiver.setTenantId(null);
            case "deployment" -> receiver.setDeploymentId(null);
            case "advertiser" -> receiver.setAdvertiserId(null);
            case "key" -> receiver.setApiKey(null);
            case "short-key" -> receiver.setApiKey("short");
            case "global-key" -> properties.getInternal().setApiKey(KEY);
            case "runtime-key" -> properties.getRuntime().getPrivateAccess().setTrustedApiKey(KEY);
            case "signing-key" -> properties.getRuntime().getPrivateAccess().setSigningKey(KEY);
            case "account-mismatch" -> when(stock.configuredAdvertiserId()).thenReturn("other-advertiser");
            case "control-character" -> receiver.setDeploymentId("deployment\n");
            default -> throw new AssertionError();
        }
        assertThatThrownBy(bindings::requireActive).isInstanceOfSatisfying(ResponseStatusException.class, denied -> {
            assertThat(denied.getStatusCode().value()).isEqualTo(503);
            assertThat(denied.getReason()).isEqualTo("The receiver binding is unavailable.");
            assertThat(denied.getMessage()).doesNotContain(KEY, "old-global-key", "other-advertiser");
        });
        verify(stock, never()).requireAvailableStock(anyString());
    }

    @Test
    void dedicatedAuthenticationNeverChangesOrUsesTheOldRuntimeAuthority() {
        bindings.authenticate(KEY);
        assertThatThrownBy(() -> bindings.authenticate("old-global-key"))
            .isInstanceOfSatisfying(ResponseStatusException.class, denied -> assertThat(denied.getStatusCode().value()).isEqualTo(401));
        assertThat(properties.getInternal().getApiKey()).isEqualTo("old-global-key");
        assertThat(properties.getRuntime().getPrivateAccess().getDeploymentId()).isEqualTo("old-deployment");
    }

    @ParameterizedTest
    @ValueSource(strings = {"binding", "customer", "tenant", "deployment", "advertiser"})
    void everyScopeComponentSeparatesTheIdempotencyNamespace(String field) {
        var before = bindings.requireActive();
        var receiver = properties.getReceiver();
        switch (field) {
            case "binding" -> receiver.setBindingId("fixture-2");
            case "customer" -> receiver.setCustomerId("customer-2");
            case "tenant" -> receiver.setTenantId("tenant-2");
            case "deployment" -> receiver.setDeploymentId("deployment-2");
            case "advertiser" -> {
                receiver.setAdvertiserId("advertiser-2");
                when(stock.configuredAdvertiserId()).thenReturn("advertiser-2");
            }
            default -> throw new AssertionError();
        }
        var after = bindings.requireActive();
        assertThat(after.fingerprint()).hasSize(64).isNotEqualTo(before.fingerprint());
        assertThat(after.storageKey("request")).isNotEqualTo(before.storageKey("request"));
        assertThat(after.storageKey("request")).isNotEqualTo(after.storageKey("other-request"));
    }

    @Test
    void credentialRotationDoesNotChangeReceiptOwnershipOrPermitTheRetiredCredential() {
        var before = bindings.requireActive();
        properties.getReceiver().setApiKey("synthetic-replacement-receiver-key-123456");
        bindings.authenticate(properties.getReceiver().getApiKey());
        assertThat(bindings.requireActive()).isEqualTo(before);
        assertThatThrownBy(() -> bindings.authenticate(KEY)).isInstanceOf(ResponseStatusException.class);
    }
}
