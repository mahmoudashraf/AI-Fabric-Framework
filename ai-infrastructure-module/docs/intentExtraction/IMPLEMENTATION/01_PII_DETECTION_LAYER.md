# PII Safeguards — What the Library Provides (and What It Doesn’t)

The core module **does not** ship with a `PIIDetectionService`, config toggles, or automatic redaction. All PII protection must be layered on top of the existing services. This guide explains where to insert those controls so that you can still take advantage of `RAGService` and `AdvancedRAGService`.

---

## Current State

- There is no `ai.pii-detection` property, DTO, or service in the codebase.
- Both indexing (`RAGService#indexContent`) and querying (`RAGService#performRag`, `AdvancedRAGService#performAdvancedRAG`) accept raw strings and forward them to embedding/LLM providers.
- OpenAI/embedding calls are made via `AICoreService`, so any unredacted text passed in will leave your system.

---

## Recommended Architecture

1. **Pre-index filter**  
   Run all documents through a PII scrubber **before** calling `indexContent`. Maintain a clean version for vector storage and a secure store for the raw record if needed.

2. **Pre-query filter**  
   Inspect or transform user input before invoking retrieval. Reject, mask, or hash sensitive fields (email, card numbers, IDs) prior to sending text to OpenAI.

3. **Post-response review**  
   Enforce a final sanitisation pass on the text you send back to the user (especially if you merge model output with internal data).

4. **Audit trail**  
   Log redaction decisions and hold raw data only in systems that meet your compliance requirements (encrypted DB, vaulted storage, etc.).

---

## Example Integration Points

```java
// Pseudocode – place this in your application layer
public RAGResponse answerQuestion(String query, String userId) {
    PiiResult incoming = piiFilter.inspect(query);
    if (incoming.hasHighRiskData()) {
        throw new ForbiddenException("Query contains sensitive data");
    }

    String safeQuery = incoming.getSanitisedText();
    RAGResponse response = ragService.performRag(
        RAGRequest.builder()
            .query(safeQuery)
            .entityType("knowledge_base")
            .limit(5)
            .build());

    String cleanAnswer = piiFilter.cleanseOutgoing(response.getResponse());
    return response.toBuilder().response(cleanAnswer).build();
}
```

Use the same pattern when ingesting content: sanitize before `indexContent`.

---

## Building Blocks You Can Use

- **Regex / rule-based filters** for obvious patterns (card numbers, SSN, phone, email).
- **Open-source detectors** such as Microsoft Presidio or AWS Comprehend if you need entity recognition.
- **Hashing/tokenisation** to preserve join keys without storing the raw value in the vector index.
- **Encryption** for any raw payloads you must keep (store outside of Lucene/Pinecone).

---

## Operational Checklist

- [ ] Inventory which fields are considered PII for your use case.
- [ ] Decide whether to block, redact, or tokenize each field.
- [ ] Ensure logs, traces, and prompts do not capture raw PII.
- [ ] Write unit/integration tests that confirm a masked payload is passed to `AICoreService`.
- [ ] Document escalation paths for false positives/negatives in your detection layer.

---

## Summary

The library leaves PII handling entirely to the host application. Treat `RAGService` and `AICoreService` as downstream dependencies that must receive already-sanitised inputs, and cleanse any outputs that might surface sensitive data back to users. This gives you full control over compliance without needing to fork the core module.
