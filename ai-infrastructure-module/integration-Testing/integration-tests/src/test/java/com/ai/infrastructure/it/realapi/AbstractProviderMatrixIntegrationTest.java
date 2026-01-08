package com.ai.infrastructure.it.realapi;

import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.provider.registry.ProviderDefinition;
import com.ai.infrastructure.provider.registry.ProviderRegistryService;
import com.ai.infrastructure.provider.registry.ProviderType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Shared harness for running suites of integration tests across multiple
 * provider combinations (LLM / Embedding / optional Vector DB).
 */
abstract class AbstractProviderMatrixIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractProviderMatrixIntegrationTest.class);

    private static final String VECTORDB_PROPERTY = "ai.vector-db.type";
    private static final String STORAGE_STRATEGY_PROPERTY = "ai-infrastructure.storage.strategy";
    private final AtomicInteger testCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);

    @TestFactory
    Stream<DynamicTest> providerMatrix() {
        beforeMatrixExecution();
        List<ProviderCombination> combinations = resolveProviderMatrix();
        Assertions.assertThat(combinations)
            .as("Provider matrix should not be empty")
            .isNotEmpty();

        // Filter combinations based on provider-specific rules
        List<ProviderCombination> filteredCombinations = filterProviderCombinations(combinations);
        
        if (filteredCombinations.size() < combinations.size()) {
            log.info("Filtered {} combinations down to {} based on provider-specific rules",
                combinations.size(), filteredCombinations.size());
        }

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("{} - Starting", suiteDisplayName());
        log.info("Total combinations to test: {}", filteredCombinations.size());
        log.info("─────────────────────────────────────────────────────────────");
        filteredCombinations.forEach(combo -> log.info("  • {}", combo.displayName()));
        log.info("═══════════════════════════════════════════════════════════════");

        testCount.set(0);
        successCount.set(0);

        return filteredCombinations.stream()
            .map(combo -> DynamicTest.dynamicTest(
                combo.displayName(),
                () -> executeCombination(filteredCombinations.size(), combo)
            ));
    }
    
    /**
     * Filter provider combinations based on provider-specific rules.
     * Override this method in subclasses to implement custom filtering.
     */
    protected List<ProviderCombination> filterProviderCombinations(List<ProviderCombination> combinations) {
        List<ProviderCombination> filtered = new ArrayList<>();
        
        for (ProviderCombination combo : combinations) {
            if (shouldIncludeCombination(combo)) {
                filtered.add(combo);
            } else {
                log.debug("Filtered out combination: {} (provider-specific rule)", combo.displayName());
            }
        }
        
        return filtered;
    }
    
    /**
     * Determine if a provider combination should be included in tests.
     * Override this method to implement custom filtering logic.
     */
    protected boolean shouldIncludeCombination(ProviderCombination combo) {
        // Default: include all combinations
        // Override in subclasses for provider-specific filtering
        
        // Example: Skip ONNX as LLM (it's only for embeddings)
        if ("onnx".equals(combo.llmProvider())) {
            return false;
        }
        
        return true;
    }

    private void executeCombination(int total, ProviderCombination combo) {
        testCount.incrementAndGet();
        log.info("[{}/{}] Running tests for: {}", testCount.get(), total, combo.displayName());

        // Validate provider combination before execution
        try {
            validateProviderCombination(combo);
        } catch (IllegalArgumentException e) {
            log.error("❌ Provider combination validation failed: {}", e.getMessage());
            throw e;
        }

        long startTime = System.currentTimeMillis();
        configureProviderProperties(combo);

        try {
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            Launcher launcher = LauncherFactory.create();

            LauncherDiscoveryRequestBuilder requestBuilder = LauncherDiscoveryRequestBuilder.request();
            for (Class<?> testClass : suiteTestClasses()) {
                requestBuilder.selectors(selectClass(testClass));
            }

            LauncherDiscoveryRequest request = requestBuilder.build();
            launcher.execute(request, listener);

            TestExecutionSummary summary = listener.getSummary();
            long duration = System.currentTimeMillis() - startTime;

            if (summary.getTotalFailureCount() > 0) {
                String failures = summary.getFailures().stream()
                    .map(failure -> {
                        String testName = failure.getTestIdentifier().getDisplayName();
                        String errorMsg = failure.getException() != null ? failure.getException().getMessage() : "Unknown error";
                        // Truncate very long error messages
                        if (errorMsg != null && errorMsg.length() > 200) {
                            errorMsg = errorMsg.substring(0, 197) + "...";
                        }
                        return testName + " -> " + errorMsg;
                    })
                    .collect(Collectors.joining(System.lineSeparator()));
                
                log.error("✗ FAILED: {} ({} ms)", combo.displayName(), duration);
                log.error("Provider combination: LLM={}, Embedding={}, VectorDB={}, Storage={}",
                    combo.llmProvider(), combo.embeddingProvider(), 
                    combo.vectorDbProvider() != null ? combo.vectorDbProvider() : "default",
                    combo.storageStrategy());
                
                throw new AssertionError("Failures detected for " + combo.displayName() + " (" +
                    summary.getTotalFailureCount() + " failures)" + System.lineSeparator() + failures);
            }

            log.debug("Combination completed successfully in {} ms. Tests: {}, Failures: {}, Skipped: {}",
                duration, summary.getTestsFoundCount(), summary.getTotalFailureCount(), summary.getTestsSkippedCount());
            successCount.incrementAndGet();
            log.info("✓ [{}] PASSED: {}", testCount.get(), combo.displayName());

        } finally {
            clearProviderProperties(combo);
        }
    }

    protected List<ProviderCombination> resolveProviderMatrix() {
        List<ProviderCombination> availableCombinations = availableProviderCombinations();
        
        // Log available combinations for debugging
        if (log.isDebugEnabled()) {
            log.debug("Available provider combinations ({}):", availableCombinations.size());
            availableCombinations.forEach(combo -> 
                log.debug("  - {}", combo.displayName()));
        }

        String matrixSpec = System.getProperty(matrixPropertyKey());
        if (!StringUtils.hasText(matrixSpec)) {
            matrixSpec = System.getenv(matrixEnvVariable());
        }

        if (!StringUtils.hasText(matrixSpec)) {
            log.info("No matrix spec provided, using all {} available combinations", availableCombinations.size());
            return availableCombinations;
        }

        log.info("Parsing matrix spec: {}", matrixSpec);
        List<ProviderCombination> requestedCombinations = parseMatrixSpec(matrixSpec);
        
        // Validate each combination individually for better error messages
        for (ProviderCombination combo : requestedCombinations) {
            try {
                validateProviderCombination(combo);
            } catch (IllegalArgumentException e) {
                log.error("Invalid provider combination: {} - {}", combo.displayName(), e.getMessage());
                throw new IllegalArgumentException(
                    "Invalid provider combination in matrix spec: " + combo.displayName() + "\n" + e.getMessage(), e);
            }
        }
        
        validateRequestedCombinations(requestedCombinations, availableCombinations);
        return requestedCombinations;
    }

    protected List<ProviderCombination> availableProviderCombinations() {
        return expandWithStorageStrategies(discoverAvailableCombinations());
    }

    private List<ProviderCombination> discoverAvailableCombinations() {
        // Try to use provider registry first (new approach)
        try {
            ProviderRegistryService registry = ProviderRegistryService.getInstance();
            List<ProviderDefinition> availableLLM = registry.getAvailableLLMProviders();
            List<ProviderDefinition> availableEmbedding = registry.getAvailableEmbeddingProviders();
            
            if (!availableLLM.isEmpty() && !availableEmbedding.isEmpty()) {
                log.info("Using provider registry for discovery: {} LLM, {} embedding providers",
                    availableLLM.size(), availableEmbedding.size());
                
                List<String> llmProviders = availableLLM.stream()
                    .map(ProviderDefinition::getName)
                    .sorted()
                    .toList();
                
                List<String> embeddingProviders = availableEmbedding.stream()
                    .map(ProviderDefinition::getName)
                    .sorted()
                    .toList();
                
                List<String> vectorDbProviders = vectorDbProviders();
                
                List<ProviderCombination> combinations = new ArrayList<>();
                for (String llm : llmProviders) {
                    for (String embedding : embeddingProviders) {
                        for (String vector : vectorDbProviders) {
                            combinations.add(new ProviderCombination(llm, embedding, vector, defaultStorageStrategy()));
                        }
                    }
                }
                return combinations;
            }
        } catch (Exception e) {
            log.warn("Failed to use provider registry, falling back to Spring context discovery: {}", e.getMessage());
        }
        
        // Fallback to original Spring context discovery (backward compatibility)
        ProviderCombination defaultCombo = new ProviderCombination(defaultLlmProvider(), defaultEmbeddingProvider(), null, defaultStorageStrategy());
        configureProviderProperties(defaultCombo);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
            .profiles(discoveryProfile())
            .properties(defaultDiscoveryProperties())
            .properties(additionalDiscoveryProperties())
            .run()) {

            AIProviderManager providerManager = context.getBean(AIProviderManager.class);
            List<String> llmProviders = providerManager.getAvailableProviders().stream()
                .map(AIProvider::getProviderName)
                .sorted()
                .toList();

            List<String> embeddingProviders = context.getBeansOfType(EmbeddingProvider.class).values().stream()
                .filter(EmbeddingProvider::isAvailable)
                .map(EmbeddingProvider::getProviderName)
                .distinct()
                .sorted()
                .toList();

            List<String> vectorDbProviders = vectorDbProviders();

            List<ProviderCombination> combinations = new ArrayList<>();
            for (String llm : llmProviders) {
                for (String embedding : embeddingProviders) {
                    for (String vector : vectorDbProviders) {
                        combinations.add(new ProviderCombination(llm, embedding, vector, defaultStorageStrategy()));
                    }
                }
            }
            return combinations;
        } finally {
            clearProviderProperties(defaultCombo);
        }
    }

    private Map<String, Object> defaultDiscoveryProperties() {
        Map<String, Object> props = new java.util.HashMap<>(Map.of(
            "ai.providers.llm-provider", defaultLlmProvider(),
            "ai.providers.embedding-provider", defaultEmbeddingProvider(),
            STORAGE_STRATEGY_PROPERTY, defaultStorageStrategy()
        ));
        
        // Include API keys from environment for provider availability checks during discovery
        // Use both kebab-case and camelCase for Spring Boot relaxed binding
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        if (anthropicKey != null && !anthropicKey.trim().isEmpty()) {
            props.put("ai.providers.anthropic.api-key", anthropicKey);
            props.put("ai.providers.anthropic.apiKey", anthropicKey); // Also set camelCase
            props.put("ai.providers.anthropic.enabled", true); // Use boolean, not string
        }
        
        String openaiKey = System.getenv("OPENAI_API_KEY");
        if (openaiKey != null && !openaiKey.trim().isEmpty()) {
            props.put("ai.providers.openai.api-key", openaiKey);
            props.put("ai.providers.openai.apiKey", openaiKey); // Also set camelCase
            props.put("ai.providers.openai.enabled", true); // Use boolean, not string
        }
        
        String geminiKey = System.getenv("GEMINI_API_KEY");
        if (geminiKey != null && !geminiKey.trim().isEmpty()) {
            props.put("ai.providers.gemini.api-key", geminiKey);
            props.put("ai.providers.gemini.apiKey", geminiKey); // Also set camelCase
            props.put("ai.providers.gemini.enabled", true); // Use boolean, not string
        }
        
        String cohereKey = System.getenv("COHERE_API_KEY");
        if (cohereKey != null && !cohereKey.trim().isEmpty()) {
            props.put("ai.providers.cohere.api-key", cohereKey);
            props.put("ai.providers.cohere.apiKey", cohereKey); // Also set camelCase
            props.put("ai.providers.cohere.enabled", true); // Use boolean, not string
        }
        
        String azureKey = System.getenv("AZURE_API_KEY");
        String azureEndpoint = System.getenv("AZURE_ENDPOINT");
        if (azureKey != null && !azureKey.trim().isEmpty() && azureEndpoint != null && !azureEndpoint.trim().isEmpty()) {
            props.put("ai.providers.azure.api-key", azureKey);
            props.put("ai.providers.azure.apiKey", azureKey); // Also set camelCase
            props.put("ai.providers.azure.endpoint", azureEndpoint);
            props.put("ai.providers.azure.deployment-name", System.getenv("AZURE_DEPLOYMENT_NAME"));
            props.put("ai.providers.azure.embedding-deployment-name", System.getenv("AZURE_EMBEDDING_DEPLOYMENT_NAME"));
            props.put("ai.providers.azure.enabled", true); // Use boolean, not string
        }
        
        return props;
    }

    private List<ProviderCombination> parseMatrixSpec(String matrixSpec) {
        return Arrays.stream(matrixSpec.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(entry -> {
                String[] parts = entry.split(":");
                if (parts.length < 2 || parts.length > 4) {
                    throw new IllegalArgumentException(
                        "Invalid provider matrix entry: '" + entry + "'. Expected llm:embedding[:vectordb][:storageStrategy]");
                }

                String llm = parts[0].trim();
                String embedding = parts[1].trim();
                String vectorDb = parts.length >= 3 ? parts[2].trim() : null;
                String storageStrategy = parts.length == 4 ? parts[3].trim() : defaultStorageStrategy();

                if (!StringUtils.hasText(llm) || !StringUtils.hasText(embedding)) {
                    throw new IllegalArgumentException(
                        "Invalid provider matrix entry: '" + entry + "'. LLM and embedding providers cannot be empty.");
                }

                if (!StringUtils.hasText(storageStrategy)) {
                    storageStrategy = defaultStorageStrategy();
                }

                return new ProviderCombination(llm, embedding, vectorDb, storageStrategy);
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void validateRequestedCombinations(List<ProviderCombination> requested,
                                               List<ProviderCombination> available) {
        Set<ProviderCombination> availableSet = new LinkedHashSet<>(available);
        List<ProviderCombination> missing = requested.stream()
            .filter(combo -> !availableSet.contains(combo))
            .toList();

        if (!missing.isEmpty()) {
            // Enhanced error message with registry information
            StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("Requested provider combinations are not available.\n\n");
            errorMsg.append("Missing combinations (").append(missing.size()).append("):\n");
            
            for (ProviderCombination combo : missing) {
                errorMsg.append("  ❌ ").append(combo.displayName()).append("\n");
                
                // Check individual providers and provide helpful messages
                String llmIssue = validateProvider(combo.llmProvider(), ProviderType.LLM);
                String embeddingIssue = validateProvider(combo.embeddingProvider(), ProviderType.EMBEDDING);
                
                if (llmIssue != null) {
                    errorMsg.append("     LLM Provider Issue: ").append(llmIssue).append("\n");
                }
                if (embeddingIssue != null) {
                    errorMsg.append("     Embedding Provider Issue: ").append(embeddingIssue).append("\n");
                }
            }
            
            errorMsg.append("\nAvailable combinations (").append(available.size()).append("):\n");
            available.stream()
                .limit(10) // Limit to first 10 to avoid overwhelming output
                .forEach(combo -> errorMsg.append("  ✅ ").append(combo.displayName()).append("\n"));
            if (available.size() > 10) {
                errorMsg.append("  ... and ").append(available.size() - 10).append(" more\n");
            }
            
            throw new IllegalArgumentException(errorMsg.toString());
        }
    }
    
    /**
     * Validate a single provider and return error message if invalid
     */
    private String validateProvider(String providerName, ProviderType type) {
        if (providerName == null || providerName.trim().isEmpty()) {
            return "Provider name is empty";
        }
        
        try {
            ProviderRegistryService registry = ProviderRegistryService.getInstance();
            ProviderDefinition def = registry.getProvider(providerName, type);
            
            if (def == null) {
                List<String> available = registry.getProviderNames(type);
                return String.format("Provider '%s' not found in registry. Available %s providers: %s",
                    providerName, type.name().toLowerCase(), String.join(", ", available));
            }
            
            if (!def.isEnabled()) {
                return String.format("Provider '%s' is disabled in registry", providerName);
            }
            
            if (!def.isAvailable()) {
                List<String> requiredVars = def.getRequiredEnvVars();
                if (!requiredVars.isEmpty()) {
                    List<String> missingVars = requiredVars.stream()
                        .filter(var -> System.getenv(var) == null || System.getenv(var).trim().isEmpty())
                        .toList();
                    
                    if (!missingVars.isEmpty()) {
                        return String.format("Missing required environment variables: %s. " +
                            "Set these variables to use provider '%s'",
                            String.join(", ", missingVars), providerName);
                    }
                }
                return String.format("Provider '%s' is not available. Check configuration and environment variables",
                    providerName);
            }
            
            return null; // Provider is valid
        } catch (Exception e) {
            log.warn("Error validating provider {}: {}", providerName, e.getMessage());
            return "Error checking provider: " + e.getMessage();
        }
    }
    
    /**
     * Validate provider combination compatibility
     */
    private void validateProviderCombination(ProviderCombination combo) {
        List<String> issues = new ArrayList<>();
        
        // Check if providers exist in registry
        String llmIssue = validateProvider(combo.llmProvider(), ProviderType.LLM);
        if (llmIssue != null) {
            issues.add("LLM: " + llmIssue);
        }
        
        String embeddingIssue = validateProvider(combo.embeddingProvider(), ProviderType.EMBEDDING);
        if (embeddingIssue != null) {
            issues.add("Embedding: " + embeddingIssue);
        }
        
        // Check for known incompatibilities
        if (combo.llmProvider().equals("onnx") && combo.embeddingProvider().equals("onnx")) {
            // ONNX as LLM doesn't exist, but this is handled by validateProvider
        }
        
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException("Provider combination validation failed for " + combo.displayName() + ":\n" +
                String.join("\n", issues));
        }
    }

    private void configureProviderProperties(ProviderCombination combination) {
        System.setProperty("LLM_PROVIDER", combination.llmProvider());
        System.setProperty("ai.providers.llm-provider", combination.llmProvider());
        System.setProperty("EMBEDDING_PROVIDER", combination.embeddingProvider());
        System.setProperty("ai.providers.embedding-provider", combination.embeddingProvider());
        System.setProperty(STORAGE_STRATEGY_PROPERTY, combination.storageStrategy());

        if (StringUtils.hasText(combination.vectorDbProvider())) {
            System.setProperty(VECTORDB_PROPERTY, combination.vectorDbProvider());
        } else {
            System.clearProperty(VECTORDB_PROPERTY);
        }
    }

    private void clearProviderProperties(ProviderCombination combination) {
        System.clearProperty("LLM_PROVIDER");
        System.clearProperty("ai.providers.llm-provider");
        System.clearProperty("EMBEDDING_PROVIDER");
        System.clearProperty("ai.providers.embedding-provider");
        System.clearProperty(VECTORDB_PROPERTY);
        System.clearProperty(STORAGE_STRATEGY_PROPERTY);
        log.debug("Cleared provider properties at {}", Instant.now());
    }

    protected void beforeMatrixExecution() {
        cleanLuceneIndex();
    }

    private void cleanLuceneIndex() {
        Path indexPath = Path.of("data", "lucene-vector-index");
        if (!Files.exists(indexPath)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(indexPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
    }

    protected String suiteDisplayName() {
        return getClass().getSimpleName();
    }

    protected String defaultLlmProvider() {
        return "openai";
    }

    protected String defaultEmbeddingProvider() {
        return "onnx";
    }

    protected String discoveryProfile() {
        return "real-api-test";
    }

    protected Map<String, Object> additionalDiscoveryProperties() {
        return Map.of();
    }

    protected abstract Class<?>[] suiteTestClasses();

    protected String matrixPropertyKey() {
        return "ai.providers.real-api.matrix";
    }

    protected String matrixEnvVariable() {
        return "AI_PROVIDERS_REAL_API_MATRIX";
    }

    protected record ProviderCombination(
        String llmProvider,
        String embeddingProvider,
        String vectorDbProvider,
        String storageStrategy
    ) {
        public ProviderCombination(String llmProvider, String embeddingProvider) {
            this(llmProvider, embeddingProvider, null, defaultStorageStrategy());
        }

        public ProviderCombination(String llmProvider, String embeddingProvider, String vectorDbProvider) {
            this(llmProvider, embeddingProvider, vectorDbProvider, defaultStorageStrategy());
        }

        public String displayName() {
            if (StringUtils.hasText(vectorDbProvider)) {
                return "LLM=" + llmProvider + " | Embedding=" + embeddingProvider + " | VectorDB=" + vectorDbProvider + " | Storage=" + storageStrategy;
            }
            return "LLM=" + llmProvider + " | Embedding=" + embeddingProvider + " | Storage=" + storageStrategy;
        }

        @Override
        public String toString() {
            if (StringUtils.hasText(vectorDbProvider)) {
                return llmProvider + "/" + embeddingProvider + "/" + vectorDbProvider + "/" + storageStrategy;
            }
            return llmProvider + "/" + embeddingProvider + "/" + storageStrategy;
        }
    }

    private List<ProviderCombination> expandWithStorageStrategies(List<ProviderCombination> base) {
        List<String> storageStrategies = storageStrategies();
        return base.stream()
            .flatMap(combo -> storageStrategies.stream()
                .map(storage -> new ProviderCombination(
                    combo.llmProvider(),
                    combo.embeddingProvider(),
                    combo.vectorDbProvider(),
                    storage)))
            .toList();
    }

    protected List<String> storageStrategies() {
        return List.of("SINGLE_TABLE", "PER_TYPE_TABLE");
    }

    protected static String defaultStorageStrategy() {
        return "PER_TYPE_TABLE";
    }

    protected List<String> vectorDbProviders() {
        return java.util.Arrays.asList("lucene", null);
    }
}
