package com.ai.fabric.runtime.boot;

import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.ai.fabric.runtime.config.RuntimeDeploymentKnowledgeSourceConfigProperties;
import com.ai.fabric.runtime.config.RuntimeDeploymentPromptConfigProperties;
import com.ai.fabric.runtime.config.RuntimeDeploymentShellConfigProperties;
import com.ai.fabric.runtime.documents.DocumentKnowledgeProperties;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/** One serving runtime for normal chat, configured assistance and managed tasks. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@AutoConfigurationPackage(basePackageClasses = AIFabricRuntimeApplication.class)
@EnableScheduling
@EnableConfigurationProperties({RuntimeDeploymentPromptConfigProperties.class,
    RuntimeDeploymentKnowledgeSourceConfigProperties.class, RuntimeDeploymentShellConfigProperties.class,
    DocumentKnowledgeProperties.class})
@ComponentScan(basePackageClasses = AIFabricRuntimeApplication.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = {TypeExcludeFilter.class, AutoConfigurationExcludeFilter.class}),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        AIFabricRuntimeApplication.class, ServingRuntimeConfiguration.class})
})
public class ServingRuntimeConfiguration { }
