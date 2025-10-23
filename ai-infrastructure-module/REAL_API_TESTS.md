# Real API Integration Tests

This document describes the real API integration tests that actually call OpenAI APIs.

## 🚨 Important Notes

- **These tests require a valid OpenAI API key**
- **These tests will make real API calls and may incur costs**
- **These tests are disabled by default** and only run when `OPENAI_API_KEY` is set

## 🧪 Test Coverage

The real API tests (`AIInfrastructureRealAPITest.java`) cover:

### 1. **Real Embedding Generation**
- Tests actual OpenAI embedding API calls
- Validates 1536-dimensional embeddings
- Verifies embedding quality and variation

### 2. **Real AI Content Generation**
- Tests actual OpenAI GPT API calls
- Validates content generation quality
- Tests different prompts and parameters

### 3. **Real Entity Processing**
- Tests complete AI processing pipeline
- Generates real embeddings for test entities
- Saves entities with real AI-generated content

### 4. **Real Search Functionality**
- Tests search with real embeddings
- Validates vector similarity search
- Tests multiple entity types

### 5. **Real AI Analysis**
- Tests AI-powered entity analysis
- Validates content generation for analysis
- Tests different analysis scenarios

### 6. **Real Validation**
- Tests AI-powered entity validation
- Validates validation pipeline
- Tests error handling

## 🚀 Running the Tests

### Prerequisites

1. **Get an OpenAI API Key**
   - Visit https://platform.openai.com/api-keys
   - Create a new API key
   - Copy the key (starts with `sk-`)

2. **Set Environment Variable**
   ```bash
   export OPENAI_API_KEY=your_actual_openai_api_key
   ```

### Method 1: Using the Script

```bash
cd ai-infrastructure-module
./run-real-api-tests.sh
```

### Method 2: Using Maven Directly

```bash
cd ai-infrastructure-module
mvn test -Dtest=AIInfrastructureRealAPITest -Dspring.profiles.active=real-api-test
```

### Method 3: Using IDE

1. Set the `OPENAI_API_KEY` environment variable in your IDE
2. Run the `AIInfrastructureRealAPITest` class
3. Make sure to use the `real-api-test` profile

## 💰 Cost Considerations

- **Embedding Generation**: ~$0.0001 per 1K tokens
- **Content Generation**: ~$0.002 per 1K tokens
- **Estimated cost per test run**: $0.01 - $0.05

## 🔧 Configuration

The tests use the following configuration:

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-3.5-turbo
    embedding-model: text-embedding-ada-002
    max-tokens: 2000
    temperature: 0.7
    timeout: 30s
```

## 📊 Expected Output

When tests pass, you should see:

```
✅ Generated embeddings for: This is a luxury watch with diamond bezel and Swiss movement
✅ First 5 dimensions: [0.1234, -0.5678, 0.9012, -0.3456, 0.7890]
✅ AI Analysis: This luxury Swiss watch features...
✅ Found 3 entities with embeddings
✅ Processed entity with embeddings: [0.1234, -0.5678, 0.9012, -0.3456, 0.7890]
```

## 🐛 Troubleshooting

### Common Issues

1. **"OPENAI_API_KEY not set"**
   - Make sure you've exported the environment variable
   - Check that the key is valid and starts with `sk-`

2. **"API rate limit exceeded"**
   - Wait a few minutes and try again
   - Check your OpenAI account usage limits

3. **"Invalid API key"**
   - Verify the API key is correct
   - Check that the key has proper permissions

4. **"Connection timeout"**
   - Check your internet connection
   - Verify OpenAI API is accessible

### Debug Mode

To see detailed logs:

```bash
mvn test -Dtest=AIInfrastructureRealAPITest -Dspring.profiles.active=real-api-test -Dlogging.level.com.ai.infrastructure=DEBUG
```

## 🔒 Security Notes

- **Never commit API keys to version control**
- **Use environment variables for API keys**
- **Consider using test-specific API keys with limited permissions**
- **Monitor API usage and costs regularly**

## 📈 Performance Expectations

- **Embedding generation**: 1-3 seconds per request
- **Content generation**: 2-5 seconds per request
- **Total test suite**: 30-60 seconds
- **Network dependent**: Times may vary based on connection

## 🎯 Test Validation

The tests validate:

- ✅ Real API connectivity
- ✅ Proper authentication
- ✅ Response quality and format
- ✅ Error handling
- ✅ Data persistence
- ✅ Integration between components
- ✅ Performance under real conditions