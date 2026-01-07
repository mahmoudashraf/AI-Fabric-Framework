# Two Annotations That Changed Everything

**Subtitle:** How we killed 400 lines of config and made any entity AI-searchable in 30 seconds

---

Last Tuesday, a junior developer asked me a question that broke my brain.

"Why do I need to configure the same field in *three different places* just to make it searchable?"

He was right. And I had no good answer.

---

## The Config Hell We Built

Here's what adding AI search to an entity looked like in our "before times":

```java
@Entity
@AICapable(
    entityType = "product",
    enableEmbedding = true,
    enableRAG = true,
    indexable = true
)
public class Product {
    
    @AIEmbedding(
        weight = 1.5,
        chunkingStrategy = "sentence",
        maxChunkSize = 500,
        model = "text-embedding-3-small",
        dimensions = 1536,
        normalize = true,
        includeInSimilarity = true
    )
    @AIKnowledge(
        category = "product",
        priority = 8,
        includeInRAG = true,
        semanticType = "DESCRIPTIVE",
        cacheable = true
    )
    private String name;
    
    // ... repeat for every field
}
```

But wait — there's more! You also needed YAML:

```yaml
ai-entities:
  product:
    entity-type: "product"
    auto-embedding: true
    indexable: true
    enable-search: true
    embeddable-fields:
      - name: "name"
        model: "text-embedding-3-small"
        auto-generate: true
    searchable-fields:
      - name: "name"
        include-in-rag: true
        enable-semantic-search: true
        weight: 1.5
```

**Same information. Three places. Zero joy.**

---

## The Question That Started It All

Our junior dev wasn't just confused. He was exposing a design flaw we'd all been too busy to see.

We had annotations with 50+ attributes. Nobody used 90% of them. And the ones people *did* use? They were duplicated in YAML anyway.

So I asked the team: **What do developers actually need to know when they annotate a field?**

The answer was embarrassingly simple.

---

## Two Questions. Two Annotations.

After a week of arguing (and a few whiteboard diagrams that looked like crime scene evidence), we landed on this:

**Question 1:** "Can users FIND this entity by searching for words related to this field?"

**Question 2:** "Does the AI need to KNOW this value when answering questions?"

That's it.

Those two questions became two annotations:

```java
@AISearchable  // Users can FIND by this
@AIContext     // AI will KNOW this
```

No attributes. No configuration. Just markers.

---

## The "After" That Made Me Smile

Here's the same entity, rewritten:

```java
@Entity
@AICapable(entityType = "product")
public class Product {
    
    @AISearchable   // "Find running shoes" → matches
    private String name;
    
    @AISearchable   // "shoes with good arch support" → matches
    private String description;
    
    @AIContext      // AI can answer: "How much?"
    private BigDecimal price;
    
    @AIContext      // AI can answer: "What brand?"
    private String brand;
    
    private String sku;  // Internal. Not in AI. Not annotated.
}
```

No YAML required. Zero configuration. Done.

---

## "But Wait — Where Did All the Config Go?"

Good question. It didn't disappear. It just moved to where it belongs.

**The insight:** Most config values are implementation details that developers shouldn't care about. Embedding models, chunking strategies, vector dimensions — those are infrastructure concerns.

Our framework now provides sensible defaults for everything. And if you *do* need to override something (rare), you use YAML:

```yaml
ai-entities:
  product:
    searchable-fields:
      - name: "name"
        weight: 3.0  # Title matters more
```

**Annotations for WHAT. YAML for HOW (when needed).**

---

## The Mental Model That Clicks

Here's the decision tree we give new developers:

```
Should this field be in the AI system?
       │
       ├── No → Don't annotate
       │
       └── Yes → Can users SEARCH by this field's meaning?
                        │
                        ├── Yes → @AISearchable
                        │
                        └── No → Does AI need to KNOW this value?
                                        │
                                        ├── Yes → @AIContext
                                        │
                                        └── No → Don't annotate
```

Three questions. Two annotations. One cup of coffee.

---

## What Actually Happens Under the Hood

When you save a `Product`, here's the flow:

```
┌─────────────────────────────────────────────────┐
│  Your Entity                                     │
│  @AISearchable name = "Bamboo Toothbrush"       │
│  @AISearchable description = "Eco-friendly..."  │
│  @AIContext price = 29.99                       │
│  @AIContext brand = "EcoLife"                   │
└─────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│  AISearchableEntity (stored in DB)              │
│                                                 │
│  searchableContent: "Bamboo Toothbrush Eco..."  │
│  metadata: {"price": 29.99, "brand": "EcoLife"} │
│  vectorId: "vec-abc-123"                        │
└─────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────┐
│  Vector DB                                      │
│  [0.023, -0.156, 0.891, ...]                    │
└─────────────────────────────────────────────────┘
```

