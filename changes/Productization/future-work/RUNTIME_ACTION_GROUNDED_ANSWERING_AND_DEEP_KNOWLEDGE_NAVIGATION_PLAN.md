# Runtime Action-Grounded Answering and Deep Knowledge Navigation Plan

Status: planning document (2026-03-31)

This document describes two related runtime capabilities:

- automatically using safe read-only actions to answer user requests
- navigating knowledge across multiple vector spaces and knowledge bases with a deeper reasoning mode

The goal is to improve correctness, usefulness, and confidence of runtime answers.

---

## 1) Executive Summary

The runtime should become better at:

1. recognizing that a user request can be improved by executing a safe read-only action
2. using the result of that action in the final answer
3. searching across multiple knowledge sources with a deeper multi-step reasoning mode when the question requires it

This should let the runtime answer questions such as:

- “How many orders are pending right now?”
- “What products are low stock and also have negative reviews?”
- “What does policy say about returns for items already delivered?”
- “Compare what our knowledge base says with what the order API currently shows.”

---

## 2) Product Goal

The platform should support an AI runtime that can:

- ground answers in live read-only action results
- ground answers in documents from one or more vector spaces
- decide when deeper reasoning is needed
- provide higher-confidence answers with clearer evidence

Target outcomes:

- fewer hallucinated factual answers
- better operational Q&A
- better knowledge navigation
- better cross-source answers

---

## 3) Read-Only Action Grounding

### 3.1 Problem

Today, the runtime can execute actions, but it does not always treat useful read-only actions as part of the answering strategy for normal user questions.

Examples:

- “What is the status of order 123?”
- “How many products are in stock?”
- “Show recent reviews for this item.”

These are often better answered by:

- selecting a read-only action
- executing it
- then using the returned data to answer naturally

### 3.2 Goal

The runtime should recognize when read-only actions are:

- safe
- relevant
- useful for grounding the answer

and automatically use them in the answering flow.

---

## 4) Deep Knowledge Navigation

### 4.1 Problem

Single-pass retrieval is not always enough.

Some questions require:

- planning across multiple vector spaces
- checking multiple knowledge bases
- comparing retrieved evidence
- iterative refinement of the query

### 4.2 Goal

The runtime should support a deeper reasoning mode that:

- searches across relevant spaces
- validates evidence
- synthesizes a better final answer

This should especially help with:

- policy questions
- cross-domain comparisons
- troubleshooting questions
- knowledge conflicts

---

## 5) Recommended Product Model

### 5.1 New action metadata

Recommended action metadata additions:

- `readOnly: true|false`
- `answerGroundingEligible: true|false`
- `sideEffectLevel: NONE|LOW|HIGH`
- `answerSummaryHint`
- `resultSchema`

This lets the runtime distinguish:

- live factual lookups
- non-destructive read APIs
- mutating or risky operations

### 5.2 New retrieval metadata

Recommended vector space / knowledge base metadata:

- `domain`
- `trustLevel`
- `preferredForQuestions`
- `crossSpaceNavigationAllowed`
- `deepThinkingPriority`

---

## 6) Runtime Orchestration Direction

### 6.1 Read-only action answering flow

Recommended flow:

1. interpret user request
2. detect candidate read-only actions
3. decide whether action grounding improves correctness
4. execute read-only action
5. normalize result
6. answer user with grounded result

### 6.2 Deep knowledge flow

Recommended flow:

1. classify question complexity
2. rank relevant vector spaces / knowledge bases
3. retrieve evidence
4. refine search if needed
5. compare or reconcile evidence
6. produce final answer with citations

### 6.3 Combined flow

For some requests, the runtime should combine both:

- live read-only action result
- retrieved policy/document context

Example:

- live order status from action
- return eligibility from policy knowledge base

---

## 7) Safety Model

### 7.1 Only safe actions

Automatic action-grounded answering should apply only to actions that are:

- explicitly marked read-only
- explicitly marked eligible for answer grounding

### 7.2 No silent mutation

Mutating actions should never be auto-executed just because a user asked a question.

### 7.3 Budget controls

Deep thinking mode should respect:

- max action count
- max retrieval rounds
- max vector spaces searched
- latency budget

---

## 8) Configurability

Recommended deployment-level config:

```yaml
ai:
  answer-grounding:
    read-only-actions:
      enabled: true
      max-actions-per-turn: 2
      require-explicit-read-only-flag: true
  knowledge-navigation:
    deep-thinking:
      enabled: true
      max-retrieval-rounds: 3
      max-vector-spaces: 4
      require-citations: true
```

Recommended modes:

- `OFF`
- `AUTO`
- `AGGRESSIVE`

---

## 9) Runtime Changes

### 9.1 Action selection improvements

Enhance orchestration so it can:

- detect fact-seeking requests
- prefer eligible read-only actions
- use action result payload as answer evidence

### 9.2 Result normalization

Add a normalization layer that converts action results into:

- answerable facts
- structured summaries
- citation-like evidence blocks

### 9.3 Deep thinking planner

Add a planning step that can:

- choose vector spaces
- sequence retrieval
- decide when more evidence is required
- stop when confidence is sufficient

---

## 10) UX and Observability

### 10.1 Traceability

The platform should show when the runtime used:

- read-only action grounding
- deep knowledge navigation
- both

### 10.2 Citations and evidence

Answers should surface:

- action used
- source records/documents
- vector spaces consulted

### 10.3 Test tools

Add testing support for:

- “force read-only action grounding”
- “force deep thinking mode”
- compare normal vs deep-thinking answer

---

## 11) Backend and Config Changes

### 11.1 Actions schema

Extend action config/schema to include:

- read-only flags
- answer-grounding hints
- result summarization hints

### 11.2 Entity / knowledge metadata

Extend knowledge configuration with:

- vector space role
- retrieval priority
- cross-space traversal policy

### 11.3 Verification

Add verification cases for:

- correct action chosen for read-only question
- no mutation on factual questions
- multi-space retrieval correctness
- citation completeness

---

## 12) Recommended Delivery Phases

### Phase 1

- read-only action metadata
- safe action-grounded answering
- trace visibility in diagnostics/test tools

### Phase 2

- retrieval planning across multiple vector spaces
- basic deep-thinking mode
- answer citations and evidence reporting

### Phase 3

- combined action + retrieval reasoning
- better confidence heuristics
- richer test console controls

### Phase 4

- advanced multi-hop reasoning
- conflict reconciliation across sources
- policy-driven evidence prioritization

---

## 13) Recommendation

The right evolution is:

- let the runtime use safe live data through read-only actions
- let it search deeper across knowledge when simple retrieval is insufficient
- make both behaviors configurable, traceable, and safe

That gives the runtime a stronger path from “chat and retrieval” to “grounded, useful, and operationally correct answering”.
