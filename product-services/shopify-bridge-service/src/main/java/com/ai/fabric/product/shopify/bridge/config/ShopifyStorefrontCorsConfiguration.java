package com.ai.fabric.product.shopify.bridge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class ShopifyStorefrontCorsConfiguration implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Public storefront endpoints are intentionally browser-callable from merchant storefronts.
        // They do not rely on cookies, so permissive cross-origin access is acceptable here.
        registry.addMapping("/api/storefront/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
        // Customer-auth endpoints are storefront-callable but never expose OAuth tokens.
        registry.addMapping("/api/customer-auth/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }
}
