package com.ai.fabric.runtime.boot;

import com.ai.fabric.runtime.AIFabricRuntimeApplication;
import com.ai.fabric.runtime.authoring.boot.ConfigurationAuthoringBootConfiguration;
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
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** The existing customer-serving application, unchanged unless the dedicated profile is selected. */
@Configuration(proxyBeanMethods = false)
@Profile("!configuration-authoring")
@EnableAutoConfiguration
@AutoConfigurationPackage(basePackageClasses = AIFabricRuntimeApplication.class)
@EnableScheduling
@EnableConfigurationProperties({RuntimeDeploymentPromptConfigProperties.class,
    RuntimeDeploymentKnowledgeSourceConfigProperties.class, RuntimeDeploymentShellConfigProperties.class,
    DocumentKnowledgeProperties.class})
@ComponentScan(basePackageClasses = AIFabricRuntimeApplication.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.CUSTOM, classes = {TypeExcludeFilter.class, AutoConfigurationExcludeFilter.class}),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        AIFabricRuntimeApplication.class, ServingRuntimeConfiguration.class, ConfigurationAuthoringBootConfiguration.class})
})
public class ServingRuntimeConfiguration { }
