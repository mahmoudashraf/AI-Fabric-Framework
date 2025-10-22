# EasyLuxury AI SaaS - Development Guide

## Project Overview

**EasyLuxury** is an AI-powered luxury e-commerce platform built with Spring Boot 3.x and Next.js. The project features advanced AI capabilities including RAG (Retrieval-Augmented Generation), security analysis, compliance monitoring, audit trails, and data privacy management.

## Architecture

### Backend Architecture
- **Framework**: Spring Boot 3.x with Java 21
- **Database**: H2 (dev/test), PostgreSQL (production)
- **AI Integration**: Custom AI Infrastructure module
- **Profiles**: `dev`, `test`, `prod` with specific configurations
- **Build Tool**: Maven

### Frontend Architecture
- **Framework**: Next.js with React 18
- **Language**: TypeScript
- **State Management**: React Query (`@tanstack/react-query`)
- **UI Components**: Material-UI (MUI)
- **Build Tool**: npm

### AI Infrastructure Module
- **Location**: `ai-infrastructure-module/` (separate Maven module)
- **Services**: RAG, Security, Compliance, Audit, Data Privacy
- **Vector Database**: Lucene, Pinecone, Memory (configurable)
- **Auto-configuration**: Spring Boot starter pattern

## Development Guidelines

### Code Style & Standards

#### Java Backend
- **Java Version**: 21 with modern features
- **Lombok**: Use `@Data`, `@RequiredArgsConstructor`, `@Slf4j`
- **Spring Annotations**: Prefer `@Service`, `@Repository`, `@RestController`
- **Configuration**: Use `@ConfigurationProperties` for externalized config
- **Dependency Injection**: Constructor injection with `@RequiredArgsConstructor`
- **Exception Handling**: Use `@ControllerAdvice` for global exception handling

#### TypeScript Frontend
- **TypeScript**: Strict mode enabled
- **React Hooks**: Custom hooks for AI services
- **Error Handling**: Try-catch with proper error boundaries
- **API Integration**: React Query for data fetching and caching
- **Component Structure**: Functional components with hooks

### Project Structure

```
├── backend/                    # Spring Boot backend
│   ├── src/main/java/
│   │   └── com/easyluxury/
│   │       ├── ai/            # AI-related services
│   │       │   ├── config/    # Configuration classes
│   │       │   ├── controller/ # REST controllers
│   │       │   ├── service/   # Business logic
│   │       │   └── facade/    # Service facades
│   │       └── EasyLuxuryApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml    # Main configuration
│   │   ├── application-dev.yml # Dev profile
│   │   └── application-prod.yml # Prod profile
│   └── src/test/              # Test classes
├── frontend/                   # Next.js frontend
│   ├── src/
│   │   ├── hooks/             # Custom React hooks
│   │   ├── components/        # React components
│   │   └── pages/             # Next.js pages
│   └── package.json
└── ai-infrastructure-module/   # AI services module
    ├── src/main/java/
    │   └── com/ai/infrastructure/
    │       ├── config/        # AI configuration
    │       ├── service/       # AI services
    │       └── dto/           # Data transfer objects
    └── pom.xml
```

## Configuration Management

### Profile Strategy
- **dev**: H2 database, mock AI services, debug logging
- **test**: H2 database, mock services, test-specific config
- **prod**: PostgreSQL, production AI services, optimized logging

### Database Configuration
```yaml
# Development (H2)
spring:
  datasource:
    url: jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password:
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  h2:
    console:
      enabled: true
      path: /h2-console
```

### AI Configuration
```yaml
ai:
  providers:
    openai-api-key: ${OPENAI_API_KEY:sk-dev-key}
    openai-model: gpt-4o-mini
    vector-db-type: memory  # memory, lucene, pinecone
    vector-db-similarity-threshold: 0.6
    vector-db-max-results: 50
```

## Development Workflow

### Starting Development
1. **Backend**:
   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

2. **Frontend**:
   ```bash
   cd frontend
   npm run dev
   ```

3. **Database Console**: `http://localhost:8080/h2-console`

### Testing Strategy
- **Unit Tests**: Individual service testing with Mockito
- **Integration Tests**: `@SpringBootTest` with profile-specific configs
- **Frontend Tests**: React component and hook testing
- **Context Loading Tests**: Verify Spring context loads correctly

### Build Commands
```bash
# Backend
mvn clean compile
mvn test
mvn spring-boot:run

# Frontend
npm run build
npm test
npm run dev

# AI Module
cd ai-infrastructure-module
mvn clean install
```

## AI Services Architecture

### Service Pattern
- **Interface**: Define AI service contracts
- **Mock Implementation**: For development and testing
- **Production Implementation**: For live AI integration
- **Configuration**: Profile-based service selection

### Available AI Services
1. **RAG Service**: Retrieval-Augmented Generation
2. **Security Service**: Threat analysis and security recommendations
3. **Compliance Service**: Regulatory compliance checking
4. **Audit Service**: Activity logging and monitoring
5. **Data Privacy Service**: Privacy impact assessment

