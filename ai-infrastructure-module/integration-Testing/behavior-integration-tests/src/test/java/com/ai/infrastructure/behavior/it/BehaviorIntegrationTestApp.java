package com.ai.infrastructure.behavior.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ai.infrastructure.behavior")
public class BehaviorIntegrationTestApp {
    public static void main(String[] args) {
        SpringApplication.run(BehaviorIntegrationTestApp.class, args);
    }
}
