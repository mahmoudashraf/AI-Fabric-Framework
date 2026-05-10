package com.ai.fabric.product.shopify.bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AIFabricShopifyBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIFabricShopifyBridgeApplication.class, args);
    }
}