### Frontend AI Hooks
- `useAISecurity`: Security analysis and recommendations
- `useAICompliance`: Compliance checking and reporting
- `useAIAudit`: Audit trail management
- `useAdvancedRAG`: RAG operations and search
- `useAIDataPrivacy`: Privacy management and assessment

## Error Handling Patterns

### Backend Error Handling
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ErrorResponse> handleAIServiceException(AIServiceException ex) {
        // Handle AI service errors
    }
}
```

### Frontend Error Handling
```typescript
const { data, error, isLoading } = useQuery({
  queryKey: ['ai-security'],
  queryFn: fetchSecurityAnalysis,
  onError: (error) => {
    console.error('AI Security Error:', error);
  }
});
```

## Common Issues & Solutions

### 1. Bean Dependency Issues
**Problem**: `UnsatisfiedDependencyException` for missing beans
**Solution**: Use `@ConditionalOnBean` for optional dependencies
```java
@ConditionalOnBean(AIProviderConfig.class)
public class AIConfigurationValidator {
    // Implementation
}
```

### 2. Configuration Property Mapping
**Problem**: `@ConfigurationProperties` not binding correctly
**Solution**: Ensure correct prefix and property naming
```java
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {
    private String openaiApiKey;  // Maps to ai.providers.openai-api-key
}
```

### 3. Database Profile Conflicts
**Problem**: Wrong datasource for active profile
**Solution**: Explicit profile-specific datasource overrides in `application.yml`

### 4. AI Module Loading
**Problem**: AI infrastructure not available
**Solution**: Import auto-configuration explicitly
```java
@SpringBootApplication
@Import(AIInfrastructureAutoConfiguration.class)
public class EasyLuxuryApplication {
    // Main class
}
```

## Performance Considerations

### Backend Optimization
- **Connection Pooling**: HikariCP with appropriate pool sizes
- **Caching**: Spring Cache for frequently accessed data
- **Async Processing**: `@Async` for long-running AI operations
- **Database Indexing**: Proper indexes for query optimization

### Frontend Optimization
- **Code Splitting**: Dynamic imports for large components
- **Memoization**: React.memo for expensive components
- **Query Caching**: React Query for API response caching
- **Bundle Analysis**: Regular bundle size monitoring

## Security Guidelines

### Backend Security
- **Input Validation**: Use `@Valid` and custom validators
- **Authentication**: JWT tokens with proper expiration
- **Authorization**: Method-level security with `@PreAuthorize`
- **Data Encryption**: Sensitive data encryption at rest

### Frontend Security
- **XSS Prevention**: Proper input sanitization
- **CSRF Protection**: CSRF tokens for state-changing operations
- **API Security**: Secure API communication with HTTPS
- **Environment Variables**: Never expose secrets in client code

## Monitoring & Logging

### Logging Strategy
- **Levels**: DEBUG (dev), INFO (test), WARN (prod)
- **Structured Logging**: Use structured log messages
- **AI Operations**: Log all AI service calls and responses
- **Performance**: Log slow operations and bottlenecks

### Health Checks
- **Database**: Connection health monitoring
- **AI Services**: Service availability checks
- **External APIs**: Third-party service health
- **Custom Metrics**: Business-specific health indicators

## Deployment Guidelines

### Environment Setup
- **Development**: Local H2 database, mock AI services
- **Testing**: Isolated test database, controlled AI responses
- **Production**: PostgreSQL, production AI services, monitoring

### Configuration Management
- **Environment Variables**: Use for sensitive configuration
- **Profile Activation**: Automatic profile detection
- **External Config**: External configuration files for different environments

## Contributing Guidelines

### Code Review Process
1. **Feature Branch**: Create feature branch from main
2. **Code Review**: Peer review required for all changes
3. **Testing**: All tests must pass before merge
4. **Documentation**: Update documentation for new features

### Commit Message Convention
```
type(scope): description

feat(ai): add RAG service implementation
fix(backend): resolve database connection issue
docs(readme): update development setup instructions
```

### Pull Request Template
- **Description**: What changes were made
- **Testing**: How the changes were tested
- **Breaking Changes**: Any breaking changes
- **Documentation**: Documentation updates needed

## Troubleshooting

### Common Development Issues
1. **Port Conflicts**: Check if ports 8080 (backend) and 3000 (frontend) are available
2. **Database Issues**: Ensure H2 console is accessible at `/h2-console`
3. **AI Service Errors**: Check AI configuration and API keys
4. **Build Failures**: Run `mvn clean install` in AI module first

### Debug Tools
- **H2 Console**: Database inspection and query execution
- **Spring Boot Actuator**: Application health and metrics
- **Browser DevTools**: Frontend debugging and network inspection
- **IDE Debugging**: Breakpoints and variable inspection

## Resources

### Documentation
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Next.js Documentation](https://nextjs.org/docs)
- [React Query Documentation](https://tanstack.com/query/latest)
- [Material-UI Documentation](https://mui.com/)

### Internal Resources
- **API Documentation**: Available at `/swagger-ui.html` when running
- **Database Schema**: Check Liquibase changelog files
- **AI Service Contracts**: See DTO classes in AI infrastructure module

---

**Last Updated**: December 2024
**Version**: 1.0.0
**Maintainers**: EasyLuxury Development Team
