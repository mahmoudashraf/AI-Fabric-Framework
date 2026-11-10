package com.ai.fabric.runtime;

import com.ai.fabric.runtime.boot.ServingRuntimeConfiguration;
import com.ai.fabric.runtime.authoring.boot.ConfigurationAuthoringBootConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@Import({ServingRuntimeConfiguration.class, ConfigurationAuthoringBootConfiguration.class})
public class AIFabricRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIFabricRuntimeApplication.class, args);
    }
}
