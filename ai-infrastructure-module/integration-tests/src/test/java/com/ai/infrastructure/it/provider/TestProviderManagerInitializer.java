package com.ai.infrastructure.it.provider;

import com.ai.infrastructure.provider.AIProviderManager;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ensures {@link AIProviderManager} has registered providers in integration tests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestProviderManagerInitializer {

    private final AIProviderManager providerManager;

    @PostConstruct
    void initProviderManager() {
        log.info("Initializing AIProviderManager for integration tests");
        providerManager.initialize();
    }
}
