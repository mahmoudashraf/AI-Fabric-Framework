package com.ai.fabric.platform.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PlatformBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformBackendApplication.class, args);
    }
}

