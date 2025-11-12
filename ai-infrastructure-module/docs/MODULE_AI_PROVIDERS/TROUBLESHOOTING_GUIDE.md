# Troubleshooting Guide

## Common Issues and Solutions

### Issue 1: Provider Not Discovered

**Symptom**: 
```
No provider found: openai
Available providers: []
```

**Possible Causes**:
1. Provider module not in dependencies
2. Provider not enabled in configuration
3. `@ConditionalOnClass` condition not met

**Solutions**:

1. **Check Dependencies**
```xml
<!-- Verify provider module is in POM -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-provider-openai</artifactId>
</dependency>
```

2. **Check Configuration**
```yaml
ai:
  providers:
    openai:
      enabled: true  # Must be true
      api-key: ${OPENAI_API_KEY}  # Must be configured
```

3. **Check Classpath**
```bash
# Verify provider SDK is on classpath
mvn dependency:tree | grep openai
```

4. **Check Auto-Configuration**
```java
// Verify @ConditionalOnClass matches SDK class
@ConditionalOnClass(name = "com.theokanning.openai-gpt3-java.service.OpenAiService")
```

### Issue 2: Wrong Provider Selected

**Symptom**: 
```
Using provider: azure instead of openai
```

**Possible Causes**:
1. Configuration not loaded correctly
2. Provider priority higher
3. Fallback activated

**Solutions**:

1. **Check Configuration**
```yaml
ai:
  providers:
    llm-provider: openai  # Explicitly set
    embedding-provider: onnx
```

2. **Check Provider Priority**
```yaml
ai:
  providers:
    openai:
      priority: 100  # Higher priority
    azure:
      priority: 90   # Lower priority
```

3. **Disable Fallback**
```yaml
ai:
  providers:
    enable-fallback: false
```

### Issue 3: Embedding Provider Not Found

**Symptom**:
```
No embedding provider found: onnx
```

**Possible Causes**:
1. Embedding provider module not included
2. Provider doesn't support embeddings
3. Configuration incorrect

**Solutions**:

1. **Check Module**
```xml
<!-- ONNX module for embeddings -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
</dependency>
```

2. **Check Configuration**
```yaml
ai:
  providers:
    embedding-provider: onnx
    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
```

3. **Check Provider Support**
```java
// Some providers don't support embeddings (e.g., Anthropic)
// Use different provider for embeddings
```

### Issue 4: Configuration Not Loading

**Symptom**:
```
Using default values instead of configuration
```

**Possible Causes**:
1. YAML syntax error
2. Property names don't match
3. Profile not active

**Solutions**:

1. **Check YAML Syntax**
```yaml
# Correct
ai:
  providers:
    openai:
      enabled: true

# Incorrect (missing indentation)
ai:
providers:
openai:
enabled: true
```

2. **Check Property Names**
```yaml
# Must match exactly (case-sensitive)
ai:
  providers:
    openai:  # Not "OpenAI" or "openAI"
      enabled: true
```

3. **Check Profile**
```bash
# Verify profile is active
mvn spring-boot:run -Dspring.profiles.active=prod
```

### Issue 5: Bean Creation Failed

**Symptom**:
```
Error creating bean 'openAIProvider'
```

**Possible Causes**:
1. Missing API key
2. Invalid configuration
3. Provider SDK not available

**Solutions**:

1. **Check API Key**
```yaml
ai:
  providers:
    openai:
      api-key: ${OPENAI_API_KEY}  # Must be set
```

```bash
# Verify environment variable
echo $OPENAI_API_KEY
```

2. **Check Configuration Validity**
```java
// ProviderConfig.isValid() checks:
// - providerName not null
// - apiKey not null
// - baseUrl not null
// - enabled = true
```

3. **Check SDK Availability**
```bash
# Verify SDK is on classpath
mvn dependency:tree | grep "openai\|anthropic\|azure"
```

### Issue 6: Performance Issues

**Symptom**:
```
Slow response times
```

**Possible Causes**:
1. Wrong provider selected
2. Network issues
3. Rate limiting

**Solutions**:

1. **Check Provider Selection**
```yaml
# Use local provider for embeddings
ai:
  providers:
    embedding-provider: onnx  # Local, fast
```

2. **Check Network**
```bash
# Test API connectivity
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"
```

3. **Check Rate Limits**
```yaml
ai:
  providers:
    openai:
      rate-limit-per-minute: 60  # Adjust if needed
```

### Issue 7: Module Not Found

**Symptom**:
```
Could not find artifact: ai-infrastructure-provider-openai
```

**Possible Causes**:
1. Module not built
2. Version mismatch
3. Repository not configured

**Solutions**:

1. **Build Module**
```bash
# Build provider module
cd ai-infrastructure-provider-openai
mvn clean install
```

2. **Check Version**
```xml
<!-- Verify version matches -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-provider-openai</artifactId>
    <version>1.0.0</version>  <!-- Match parent version -->
</dependency>
```

3. **Check Repository**
```xml
<!-- If using local repository -->
<repositories>
    <repository>
        <id>local</id>
        <url>file://${project.basedir}/../local-repo</url>
    </repository>
</repositories>
```

## Debugging Tips

### Enable Debug Logging

```yaml
logging:
  level:
    com.ai.infrastructure: DEBUG
    org.springframework.boot.autoconfigure: DEBUG
```

### Check Auto-Configuration

```bash
# Enable auto-configuration report
mvn spring-boot:run -Ddebug=true
```

### Verify Bean Creation

```java
@Autowired
private ApplicationContext context;

@Test
void testBeans() {
    // Check provider beans
    String[] providerBeans = context.getBeanNamesForType(AIProvider.class);
    System.out.println("Providers: " + Arrays.toString(providerBeans));
    
    // Check embedding provider beans
    String[] embeddingBeans = context.getBeanNamesForType(EmbeddingProvider.class);
    System.out.println("Embedding providers: " + Arrays.toString(embeddingBeans));
}
```

### Check Configuration Loading

```java
@Autowired
private AIProviderConfig config;

@Test
void testConfig() {
    System.out.println("LLM Provider: " + config.getLlmProvider());
    System.out.println("Embedding Provider: " + config.getEmbeddingProvider());
    System.out.println("OpenAI Enabled: " + config.getOpenai().getEnabled());
}
```

## Getting Help

If you're still experiencing issues:

1. **Check Logs**: Review application logs for error messages
2. **Verify Configuration**: Compare with examples in documentation
3. **Test Minimal Config**: Start with minimal configuration
4. **Check Versions**: Verify all versions are compatible
5. **Review Migration Guide**: Check migration guide for common issues
6. **Contact Support**: Provide logs and configuration (sanitized)

## Prevention

To avoid common issues:

1. **Use Environment Variables**: Store API keys in environment variables
2. **Validate Configuration**: Test configuration before deployment
3. **Monitor Logs**: Set up log monitoring
4. **Test Fallbacks**: Test fallback scenarios
5. **Document Configuration**: Keep configuration documented
6. **Version Control**: Track configuration changes
