package com.ai.infrastructure.connector.rest;

import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.ai.infrastructure.connector.rest.config.RestConnectorAdminAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@EnableScheduling
@EnableConfigurationProperties({
    RestConnectorAdminAuthProperties.class,
    RestConnectorServiceProperties.class
})
public class AIFabricGenericRestConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIFabricGenericRestConnectorApplication.class, args);
    }
}
