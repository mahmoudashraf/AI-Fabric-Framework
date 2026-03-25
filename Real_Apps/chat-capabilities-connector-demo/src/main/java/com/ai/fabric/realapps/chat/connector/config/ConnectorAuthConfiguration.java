package com.ai.fabric.realapps.chat.connector.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConnectorAuthProperties.class)
public class ConnectorAuthConfiguration {
}

