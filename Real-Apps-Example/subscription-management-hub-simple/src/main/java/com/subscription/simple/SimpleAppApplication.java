package com.subscription.simple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.subscription.simple", "com.ai.infrastructure"})
@EntityScan(basePackages = {"com.subscription.simple.entity", "com.ai.infrastructure.entity"})
@EnableJpaRepositories(basePackages = {"com.subscription.simple.repository", "com.ai.infrastructure.repository"})
public class SimpleAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAppApplication.class, args);
    }
}
