package com.ai.infrastructure.connector.rest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rest-connector")
public class RestConnectorServiceProperties {

    /**
     * Location of the routing YAML file. Supports {@code classpath:} and {@code file:}.
     */
    private String routingConfigLocation = "classpath:actions-routing.yml";
}

