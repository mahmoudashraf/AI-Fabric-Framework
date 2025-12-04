package com.ai.behavior.realapi;

import com.ai.behavior.realapi.support.BehaviorRealApiTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Slf4j
@EnabledIfSystemProperty(named = "ai.behavior.realapi.enabled", matches = "true")
public class BehaviorRealApiProviderMatrixIntegrationTest {

    private static final String MATRIX_PROPERTY = "ai.behavior.realapi.matrix";
    private static final String MATRIX_ENV = "AI_BEHAVIOR_REALAPI_MATRIX";

    private static final Class<?>[] REAL_API_TESTS = {
        BehaviorRealApiSemanticSearchIntegrationTest.class,
        BehaviorRealApiOrchestratedSearchIntegrationTest.class
    };

    private static final List<ProviderCombination> DEFAULT_COMBINATIONS = List.of(
        new ProviderCombination("openai", "onnx")
    );

    private final AtomicInteger executed = new AtomicInteger();
    private final AtomicInteger passed = new AtomicInteger();

    @TestFactory
    Stream<DynamicTest> providerMatrix() {
        BehaviorRealApiTestSupport.ensureProvidersConfigured();
        Assumptions.assumeTrue(
            BehaviorRealApiTestSupport.hasOpenAIKey(),
            "OPENAI_API_KEY must be configured for real provider matrix"
        );

        List<ProviderCombination> combinations = resolveMatrix();
        Assertions.assertThat(combinations)
            .as("Real API provider matrix should not be empty")
            .isNotEmpty();

        logStart(combinations);
        executed.set(0);
        passed.set(0);

        return combinations.stream()
            .map(combo -> DynamicTest.dynamicTest(combo.displayName(), () -> executeCombination(combinations.size(), combo)));
    }

    private void executeCombination(int total, ProviderCombination combination) {
        executed.incrementAndGet();
        log.info("[{}/{}] Running real behavior suite for {}", executed.get(), total, combination.displayName());
        long start = System.currentTimeMillis();
        configureProviderProperties(combination);

        try {
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            Launcher launcher = LauncherFactory.create();
            LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
            Arrays.stream(REAL_API_TESTS)
                .map(klass -> selectClass(klass))
                .forEach(builder::selectors);

            LauncherDiscoveryRequest request = builder.build();
            launcher.execute(request, listener);

            TestExecutionSummary summary = listener.getSummary();
            long duration = System.currentTimeMillis() - start;
            if (summary.getTotalFailureCount() > 0) {
                String failures = summary.getFailures().stream()
                    .map(failure -> failure.getTestIdentifier().getDisplayName() + " -> "
                        + (failure.getException() != null ? failure.getException().getMessage() : "Unknown error"))
                    .collect(Collectors.joining(System.lineSeparator()));
                log.error("✗ FAILED: {} ({} ms)", combination.displayName(), duration);
                throw new AssertionError("Failures detected for "
                    + combination.displayName()
                    + " (" + summary.getTotalFailureCount() + " failures)\n" + failures);
            }

            passed.incrementAndGet();
            log.info("✓ PASSED: {} ({} ms)", combination.displayName(), duration);
        } finally {
            clearProviderProperties();
        }
    }

    private void configureProviderProperties(ProviderCombination combination) {
        System.setProperty("LLM_PROVIDER", combination.llm());
        System.setProperty("ai.providers.llm-provider", combination.llm());
        System.setProperty("EMBEDDING_PROVIDER", combination.embedding());
        System.setProperty("ai.providers.embedding-provider", combination.embedding());
        System.setProperty("ai.behavior.test.use-real-ai", "true");
        System.setProperty("ai.behavior.test.mock-policy", "false");
        System.setProperty("spring.profiles.active", "real-api-test");
        log.debug("Applied provider properties at {}", Instant.now());
    }

    private void clearProviderProperties() {
        System.clearProperty("LLM_PROVIDER");
        System.clearProperty("ai.providers.llm-provider");
        System.clearProperty("EMBEDDING_PROVIDER");
        System.clearProperty("ai.providers.embedding-provider");
        System.clearProperty("ai.behavior.test.use-real-ai");
        System.clearProperty("ai.behavior.test.mock-policy");
        System.clearProperty("spring.profiles.active");
        log.debug("Cleared provider properties at {}", Instant.now());
    }

    private List<ProviderCombination> resolveMatrix() {
        String matrixSpec = System.getProperty(MATRIX_PROPERTY);
        if (!StringUtils.hasText(matrixSpec)) {
            matrixSpec = System.getenv(MATRIX_ENV);
        }
        if (!StringUtils.hasText(matrixSpec)) {
            return DEFAULT_COMBINATIONS;
        }

        List<ProviderCombination> requested = new ArrayList<>();
        Arrays.stream(matrixSpec.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .forEach(entry -> {
                String[] parts = entry.split(":");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid matrix entry '" + entry
                        + "'. Expected llm:embedding format.");
                }
                requested.add(new ProviderCombination(parts[0].trim(), parts[1].trim()));
            });
        return requested;
    }

    private void logStart(List<ProviderCombination> combinations) {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("BehaviorRealApiProviderMatrixIntegrationTest - Starting");
        log.info("Total combinations to test: {}", combinations.size());
        combinations.forEach(combo -> log.info("  • {}", combo.displayName()));
        log.info("═══════════════════════════════════════════════════════════════");
    }

    private record ProviderCombination(String llm, String embedding) {
        String displayName() {
            return "LLM=" + llm + " | Embedding=" + embedding;
        }
    }
}
