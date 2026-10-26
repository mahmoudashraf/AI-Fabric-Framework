package com.loomai.demo.dealership.config;

import com.loomai.demo.dealership.security.ReceiverBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionConfigurationValidatorTest {
    private DealershipDemoProperties properties;
    private ReceiverBindingService receivers;
    private ProductionConfigurationValidator validator;

    @BeforeEach
    void configureProduction() {
        properties = new DealershipDemoProperties();
        properties.setProductionGuardsEnabled(true);
        properties.getStaff().setUsername("fixture-staff");
        properties.getStaff().setPasswordHashBase64(encoded(new BCryptPasswordEncoder(4).encode("fixture-password")));
        properties.getInternal().setApiKey("fixture-internal-key");
        properties.getPrivacy().setEncryptionKeyBase64(encoded("0123456789abcdef0123456789abcdef"));
        properties.setPublicMediaBaseUrl("https://dealership.example.test");
        receivers = mock(ReceiverBindingService.class);
        validator = new ProductionConfigurationValidator(properties, receivers);
    }

    @Test
    void validProductionConfigurationRequiresNoRetiredInboxIntegration() {
        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
        verifyNoInteractions(receivers);
    }

    @Test
    void existingStaffPrivacyAndMediaGuardsRemainRequired() {
        properties.getStaff().setUsername(null);
        properties.getInternal().setApiKey(null);
        properties.getPrivacy().setEncryptionKeyBase64(encoded("short"));
        properties.setPublicMediaBaseUrl("http://dealership.example.test");
        assertThatThrownBy(() -> validator.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_STAFF_USERNAME")
            .hasMessageContaining("APP_INTERNAL_API_KEY")
            .hasMessageContaining("APP_PII_ENCRYPTION_KEY_BASE64")
            .hasMessageContaining("DEALERSHIP_PUBLIC_MEDIA_BASE_URL");
    }

    @Test
    void enabledRuntimeAndSimulatorKeepTheirIndependentConfigurationRequirements() {
        properties.getRuntime().setEnabled(true);
        properties.getProviderSimulator().setEnabled(true);
        assertThatThrownBy(() -> validator.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LOOMAI_RUNTIME_BASE_URL")
            .hasMessageContaining("LOOMAI_RUNTIME_TRUSTED_API_KEY")
            .hasMessageContaining("LOOMAI_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")
            .hasMessageContaining("PROVIDER_SIMULATOR_BASE_URL")
            .hasMessageContaining("PROVIDER_SIMULATOR_CONTROL_API_KEY")
            .hasMessageContaining("PROVIDER_SIMULATOR_WEBHOOK_TARGET_URL");
        verifyNoInteractions(receivers);
    }

    @Test
    void enabledReceiverStillRequiresItsExistingDedicatedBinding() {
        properties.getReceiver().setEnabled(true);
        when(receivers.requireActive()).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> validator.run(null)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DEALERSHIP_RECEIVER_*");
        verify(receivers).requireActive();
    }

    private static String encoded(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
