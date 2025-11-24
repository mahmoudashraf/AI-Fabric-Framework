# Custom Relationship Mapping

Use this guide when auto-discovery does not capture your intended entity graph.

## 1. Register Entity Types Manually
```java
@Configuration
class RelationshipMappingConfig {
    @Bean
    CommandLineRunner relationshipMappingInitializer(EntityRelationshipMapper mapper) {
        return args -> {
            mapper.registerEntityType("support-ticket", SupportTicketEntity.class);
            mapper.registerEntityType("agent", SupportAgentEntity.class);
        };
    }
}
```
`registerEntityType` normalizes the type key (lowercase, hyphenated). Trying to register the same type with a different class will throw to guard against mismatches.

## 2. Register Relationships
```java
mapper.registerRelationship(
    "support-ticket",
    "agent",
    "assignee",
    RelationshipDirection.FORWARD,
    false
);
```
Parameters:
- `fromEntityType` — logical type (normalized automatically)
- `toEntityType`
- `fieldName` — JPA association name on the source entity
- `direction` — `FORWARD` (default) or `REVERSE`
- `optional` — controls `LEFT JOIN` vs `INNER JOIN`

## 3. Handling Multiple Associations to the Same Type
Use distinct logical to-entity names when needed (see the financial fraud test for `destination-account` vs `origin-account`). Map them to the same class:
```java
mapper.registerEntityType("destination-account", AccountEntity.class);
mapper.registerEntityType("origin-account", AccountEntity.class);
```

## 4. Metadata-Only Entities
If an entity does not exist in JPA but you still want to expose it to the planner (e.g., external knowledge graph), pass the fully-qualified class name:
```java
mapper.registerEntityType("legacy-order", "com.example.LegacyOrderProjection");
```

## 5. Refreshing the Schema
After manual registrations, call:
```java
relationshipSchemaProvider.refreshSchema();
```
This rebuilds the schema cache so the planner sees the updated graph.

## 6. Validation
`RelationshipQueryValidator` checks that both entity types referenced in a plan exist in the mapper. Ensure your manual mappings run before the module starts serving queries (e.g., via `ApplicationRunner`).
