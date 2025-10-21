package com.easyluxury;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;

@SpringBootApplication
@EnableJpaAuditing
@Import(AIInfrastructureAutoConfiguration.class)
public class EasyLuxuryApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyLuxuryApplication.class, args);
    }
}
