package com.ai.infrastructure.connector.rest;

import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.ai.infrastructure.connector.rest.config.RestConnectorAdminAuthProperties;
import com.ai.infrastructure.connector.rest.config.RestConnectorRuntimeProxyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    RestConnectorAdminAuthProperties.class,
    RestConnectorServiceProperties.class,
    RestConnectorRuntimeProxyProperties.class
})
public class AIFabricGenericRestConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIFabricGenericRestConnectorApplication.class, args);
    }
}
