package com.subscription.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.subscription.hub.entity")
@EnableJpaRepositories("com.subscription.hub.repository")
public class SubscriptionManagementHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionManagementHubApplication.class, args);
    }
}
