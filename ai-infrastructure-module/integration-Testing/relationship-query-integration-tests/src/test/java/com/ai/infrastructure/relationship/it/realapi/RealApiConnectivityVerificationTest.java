package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.ProviderConfiguration;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.testing.RealApiConnectivityVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Explicit Real API connectivity check.
 *
 * <p>This test is intentionally disabled by default and is meant to be invoked
 * explicitly from CI (or locally) before running long realapi suites.</p>
 */
@EnabledIfSystemProperty(named = "ai.realapi.connectivity.check", matches = "true")
@SpringBootTest(
    classes = RealApiConnectivityVerificationTest.ConnectivityApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("realapi")
class RealApiConnectivityVerificationTest {

    @Autowired
    private AIProviderManager providerManager;

    @Autowired
    private AIProviderConfig providerConfig;

    @Test
    void shouldReachConfiguredLlmProvider() {
        RealApiConnectivityVerifier.verifyLlmOrThrow(providerManager, providerConfig);
    }

    /**
     * Nested configuration to avoid being discovered by component scanning in the main IT app.
     *
     * <p>We exclude JPA auto-config here to keep the connectivity check focused purely on provider reachability.
     * Importantly, since this is nested, it cannot accidentally disable JPA for the real relationship-query tests.</p>
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
    @EnableConfigurationProperties(AIProviderConfig.class)
    @ComponentScan(basePackageClasses = {AIProviderManager.class})
    @Import(ProviderConfiguration.class)
    static class ConnectivityApp {
    }
}

