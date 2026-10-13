package com.loomai.inbox.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BasicInboxJsonConfiguration {

    @Bean
    ObjectMapper basicInboxObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
