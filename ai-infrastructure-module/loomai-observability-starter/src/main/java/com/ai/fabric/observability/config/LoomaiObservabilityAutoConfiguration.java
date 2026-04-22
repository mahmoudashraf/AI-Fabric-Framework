package com.ai.fabric.observability.config;

import com.ai.fabric.observability.logging.LoomaiMdcTaskDecorator;
import com.ai.fabric.observability.logging.LoomaiObservabilityContextContributor;
import com.ai.fabric.observability.logging.LoomaiPathVariableInterceptor;
import com.ai.fabric.observability.logging.LoomaiRequestCorrelationFilter;
import com.ai.fabric.observability.metrics.LoomaiCardinalityMeterFilter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheus.HistogramFlavor;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exemplars.ExemplarSampler;
import jakarta.servlet.DispatcherType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusProperties;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@EnableConfigurationProperties(LoomaiObservabilityProperties.class)
@ConditionalOnProperty(prefix = "loomai.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoomaiObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "loomaiObservabilityTaskDecorator")
    TaskDecorator loomaiObservabilityTaskDecorator() {
        return new LoomaiMdcTaskDecorator();
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> loomaiCommonTagsCustomizer(LoomaiObservabilityProperties properties) {
        return registry -> {
            List<String> commonTags = new ArrayList<>();
            commonTags.add("product");
            commonTags.add(properties.getProduct());
            commonTags.add("service");
            commonTags.add(properties.getService());
            commonTags.add("environment");
            commonTags.add(properties.getEnvironment());
            properties.getMetrics().getCommonTags().forEach((key, value) -> {
                commonTags.add(key);
                commonTags.add(value);
            });
            registry.config().commonTags(commonTags.toArray(String[]::new));
        };
    }

    @Bean
    MeterFilter loomaiDeniedCardinalityTagsFilter(LoomaiObservabilityProperties properties) {
        return new LoomaiCardinalityMeterFilter(properties.getMetrics().getDeniedTagKeys());
    }

    @Bean
    MeterFilter loomaiUriCardinalityFilter(LoomaiObservabilityProperties properties) {
        return MeterFilter.maximumAllowableTags(
            "http.server.requests",
            "uri",
            properties.getMetrics().getMaxUriTags(),
            MeterFilter.deny()
        );
    }

    @Bean
    @ConditionalOnClass({CollectorRegistry.class, PrometheusMeterRegistry.class})
    @ConditionalOnProperty(
        prefix = "management.prometheus.metrics.export",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean
    PrometheusConfig loomaiPrometheusConfig(ObjectProvider<PrometheusProperties> propertiesProvider) {
        PrometheusProperties properties = propertiesProvider.getIfAvailable();
        if (properties == null) {
            return PrometheusConfig.DEFAULT;
        }
        return new PrometheusConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public boolean descriptions() {
                return properties.isDescriptions();
            }

            @Override
            public HistogramFlavor histogramFlavor() {
                HistogramFlavor histogramFlavor = properties.getHistogramFlavor();
                return histogramFlavor != null ? histogramFlavor : PrometheusConfig.super.histogramFlavor();
            }

            @Override
            public java.time.Duration step() {
                java.time.Duration step = properties.getStep();
                return step != null ? step : PrometheusConfig.super.step();
            }
        };
    }

    @Bean
    @ConditionalOnClass(CollectorRegistry.class)
    @ConditionalOnProperty(
        prefix = "management.prometheus.metrics.export",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean
    CollectorRegistry loomaiCollectorRegistry() {
        return new CollectorRegistry(true);
    }

    @Bean
    @ConditionalOnClass({CollectorRegistry.class, PrometheusMeterRegistry.class})
    @ConditionalOnProperty(
        prefix = "management.prometheus.metrics.export",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean(PrometheusMeterRegistry.class)
    PrometheusMeterRegistry loomaiPrometheusMeterRegistry(
        PrometheusConfig config,
        CollectorRegistry collectorRegistry,
        ObjectProvider<Clock> clockProvider,
        ObjectProvider<ExemplarSampler> exemplarSamplerProvider
    ) {
        return new PrometheusMeterRegistry(
            config,
            collectorRegistry,
            Objects.requireNonNullElseGet(clockProvider.getIfAvailable(), () -> Clock.SYSTEM),
            exemplarSamplerProvider.getIfAvailable()
        );
    }

    @Bean
    @ConditionalOnClass({CollectorRegistry.class, PrometheusScrapeEndpoint.class})
    @ConditionalOnProperty(
        prefix = "management.prometheus.metrics.export",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    @ConditionalOnMissingBean
    PrometheusScrapeEndpoint loomaiPrometheusScrapeEndpoint(CollectorRegistry collectorRegistry) {
        return new PrometheusScrapeEndpoint(collectorRegistry);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    FilterRegistrationBean<LoomaiRequestCorrelationFilter> loomaiRequestCorrelationFilter(
        LoomaiObservabilityProperties properties,
        ObjectProvider<LoomaiObservabilityContextContributor> contributors
    ) {
        FilterRegistrationBean<LoomaiRequestCorrelationFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new LoomaiRequestCorrelationFilter(properties, contributors.orderedStream().toList()));
        bean.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    WebMvcConfigurer loomaiObservabilityWebMvcConfigurer(LoomaiObservabilityProperties properties) {
        LoomaiPathVariableInterceptor interceptor = new LoomaiPathVariableInterceptor(
            properties.getHttp().getPathVariableKeys()
        );
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
