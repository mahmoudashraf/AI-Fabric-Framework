package com.loomai.demo.dealership;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.loomai.demo.dealership.config.ManagedDependencyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({DealershipDemoProperties.class, ManagedDependencyProperties.class})
public class DealershipDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DealershipDemoApplication.class, args);
    }
}
