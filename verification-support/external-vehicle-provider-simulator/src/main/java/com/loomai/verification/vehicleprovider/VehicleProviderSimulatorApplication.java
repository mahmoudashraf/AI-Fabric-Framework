package com.loomai.verification.vehicleprovider;

import com.loomai.verification.vehicleprovider.config.SimulatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class VehicleProviderSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehicleProviderSimulatorApplication.class, args);
    }
}
