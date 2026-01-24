package com.ai.infrastructure.it.migration;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = "com.ai.infrastructure",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.ai\\.infrastructure\\.it\\..*"
    )
)
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.migration.domain",
    "com.ai.infrastructure.it.entity",
    "com.ai.infrastructure.it.migration"
})
@EnableJpaRepositories(basePackages = {
    "com.ai.infrastructure.repository",
    "com.ai.infrastructure.migration.repository",
    "com.ai.infrastructure.it.repository",
    "com.ai.infrastructure.it.migration"
})
@Profile("migration-test")
public class TestMigrationApplication {
}
