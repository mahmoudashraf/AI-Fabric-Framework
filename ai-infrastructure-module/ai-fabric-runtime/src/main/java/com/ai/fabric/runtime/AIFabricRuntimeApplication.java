package com.ai.fabric.runtime;

import com.ai.fabric.runtime.config.RuntimeDeploymentPromptConfigProperties;
import com.ai.fabric.runtime.config.RuntimeDeploymentKnowledgeSourceConfigProperties;
import com.ai.fabric.runtime.config.RuntimeDeploymentShellConfigProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    RuntimeDeploymentPromptConfigProperties.class,
    RuntimeDeploymentKnowledgeSourceConfigProperties.class,
    RuntimeDeploymentShellConfigProperties.class
})
public class AIFabricRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIFabricRuntimeApplication.class, args);
    }
}
