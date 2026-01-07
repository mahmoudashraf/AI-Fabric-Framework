# 🤖 OpenAI Provider: Production-Ready Integration in One Dependency

> **How we built a production-ready OpenAI provider that plugs into the framework—zero code changes, auto-configuration, health checks, metrics, and swappable providers**  
> *Part of the AI Fabric Framework series — under active development for Q1 2026*

🚧 **Status:** Under active development | Q1 2026 release | Production-tested with GPT-4, GPT-3.5, embeddings | Auto-configuration ready

---

## The Integration Nightmare: 200 Lines of Boilerplate

**Customer support chatbot. Need GPT-4 integration.**

**Traditional approach:**

```java
@Service
public class ChatbotService {
    
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl = "https://api.openai.com/v1";
    
    public ChatbotService(RestTemplate restTemplate, 
                         @Value("${openai.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }
    
    public String generateResponse(String userMessage) {
        try {
            // Build headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);
            
            // Build request
            Map<String, Object> request = new HashMap<>();
            request.put("model", "gpt-4");
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", 
                               "content", "You are a helpful assistant"));
            messages.add(Map.of("role", "user", 
                               "content", userMessage));
            request.put("messages", messages);
            request.put("temperature", 0.7);
            request.put("max_tokens", 1000);
            
            // Make request
            HttpEntity<Map<String, Object>> entity = 
                new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            // Parse response
            Map<String, Object> body = response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = 
                (List<Map<String, Object>>) body.get("choices");
            @SuppressWarnings("unchecked")
            Map<String, String> message = 
                (Map<String, String>) choices.get(0).get("message");
            String content = message.get("content");
            
            return content;
            
        } catch (HttpClientErrorException e) {
            // Handle 4xx errors
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                // Rate limit - retry?
                throw new RateLimitException("OpenAI rate limit exceeded");
            }
            throw new RuntimeException("OpenAI API error: " + e.getMessage(), e);
            
        } catch (HttpServerErrorException e) {
            // Handle 5xx errors
            // Retry logic?
            throw new RuntimeException("OpenAI server error: " + e.getMessage(), e);
            
        } catch (Exception e) {
            // Handle other errors
            throw new RuntimeException("Failed to call OpenAI: " + e.getMessage(), e);
        }
    }
    
    // Need to add:
    // - Retry logic
    // - Rate limiting
    // - Metrics
    // - Health checks
    // - Timeout handling
    // - Error recovery
    // - ... 100 more lines
}
```

**Problems:**
- ❌ 200+ lines of boilerplate
- ❌ Error handling scattered
- ❌ No retry logic
- ❌ No rate limiting
- ❌ No metrics
- ❌ No health checks
- ❌ Hard to test
- ❌ Hard to swap providers
- ❌ Duplicated across services

**Every developer's integration nightmare.**

---

## Our Solution: Just Add a Dependency

**Add dependency. Configure. Use. That's it.**

**From pom.xml:**

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-provider-openai</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Configure (application.yml):**

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: openai  # Optional
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
      embedding-model: text-embedding-3-small
      temperature: 0.7
      max-tokens: 1000
      timeout: 30
```

**Use (same interface as any provider):**

```java
@Autowired
private AICoreService coreService;

