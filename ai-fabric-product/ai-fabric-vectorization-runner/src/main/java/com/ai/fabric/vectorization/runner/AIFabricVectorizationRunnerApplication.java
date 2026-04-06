package com.ai.fabric.vectorization.runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AIFabricVectorizationRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIFabricVectorizationRunnerApplication.class, args);
    }
}
