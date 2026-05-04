package com.ai.fabric.product.mcp.gateway;

import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(McpGatewayProperties.class)
public class McpExecutionGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpExecutionGatewayApplication.class, args);
    }
}