public String generateResponse(String userMessage) {
    AIGenerationResponse response = coreService.generateContent(
        AIGenerationRequest.builder()
            .prompt(userMessage)
            .systemPrompt("You are a helpful assistant")
            .temperature(0.7)
            .maxTokens(1000)
            .build()
    );
    return response.getContent();
}
```

**Result:** Zero boilerplate. Production-ready. Swappable providers. Health checks. Metrics. Retry logic. Rate limiting. All included.

---

## Complete Technical Flow

```
┌──────────────────────────────────────────────────────────┐
│  USER REQUEST                                            │
│  POST /api/chat                                          │
│  Body: "What's your return policy?"                     │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  YOUR SERVICE CODE                                        │
│  ChatbotService.generateResponse()                        │
│  ════════════════════════════════════════════════════════│
│  AIGenerationRequest request = AIGenerationRequest       │
│    .builder()                                             │
│    .prompt(userMessage)                                  │
│    .systemPrompt("You are a helpful assistant")          │
│    .temperature(0.7)                                     │
│    .maxTokens(1000)                                      │
│    .build();                                             │
│                                                           │
│  AIGenerationResponse response =                          │
│    coreService.generateContent(request);                  │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  AI CORE SERVICE                                         │
│  AICoreService.generateContent()                          │
│  ════════════════════════════════════════════════════════│
│  1. Get configured provider (OpenAI)                      │
│  2. Check if available                                   │
│  3. Delegate to provider                                  │
│  4. Handle errors (fallback if configured)                │
│  5. Cache response (if enabled)                          │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  OPENAI PROVIDER                                         │
│  OpenAIProvider.generateContent() (line 69-177)          │
│  ════════════════════════════════════════════════════════│
│  1. Track metrics (start timer)                           │
│  2. Build request URL                                     │
│  3. Build headers (Authorization, Content-Type)          │
│  4. Build messages (system + user)                        │
│  5. Build request body (model, messages, params)         │
│  6. Make HTTP request                                     │
│  7. Parse response                                        │
│  8. Update metrics (success/failure, response time)      │
│  9. Return AIGenerationResponse                           │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  OPENAI API REQUEST                                       │
│  POST https://api.openai.com/v1/chat/completions         │
│  ════════════════════════════════════════════════════════│
│  Headers:                                                 │
│  {                                                        │
│    "Authorization": "Bearer sk-...",                      │
│    "Content-Type": "application/json"                     │
│  }                                                        │
│                                                           │
│  Body:                                                    │
│  {                                                        │
│    "model": "gpt-4o",                                    │
│    "messages": [                                         │
│      {                                                    │
│        "role": "system",                                 │
│        "content": "You are a helpful assistant"           │
│      },                                                  │
│      {                                                    │
│        "role": "user",                                   │
│        "content": "What's your return policy?"           │
│      }                                                   │
│    ],                                                    │
│    "temperature": 0.7,                                   │
│    "max_tokens": 1000,                                   │
│    "top_p": 0.1                                          │
│  }                                                       │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  OPENAI API RESPONSE                                      │
│  ════════════════════════════════════════════════════════│
│  {                                                        │
│    "id": "chatcmpl-abc123",                              │
│    "object": "chat.completion",                          │
│    "created": 1677652288,                                │
│    "model": "gpt-4o",                                    │
│    "choices": [{                                         │
│      "index": 0,                                         │
│      "message": {                                        │
│        "role": "assistant",                              │
│        "content": "Our return policy allows..."          │
│      },                                                  │
│      "finish_reason": "stop"                             │
│    }],                                                   │
│    "usage": {                                            │
│      "prompt_tokens": 15,                                │
│      "completion_tokens": 50,                             │
│      "total_tokens": 65                                  │
│    }                                                     │
│  }                                                       │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  PARSE RESPONSE                                           │
│  OpenAIProvider (line 136-165)                            │
│  ════════════════════════════════════════════════════════│
│  Map<String, Object> responseBody = response.getBody();  │
│  List<Map<String, Object>> choices =                     │
│    (List<Map<String, Object>>) responseBody.get("choices");│
│  Map<String, String> message =                           │
│    (Map<String, String>) choices.get(0).get("message"); │
│  String content = message.get("content");                 │
│                                                           │
│  Map<String, Object> usage =                              │
│    createUsageFromResponse(responseBody);                 │
│                                                           │
│  long responseTime = System.currentTimeMillis() - startTime;│
│  updateMetrics(true, responseTime);                       │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  RETURN TO YOUR CODE                                      │
│  AIGenerationResponse                                      │
│  ════════════════════════════════════════════════════════│
│  {                                                        │
│    content: "Our return policy allows...",                │
│    model: "gpt-4o",                                      │
│    usage: {                                              │
│      prompt_tokens: 15,                                  │
│      completion_tokens: 50,                               │
│      total_tokens: 65                                    │
│    },                                                    │
│    processingTimeMs: 1234,                               │
│    requestId: "req-abc123"                               │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
```

---

## LLM Generation Implementation

**From OpenAIProvider.java (line 69-177):**

### Complete Implementation:

```java
@Override
public AIGenerationResponse generateContent(AIGenerationRequest request) {
    long startTime = System.currentTimeMillis();
    totalRequests.incrementAndGet();
    
    try {
        log.debug("Generating content with OpenAI: model={}, prompt={}", 
                 request.getModel(), 
                 request.getPrompt().substring(0, 
                     Math.min(100, request.getPrompt().length())));
        
        // Build API URL
        String url = OPENAI_BASE_URL + "/chat/completions";
        
        // Build headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + config.getApiKey());
        
        // Build messages with system and user roles
        List<Map<String, String>> messages = new ArrayList<>();
        
        // Add system prompt if provided
        if (request.getSystemPrompt() != null && 
            !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of(
                "role", "system",
                "content", request.getSystemPrompt()
            ));
        }
        
        // Add user prompt
        messages.add(Map.of(
            "role", "user",
            "content", request.getPrompt()
        ));
        
        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", 
            request.getModel() != null ? 
            request.getModel() : config.getDefaultModel());
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 
            request.getMaxTokens() != null ? 
            request.getMaxTokens() : config.getMaxTokens());
        requestBody.put("temperature", 
            request.getTemperature() != null ? 
            request.getTemperature() : config.getTemperature());
        requestBody.put("top_p", 0.1);  // More deterministic
        
        // Make HTTP request
        HttpEntity<Map<String, Object>> entity = 
            new HttpEntity<>(requestBody, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.POST, entity, Map.class);
        
        long responseTime = System.currentTimeMillis() - startTime;
        updateMetrics(true, responseTime);
        
        // Parse response
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = 
            (List<Map<String, Object>>) responseBody.get("choices");
        @SuppressWarnings("unchecked")
        Map<String, String> message = 
            (Map<String, String>) choices.get(0).get("message");
        String content = message.get("content");
        
        log.debug("OpenAI content generation completed in {}ms", responseTime);
        
        // Build response
        return AIGenerationResponse.builder()
            .content(content)
            .model((String) responseBody.get("model"))
            .usage(createUsageFromResponse(responseBody))
            .processingTimeMs(responseTime)
            .requestId(UUID.randomUUID().toString())
            .build();
            
    } catch (Exception e) {
        long responseTime = System.currentTimeMillis() - startTime;
        updateMetrics(false, responseTime);
        
        log.error("OpenAI content generation failed", e);
        lastError.set(LocalDateTime.now());
        lastErrorMessage.set(e.getMessage());
        
        throw new RuntimeException(
            "OpenAI content generation failed: " + e.getMessage(), e);
    }
}
```

**Features:**
- ✅ System prompt support
- ✅ User prompt support
- ✅ Model selection (GPT-4, GPT-3.5, etc.)
- ✅ Temperature control
- ✅ Max tokens limit
- ✅ Usage tracking (prompt/completion/total tokens)
- ✅ Metrics tracking
- ✅ Error handling

---

## Embedding Generation Implementation

**From OpenAIEmbeddingProvider.java (line 96-131):**

### Complete Implementation:

```java
@Override
public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
    try {
        if (!isAvailable()) {
            throw new AIServiceException(
                "OpenAI Embedding Provider is not available");
        }
        
        log.debug("Generating embedding using OpenAI for text: {}", 
                 request.getText());
        
        long startTime = System.currentTimeMillis();
        
        // Build embedding request
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
            .model(request.getModel() != null ? 
                  request.getModel() : 
                  config.getOpenai().getEmbeddingModel())
            .input(List.of(request.getText()))
            .build();
        
        // Call OpenAI API
        EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
        var embedding = result.getData().get(0).getEmbedding();
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        log.debug("Successfully generated OpenAI embedding with {} dimensions in {}ms", 
                 embedding.size(), processingTime);
        
        // Build response
        return AIEmbeddingResponse.builder()
            .embedding(embedding)
            .model(request.getModel() != null ? 
                  request.getModel() : 
                  config.getOpenai().getEmbeddingModel())
            .dimensions(embedding.size())
            .processingTimeMs(processingTime)
            .requestId(UUID.randomUUID().toString())
            .build();
            
    } catch (Exception e) {
        log.error("Error generating OpenAI embedding", e);
        throw new AIServiceException(
            "Failed to generate OpenAI embedding", e);
    }
}
```

**Batch Embeddings (line 134-172):**

```java
@Override
public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
    try {
        if (!isAvailable()) {
            throw new AIServiceException(
                "OpenAI Embedding Provider is not available");
        }
        
        log.debug("Generating {} embeddings using OpenAI", texts.size());
        
        long startTime = System.currentTimeMillis();
        
        // Build batch request
        EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
            .model(config.getOpenai().getEmbeddingModel())
            .input(texts)  // Batch input
            .build();
        
        // Call OpenAI API (batch)
        EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Map results
        List<AIEmbeddingResponse> responses = result.getData().stream()
            .map(data -> AIEmbeddingResponse.builder()
                .embedding(data.getEmbedding())
                .model(config.getOpenai().getEmbeddingModel())
                .dimensions(data.getEmbedding().size())
                .processingTimeMs(processingTime / texts.size())
                .requestId(UUID.randomUUID().toString())
                .build())
            .collect(Collectors.toList());
        
        log.debug("Successfully generated {} OpenAI embeddings in {}ms", 
                 responses.size(), processingTime);
        
        return responses;
            
    } catch (Exception e) {
        log.error("Error generating batch OpenAI embeddings", e);
        throw new AIServiceException(
            "Failed to generate batch OpenAI embeddings", e);
    }
}
```

**Supported Models:**
- ✅ text-embedding-3-small (1536 dimensions)
- ✅ text-embedding-3-large (3072 dimensions)
- ✅ text-embedding-ada-002 (1536 dimensions)

---

## Auto-Configuration

**From OpenAIAutoConfiguration.java (line 22-65):**

### Complete Auto-Configuration:

```java
@AutoConfiguration
@ConditionalOnClass(OpenAIProvider.class)
public class OpenAIAutoConfiguration {
    
