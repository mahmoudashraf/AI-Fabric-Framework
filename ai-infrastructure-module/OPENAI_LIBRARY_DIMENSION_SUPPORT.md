# OpenAI Library Dimension Support

## Library Information

**Library**: `com.theokanning.openai-gpt3-java:service`  
**Version**: `0.18.2`  
**Location**: Used in `OpenAIEmbeddingProvider.java`

## Issue

The OpenAI API supports dimension reduction for `text-embedding-3-small` and `text-embedding-3-large` models via a `dimensions` parameter in the request body. However, the Java library version `0.18.2` does not expose this parameter in the `EmbeddingRequest.Builder` class.

## Current Status

- ✅ **Property Binding**: Fixed - `ai.providers.openai.embedding-dimensions` is now correctly read
- ❌ **Library Support**: The library doesn't have a `dimensions()` method in `EmbeddingRequest.Builder`
- ⚠️ **Workaround Attempted**: Reflection to set field directly (may not work if field doesn't exist)

## Solutions

### Option 1: Upgrade Library (Recommended)
Check if a newer version of `com.theokanning.openai-gpt3-java` supports dimensions:

```xml
<dependency>
    <groupId>com.theokanning.openai-gpt3-java</groupId>
    <artifactId>service</artifactId>
    <version>0.18.3</version> <!-- or newer -->
</dependency>
```

### Option 2: Use Direct HTTP Calls
Bypass the library and make direct HTTP calls to OpenAI API with dimensions parameter:

```java
// Use RestTemplate to call OpenAI API directly
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("model", "text-embedding-3-small");
requestBody.put("input", List.of(text));
requestBody.put("dimensions", 512); // Add dimensions parameter
```

### Option 3: Use Different Library
Switch to a different OpenAI Java client that supports dimensions:
- `com.azure:azure-ai-openai` (Azure OpenAI SDK - also works with OpenAI)
- `io.github.sashirestela:openai` (Another OpenAI Java client)

### Option 4: Truncate Embeddings (Not Recommended)
Receive full 1536 dimensions and truncate to desired size (loses information quality).

## Current Implementation

The code attempts to:
1. Use `dimensions()` method if available (doesn't exist in 0.18.2)
2. Fall back to reflection to set `dimensions` field directly (may not work)
3. Log warning if neither approach works

## Recommendation

**Best approach**: Check if newer version of `theokanning` library supports dimensions, or switch to making direct HTTP calls to OpenAI API with full control over request parameters.
