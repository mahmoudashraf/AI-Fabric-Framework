package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceConfiguration;
import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSelfServicePropertiesBindingTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(CustomerSelfServiceConfiguration.class)
        .withPropertyValues(
            "platform.customer-self-service.trusted-servers.CUSTOMER_STAGING.uuid=staging-server",
            "platform.customer-self-service.trusted-servers.CUSTOMER_STAGING.address=192.0.2.10",
            "platform.customer-self-service.trusted-servers.CUSTOMER_PRODUCTION.uuid=production-server",
            "platform.customer-self-service.trusted-servers.CUSTOMER_PRODUCTION.address=192.0.2.20",
            "platform.customer-self-service.evidence-max-age-seconds=900"
        );

    @Test
    void bindsBothTrustedPlacementAuthorities() {
        context.run(application -> {
            CustomerSelfServiceProperties properties = application.getBean(CustomerSelfServiceProperties.class);

            assertThat(properties.trustedServers().get(TargetIntent.CUSTOMER_STAGING))
                .isEqualTo(new CustomerSelfServiceProperties.TrustedServer("staging-server", "192.0.2.10"));
            assertThat(properties.trustedServers().get(TargetIntent.CUSTOMER_PRODUCTION))
                .isEqualTo(new CustomerSelfServiceProperties.TrustedServer("production-server", "192.0.2.20"));
            assertThat(properties.evidenceMaxAgeSeconds()).isEqualTo(900);
        });
    }
}
