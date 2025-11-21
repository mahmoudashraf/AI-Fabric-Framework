package com.easyluxury;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;

@SpringBootApplication(scanBasePackages = {"com.easyluxury", "com.ai.infrastructure", "com.ai.behavior"})
@EntityScan(basePackages = {"com.easyluxury.entity", "com.ai.infrastructure.entity", "com.ai.behavior.model"})
@EnableJpaRepositories(basePackages = {"com.easyluxury.repository", "com.ai.infrastructure.repository", "com.ai.behavior.storage"})
@EnableJpaAuditing
@Import(AIInfrastructureAutoConfiguration.class)
public class EasyLuxuryApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyLuxuryApplication.class, args);
    }
}
