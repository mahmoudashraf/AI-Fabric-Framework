package com.ai.fabric.runtime.config;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RuntimeAuthProperties.class)
public class RuntimeAuthConfiguration {

    @Bean
    RuntimeRequestAuthResolver runtimeRequestAuthResolver(RuntimeAuthProperties properties) {
        return new RuntimeRequestAuthResolver(properties);
    }
}