    @Bean(name = "openAIProviderConfig")
    @ConditionalOnMissingBean(name = "openAIProviderConfig")
    @ConditionalOnProperty(
        prefix = "ai.providers.openai", 
        name = "enabled", 
        havingValue = "true", 
        matchIfMissing = true
    )
    public ProviderConfig openAIProviderConfig(AIProviderConfig aiProviderConfig) {
        AIProviderConfig.OpenAIConfig openai = aiProviderConfig.getOpenai();
        boolean hasApiKey = openai.getApiKey() != null && 
                           !openai.getApiKey().isBlank();
        
        return ProviderConfig.builder()
            .providerName("openai")
            .apiKey(openai.getApiKey())
            .baseUrl(openai.getBaseUrl())
            .defaultModel(openai.getModel())
            .defaultEmbeddingModel(openai.getEmbeddingModel())
            .maxTokens(openai.getMaxTokens())
            .temperature(openai.getTemperature())
            .timeoutSeconds(openai.getTimeout())
            .maxRetries(3)
            .retryDelayMs(1000L)
            .rateLimitPerMinute(60)
            .rateLimitPerDay(10_000)
            .enabled(openai.isEnabled() && hasApiKey)
            .priority(openai.getPriority())
            .build();
    }
    
    @Bean
    @ConditionalOnBean(name = "openAIProviderConfig")
    public OpenAIProvider openAIProvider(
        @Qualifier("openAIProviderConfig") ProviderConfig providerConfig,
        ObjectProvider<RestTemplate> restTemplateProvider) {
        RestTemplate restTemplate = 
            restTemplateProvider.getIfAvailable(RestTemplate::new);
        return new OpenAIProvider(providerConfig, restTemplate);
    }
    
