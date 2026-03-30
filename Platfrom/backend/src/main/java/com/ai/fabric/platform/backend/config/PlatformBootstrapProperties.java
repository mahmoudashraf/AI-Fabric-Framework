package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.bootstrap")
public record PlatformBootstrapProperties(
    boolean sampleEnabled,
    EcommerceDemoProperties ecommerceDemo
) {

    public PlatformBootstrapProperties {
        ecommerceDemo = ecommerceDemo == null
            ? new EcommerceDemoProperties(false, false)
            : ecommerceDemo;
    }

    public record EcommerceDemoProperties(
        boolean enabled,
        boolean autoApply
    ) {}
}
