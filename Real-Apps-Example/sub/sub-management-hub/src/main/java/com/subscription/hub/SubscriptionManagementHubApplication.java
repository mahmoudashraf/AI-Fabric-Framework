package com.subscription.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.subscription.hub", "com.ai.infrastructure"})
@EntityScan({"com.subscription.hub.entity", "com.ai.infrastructure.entity", "com.ai.infrastructure.behavior.entity"})
@EnableJpaRepositories({"com.subscription.hub.repository", "com.ai.infrastructure.repository", "com.ai.infrastructure.behavior.repository"})
public class SubscriptionManagementHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionManagementHubApplication.class, args);
    }
}
