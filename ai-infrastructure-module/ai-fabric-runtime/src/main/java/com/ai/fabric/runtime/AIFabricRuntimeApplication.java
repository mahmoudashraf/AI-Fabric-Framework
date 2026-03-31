package com.ai.fabric.runtime;

import com.ai.fabric.runtime.config.RuntimeDevDefaultsProperties;
import com.ai.fabric.runtime.config.RuntimeDeploymentPromptConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    RuntimeDevDefaultsProperties.class,
    RuntimeDeploymentPromptConfigProperties.class
})
public class AIFabricRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIFabricRuntimeApplication.class, args);
    }
}