    @Bean
    @ConditionalOnBean(name = "openAIProviderConfig")
    @ConditionalOnProperty(
        name = "ai.providers.embedding-provider", 
        havingValue = "openai"
    )
    public EmbeddingProvider openAIEmbeddingProvider(
        AIProviderConfig aiProviderConfig) {
        return new OpenAIEmbeddingProvider(aiProviderConfig);
    }
}
```

**Auto-configuration features:**
- ✅ Conditional on classpath (only if dependency present)
- ✅ Conditional on property (enabled by default)
- ✅ Auto-wires configuration from application.yml
- ✅ Creates provider beans automatically
- ✅ Registers as EmbeddingProvider if configured

**Zero code. Just configuration. Spring Boot auto-discovers.**

---

## Health Checks & Metrics

**From OpenAIProvider.java (line 236-310):**

### Status & Health:

```java
@Override
public ProviderStatus getStatus() {
    return ProviderStatus.builder()
        .providerName(getProviderName())
        .available(isAvailable())
        .healthy(isHealthy())
        .lastSuccess(lastSuccess.get())
        .lastError(lastError.get())
        .lastErrorMessage(lastErrorMessage.get())
        .totalRequests(totalRequests.get())
        .successfulRequests(successfulRequests.get())
        .failedRequests(failedRequests.get())
        .averageResponseTime(averageResponseTime.get())
        .successRate(calculateSuccessRate())
        .lastUpdated(LocalDateTime.now())
        .build();
}

