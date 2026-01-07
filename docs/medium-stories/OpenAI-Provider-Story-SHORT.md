# 🤖 OpenAI Provider: When You Need the Best LLM, Just Add a Dependency

*How we built a production-ready OpenAI provider that plugs into the framework—zero code changes, just configuration*

🚧 **Under active development | Q1 2026 release | Production-tested with GPT-4, GPT-3.5, embeddings**

---

## The Problem: OpenAI Integration is Hard

**Traditional approach:**

```java
// Manual OpenAI integration
RestTemplate restTemplate = new RestTemplate();
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + apiKey);
headers.setContentType(MediaType.APPLICATION_JSON);

Map<String, Object> request = new HashMap<>();
request.put("model", "gpt-4");
request.put("messages", List.of(
    Map.of("role", "user", "content", prompt)
));

HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
ResponseEntity<Map> response = restTemplate.postForEntity(
    "https://api.openai.com/v1/chat/completions",
    entity,
    Map.class
);

// Parse response...
// Handle errors...
// Retry logic...
// Rate limiting...
// Metrics...
```

**Problems:**
- ❌ 50+ lines of boilerplate
- ❌ Error handling scattered
- ❌ No retry logic
- ❌ No rate limiting
- ❌ No metrics
- ❌ Hard to test
- ❌ Hard to swap providers

**Every developer's integration nightmare.**

---

## Our Approach: Just Add a Dependency

**Add dependency. Configure. Use. That's it.**

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-provider-openai</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Configure:**

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: openai  # Optional
    openai:
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

public String generateContent(String prompt) {
    AIGenerationResponse response = coreService.generateContent(
        AIGenerationRequest.builder()
            .prompt(prompt)
            .build()
    );
    return response.getContent();
}
```

**Result:** Zero boilerplate. Production-ready. Swappable providers.

---

## What It Provides

### 1. LLM Generation (Chat Completions)

**From OpenAIProvider.java (line 69-177):**

```java
@Override
public AIGenerationResponse generateContent(AIGenerationRequest request) {
    String url = "https://api.openai.com/v1/chat/completions";
    
    // Build messages with system and user roles
    List<Map<String, String>> messages = new ArrayList<>();
    if (request.getSystemPrompt() != null) {
        messages.add(Map.of("role", "system", 
                           "content", request.getSystemPrompt()));
    }
    messages.add(Map.of("role", "user", 
                       "content", request.getPrompt()));
    
    // Call OpenAI API
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("model", request.getModel() != null ? 
                   request.getModel() : config.getDefaultModel());
    requestBody.put("messages", messages);
    requestBody.put("temperature", request.getTemperature());
    requestBody.put("max_tokens", request.getMaxTokens());
    
    ResponseEntity<Map> response = restTemplate.exchange(
        url, HttpMethod.POST, entity, Map.class);
    
    // Parse and return
    return AIGenerationResponse.builder()
        .content(extractContent(response))
        .model(extractModel(response))
        .usage(extractUsage(response))
        .build();
}
```

**Supports:**
- ✅ GPT-4, GPT-4o, GPT-3.5-turbo
- ✅ System prompts
- ✅ Temperature control
- ✅ Max tokens
- ✅ Usage tracking

---

### 2. Embedding Generation

**From OpenAIEmbeddingProvider.java (line 96-131):**

```java
@Override
public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
    EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
        .model(request.getModel() != null ? 
              request.getModel() : config.getOpenai().getEmbeddingModel())
        .input(List.of(request.getText()))
        .build();
    
    EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
    
    return AIEmbeddingResponse.builder()
        .embedding(result.getData().get(0).getEmbedding())
        .model(config.getOpenai().getEmbeddingModel())
        .dimensions(embedding.size())
        .build();
}
```

**Supports:**
- ✅ text-embedding-3-small (1536 dims)
- ✅ text-embedding-3-large (3072 dims)
- ✅ text-embedding-ada-002 (1536 dims)
- ✅ Batch embeddings

---

## The Complete Flow

```
User: "Generate a product description"
    ↓
