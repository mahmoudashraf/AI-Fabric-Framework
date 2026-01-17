package com.ai.fabric.realapps.chat;

import com.ai.infrastructure.annotation.EnableAIInfrastructure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EnableAIInfrastructure
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.chat.domain"
})
public class ChatCapabilitiesDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatCapabilitiesDemoApplication.class, args);
    }
}
