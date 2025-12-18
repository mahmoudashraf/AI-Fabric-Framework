# AI Infrastructure Transformation - Architecture Diagram

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    AI Infrastructure Module                     │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ @AICapable      │    │ AICapableAspect │    │ AI Services │  │
│  │ Annotation      │    │ (AOP Processing)│    │             │  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
│           │                       │                       │      │
│           │                       │                       │      │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ AIEntityConfig  │    │ AICapability    │    │ AI Entities │  │
│  │ Loader          │    │ Service         │    │             │  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
│           │                       │                       │      │
│           │                       │                       │      │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ AIEntity        │    │ AISearchable    │    │ Behavior    │  │
│  │ Registry        │    │ Entity          │    │ Entity      │  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                │
┌─────────────────────────────────────────────────────────────────┐
│                      Backend Module                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ User Entity     │    │ Product Entity  │    │ Order Entity│  │
│  │ (Clean)         │    │ (Clean)         │    │ (Clean)     │  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
│           │                       │                       │      │
│           │                       │                       │      │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ UserService     │    │ ProductService  │    │ OrderService│  │
│  │ @AICapable      │    │ @AICapable      │    │ @AICapable  │  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
│           │                       │                       │      │
│           │                       │                       │      │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ AI Entity       │    │ AI Entity       │    │ AI Entity   │  │
│  │ Configuration   │    │ Configuration   │    │ Configuration│  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                                │
                                │
┌─────────────────────────────────────────────────────────────────┐
│                    Configuration Layer                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ ai-entity-      │    │ Searchable      │    │ Embeddable  │  │
│  │ config.yml      │    │ Fields Config   │    │ Fields Config│  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
│           │                       │                       │      │
│           │                       │                       │      │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐  │
│  │ CRUD Operations │    │ AI Features     │    │ Metadata    │  │
│  │ Configuration   │    │ Configuration   │    │ Configuration│  │
│  └─────────────────┘    └─────────────────┘    └─────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

### 1. Entity Creation Flow
```
UserService.createUser() 
    ↓
@AICapable annotation triggers AICapableAspect
    ↓
AICapableAspect calls AICapabilityService.makeEntityAICapable()
    ↓
AICapabilityService loads AIEntityConfig from ai-entity-config.yml
    ↓
AICapabilityService extracts searchable content from User entity
    ↓
AICapabilityService generates embedding using AI services
    ↓
AICapabilityService stores in AISearchableEntity
    ↓
User entity is now AI-capable and searchable
```

### 2. AI Search Flow
```
AISearchService.searchEntities("user", "john doe")
    ↓
AISearchService queries AISearchableEntity for "user" type
    ↓
AISearchService performs semantic search using embeddings
    ↓
AISearchService returns matching User entities
    ↓
Search results are returned to caller
```

### 3. Configuration Loading Flow
```
Application startup
    ↓
AIEntityConfigurationLoader.loadConfigurations()
    ↓
Load ai-entity-config.yml from classpath
    ↓
Parse YAML configuration into AIEntityConfig objects
    ↓
Register configurations in AIEntityRegistry
    ↓
AI entities are ready for processing
```

## Component Interactions

### AI Infrastructure Module Components
- **@AICapable**: Single annotation for marking entities as AI-capable
- **AICapableAspect**: AOP aspect that intercepts CRUD operations
- **AIEntityConfigurationLoader**: Loads and parses YAML configuration
- **AIEntityRegistry**: Registry for AI entity configurations
- **AICapabilityService**: Core service for AI capability management
- **AISearchableEntity**: Generic entity for AI search data
- **AI Services**: Generic AI services (embedding, search, RAG)

### Backend Module Components
- **Domain Entities**: Clean domain entities without AI coupling
- **Domain Services**: Services with @AICapable annotations
- **Configuration**: YAML configuration for AI behavior
- **Adapters**: Domain-specific AI service adapters

### Configuration Layer
- **YAML Configuration**: Declarative AI behavior configuration
- **Field Mappings**: Searchable and embeddable field definitions
- **CRUD Operations**: AI processing configuration for each operation
- **AI Features**: Feature flags for different AI capabilities

## Key Benefits

### 1. Single Annotation Approach
- Only `@AICapable` annotation needed per entity
- No field-level annotations required
- Clean, readable domain code

### 2. Configuration-Driven
- All AI behavior defined in YAML
- Easy to modify without code changes
- Consistent behavior across entities

### 3. Automatic Processing
- AI processing happens automatically via AOP
- No manual AI processing required
- Consistent AI behavior

### 4. Generic & Reusable
- Works with any domain
- Works with any entity type
- Easy to add new entities

### 5. Clean Separation
- Domain entities remain clean
- AI logic separated from domain logic
- Clear boundaries and responsibilities

## Migration Path

### Phase 1: Add Infrastructure
- Add AI infrastructure components
- Create configuration system
- Implement AOP processing

### Phase 2: Separate Entities
- Move AI-specific entities to infrastructure
- Clean domain entities
- Create configuration files

### Phase 3: Migrate Services
- Add @AICapable annotations to services
- Remove manual AI processing
- Test automatic processing

### Phase 4: Validate & Cleanup
- Test all functionality
- Remove old AI coupling
- Update documentation

This architecture provides a clean, maintainable, and reusable AI infrastructure that can make any entity AI-capable with minimal code and maximum flexibility.