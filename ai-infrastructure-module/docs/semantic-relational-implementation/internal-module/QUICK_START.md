# Relationship Query Module — Quick Start

This guide walks through bootstrapping the `ai-infrastructure-relationship-query` module in a Spring Boot application and verifying the end-to-end relationship-aware query flow.

## 1. Prerequisites
- Java 21+
- Maven 3.9+
- Access to OpenAI (or override with test keys)
- Optional: ONNX runtime dependencies already shipped with the repo

## 2. Add the Dependency
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
    <version>${project.version}</version>
</dependency>
```
The module auto-registers via `RelationshipQueryAutoConfiguration`.

## 3. Minimum Configuration
```properties
ai.infrastructure.relationship.enabled=true
ai.infrastructure.relationship.max-traversal-depth=3
ai.providers.llm-provider=openai
ai.providers.embedding-provider=onnx
ai.vector-db.type=lucene
ai.providers.openai.api-key=${OPENAI_API_KEY}
```
See the configuration guide for advanced options.

## 4. Annotate Entities
Mark JPA entities that should participate with `@AICapable(entityType = "document")`. The `EntityRelationshipMapper` auto-discovers these types and uses them for traversal planning.

## 5. Inject the Service
```java
@Autowired
private ReliableRelationshipQueryService relationshipQueryService;

RAGResponse response = relationshipQueryService.execute(
    "Find active contracts for Ada",
    List.of("document")
);
```
`ReliableRelationshipQueryService` orchestrates planner → JPA traversal → metadata/vector fallbacks.

## 6. Run the Verification Suite
From the repo root:
```bash
mvn -pl ai-infrastructure-relationship-query test
```
Key integration suites (`RelationshipQueryIntegrationTest`, use-case tests, security guards) should pass to confirm the environment.

## 7. Observe Logs
Use-case tests emit:
- User query text
- Planner JSON plan
- Generated JPQL
- Result document metadata

These logs ensure transparency for compliance and debugging.

## 8. Next Steps
- Review `CONFIGURATION_GUIDE.md` to tailor properties.
- Study `USE_CASE_EXAMPLES.md` for real-world scenarios.
- Follow `EXTENSION_GUIDE.md` if you need custom planners or traversal strategies.
