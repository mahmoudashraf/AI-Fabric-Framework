package com.ai.fabric.runtime.authoring.boot;

import ai.fabric.config.AIProviderConfig;
import ai.fabric.config.PromptBundleProperties;
import ai.fabric.core.AICoreService;
import ai.fabric.llm.structured.DefaultStructuredJsonCallExecutor;
import ai.fabric.llm.structured.StructuredJsonExtractor;
import ai.fabric.prompt.ClasspathPromptTemplateStore;
import ai.fabric.prompt.PromptRenderer;
import ai.fabric.prompt.PromptTemplateResolver;
import ai.fabric.provider.AIProvider;
import ai.fabric.provider.AIProviderManager;
import ai.fabric.provider.springai.SpringAiChatProvider;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.runtime.authoring.AuthoringBodyLimitAdvice;
import com.ai.fabric.runtime.authoring.ConfigurationAuthoringConfiguration;
import com.ai.fabric.runtime.authoring.ConfigurationAuthoringController;
import com.ai.fabric.runtime.budget.RuntimeProviderBudgetAdminController;
import com.ai.fabric.runtime.budget.RuntimeProviderBudgetSpringConfiguration;
import com.ai.fabric.runtime.config.RuntimeAuthConfiguration;
import com.ai.fabric.runtime.config.RuntimeAuthStartupValidator;
import com.ai.fabric.runtime.config.RuntimeJacksonCompatibilityConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.jackson.JacksonEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.LifecycleAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.actuate.endpoint.web.WebMvcHealthEndpointExtensionAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;

/** Explicit imports prevent classpath auto-discovery from starting customer-serving capabilities. */
@Configuration(proxyBeanMethods = false)
@Profile("configuration-authoring")
@EnableConfigurationProperties({AIProviderConfig.class, PromptBundleProperties.class})
@ImportAutoConfiguration({PropertyPlaceholderAutoConfiguration.class, ConfigurationPropertiesAutoConfiguration.class,
    LifecycleAutoConfiguration.class, ApplicationAvailabilityAutoConfiguration.class, SslAutoConfiguration.class,
    TaskExecutionAutoConfiguration.class, TomcatServletWebServerAutoConfiguration.class,
    DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class, ErrorMvcAutoConfiguration.class,
    HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class, ValidationAutoConfiguration.class,
    DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class, EndpointAutoConfiguration.class,
    JacksonEndpointAutoConfiguration.class, WebEndpointAutoConfiguration.class, ManagementContextAutoConfiguration.class,
    HealthContributorRegistryAutoConfiguration.class, HealthEndpointAutoConfiguration.class,
    AvailabilityProbesAutoConfiguration.class, WebMvcHealthEndpointExtensionAutoConfiguration.class})
@Import({RuntimeAuthConfiguration.class, RuntimeAuthStartupValidator.class, RuntimeJacksonCompatibilityConfiguration.class,
    RuntimeProviderBudgetSpringConfiguration.class, RuntimeProviderBudgetAdminController.class,
    ConfigurationAuthoringConfiguration.class, ConfigurationAuthoringController.class, AuthoringBodyLimitAdvice.class,
    AICoreService.class, AIProviderManager.class, DefaultStructuredJsonCallExecutor.class,
    StructuredJsonExtractor.class, PromptRenderer.class, ConfigurationAuthoringBootValidator.class})
public class ConfigurationAuthoringBootConfiguration {
    @Bean
    AIProvider authoringOpenAiProvider(BudgetGuardedSpringAiModelResolver resolver) {
        return new SpringAiChatProvider("openai", resolver);
    }

    @Bean
    PromptTemplateResolver authoringPromptTemplateResolver(ResourceLoader resources, PromptBundleProperties properties) {
        return new PromptTemplateResolver(new ClasspathPromptTemplateStore(resources), properties);
    }
}