private boolean isHealthy() {
    if (!isAvailable()) {
        return false;
    }
    
    // Check if we have recent successful requests
    LocalDateTime recentSuccess = lastSuccess.get();
    if (recentSuccess == null) {
        return false;
    }
    
    // Consider healthy if last success was within last 5 minutes
    return recentSuccess.isAfter(LocalDateTime.now().minusMinutes(5));
}

private double calculateSuccessRate() {
    long total = totalRequests.get();
    if (total == 0) {
        return 0.0;
    }
    return (double) successfulRequests.get() / total;
}

private void updateMetrics(boolean success, long responseTime) {
    if (success) {
        successfulRequests.incrementAndGet();
        lastSuccess.set(LocalDateTime.now());
    } else {
        failedRequests.incrementAndGet();
    }
    
    // Update average response time
    long total = totalRequests.get();
    double currentAvg = averageResponseTime.get();
    double newAvg = ((currentAvg * (total - 1)) + responseTime) / total;
    averageResponseTime.set(newAvg);
    
    log.debug("Updated OpenAI metrics: success={}, responseTime={}ms, successRate={}", 
             success, responseTime, calculateSuccessRate());
}
```

**Metrics tracked:**
- ✅ Total requests
- ✅ Successful requests
- ✅ Failed requests
- ✅ Success rate
- ✅ Average response time
- ✅ Last success timestamp
- ✅ Last error timestamp
- ✅ Last error message

**Health check endpoint:**

```java
GET /actuator/health/ai

{
  "status": "UP",
  "llmProvider": "openai",
  "embeddingProvider": "openai",
  "openai": {
    "available": true,
    "healthy": true,
    "totalRequests": 1250,
    "successfulRequests": 1245,
    "failedRequests": 5,
    "successRate": 0.996,
    "averageResponseTime": 1234.5,
    "lastSuccess": "2025-01-02T10:30:00",
    "lastError": null
  }
}
```

---

## Configuration Reference

**Complete configuration options:**

```yaml
ai:
  providers:
    # Select OpenAI as provider
    llm-provider: openai
    embedding-provider: openai  # Optional
    
    openai:
      # Enable/disable
      enabled: true
      
      # API Configuration
      api-key: ${OPENAI_API_KEY}  # Required
      base-url: https://api.openai.com/v1  # Optional (default)
      
      # LLM Configuration
      model: gpt-4o  # gpt-4, gpt-4o, gpt-3.5-turbo
      temperature: 0.7  # 0.0 - 2.0 (creativity)
      max-tokens: 1000  # Max response length
      timeout: 30  # Seconds
      
      # Embedding Configuration
      embedding-model: text-embedding-3-small
      # Options:
      # - text-embedding-3-small (1536 dims, fast, cheap)
      # - text-embedding-3-large (3072 dims, accurate, expensive)
      # - text-embedding-ada-002 (1536 dims, legacy)
      
      # Advanced Configuration
      priority: 1  # Provider priority (for fallback)
      max-retries: 3
      retry-delay-ms: 1000
      rate-limit-per-minute: 60
      rate-limit-per-day: 10000
```

---

## Real-World Examples

### Example 1: Customer Support Chatbot

**Challenge:** Generate helpful responses using GPT-4.

**Configuration:**

```yaml
ai:
  providers:
    llm-provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
      temperature: 0.7  # Balanced
      max-tokens: 500
```

**Code:**

```java
@Service
public class SupportChatbotService {
    
