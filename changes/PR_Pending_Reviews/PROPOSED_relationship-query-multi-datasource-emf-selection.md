# Proposed Change: Relationship-Query Multi-Datasource EntityManagerFactory Selection

## Problem
The relationship-query module needs an `EntityManager` to:
- build/inspect relationship schema metadata
- execute JPQL-based traversal (JPA traversal service)

In Spring Boot apps with **multiple datasources**, there can be **multiple** `EntityManagerFactory` beans (and thus multiple `EntityManager`s). In that case, “pick whatever Spring injects” is ambiguous and can lead to:
- wiring the wrong persistence unit (missing entity mappings, wrong schema)
- runtime failures (JPQL errors, table not found)
- or silent non-activation if we refuse to guess

## Current Behavior (as of PR #117)
`RelationshipQueryConfiguration` uses `@ConditionalOnSingleCandidate(EntityManagerFactory.class)` for JPA-dependent beans.

Effect:
- ✅ works reliably when there is a single unambiguous `EntityManagerFactory`
- ✅ avoids silently wiring the wrong datasource
- ❌ relationship-query JPA features don’t auto-configure when the host app has multiple EMFs

## Goal
Add **explicit selection** of the `EntityManagerFactory` to use when multiple are present, while keeping the default behavior safe and simple for the common case.

## Non-Goals
- Automatically “guessing” the correct datasource in multi-EMF apps without explicit configuration.
- Providing cross-EMF traversal in a single query (out of scope).

## Proposed API
### New property (RelationshipQueryProperties)
Add:

```yaml
ai:
  relationship-query:
    entity-manager-factory-bean-name: primaryEntityManagerFactory
```

- Type: `String`
- Default: empty/`null`
- Semantics:
  - If set: relationship-query uses `ApplicationContext.getBean(beanName, EntityManagerFactory.class)`
  - If not set: keep current “single candidate only” behavior (safe default)

### Optional companion property (future)
If needed later:

```yaml
ai:
  relationship-query:
    entity-manager-bean-name: primaryEntityManager
```

But the preferred approach is selecting the EMF and using `SharedEntityManagerCreator`.

## Wiring Logic
### When `entity-manager-factory-bean-name` is set
1. Resolve `EntityManagerFactory` by name from the Spring context.
2. Create `EntityManager` using `SharedEntityManagerCreator.createSharedEntityManager(emf)`.
3. Wire:
   - `RelationshipSchemaProvider`
   - `JpaRelationshipTraversalService`

### When property is NOT set
Retain current behavior:
- `@ConditionalOnSingleCandidate(EntityManagerFactory.class)`
- Spring injects the single candidate and we create a shared EM

## Error Handling
When the bean name is provided but cannot be resolved:
- Fail fast with a clear message:
  - `IllegalStateException: No EntityManagerFactory bean named 'X' found (available: [...])`

This avoids “silent partial enablement” which is hard to debug.

## Compatibility
### Backward compatible
- Apps with a single datasource: no changes required.
- Apps with multiple datasources: can explicitly opt-in to enable relationship-query JPA traversal against a chosen persistence unit.

### No behavioral change by default
If the new property is not used, behavior remains as it is now.

## Implementation Sketch
1. Extend `RelationshipQueryProperties` with `entityManagerFactoryBeanName`.
2. Update `RelationshipQueryConfiguration`:
   - Add a new bean method branch that activates when property is set, and resolves EMF via `ApplicationContext`.
   - Keep the existing `@ConditionalOnSingleCandidate` methods for the default path.
3. Update docs:
   - Add a short “Multi-datasource” section with a copy/paste configuration snippet.

## Tests
Add a focused test in `ai-infrastructure-relationship-query` (unit or slice):
- Create a Spring context with:
  - two `EntityManagerFactory` mocks (or two in-memory persistence units if feasible)
  - the property set to one bean name
- Assert the module wires the schema provider / traversal service using the selected EMF.

## Why This Design
- Keeps the common case simple (zero config).
- Prevents accidental wrong-datasource wiring.
- Gives multi-datasource apps an explicit, deterministic configuration path.

