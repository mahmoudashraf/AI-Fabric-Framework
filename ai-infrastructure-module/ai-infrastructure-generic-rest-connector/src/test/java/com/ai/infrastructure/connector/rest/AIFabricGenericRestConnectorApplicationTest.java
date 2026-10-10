package com.ai.infrastructure.connector.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AIFabricGenericRestConnectorApplicationTest {

    @Test
    void startsWithoutAiProviderConfiguration() {
    }

    @Test
    void keepsDatabaseInfrastructureBehindConnectorPersistenceOptIn() {
        SpringBootApplication annotation = AIFabricGenericRestConnectorApplication.class
            .getAnnotation(SpringBootApplication.class);

        assertThat(annotation.exclude()).containsExactlyInAnyOrder(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
        );
    }
}