┌──────────────────────────────────────────┐
│ YOUR CODE                                │
│ coreService.generateContent(request)     │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ AI CORE SERVICE                          │
│ Routes to configured provider            │
│ (OpenAI, Anthropic, Azure, etc.)         │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ OPENAI PROVIDER                          │
│ OpenAIProvider.generateContent()         │
│ ════════════════════════════════════════│
│ 1. Build request                         │
│ 2. Add system prompt (if provided)      │
│ 3. Add user prompt                       │
│ 4. Set model, temperature, max_tokens    │
│ 5. Call OpenAI API                       │
│ 6. Parse response                        │
│ 7. Track metrics                         │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ OPENAI API                                │
│ POST /v1/chat/completions                 │
│ {                                        │
│   "model": "gpt-4o",                     │
│   "messages": [                          │
│     {"role": "system", "content": "..."},│
│     {"role": "user", "content": "..."}   │
│   ],                                     │
│   "temperature": 0.7,                    │
│   "max_tokens": 1000                     │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ RESPONSE                                  │
│ {                                        │
│   "choices": [{                          │
│     "message": {                         │
│       "content": "Generated text..."      │
│     }                                    │
│   }],                                    │
│   "usage": {                             │
│     "prompt_tokens": 10,                 │
│     "completion_tokens": 50,              │
│     "total_tokens": 60                   │
│   }                                      │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ RETURN TO YOUR CODE                      │
│ AIGenerationResponse                      │
│ ════════════════════════════════════════│
│ {                                        │
│   content: "Generated text...",          │
│   model: "gpt-4o",                       │
│   usage: {...},                          │
│   processingTimeMs: 1234                 │
│ }                                        │
└──────────────────────────────────────────┘
```

---

## Auto-Configuration

**From OpenAIAutoConfiguration.java (line 22-65):**

```java
@AutoConfiguration
@ConditionalOnClass(OpenAIProvider.class)
public class OpenAIAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(prefix = "ai.providers.openai", 
                          name = "enabled", 
                          havingValue = "true", 
                          matchIfMissing = true)
    public ProviderConfig openAIProviderConfig(AIProviderConfig config) {
        // Auto-configure from application.yml
        return ProviderConfig.builder()
            .providerName("openai")
            .apiKey(config.getOpenai().getApiKey())
            .defaultModel(config.getOpenai().getModel())
            .defaultEmbeddingModel(config.getOpenai().getEmbeddingModel())
            .temperature(config.getOpenai().getTemperature())
            .maxTokens(config.getOpenai().getMaxTokens())
            .build();
    }
    
    @Bean
    @ConditionalOnBean(name = "openAIProviderConfig")
    public OpenAIProvider openAIProvider(ProviderConfig config) {
        return new OpenAIProvider(config, restTemplate);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", 
                          havingValue = "openai")
    public EmbeddingProvider openAIEmbeddingProvider(AIProviderConfig config) {
        return new OpenAIEmbeddingProvider(config);
    }
}
```

**Zero code. Just configuration. Spring Boot auto-discovers.**

---

## Health Checks & Metrics

**From OpenAIProvider.java (line 236-251):**

```java
@Override
public ProviderStatus getStatus() {
    return ProviderStatus.builder()
        .providerName("openai")
        .available(isAvailable())
        .healthy(isHealthy())
        .totalRequests(totalRequests.get())
        .successfulRequests(successfulRequests.get())
        .failedRequests(failedRequests.get())
        .averageResponseTime(averageResponseTime.get())
        .successRate(calculateSuccessRate())
        .lastSuccess(lastSuccess.get())
        .lastError(lastError.get())
        .build();
}
```

**Metrics tracked:**
- ✅ Total requests
- ✅ Success/failure counts
- ✅ Average response time
- ✅ Success rate
- ✅ Last success/error timestamps

**Health check:**

```java
GET /actuator/health/ai

{
  "status": "UP",
  "llmProvider": "openai",
  "embeddingProvider": "openai",
  "openai": {
    "available": true,
    "healthy": true,
    "successRate": 0.99,
    "averageResponseTime": 1234
  }
}
```

---

## Configuration Options

**Complete configuration:**

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: openai
    
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1  # Optional
      
      # LLM Configuration
      model: gpt-4o                        # gpt-4, gpt-4o, gpt-3.5-turbo
      temperature: 0.7                     # 0.0 - 2.0
      max-tokens: 1000                     # Max response length
      timeout: 30                          # Seconds
      
      # Embedding Configuration
      embedding-model: text-embedding-3-small  # text-embedding-3-small/large, ada-002
      
      # Advanced
      priority: 1                          # Provider priority (for fallback)
      max-retries: 3
      retry-delay-ms: 1000
      rate-limit-per-minute: 60
      rate-limit-per-day: 10000
```

---

## Real-World Example: E-Commerce Product Descriptions

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
      max-tokens: 500
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
            "Category: %s. Price: $%.2f",
            product.getName(),
            product.getCategory(),
            product.getPrice()
        );
        
        AIGenerationResponse response = coreService.generateContent(
            AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt("You are a creative marketing writer")
                .temperature(0.8)
                .maxTokens(500)
                .build()
        );
        
        return response.getContent();
    }
}
```

**Result:** High-quality product descriptions. Zero boilerplate. Production-ready.

---

## The Bottom Line

**OpenAI Provider = Production-ready OpenAI integration.**  
**Auto-configuration = Zero code changes.**  
**Swappable = Change provider in config.**

**What you get:**
- 🤖 LLM generation (GPT-4, GPT-3.5)
- 📊 Embedding generation (text-embedding-3-small/large)
- 🔄 Auto-configuration (Spring Boot)
- 📈 Health checks & metrics
- ⚡ Fast (optimized API calls)
- 🛡️ Error handling (retries, fallbacks)
- 🎯 Same interface (swappable providers)

**What you configure:**
- API key
- Model selection
- Temperature, max tokens
- Timeout settings

**Result:** Best-in-class LLM. Zero boilerplate. Production-ready. Swappable.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [OpenAI Provider Guide](link)  
💬 **Community:** [Join discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- [Custom Access Policy: Fail Closed Security](link)
- [PII Detection: Privacy by Default](link)
- **OpenAI Provider: Best-in-Class LLM** (you are here)
- [The Core: Foundation](link)

---

*Built with ❤️ for developers who want production-ready AI, not boilerplate*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your OpenAI use cases
- 🔄 Follow for Q1 2026 launch

**Stop writing boilerplate. Start using GPT-4. Just add a dependency.** 🤖

