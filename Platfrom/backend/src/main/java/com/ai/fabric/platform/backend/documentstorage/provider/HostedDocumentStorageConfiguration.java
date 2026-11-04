package com.ai.fabric.platform.backend.documentstorage.provider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HostedDocumentStorageProperties.class)
public class HostedDocumentStorageConfiguration { }
