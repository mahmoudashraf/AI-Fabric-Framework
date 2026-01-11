# AI Fabric Framework - Developer Experience Enhancement Guide

> **Version:** 1.0.0
> **Status:** Proposal
> **Last Updated:** January 2026

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Problem Analysis](#problem-analysis)
3. [Solution Architecture](#solution-architecture)
4. [Implementation Guide](#implementation-guide)
5. [Migration Guide](#migration-guide)
6. [Best Practices](#best-practices)
7. [Appendix](#appendix)

---

## Executive Summary

This document outlines comprehensive enhancements to the AI Fabric Framework to improve developer experience (DX). The primary goals are:

- **Zero-configuration setup** for common use cases
- **Graceful degradation** when optional modules are not configured
- **Clear error messages** when configuration is missing
- **Backward compatibility** with existing applications

### Key Issues Addressed

| Issue | Impact | Solution |
|-------|--------|----------|
| Manual `@EntityScan` required | High friction for new users | Auto-registration via `BeanFactoryPostProcessor` |
| Manual `@ComponentScan` required | Boilerplate in every app | Custom `@EnableAIInfrastructure` annotation |
| Hard dependencies on optional modules | Application fails to start | `ObjectProvider` + `@ConditionalOnBean` |
| Missing provider configuration | Unclear errors | Validation with actionable messages |
| Transitive dependencies not pulled | Runtime `ClassNotFoundException` | Starter POMs with sensible defaults |

---

## Problem Analysis

### 1. Entity/Repository Scanning Gap

**Current Behavior:**
```java
// User MUST add all these annotations manually
@SpringBootApplication
@ComponentScan({"com.myapp", "com.ai.infrastructure"})
@EntityScan({"com.myapp.entity", "com.ai.infrastructure.entity",
             "com.ai.infrastructure.behavior.entity"})
@EnableJpaRepositories({"com.myapp.repository", "com.ai.infrastructure.repository",
                        "com.ai.infrastructure.behavior.repository"})
public class MyApplication { }
```

**Root Cause:**
- Spring Boot's `@SpringBootApplication` only scans the package where it's declared
- `AutoConfiguration.imports` registers configuration classes but doesn't extend scanning
- JPA's `@EntityScan` and `@EnableJpaRepositories` don't automatically include library packages

**Impact:**
- `BeanCreationException` for missing repositories
- `UnknownEntityException` for unmapped entities
- Confusing errors that don't indicate the actual fix

### 2. Hard Dependencies on Optional Modules

**Current Behavior:**
```java
// This FAILS if RAGProvider is not configured
@Component
@RequiredArgsConstructor
public class SmartSuggestionsStep {
    private final RAGProvider ragProvider;  // NoSuchBeanDefinitionException!
}
```

**Root Cause:**
- Constructor injection requires all dependencies at instantiation time
- No conditional loading based on module availability
- Missing `@ConditionalOnBean` guards

### 3. Provider Configuration Confusion

**Current Behavior:**
```yaml
# User doesn't know which providers are available or required
ai:
  providers:
    # What goes here? What's required vs optional?
```

**Root Cause:**
- No validation of required configuration
- Silent fallbacks that may not work
- No documentation of provider requirements

---

## Solution Architecture

### Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    User Application                                  │
│  @SpringBootApplication                                             │
│  @EnableAIInfrastructure(behavior = true)  ← Single annotation      │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│              AI Infrastructure Starter (NEW)                         │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  AIInfrastructureStarterAutoConfiguration                     │  │
│  │  - Registers core entities automatically                      │  │
│  │  - Pulls sensible default dependencies                        │  │
│  │  - Validates configuration on startup                         │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                                │
                ┌───────────────┼───────────────┐
                ▼               ▼               ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│   Core Module    │ │  Behavior Module │ │    RAG Module    │
│                  │ │  (Optional)      │ │   (Optional)     │
│ Auto-registers:  │ │ Conditional on:  │ │ Conditional on:  │
│ - Entities       │ │ ai.behavior.     │ │ ai.rag.enabled   │
│ - Repositories   │ │ enabled=true     │ │                  │
│ - Components     │ │                  │ │                  │
└──────────────────┘ └──────────────────┘ └──────────────────┘
```

### Design Principles

1. **Convention over Configuration** - Sensible defaults that work out of the box
2. **Explicit over Implicit** - Clear opt-in for optional features
3. **Fail Fast with Guidance** - Validate early, provide actionable errors
4. **Backward Compatible** - Existing apps continue to work

---

## Implementation Guide

### Phase 1: Auto-Registration of Entities and Repositories

#### 1.1 Create Entity Registration Post-Processor

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIEntityRegistrationPostProcessor.java`

```java
package com.ai.infrastructure.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/**
 * Automatically registers AI Infrastructure entity packages for JPA scanning.
 *
 * <p>This post-processor runs early in the application context lifecycle,
 * before JPA auto-configuration, ensuring entities are discovered.</p>
 *
 * @since 1.1.0
 */
public class AIEntityRegistrationPostProcessor
        implements BeanFactoryPostProcessor, PriorityOrdered {

    private static final String[] CORE_ENTITY_PACKAGES = {
        "com.ai.infrastructure.entity"
    };

    private static final String[] CORE_REPOSITORY_PACKAGES = {
        "com.ai.infrastructure.repository"
    };

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (beanFactory instanceof BeanDefinitionRegistry registry) {
            // Register entity packages
            EntityScanPackages.register(registry, CORE_ENTITY_PACKAGES);

            // Log registration for debugging
            logRegistration("core entities", CORE_ENTITY_PACKAGES);
        }
    }

    @Override
    public int getOrder() {
        // Run before HibernateJpaAutoConfiguration
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private void logRegistration(String type, String[] packages) {
        // Use System.out for early logging (logger may not be ready)
        if (Boolean.getBoolean("ai.infrastructure.debug")) {
            System.out.println("[AI-Infrastructure] Registered " + type + ": "
                + String.join(", ", packages));
        }
    }
}
```

#### 1.2 Update Core Auto-Configuration

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`

```java
@AutoConfiguration
@AutoConfigureBefore({
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AIInfrastructureAutoConfiguration {

    /**
     * Registers core entity packages for JPA scanning.
     * Must be static to be processed early in lifecycle.
     */
    @Bean
    static AIEntityRegistrationPostProcessor aiEntityRegistrationPostProcessor() {
        return new AIEntityRegistrationPostProcessor();
    }

    // ... existing beans ...
}
```

#### 1.3 Create Behavior Module Entity Registrar

**File:** `ai-infrastructure-behavior/src/main/java/com/ai/infrastructure/behavior/config/BehaviorEntityRegistrationPostProcessor.java`

```java
package com.ai.infrastructure.behavior.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/**
 * Registers behavior module entities when the module is enabled.
 */
public class BehaviorEntityRegistrationPostProcessor
        implements BeanFactoryPostProcessor, PriorityOrdered {

    private static final String[] BEHAVIOR_ENTITY_PACKAGES = {
        "com.ai.infrastructure.behavior.entity"
    };

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (beanFactory instanceof BeanDefinitionRegistry registry) {
            EntityScanPackages.register(registry, BEHAVIOR_ENTITY_PACKAGES);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 11;
    }
}
```

**Update BehaviorAIAutoConfiguration:**

```java
@AutoConfiguration
@AutoConfigureBefore(HibernateJpaAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ai.behavior", name = "enabled", havingValue = "true")
public class BehaviorAIAutoConfiguration {

    @Bean
    static BehaviorEntityRegistrationPostProcessor behaviorEntityRegistrar() {
        return new BehaviorEntityRegistrationPostProcessor();
    }

    // ... existing beans ...
}
```

---

### Phase 2: Custom Enable Annotation

#### 2.1 Create the Enable Annotation

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/annotation/EnableAIInfrastructure.java`

```java
package com.ai.infrastructure.annotation;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Enables AI Infrastructure in a Spring Boot application.
 *
 * <p>This annotation provides a convenient way to configure AI Infrastructure
 * with sensible defaults. It automatically:</p>
 * <ul>
 *   <li>Scans for AI Infrastructure components</li>
 *   <li>Registers entity packages for JPA</li>
 *   <li>Enables repository scanning</li>
 *   <li>Configures optional modules based on flags</li>
 * </ul>
 *
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableAIInfrastructure
 * public class MyApplication { }
 * }</pre>
 *
 * <h3>With Behavior Module:</h3>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableAIInfrastructure(enableBehavior = true)
 * public class MyApplication { }
 * }</pre>
 *
 * <h3>Full Control:</h3>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableAIInfrastructure(
 *     enableBehavior = true,
 *     enableRAG = true,
 *     validateConfiguration = true
 * )
 * public class MyApplication { }
 * }</pre>
 *
 * @since 1.1.0
 * @see AIInfrastructureRegistrar
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AIInfrastructureRegistrar.class)
public @interface EnableAIInfrastructure {

    /**
     * Enable the behavior analytics module.
     * Equivalent to setting {@code ai.behavior.enabled=true}.
     *
     * @return true to enable behavior module
     */
    boolean enableBehavior() default false;

    /**
     * Enable the RAG (Retrieval-Augmented Generation) module.
     * Equivalent to setting {@code ai.infrastructure.rag.enabled=true}.
     *
     * @return true to enable RAG module
     */
    boolean enableRAG() default true;

    /**
     * Enable configuration validation on startup.
     * When enabled, the application will fail fast if required
     * configuration is missing.
     *
     * @return true to validate configuration
     */
    boolean validateConfiguration() default true;

    /**
     * Additional entity packages to scan.
     * Use this to include your application's entity packages.
     *
     * @return array of package names
     */
    String[] entityPackages() default {};

    /**
     * Additional repository packages to scan.
     *
     * @return array of package names
     */
    String[] repositoryPackages() default {};

    /**
     * Additional component packages to scan.
     *
     * @return array of package names
     */
    String[] componentPackages() default {};
}
```

#### 2.2 Create the Import Registrar

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/annotation/AIInfrastructureRegistrar.java`

```java
package com.ai.infrastructure.annotation;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.jpa.repository.config.EnableJpaRepositoriesRegistrar;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Registrar that processes {@link EnableAIInfrastructure} annotation.
 *
 * <p>This registrar:</p>
 * <ul>
 *   <li>Registers entity packages for JPA scanning</li>
 *   <li>Sets environment properties for module enablement</li>
 *   <li>Registers configuration validation if requested</li>
 * </ul>
 */
public class AIInfrastructureRegistrar
        implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private static final String ANNOTATION_NAME = EnableAIInfrastructure.class.getName();

    // Core packages (always included)
    private static final List<String> CORE_ENTITY_PACKAGES = List.of(
        "com.ai.infrastructure.entity"
    );

    private static final List<String> CORE_REPOSITORY_PACKAGES = List.of(
        "com.ai.infrastructure.repository"
    );

    private static final List<String> CORE_COMPONENT_PACKAGES = List.of(
        "com.ai.infrastructure"
    );

    // Behavior module packages
    private static final List<String> BEHAVIOR_ENTITY_PACKAGES = List.of(
        "com.ai.infrastructure.behavior.entity"
    );

    private static final List<String> BEHAVIOR_REPOSITORY_PACKAGES = List.of(
        "com.ai.infrastructure.behavior.repository"
    );

    private static final List<String> BEHAVIOR_COMPONENT_PACKAGES = List.of(
        "com.ai.infrastructure.behavior"
    );

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata,
                                        BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
            metadata.getAnnotationAttributes(ANNOTATION_NAME, false)
        );

        if (attributes == null) {
            return;
        }

        // Get annotation values
        boolean enableBehavior = attributes.getBoolean("enableBehavior");
        boolean enableRAG = attributes.getBoolean("enableRAG");
        boolean validateConfiguration = attributes.getBoolean("validateConfiguration");
        String[] additionalEntityPackages = attributes.getStringArray("entityPackages");
        String[] additionalRepoPackages = attributes.getStringArray("repositoryPackages");
        String[] additionalComponentPackages = attributes.getStringArray("componentPackages");

        // Get the declaring class package for default scanning
        String declaringPackage = ClassUtils.getPackageName(metadata.getClassName());

        // Build package lists
        List<String> entityPackages = buildEntityPackages(
            enableBehavior, declaringPackage, additionalEntityPackages);
        List<String> repoPackages = buildRepositoryPackages(
            enableBehavior, declaringPackage, additionalRepoPackages);
        List<String> componentPackages = buildComponentPackages(
            enableBehavior, declaringPackage, additionalComponentPackages);

        // Register entity packages
        EntityScanPackages.register(registry, entityPackages);

        // Set environment properties for module enablement
        setEnvironmentProperties(enableBehavior, enableRAG);

        // Register configuration validator if requested
        if (validateConfiguration) {
            registerConfigurationValidator(registry);
        }

        // Log what was registered
        logConfiguration(enableBehavior, enableRAG, entityPackages, repoPackages);
    }

    private List<String> buildEntityPackages(boolean enableBehavior,
                                              String declaringPackage,
                                              String[] additional) {
        List<String> packages = new ArrayList<>(CORE_ENTITY_PACKAGES);

        if (enableBehavior) {
            packages.addAll(BEHAVIOR_ENTITY_PACKAGES);
        }

        // Add declaring package entities (convention: {basePackage}.entity)
        packages.add(declaringPackage + ".entity");

        // Add any additional packages
        if (additional != null) {
            packages.addAll(Arrays.asList(additional));
        }

        return packages;
    }

    private List<String> buildRepositoryPackages(boolean enableBehavior,
                                                  String declaringPackage,
                                                  String[] additional) {
        List<String> packages = new ArrayList<>(CORE_REPOSITORY_PACKAGES);

        if (enableBehavior) {
            packages.addAll(BEHAVIOR_REPOSITORY_PACKAGES);
        }

        packages.add(declaringPackage + ".repository");

        if (additional != null) {
            packages.addAll(Arrays.asList(additional));
        }

        return packages;
    }

    private List<String> buildComponentPackages(boolean enableBehavior,
                                                 String declaringPackage,
                                                 String[] additional) {
        List<String> packages = new ArrayList<>(CORE_COMPONENT_PACKAGES);

        if (enableBehavior) {
            packages.addAll(BEHAVIOR_COMPONENT_PACKAGES);
        }

        packages.add(declaringPackage);

        if (additional != null) {
            packages.addAll(Arrays.asList(additional));
        }

        return packages;
    }

    private void setEnvironmentProperties(boolean enableBehavior, boolean enableRAG) {
        if (environment instanceof ConfigurableEnvironment configEnv) {
            Map<String, Object> properties = new HashMap<>();

            // Only set if not already configured (don't override explicit config)
            if (enableBehavior && !isPropertySet("ai.behavior.enabled")) {
                properties.put("ai.behavior.enabled", "true");
            }

            if (enableRAG && !isPropertySet("ai.infrastructure.rag.enabled")) {
                properties.put("ai.infrastructure.rag.enabled", "true");
            }

            if (!properties.isEmpty()) {
                configEnv.getPropertySources().addFirst(
                    new MapPropertySource("aiInfrastructureDefaults", properties)
                );
            }
        }
    }

    private boolean isPropertySet(String key) {
        String value = environment.getProperty(key);
        return StringUtils.hasText(value);
    }

    private void registerConfigurationValidator(BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition("aiConfigurationValidator")) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                .genericBeanDefinition(AIConfigurationValidator.class);
            registry.registerBeanDefinition("aiConfigurationValidator",
                builder.getBeanDefinition());
        }
    }

    private void logConfiguration(boolean enableBehavior, boolean enableRAG,
                                  List<String> entityPackages,
                                  List<String> repoPackages) {
        if (Boolean.parseBoolean(environment.getProperty("ai.infrastructure.debug", "false"))) {
            System.out.println("=".repeat(60));
            System.out.println("[AI-Infrastructure] Configuration Summary");
            System.out.println("=".repeat(60));
            System.out.println("Behavior Module: " + (enableBehavior ? "ENABLED" : "DISABLED"));
            System.out.println("RAG Module: " + (enableRAG ? "ENABLED" : "DISABLED"));
            System.out.println("Entity Packages: " + entityPackages);
            System.out.println("Repository Packages: " + repoPackages);
            System.out.println("=".repeat(60));
        }
    }
}
```

#### 2.3 Create Configuration Validator

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/annotation/AIConfigurationValidator.java`

```java
package com.ai.infrastructure.annotation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates AI Infrastructure configuration on startup.
 *
 * <p>Provides clear, actionable error messages when required
 * configuration is missing or invalid.</p>
 */
public class AIConfigurationValidator implements InitializingBean {

    private final Environment environment;

    public AIConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check if AI is enabled
        boolean aiEnabled = environment.getProperty("ai.enabled", Boolean.class, true);
        if (!aiEnabled) {
            return; // AI disabled, skip validation
        }

        // Validate embedding provider
        validateEmbeddingProvider(errors, warnings);

        // Validate LLM provider
        validateLLMProvider(errors, warnings);

        // Validate vector database
        validateVectorDatabase(errors, warnings);

        // Validate behavior module if enabled
        if (isModuleEnabled("ai.behavior.enabled")) {
            validateBehaviorModule(errors, warnings);
        }

        // Validate RAG module if enabled
        if (isModuleEnabled("ai.infrastructure.rag.enabled")) {
            validateRAGModule(errors, warnings);
        }

        // Report results
        reportValidationResults(errors, warnings);
    }

    private void validateEmbeddingProvider(List<String> errors, List<String> warnings) {
        String provider = environment.getProperty("ai.providers.embedding-provider");

        if (!StringUtils.hasText(provider)) {
            warnings.add(
                "No embedding provider specified (ai.providers.embedding-provider). " +
                "Defaulting to ONNX. Available providers: openai, cohere, azure, onnx"
            );
            return;
        }

        // Validate provider-specific configuration
        switch (provider.toLowerCase()) {
            case "openai" -> validateOpenAIConfig(errors);
            case "cohere" -> validateCohereConfig(errors);
            case "azure" -> validateAzureConfig(errors);
            case "onnx" -> {} // No external config required
            default -> warnings.add("Unknown embedding provider: " + provider);
        }
    }

    private void validateLLMProvider(List<String> errors, List<String> warnings) {
        String provider = environment.getProperty("ai.providers.llm-provider");

        if (!StringUtils.hasText(provider)) {
            warnings.add(
                "No LLM provider specified (ai.providers.llm-provider). " +
                "LLM features will be disabled. Available providers: openai, cohere, anthropic, azure, gemini"
            );
        }
    }

    private void validateVectorDatabase(List<String> errors, List<String> warnings) {
        String dbType = environment.getProperty("ai.vector-db.type", "lucene");

        switch (dbType.toLowerCase()) {
            case "lucene", "memory" -> {} // No external config required
            case "pinecone" -> validatePineconeConfig(errors);
            case "qdrant" -> validateQdrantConfig(errors);
            case "weaviate" -> validateWeaviateConfig(errors);
            case "milvus" -> validateMilvusConfig(errors);
            default -> warnings.add("Unknown vector database type: " + dbType);
        }
    }

    private void validateOpenAIConfig(List<String> errors) {
        if (!hasProperty("ai.providers.openai.api-key") &&
            !hasProperty("OPENAI_API_KEY")) {
            errors.add(
                "OpenAI API key not configured. Set either:\n" +
                "  - ai.providers.openai.api-key in application.yml\n" +
                "  - OPENAI_API_KEY environment variable"
            );
        }
    }

    private void validateCohereConfig(List<String> errors) {
        if (!hasProperty("ai.providers.cohere.api-key") &&
            !hasProperty("COHERE_API_KEY")) {
            errors.add(
                "Cohere API key not configured. Set either:\n" +
                "  - ai.providers.cohere.api-key in application.yml\n" +
                "  - COHERE_API_KEY environment variable"
            );
        }
    }

    private void validateAzureConfig(List<String> errors) {
        List<String> missing = new ArrayList<>();

        if (!hasProperty("ai.providers.azure.endpoint")) {
            missing.add("ai.providers.azure.endpoint");
        }
        if (!hasProperty("ai.providers.azure.api-key") &&
            !hasProperty("AZURE_OPENAI_API_KEY")) {
            missing.add("ai.providers.azure.api-key or AZURE_OPENAI_API_KEY");
        }

        if (!missing.isEmpty()) {
            errors.add("Azure OpenAI configuration incomplete. Missing: " +
                String.join(", ", missing));
        }
    }

    private void validatePineconeConfig(List<String> errors) {
        if (!hasProperty("ai.vector-db.pinecone.api-key")) {
            errors.add("Pinecone API key required: ai.vector-db.pinecone.api-key");
        }
        if (!hasProperty("ai.vector-db.pinecone.environment")) {
            errors.add("Pinecone environment required: ai.vector-db.pinecone.environment");
        }
    }

    private void validateQdrantConfig(List<String> errors) {
        if (!hasProperty("ai.vector-db.qdrant.host")) {
            errors.add("Qdrant host required: ai.vector-db.qdrant.host");
        }
    }

    private void validateWeaviateConfig(List<String> errors) {
        if (!hasProperty("ai.vector-db.weaviate.host")) {
            errors.add("Weaviate host required: ai.vector-db.weaviate.host");
        }
    }

    private void validateMilvusConfig(List<String> errors) {
        if (!hasProperty("ai.vector-db.milvus.host")) {
            errors.add("Milvus host required: ai.vector-db.milvus.host");
        }
    }

    private void validateBehaviorModule(List<String> errors, List<String> warnings) {
        // Behavior module validation - check JPA is available
        try {
            Class.forName("jakarta.persistence.EntityManager");
        } catch (ClassNotFoundException e) {
            errors.add(
                "Behavior module requires JPA. Add spring-boot-starter-data-jpa dependency."
            );
        }
    }

    private void validateRAGModule(List<String> errors, List<String> warnings) {
        // RAG requires embedding and vector database
        if (!hasProperty("ai.providers.embedding-provider") &&
            !isClassPresent("com.ai.infrastructure.provider.onnx.ONNXEmbeddingProvider")) {
            warnings.add(
                "RAG module enabled but no embedding provider configured. " +
                "Add ai-infrastructure-onnx-starter for local embeddings."
            );
        }
    }

    private boolean isModuleEnabled(String property) {
        return environment.getProperty(property, Boolean.class, false);
    }

    private boolean hasProperty(String key) {
        return StringUtils.hasText(environment.getProperty(key));
    }

    private boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void reportValidationResults(List<String> errors, List<String> warnings) {
        if (!warnings.isEmpty()) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("[AI-Infrastructure] Configuration Warnings");
            System.out.println("=".repeat(60));
            for (int i = 0; i < warnings.size(); i++) {
                System.out.println((i + 1) + ". " + warnings.get(i));
            }
            System.out.println("=".repeat(60) + "\n");
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("=".repeat(60)).append("\n");
            sb.append("[AI-Infrastructure] Configuration Errors\n");
            sb.append("=".repeat(60)).append("\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append(i + 1).append(". ").append(errors.get(i)).append("\n\n");
            }
            sb.append("=".repeat(60)).append("\n");
            sb.append("Fix the above errors or disable validation with:\n");
            sb.append("@EnableAIInfrastructure(validateConfiguration = false)\n");
            sb.append("=".repeat(60));

            throw new AIConfigurationException(sb.toString());
        }
    }
}
```

#### 2.4 Create Configuration Exception

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/annotation/AIConfigurationException.java`

```java
package com.ai.infrastructure.annotation;

/**
 * Exception thrown when AI Infrastructure configuration is invalid.
 */
public class AIConfigurationException extends RuntimeException {

    public AIConfigurationException(String message) {
        super(message);
    }

    public AIConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Phase 3: Optional Dependency Patterns

#### 3.1 Pattern: ObjectProvider for Optional Dependencies

**Before (Breaks when dependency missing):**
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final RAGProvider ragProvider;  // FAILS if not configured
}
```

**After (Graceful handling):**
```java
@Service
public class MyService {

    private final ObjectProvider<RAGProvider> ragProviderProvider;

    public MyService(ObjectProvider<RAGProvider> ragProviderProvider) {
        this.ragProviderProvider = ragProviderProvider;
    }

    public void doSomething() {
        RAGProvider provider = ragProviderProvider.getIfAvailable();
        if (provider == null) {
            // Handle gracefully - log warning, use fallback, or throw clear error
            log.warn("RAG functionality not available - RAGProvider not configured");
            return;
        }
        provider.performRag(request);
    }
}
```

#### 3.2 Pattern: Conditional Bean Registration

**Example - SmartSuggestionsStep:**
```java
@Component
@ConditionalOnBean(RAGProvider.class)  // Only create if RAG is available
@RequiredArgsConstructor
public class SmartSuggestionsStep implements PipelineStep {

    private final RAGProvider ragProvider;  // Safe - guaranteed to exist

    // ...
}
```

#### 3.3 Pattern: Optional Interface with Default

```java
public interface BehaviorContextProvider {

    Optional<BehaviorContext> getContext(String userId);

    // Default implementation for when behavior module is disabled
    static BehaviorContextProvider noop() {
        return userId -> Optional.empty();
    }
}

// In configuration
@Bean
@ConditionalOnMissingBean
public BehaviorContextProvider defaultBehaviorContextProvider() {
    return BehaviorContextProvider.noop();
}
```

#### 3.4 Apply Pattern to Behavior Services

**File:** Update `BehaviorStorageAdapter.java`

```java
@Component
@ConditionalOnBean(BehaviorInsightsRepository.class)
public class BehaviorStorageAdapter {
    // Only created when JPA repository exists
}
```

**File:** Update `BehaviorContextProviderImpl.java`

```java
@Component
@ConditionalOnProperty(prefix = "ai.behavior", name = "enabled", havingValue = "true")
public class BehaviorContextProviderImpl implements BehaviorContextProvider {

    private final ObjectProvider<BehaviorStorageAdapter> storageAdapterProvider;

    public BehaviorContextProviderImpl(
            ObjectProvider<BehaviorStorageAdapter> storageAdapterProvider) {
        this.storageAdapterProvider = storageAdapterProvider;
    }

    @Override
    public Optional<BehaviorContext> getContext(String userId) {
        BehaviorStorageAdapter adapter = storageAdapterProvider.getIfAvailable();
        if (adapter == null) {
            log.debug("BehaviorStorageAdapter not available for user {}", userId);
            return Optional.empty();
        }
        return adapter.getContext(userId);
    }
}
```

---

### Phase 4: Spring Boot Starter Module

#### 4.1 Create Starter POM

**File:** `ai-infrastructure-spring-boot-starter/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-module</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
    <name>AI Infrastructure Spring Boot Starter</name>
    <description>
        Spring Boot Starter for AI Infrastructure - provides auto-configuration
        with sensible defaults for quick setup.
    </description>

    <dependencies>
        <!-- Core module (required) -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
        </dependency>

        <!-- Default vector database (Lucene - no external service needed) -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-vector-lucene</artifactId>
        </dependency>

        <!-- Default embedding provider (ONNX - local, no API key needed) -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-onnx-starter</artifactId>
        </dependency>

        <!-- RAG module (commonly needed) -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-rag</artifactId>
        </dependency>

        <!-- Web module (REST APIs) -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-web</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

#### 4.2 Create Starter Auto-Configuration

**File:** `ai-infrastructure-spring-boot-starter/src/main/java/com/ai/infrastructure/starter/AIInfrastructureStarterAutoConfiguration.java`

```java
package com.ai.infrastructure.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Auto-configuration for AI Infrastructure Starter.
 *
 * <p>This configuration provides sensible defaults:</p>
 * <ul>
 *   <li>Lucene vector database (local, no setup required)</li>
 *   <li>ONNX embeddings (local, no API key required)</li>
 *   <li>RAG enabled by default</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AIStarterProperties.class)
public class AIInfrastructureStarterAutoConfiguration {

    @Bean
    public StarterConfigurationReport starterConfigurationReport(
            AIStarterProperties properties) {
        return new StarterConfigurationReport(properties);
    }
}
```

#### 4.3 Create Starter Properties

**File:** `ai-infrastructure-spring-boot-starter/src/main/java/com/ai/infrastructure/starter/AIStarterProperties.java`

```java
package com.ai.infrastructure.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.starter")
public class AIStarterProperties {

    /**
     * Print configuration summary on startup.
     */
    private boolean printSummary = true;

    /**
     * Enable quick-start mode with all defaults.
     */
    private boolean quickStart = false;

    // Getters and setters
    public boolean isPrintSummary() { return printSummary; }
    public void setPrintSummary(boolean printSummary) { this.printSummary = printSummary; }
    public boolean isQuickStart() { return quickStart; }
    public void setQuickStart(boolean quickStart) { this.quickStart = quickStart; }
}
```

#### 4.4 Create Default Configuration

**File:** `ai-infrastructure-spring-boot-starter/src/main/resources/META-INF/spring/ai-infrastructure-defaults.yml`

```yaml
# AI Infrastructure Starter Defaults
# These can be overridden in your application.yml

ai:
  enabled: true

  providers:
    # Default to ONNX (local embeddings, no API key needed)
    embedding-provider: onnx

  vector-db:
    # Default to Lucene (local, no external service needed)
    type: lucene
    lucene:
      index-path: ${java.io.tmpdir}/ai-infrastructure/lucene-index

  infrastructure:
    rag:
      enabled: true

  # Indexing defaults
  indexing:
    enabled: true
    strategy: ASYNC
    batch-size: 100

  # Cleanup defaults
  cleanup:
    enabled: true
    retention-days: 90
```

---

## Migration Guide

### For Existing Applications

#### Step 1: Update Dependencies

**Before:**
```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-vector-lucene</artifactId>
</dependency>
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
</dependency>
<!-- ... more dependencies ... -->
```

**After:**
```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
</dependency>
```

#### Step 2: Simplify Main Application Class

**Before:**
```java
@SpringBootApplication
@ComponentScan({"com.myapp", "com.ai.infrastructure"})
@EntityScan({"com.myapp.entity", "com.ai.infrastructure.entity",
             "com.ai.infrastructure.behavior.entity"})
@EnableJpaRepositories({"com.myapp.repository", "com.ai.infrastructure.repository",
                        "com.ai.infrastructure.behavior.repository"})
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

**After:**
```java
@SpringBootApplication
@EnableAIInfrastructure(enableBehavior = true)
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

#### Step 3: Clean Up Configuration (Optional)

Remove redundant configuration that's now defaulted:

```yaml
# Before - verbose
ai:
  enabled: true
  providers:
    embedding-provider: onnx
  vector-db:
    type: lucene
  infrastructure:
    rag:
      enabled: true

# After - minimal (defaults apply)
ai:
  enabled: true
  # Only specify what differs from defaults
```

### Backward Compatibility

Existing applications with explicit annotations will continue to work:

```java
// This still works - explicit config takes precedence
@SpringBootApplication
@ComponentScan({"com.myapp", "com.ai.infrastructure"})
@EntityScan({"com.myapp.entity", "com.ai.infrastructure.entity"})
@EnableJpaRepositories({"com.myapp.repository", "com.ai.infrastructure.repository"})
public class MyApplication { }
```

---

## Best Practices

### 1. Use the Starter for New Projects

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
</dependency>
```

### 2. Enable Validation During Development

```java
@EnableAIInfrastructure(validateConfiguration = true)
```

### 3. Use Debug Mode for Troubleshooting

```yaml
ai:
  infrastructure:
    debug: true  # Prints configuration summary
```

### 4. Follow Optional Dependency Patterns

```java
// Always use ObjectProvider for optional dependencies
private final ObjectProvider<SomeOptionalService> serviceProvider;

public void process() {
    SomeOptionalService service = serviceProvider.getIfAvailable();
    if (service != null) {
        service.doSomething();
    }
}
```

### 5. Document Module Dependencies

When creating custom modules, clearly document requirements:

```java
/**
 * Requires:
 * - ai-infrastructure-core
 * - ai-infrastructure-rag (optional, enables advanced features)
 *
 * Configuration:
 * - ai.mymodule.enabled=true
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ai.mymodule", name = "enabled", havingValue = "true")
public class MyModuleAutoConfiguration { }
```

---

## Appendix

### A. Complete Configuration Reference

```yaml
ai:
  # Master switch
  enabled: true

  # Provider configuration
  providers:
    # Embedding provider: openai, cohere, azure, onnx, gemini
    embedding-provider: onnx

    # LLM provider: openai, cohere, anthropic, azure, gemini
    llm-provider: cohere

    openai:
      enabled: false
      api-key: ${OPENAI_API_KEY:}
      model: gpt-4
      embedding-model: text-embedding-3-small

    cohere:
      enabled: true
      api-key: ${COHERE_API_KEY:}
      model: command-r-plus
      embedding-model: embed-english-v3.0

    anthropic:
      enabled: false
      api-key: ${ANTHROPIC_API_KEY:}
      model: claude-3-opus-20240229

    azure:
      enabled: false
      endpoint: ${AZURE_OPENAI_ENDPOINT:}
      api-key: ${AZURE_OPENAI_API_KEY:}
      deployment-name: gpt-4

    onnx:
      enabled: true
      model-path: classpath:models/all-MiniLM-L6-v2

  # Vector database configuration
  vector-db:
    # Type: lucene, memory, pinecone, qdrant, weaviate, milvus
    type: lucene

    lucene:
      index-path: ./data/lucene-index

    pinecone:
      api-key: ${PINECONE_API_KEY:}
      environment: us-east-1
      index-name: ai-infrastructure

    qdrant:
      host: localhost
      port: 6333

  # Module switches
  behavior:
    enabled: false
    mode: LIGHT  # LIGHT or FULL

  infrastructure:
    rag:
      enabled: true
      advanced:
        enabled: false

    relationship:
      enabled: false

  web:
    enabled: true

  # Indexing configuration
  indexing:
    enabled: true
    strategy: ASYNC  # SYNC, ASYNC, BATCH
    batch-size: 100
    async-threads: 4

  # Cleanup configuration
  cleanup:
    enabled: true
    retention-days: 90
    schedule: "0 0 2 * * ?"  # 2 AM daily

  # Starter options
  starter:
    print-summary: true
    quick-start: false

  # Debug options
  infrastructure:
    debug: false
```

### B. Troubleshooting Guide

| Error | Cause | Solution |
|-------|-------|----------|
| `NoSuchBeanDefinitionException: RAGProvider` | RAG not configured | Add `ai-infrastructure-rag` dependency and enable in config |
| `UnknownEntityException` | Entity not scanned | Use `@EnableAIInfrastructure` or add `@EntityScan` |
| `BeanCreationException: repository` | Repository not scanned | Use `@EnableAIInfrastructure` or add `@EnableJpaRepositories` |
| `AIConfigurationException` | Invalid config | Check the error message for specific fixes |
| Embeddings returning null | No embedding provider | Configure `ai.providers.embedding-provider` |

### C. Module Dependency Matrix

| Module | Required | Optional | Auto-Enabled By Starter |
|--------|----------|----------|------------------------|
| core | - | behavior, rag | Yes |
| rag | core, vector-db | embedding-provider | Yes |
| behavior | core, JPA | rag | No |
| web | core | rag, behavior | Optional |
| vector-lucene | core | - | Yes |
| onnx-starter | core | - | Yes |
| openai-provider | core | - | No |
| cohere-provider | core | - | No |

---

## Changelog

### Version 1.1.0 (Proposed)

- Added `@EnableAIInfrastructure` annotation
- Added automatic entity/repository registration
- Added configuration validation
- Added `ai-infrastructure-spring-boot-starter`
- Fixed optional dependency patterns in behavior module
- Fixed optional dependency patterns in RAG pipeline steps

---

*Document maintained by AI Infrastructure Team*
