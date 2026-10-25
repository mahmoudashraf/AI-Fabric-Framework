package com.loomai.operations;

import com.loomai.operations.config.OperationsProperties;
import com.loomai.operations.lifecycle.LifecycleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({OperationsProperties.class, LifecycleProperties.class})
@EnableScheduling
public class DeploymentOperationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeploymentOperationsApplication.class, args);
    }
}