**`@AISearchable` fields** → Concatenated into `searchableContent` → Embedded → Vectorized → Searchable by meaning.

**`@AIContext` fields** → Stored as JSON metadata → Passed to LLM during responses.

That's the whole system.

---

## The Moment It Clicked for Me

A product manager asked: "Can users search for 'eco-friendly dental products'?"

I looked at our Product entity:

```java
@AISearchable
private String description;  // Contains "eco-friendly dental care"
```

"Yes."

"Even though nobody types those exact words?"

"Yes. That's what semantic search means."

He paused. "And the AI knows the price?"

```java
@AIContext
private BigDecimal price;
```

"Yes. It's in the metadata."

"So if someone asks 'What eco-friendly products do you have under $30?'..."

"The AI finds products by meaning, then filters by price, then tells them 'The Bamboo Toothbrush by EcoLife is $29.99.'"

He leaned back. "That's... actually useful."

---

## The Numbers Don't Lie

After the refactor:

| Metric | Before | After |
|--------|--------|-------|
| Lines of annotation code | 847 | 156 |
| YAML config lines | 423 | 0 (optional) |
| Time to add AI to new entity | ~2 hours | ~2 minutes |
| "WTF" moments per sprint | 7 | 0* |

*Okay, 1. Someone still tried to put `@AISearchable` on a `Long` field.

---

## What This Doesn't Solve

Let's be honest:

**It's not magic.** If your field contains garbage, you'll search through garbage. "Product #12847" is a terrible name. The AI can't fix that.

**It's not free.** Embeddings cost money. Vector storage costs money. Every `@AISearchable` field is an embedding call. Don't annotate everything.

**It's not instant.** By default, indexing is async. There's a brief delay between save and searchable. If you need sync, specify it:

```java
@AICapable(
    entityType = "product",
    onCreateStrategy = IndexingStrategy.SYNC
)
```

**It's not a replacement for good data modeling.** If your domain is a mess, AI search won't save you. It'll just help users find the mess faster.

---

## The Philosophy We Landed On

Code should express intent, not implementation.

`@AISearchable` says "users can find this by meaning." It doesn't say "generate a 1536-dimensional vector using text-embedding-3-small with sentence chunking and L2 normalization."

The first is a business decision. The second is an engineering detail.

Annotations handle the first. The framework handles the second.

---

## Try It Yourself

Here's a complete example:

```java
@Entity
@AICapable(entityType = "support-ticket")
public class SupportTicket {
    
    @Id
    private Long id;
    
    @AISearchable   // Find similar issues
    private String subject;
    
    @AISearchable   // Find by problem description
    private String issueDescription;
    
    @AISearchable   // Find by solution
    private String resolution;
    
    @AIContext      // AI knows status
    private String status;
    
    @AIContext      // AI knows priority
    private String priority;
    
    private String customerId;     // Not in AI (privacy)
    private String internalNotes;  // Not in AI (internal)
}
```

Now users can search: "login problems after password reset"

And find tickets about authentication issues, even if nobody typed those exact words.

The AI can respond: "I found 3 similar tickets. The most common resolution was clearing browser cookies. Average resolution time was 15 minutes."

**Two annotations. Zero YAML. Full semantic search.**

---

## The Refactor We Didn't Expect

Here's the wild part.

When we simplified the annotations, we started seeing patterns in our codebase we'd never noticed. Fields that were annotated but never searched. YAML configs that contradicted annotations. Duplicate embeddings burning money.

Simplicity exposed complexity.

Sometimes the best feature isn't what you add. It's what you take away.

---

## What's Next

We're not done. There's still work to do:

- **Relationship search:** Find products by related category meaning
- **Multi-modal:** Images, documents, not just text
- **Fine-tuning:** Custom embedding models per entity type

But the foundation is solid. Two annotations. One mental model. Infinite possibilities.

---

**The junior dev who asked that question?** He shipped a new AI-searchable entity last week. Took him 10 minutes. No config files. No architecture review.

He just asked himself two questions, added two annotations, and moved on.

That's the whole point.

---

*Have a config nightmare story? Found a creative use for @AISearchable? Drop a comment below. I read every one.*

---

**Tags:** Java, AI, Machine Learning, Software Architecture, Clean Code, Spring Boot

**Suggested header image:** A before/after split image - left side shows tangled configuration files, right side shows clean, minimal code with just two annotations