    @Autowired
    private AICoreService coreService;
    
    public String respondToCustomer(String customerMessage, 
                                    String context) {
        String systemPrompt = String.format(
            "You are a helpful customer support agent. " +
            "Context: %s",
            context
        );
        
        AIGenerationResponse response = coreService.generateContent(
            AIGenerationRequest.builder()
                .prompt(customerMessage)
                .systemPrompt(systemPrompt)
                .temperature(0.7)
                .maxTokens(500)
                .build()
        );
        
        return response.getContent();
    }
}
```

**Result:** High-quality support responses. Zero boilerplate.

---

### Example 2: Product Description Generator

**Challenge:** Generate product descriptions using GPT-4.

**Configuration:**

```yaml
ai:
  providers:
    llm-provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o
      temperature: 0.8  # More creative
      max-tokens: 300
```

**Code:**

```java
@Service
public class ProductDescriptionService {
    
    @Autowired
    private AICoreService coreService;
    
    public String generateDescription(Product product) {
        String prompt = String.format(
            "Write a compelling product description for: %s. " +
            "Category: %s. Price: $%.2f. Features: %s",
            product.getName(),
            product.getCategory(),
            product.getPrice(),
            String.join(", ", product.getFeatures())
        );
        
        AIGenerationResponse response = coreService.generateContent(
            AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a creative marketing writer")
                .temperature(0.8)
                .maxTokens(300)
                .build()
        );
        
        return response.getContent();
    }
}
```

**Result:** High-quality product descriptions. Creative. Consistent.

---

### Example 3: Semantic Search with OpenAI Embeddings

**Challenge:** Use OpenAI embeddings for semantic search.

**Configuration:**

```yaml
ai:
  providers:
    embedding-provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding-model: text-embedding-3-small
```

**Code:**

```java
@Service
public class SemanticSearchService {
    
    @Autowired
    private AIEmbeddingService embeddingService;
    
    @Autowired
    private AISearchService searchService;
    
    public List<Product> searchSimilar(String query) {
        // Generate embedding using OpenAI
        AIEmbeddingResponse embedding = embeddingService.generateEmbedding(
            AIEmbeddingRequest.builder()
                .text(query)
                .build()
        );
        
        // Search using embedding
        AISearchResponse results = searchService.search(
            embedding.getEmbedding(),
            AISearchRequest.builder()
                .query(query)
                .entityType("product")
                .limit(10)
                .build()
        );
        
        return convertToProducts(results);
    }
}
```

**Result:** High-quality semantic search. OpenAI embeddings. Zero boilerplate.

---

## The Bottom Line

**OpenAI Provider = Production-ready OpenAI integration.**  
**Auto-configuration = Zero code changes.**  
**Swappable = Change provider in config.**

**What you get:**
- 🤖 LLM generation (GPT-4, GPT-4o, GPT-3.5-turbo)
- 📊 Embedding generation (text-embedding-3-small/large, ada-002)
- 🔄 Auto-configuration (Spring Boot)
- 📈 Health checks & metrics (production-ready)
- ⚡ Fast (optimized API calls)
- 🛡️ Error handling (retries, fallbacks)
- 🎯 Same interface (swappable providers)
- 📋 Usage tracking (token counts)
- 🔒 Secure (API key management)

**What you configure:**
- API key (environment variable)
- Model selection (GPT-4, GPT-3.5, etc.)
- Temperature, max tokens
- Timeout settings
- Embedding model

**Result:** Best-in-class LLM. Zero boilerplate. Production-ready. Swappable. Health checks. Metrics. All included.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [OpenAI Provider Complete Guide](link)  
💬 **Community:** [Join discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- [Custom Access Policy: Fail Closed Security](link)
- [PII Detection: Privacy by Default](link)
- **OpenAI Provider: Best-in-Class LLM** (you are here)
- [The Core: Foundation](link)
- [Behavior Analytics: Churn Prediction](link)

---

*Built with ❤️ for developers who want production-ready AI, not boilerplate*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your OpenAI use cases
- 🔄 Follow for Q1 2026 launch

**Stop writing boilerplate. Start using GPT-4. Just add a dependency. Production-ready. Swappable. Zero code changes.** 🤖

