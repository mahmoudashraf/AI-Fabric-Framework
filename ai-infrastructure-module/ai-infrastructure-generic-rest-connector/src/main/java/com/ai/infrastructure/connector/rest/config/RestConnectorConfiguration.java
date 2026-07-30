package com.ai.infrastructure.connector.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RestConnectorConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestRoutingConfig restRoutingConfig(RestRoutingConfigLoader loader, RestConnectorServiceProperties properties) {
        String location = properties != null ? properties.getRoutingConfigLocation() : null;
        return loader.load(location);
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
