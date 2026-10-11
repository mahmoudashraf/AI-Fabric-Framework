package com.ai.infrastructure.connector.rest;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.flyway.enabled=false"
)
@Import(AIFabricGenericRestConnectorApplicationTest.TestDataSourceConfiguration.class)
class AIFabricGenericRestConnectorApplicationTest {

    private final ApplicationContext applicationContext;

    @Autowired
    AIFabricGenericRestConnectorApplicationTest(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Test
    void startsWithoutAiProviderConfiguration() {
        assertThat(applicationContext.containsBean("entityManagerFactory")).isFalse();
    }

    @Test
    void keepsDatabaseInfrastructureBehindConnectorPersistenceOptIn() {
        SpringBootApplication annotation = AIFabricGenericRestConnectorApplication.class
            .getAnnotation(SpringBootApplication.class);

        assertThat(annotation.exclude()).containsExactlyInAnyOrder(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDataSourceConfiguration {

        @Bean(destroyMethod = "close")
        HikariDataSource connectorTestDataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:mem:connector-startup;DB_CLOSE_DELAY=-1");
            config.setUsername("sa");
            config.setPassword("");
            return new HikariDataSource(config);
        }
    }
}
