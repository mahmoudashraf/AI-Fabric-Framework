package com.loomai.inbox;

import com.loomai.inbox.config.BasicInboxProperties;
import com.loomai.inbox.config.ManagedDependencyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({BasicInboxProperties.class, ManagedDependencyProperties.class})
public class BasicLoomAiInboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(BasicLoomAiInboxApplication.class, args);
    }
}
