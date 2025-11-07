# Smart Suggestions — Building on Top of Existing APIs

The previous document described an automated “Layer 4” that produced next-step recommendations by default. The current codebase does not include that functionality. Instead, you can assemble smart suggestions using the retrieval helpers that already exist.

---

## What’s Available Today

- `AICoreService#generateRecommendations` — vector similarity search that returns related entities for a given context.
- `AdvancedRAGService#performAdvancedRAG` — returns the primary answer plus the ranked documents used to create it.
- `AICoreService#generateContent` — can be prompted to draft a follow-up message based on your own template.

```160:190:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/core/AICoreService.java
public List<Map<String, Object>> generateRecommendations(String entityType, String context, int limit) {
    ...
    AISearchResponse searchResponse = searchService.search(embedding.getEmbedding(), searchRequest);
    ...
    return searchResponse.getResults();
}
```

---

## Recommended Pattern

1. **Handle the primary request**  
   Answer the user with `RAGService` or `AdvancedRAGService`.

2. **Derive context for suggestions**  
   - Use the user’s original query.
   - Optionally enrich with metadata from the returned documents.
   - Optionally add “intent” labels from your classifier.

3. **Fetch related content**  
   Call `generateRecommendations(entityType, context, limit)` to retrieve additional items (articles, FAQs, actions).

4. **Format the suggestions**  
   Decide how results should appear (bullet list, buttons, cards). Include metadata that helps the user act, such as document titles or action IDs.

5. **Optional: summarise suggestions**  
   Feed the recommendation results into `generateContent` to produce natural-language follow-ups (“You might also want to review…”).

---

## Example Implementation

```java
public class SuggestionService {

    private final AICoreService aiCoreService;

    public SuggestionService(AICoreService aiCoreService) {
        this.aiCoreService = aiCoreService;
    }

    public List<Suggestion> suggestNextSteps(String entityType, String userQuery, List<RAGResponse.RAGDocument> supportingDocs) {
        StringBuilder context = new StringBuilder(userQuery);
        supportingDocs.stream()
            .limit(3)
            .forEach(doc -> context.append("\nDoc: ").append(doc.getTitle()).append(" — ").append(doc.getContent()));

        List<Map<String, Object>> related = aiCoreService.generateRecommendations(entityType, context.toString(), 3);

        return related.stream()
            .map(result -> new Suggestion(
                (String) result.getOrDefault("title", "Suggested item"),
                (String) result.getOrDefault("content", ""),
                (String) result.getOrDefault("id", "")))
            .toList();
    }
}
```

---

## UX Considerations

- **Confidence:** Only present a suggestion when the recommendation score exceeds your threshold.
- **Diversity:** Mix suggestion types (articles, actions) so the user has real choices.
- **Tracking:** Capture which suggestions are clicked to refine future recommendations.
- **Fallbacks:** If the recommendation call fails, continue returning the primary answer—treat suggestions as an enhancement, not a dependency.

---

## Checklist

- [ ] Define what “suggestion” means in your product (article, form, action, escalation).
- [ ] Curate or tag documents/actions so that vector similarity produces useful results.
- [ ] Decide on presentation (text links, buttons, UI cards) and required metadata.
- [ ] Log recommendation inputs/outputs for tuning.
- [ ] Add tests to ensure `generateRecommendations` is called with redacted/safe context.

---

## Summary

There is no baked-in smart suggestion layer. Use `generateRecommendations`, the advanced RAG context, and your own prompt templates to deliver proactive guidance that matches your product’s needs.
