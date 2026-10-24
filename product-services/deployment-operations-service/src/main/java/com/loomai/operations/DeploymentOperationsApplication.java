package com.loomai.operations;

import com.loomai.operations.config.OperationsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OperationsProperties.class)
public class DeploymentOperationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeploymentOperationsApplication.class, args);
    }
}
