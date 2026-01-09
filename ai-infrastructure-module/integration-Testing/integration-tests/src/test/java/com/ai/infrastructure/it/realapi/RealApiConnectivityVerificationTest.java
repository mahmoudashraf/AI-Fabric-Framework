package com.ai.infrastructure.it.realapi;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.testing.RealApiConnectivityVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Explicit Real API connectivity check.
 *
 * <p>This test is intentionally disabled by default and is meant to be invoked
 * explicitly from CI (or locally) before running long real-api suites.</p>
 */
@EnabledIfSystemProperty(named = "ai.realapi.connectivity.check", matches = "true")
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
class RealApiConnectivityVerificationTest {

    @Autowired
    private AIProviderManager providerManager;

    @Autowired
    private AIProviderConfig providerConfig;

    @Test
    void shouldReachConfiguredLlmProvider() {
        RealApiConnectivityVerifier.verifyLlmOrThrow(providerManager, providerConfig);
    }
}

