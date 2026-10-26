package com.loomai.desk;

import com.loomai.desk.config.DeskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DeskProperties.class)
public class LoomAiDeskApplication {
  public static void main(String[] args) {
    SpringApplication.run(LoomAiDeskApplication.class, args);
  }
}
