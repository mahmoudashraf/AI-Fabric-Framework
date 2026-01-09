# Verify Fix Summary

## Issue Found
The behavior integration tests were failing when `OPENAI_API_KEY` was not set because:
- I changed `openai.enabled: false` → `openai.enabled: true` (default)
- This caused Spring Boot to try initializing OpenAI provider even when not needed
- Without API key, initialization failed and tests errored

## Fix Applied
Changed `application.yml` to default OpenAI to disabled:
```yaml
openai:
  enabled: ${AI_INFRASTRUCTURE_OPENAI_ENABLED:false}  # Changed from true to false
```

## Why This Works
- **Default behavior**: OpenAI is disabled (tests use ONNX by default)
- **When selected**: Scripts pass `-Dai.providers.embedding-provider=openai` which enables it
- **No API key required**: When OpenAI is disabled, no API key is needed for tests that don't use it

## Verification
✅ **Before fix**: Tests failed when OPENAI_API_KEY not set
✅ **After fix**: Tests pass with or without OPENAI_API_KEY
✅ **Full verify**: `mvn verify` passes successfully

## Result
- ✅ Behavior tests pass without requiring OpenAI API key
- ✅ OpenAI provider still works when explicitly selected via system property
- ✅ All modules verify successfully
