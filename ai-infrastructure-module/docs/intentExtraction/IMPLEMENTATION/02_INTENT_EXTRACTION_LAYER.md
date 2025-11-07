# Intent Handling — External to the Library

There is no `Intent`, `MultiIntentResponse`, or `IntentQueryExtractor` class in `ai-infrastructure-core`. The module assumes **you** determine what the user is trying to do, then decide how to call the retrieval services. This document outlines practical patterns for doing that with the primitives that exist today.

---

## What the Library Does Provide

- `RAGService` and `AdvancedRAGService` accept a plain-text query and optional metadata.
- They return ranked documents plus a generated summary (for the advanced variant).
- No action routing or downstream execution is attempted; the response is informational only.

---

## Recommended Intent Workflow

1. **Classify the request**  
   Use your own rules, a lightweight fine-tuned model, or a prompt-based classifier to determine:
   - Is this a retrieval question?
   - Does it require a domain action you own?
   - Is it out of scope / should be escalated?

2. **Route accordingly**
   - **Information requests:** call `RAGService#performRag` or `AdvancedRAGService#performAdvancedRAG`.
   - **Transactional actions:** execute your own service method (optionally using the retrieved context as supporting data).
   - **Out-of-scope:** return a safe fallback answer or hand off to a human.

3. **Compose the response**  
   Blend the retrieval output (if any) with your business response template. Add confirmation text, citations, or follow-up steps as needed.

---

## Simple Prompt-Based Classifier (Example)

```java
public enum IntentType { RETRIEVE_KB, DOMAIN_ACTION, ESCALATE }

public IntentType classify(String userMessage) {
    String prompt =
        "Classify the following message into one of: RETRIEVE_KB, DOMAIN_ACTION, ESCALATE.\n" +
        "Message: \"" + userMessage + "\"\n" +
        "Respond with the label only.";

    String raw = aiCoreService.generateText(prompt).trim();
    return IntentType.valueOf(raw.toUpperCase(Locale.ROOT));
}
```

Once you have the label, plug it into the routing logic described above.

---

## Wiring Retrieval after Classification

```java
public AnswerEnvelope answer(String userMessage, String userId) {
    IntentType intent = classify(userMessage);

    if (intent == IntentType.RETRIEVE_KB) {
        RAGResponse rag = ragService.performRag(
            RAGRequest.builder()
                .query(userMessage)
                .entityType("knowledge_base")
                .limit(5)
                .build());

        return AnswerEnvelope.information(rag.getResponse(), rag.getDocuments());
    }

    if (intent == IntentType.DOMAIN_ACTION) {
        ActionResult result = myDomainService.handle(userMessage, userId);
        return AnswerEnvelope.action(result);
    }

    return AnswerEnvelope.escalate("I cannot help with that request.");
}
```

This keeps the “intent layer” in your application, while relying on the library solely for retrieval.

---

## When You Need Multiple Intents

For compound requests (“Cancel my order and tell me the refund policy”), run the classifier on each clause or call a more sophisticated model that returns a list of intents. Handle the action part first, then run retrieval to supply supporting information.

---

## Checklist

- [ ] Decide on an intent taxonomy (information vs. action vs. escalation, etc.).
- [ ] Implement a classifier (rules, prompt engineering, fine-tune, or third-party service).
- [ ] Cover edge cases with tests: ambiguous wording, unsupported requests, malicious input.
- [ ] Ensure the classifier output drives the correct downstream code path (retrieval or action).
- [ ] Log intent decisions for monitoring and future tuning.

---

## Key Takeaway

`ai-infrastructure-core` expects you to bring your own intent logic. Combine that logic with `RAGService` and `AdvancedRAGService` to build the user experience promised in the earlier documentation without relying on non-existent classes.
