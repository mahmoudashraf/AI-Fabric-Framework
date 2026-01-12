package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.http.AIHttpClientFactory;
import com.ai.infrastructure.http.AIHttpClientProperties;
import com.ai.infrastructure.http.DefaultAIHttpClientFactory;
import com.ai.infrastructure.http.HttpClient;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        AIInfrastructureAutoConfiguration.class
    })
    @EnableConfigurationProperties({AIProviderConfig.class, AIHttpClientProperties.class})
    @ComponentScan(basePackageClasses = {AIProviderManager.class})
    @Import(ConnectivityHttpClientConfiguration.class)
    static class ConnectivityApp {
    }

    @Configuration(proxyBeanMethods = false)
    static class ConnectivityHttpClientConfiguration {

        @Bean
        AIHttpClientFactory aiHttpClientFactory(RestTemplateBuilder restTemplateBuilder,
                                                AIHttpClientProperties properties) {
            return new DefaultAIHttpClientFactory(restTemplateBuilder, properties);
        }

        @Bean
        HttpClient httpClient(AIHttpClientFactory factory) {
            return factory.create();
        }
    }
}
