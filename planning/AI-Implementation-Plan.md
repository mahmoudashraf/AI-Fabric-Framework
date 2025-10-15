# AI Profile Implementation Plan

## Overview
This document outlines the comprehensive implementation plan for the AI-powered user profile generation feature that enables users to create professional profiles from their CV using ChatGPT (OpenAI GPT).

## Table of Contents
1. [Feature Requirements](#feature-requirements)
2. [Technical Architecture](#technical-architecture)
3. [Database Design](#database-design)
4. [Backend Implementation](#backend-implementation)
5. [Frontend Implementation](#frontend-implementation)
6. [AI Integration](#ai-integration)
7. [Security & Validation](#security--validation)
8. [Production Configuration](#production-configuration)
9. [Testing Strategy](#testing-strategy)
10. [Deployment Checklist](#deployment-checklist)

## Feature Requirements

### Core Functionality
- **CV Upload & Processing**: Users can upload CV files or paste content
- **AI Profile Generation**: ChatGPT analyzes CV and generates structured profile data
- **Photo Suggestions**: AI suggests photo types based on professional context
- **Profile Review**: Users can review and edit AI-generated profiles
- **Photo Upload**: Dynamic photo upload based on AI suggestions
- **Profile Integration**: Seamless integration with existing social profile UI

### Data Structure
```json
{
  "name": "Full Name",
  "jobTitle": "Current Job Title",
  "companies": [
    {
      "name": "Company Name",
      "icon": "https://logo.clearbit.com/company.com",
      "position": "Job Title",
      "duration": "2020-2024"
    }
  ],
  "profileSummary": "Professional summary (max 500 chars)",
  "skills": ["Skill1", "Skill2", "Skill3"],
  "experience": 5,
  "photos": {
    "profilePhoto": "placeholder://profile-photo",
    "coverPhoto": "placeholder://cover-photo",
    "professional": ["placeholder://professional-1"],
    "team": ["placeholder://team-1"],
    "project": ["placeholder://project-1"]
  },
  "photoSuggestions": {
    "profilePhoto": {
      "required": true,
      "count": 1,
      "suggestions": ["Professional headshot"],
      "description": "Clear professional headshot"
    }
  }
}
```

## Technical Architecture

### Backend Stack
- **Framework**: Spring Boot 3.3.5 with Java 21
- **Database**: PostgreSQL with Liquibase migrations
- **AI Service**: OpenAI GPT-4o-mini via Java SDK
- **File Storage**: MinIO for CV files and photos
- **Security**: JWT authentication, input validation, rate limiting

### Frontend Stack
- **Framework**: Next.js 15.5.4 with React 19.2.0
- **UI Library**: Material-UI v7
- **State Management**: React Query (server state) + Context API (UI state)
- **Form Handling**: React Hook Form + Zod validation
- **TypeScript**: Full type safety
- **Styling**: SCSS modules + Emotion CSS-in-JS

### Integration Points
- **AI Service**: OpenAI API for CV parsing
- **File Storage**: MinIO for document and image storage
- **Database**: PostgreSQL for profile persistence
- **Authentication**: Supabase Auth (JWT verification via JWKS)
- **UI Integration**: Existing social profile components
- **State Management**: React Query for server state, Context API for UI state

## Database Design

### AI Profiles Table
```sql
CREATE TABLE ai_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ai_attributes JSONB,
    cv_file_url VARCHAR(500),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

-- Indexes
CREATE INDEX idx_ai_profiles_user_id ON ai_profiles(user_id);
CREATE INDEX idx_ai_profiles_status ON ai_profiles(status);
CREATE INDEX idx_ai_profiles_created_at ON ai_profiles(created_at);
```

### Status Enumeration
- **DRAFT**: Initial AI generation, awaiting user review
- **PHOTOS_PENDING**: Profile created, photos being uploaded
- **COMPLETE**: Profile fully populated with photos
- **ARCHIVED**: Profile archived or replaced

## Backend Implementation

### 1. Entity Layer
```java
@Entity
@Table(name = "ai_profiles")
public class AIProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "ai_attributes", columnDefinition = "jsonb")
    private String aiAttributes;
    
    @Column(name = "cv_file_url")
    private String cvFileUrl;
    
    @Enumerated(EnumType.STRING)
    private AIProfileStatus status;
    
    // Audit fields
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

### 2. Repository Layer
```java
@Repository
public interface AIProfileRepository extends JpaRepository<AIProfile, UUID> {
    Optional<AIProfile> findByUserAndStatus(User user, AIProfileStatus status);
    List<AIProfile> findByUserOrderByCreatedAtDesc(User user);
    boolean existsByUserAndStatus(User user, AIProfileStatus status);
}
```

### 3. Service Layer
- **AIProfileService**: Core business logic
- **AIService**: OpenAI integration
- **MinIOService**: File storage operations
- **CVContentValidator**: Input validation and sanitization
- **AIRateLimitService**: Rate limiting per user

### 4. Facade Layer
```java
@Service
@Transactional
public class AIProfileFacade {
    private final AIProfileService aiProfileService;
    private final MinIOService minIOService;
    private final CVContentValidator cvValidator;
    
    public AIProfileDto createProfileWithValidation(User user, String cvContent) {
        // Orchestrate validation, AI processing, and profile creation
    }
    
    public AIProfileDto uploadCVWithProcessing(User user, MultipartFile cv) {
        // Orchestrate CV upload, AI processing, and profile generation
    }
}
```

### 5. Controller Layer
```java
@RestController
@RequestMapping("/api/ai-profile")
@Tag(name = "AI Profile", description = "AI Profile management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AIProfileController {
    
    @PostMapping("/generate")
    @Operation(summary = "Generate AI profile from CV content")
    @IdempotencyKey
    public ResponseEntity<AIProfileDto> generateProfile(
        @RequestBody @Valid GenerateProfileRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey);
    
    @PostMapping("/upload-cv")
    @Operation(summary = "Upload CV file and generate profile")
    @IdempotencyKey
    public ResponseEntity<AIProfileDto> uploadCV(
        @RequestParam("file") @Valid MultipartFile file,
        @RequestHeader("Idempotency-Key") String idempotencyKey);
    
    @PostMapping("/upload-photo")
    @Operation(summary = "Upload photo for AI profile")
    public ResponseEntity<AIProfileDto> uploadPhoto(
        @RequestParam("file") @Valid MultipartFile file,
        @RequestParam("photoType") @Valid PhotoType photoType);
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get AI profile for user")
    public ResponseEntity<AIProfileDto> getAIProfile(@PathVariable UUID userId);
}
```

### 6. Error Handling
```java
@ControllerAdvice
public class AIProfileExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.badRequest()
            .body(ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Invalid input data")
                .details(List.of(ex.getMessage()))
                .build());
    }
    
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(429)
            .body(ErrorResponse.builder()
                .code("RATE_LIMIT_EXCEEDED")
                .message("Too many requests")
                .details(List.of("Please try again later"))
                .build());
    }
}

// Error Response DTO
@Data
@Builder
public class ErrorResponse {
    private String code;
    private String message;
    private List<String> details;
}
```

## Frontend Implementation

### 1. Type Definitions
```typescript
export interface AIProfileData {
  name: string;
  jobTitle: string;
  companies: Company[];
  profileSummary: string;
  skills: string[];
  experience: number;
  photos: PhotoCollection;
  photoSuggestions: PhotoSuggestions;
}

export interface AIProfile {
  id: string;
  userId: string;
  aiAttributes: string;
  cvFileUrl?: string;
  status: AIProfileStatus;
  createdAt: string;
  updatedAt: string;
}
```

### 2. State Management Strategy
```typescript
// React Query for server state management
export const useAIProfileQuery = (userId: string) => {
  return useQuery({
    queryKey: ['aiProfile', userId],
    queryFn: () => api.getAIProfile(userId),
    enabled: !!userId,
  });
};

export const useGenerateProfileMutation = () => {
  return useMutation({
    mutationFn: (cvContent: string) => api.generateProfile(cvContent),
    onSuccess: (data) => {
      queryClient.invalidateQueries(['aiProfile']);
    },
  });
};

// Context API for UI state management
export const AIProfileProvider = ({ children }: { children: ReactNode }) => {
  const [uiState, setUiState] = useState({
    isGenerating: false,
    selectedPhotoType: null,
    showPreview: false,
  });
  
  return (
    <AIProfileContext.Provider value={{ uiState, setUiState }}>
      {children}
    </AIProfileContext.Provider>
  );
};
```

### 3. Component Reuse Strategy
```typescript
// Leverage existing components from /src/components/
import MainCard from 'ui-component/cards/MainCard';
import FileUpload from 'components/forms/FileUpload';
import DataGrid from 'components/data-grid/DataGrid';
import Snackbar from 'components/ui-element/advance/snackbar';
import Dialog from 'components/ui-element/advance/dialog';

// Reuse existing layouts
import DashboardLayout from 'layout/DashboardLayout';
import MinimalLayout from 'layout/MinimalLayout';

// Extend existing pages
import SocialProfile from 'views/apps/user/social-profile';
```

### 4. UI Components
- **AIProfile**: Main component extending existing social profile structure
- **PhotoUploadSlot**: Reusing existing FileUpload component with AI suggestions
- **ProfilePreview**: Using existing DataGrid and Card components
- **Integration**: Seamless integration with existing social profile tabs
- **Form Handling**: React Hook Form + Zod validation for all inputs

### 5. Component Mapping Strategy
```typescript
// Existing Components to Reuse
const componentMapping = {
  // Layout Components
  layouts: {
    main: 'layout/DashboardLayout',
    minimal: 'layout/MinimalLayout',
  },
  
  // UI Components
  ui: {
    cards: 'ui-component/cards/MainCard',
    dialogs: 'components/ui-element/advance/dialog',
    snackbars: 'components/ui-element/advance/snackbar',
    buttons: 'components/ui-element/basic/button',
    chips: 'components/ui-element/basic/chip',
  },
  
  // Form Components
  forms: {
    fileUpload: 'components/forms/FileUpload',
    textField: 'components/forms/components/text-field',
    validation: 'components/forms/forms-validation',
  },
  
  // Data Components
  data: {
    dataGrid: 'components/data-grid/DataGrid',
    tables: 'components/forms/tables/tbl-basic',
  },
  
  // Existing Pages to Extend
  pages: {
    socialProfile: 'views/apps/user/social-profile',
    userProfile: 'views/apps/user/account-profile/profile1',
  },
};
```

## AI Integration

### 1. OpenAI Configuration
```yaml
openai:
  api-key: ${OPENAI_API_KEY}
  model: ${OPENAI_MODEL:gpt-4o-mini}
  max-tokens: ${OPENAI_MAX_TOKENS:2000}
  temperature: ${OPENAI_TEMPERATURE:0.3}
  timeout: ${OPENAI_TIMEOUT:60}
```

### 2. Prompt Engineering
```java
private String getSystemPrompt() {
    return """
        You are an AI assistant specialized in extracting professional profile information from CVs.
        
        Analyze the provided CV content and extract the following information in the exact JSON format:
        {
            "name": "Full Name",
            "jobTitle": "Current Job Title",
            "companies": [...],
            "profileSummary": "Professional summary (max 500 characters)",
            "skills": [...],
            "experience": 5,
            "photos": {...},
            "photoSuggestions": {...}
        }
        
        Guidelines:
        - Extract only information clearly stated in the CV
        - Keep profileSummary under 500 characters
        - Generate 3-5 relevant skills
        - Calculate experience years from work history
        - Use placeholder:// URLs for photos
        - Return ONLY the JSON object
        """;
}
```

### 3. Fallback Strategy
- **Primary**: OpenAI GPT-4o-mini for real AI processing
- **Fallback**: Mock data generation if AI service unavailable
- **Error Handling**: Graceful degradation with user notification

## Security & Validation

### 1. Input Validation
```java
@Component
public class CVContentValidator {
    private static final int MAX_CV_LENGTH = 50000;
    private static final int MIN_CV_LENGTH = 100;
    
    @Valid
    public ValidationResult validate(@NotNull @NotBlank String cvContent) {
        // Length validation
        // Malicious content detection
        // Structure validation
        // Sanitization
    }
}

// DTO Validation
public class GenerateProfileRequest {
    @NotBlank(message = "CV content is required")
    @Size(min = 100, max = 50000, message = "CV content must be between 100 and 50000 characters")
    private String cvContent;
    
    @Valid
    @NotNull
    private MultipartFile cvFile;
}
```

### 2. Rate Limiting
```java
@Service
public class AIRateLimitService {
    @Value("${openai.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;
    
    @Value("${openai.rate-limit.requests-per-hour:1000}")
    private int requestsPerHour;
    
    public boolean isRateLimited(String userId) {
        // Per-user rate limiting logic
    }
}
```

### 3. Security Measures
- **Input Sanitization**: Remove malicious content
- **Rate Limiting**: Prevent API abuse
- **Authentication**: Supabase JWT verification via JWKS
- **Authorization**: RBAC with @PreAuthorize + row-level ownership checks
- **File Validation**: Secure file upload handling
- **Idempotency**: Idempotency-Key headers on POST operations
- **Error Handling**: ControllerAdvice with standardized error envelope

## Production Configuration

### 1. Environment Variables
```bash
# OpenAI Configuration
OPENAI_API_KEY=sk-your-openai-api-key-here
OPENAI_MODEL=gpt-4o-mini
OPENAI_MAX_TOKENS=2000
OPENAI_TEMPERATURE=0.3
OPENAI_TIMEOUT=60

# Rate Limiting
OPENAI_RATE_LIMIT_REQUESTS_PER_MINUTE=60
OPENAI_RATE_LIMIT_REQUESTS_PER_HOUR=1000

# MinIO Configuration
MINIO_URL=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=ai-profiles

# Supabase Configuration
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPABASE_JWT_SECRET=your-jwt-secret

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/easyluxury
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
```

### 2. Dependencies
```xml
<!-- OpenAI Java SDK -->
<dependency>
    <groupId>com.theokanning.openai-gpt3-java</groupId>
    <artifactId>service</artifactId>
    <version>0.18.2</version>
</dependency>

<!-- MinIO SDK -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.7</version>
</dependency>

<!-- Supabase JWT Verification -->
<dependency>
    <groupId>com.auth0</groupId>
    <artifactId>jwks-rsa</artifactId>
    <version>0.22.1</version>
</dependency>

<!-- Jakarta Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- Springdoc OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```

### 3. Database Migration
```xml
<changeSet id="create-ai-profiles-table" author="system">
    <createTable tableName="ai_profiles">
        <!-- Table definition -->
    </createTable>
    <addForeignKeyConstraint ... />
    <createIndex ... />
</changeSet>
```

### 4. OpenAPI Documentation
```java
@RestController
@RequestMapping("/api/ai-profile")
@Tag(name = "AI Profile", description = "AI Profile management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AIProfileController {
    
    @PostMapping("/generate")
    @Operation(
        summary = "Generate AI profile from CV content",
        description = "Uses OpenAI GPT to analyze CV content and generate structured profile data",
        responses = {
            @ApiResponse(responseCode = "200", description = "Profile generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    public ResponseEntity<AIProfileDto> generateProfile(@RequestBody GenerateProfileRequest request);
}
```

## Testing Strategy

### 1. Unit Tests
- **Service Layer**: AIProfileService, AIService, MinIOService
- **Facade Layer**: AIProfileFacade orchestration logic
- **Validation**: CVContentValidator, AIRateLimitService
- **Controllers**: AIProfileController endpoints with OpenAPI annotations
- **Frontend**: React components, hooks, and React Query mutations
- **Coverage Target**: 70%+ for service and controller layers

### 2. Integration Tests
- **API Endpoints**: Full request/response cycle with Testcontainers
- **Database**: Entity persistence and queries with PostgreSQL container
- **File Storage**: MinIO upload/download operations
- **AI Service**: Mock and real AI responses
- **Authentication**: Supabase JWT verification flow
- **Error Handling**: ControllerAdvice error envelope validation

### 3. End-to-End Tests
- **User Workflow**: Complete CV upload to profile creation
- **Photo Upload**: Dynamic photo upload based on AI suggestions
- **Error Scenarios**: Network failures, invalid inputs, rate limiting
- **Authentication Flow**: Supabase login to AI profile access
- **Component Integration**: React Query + Context API state management
- **UI/UX Testing**: Material-UI component rendering and interactions

## Deployment Checklist

### Pre-Deployment
- [ ] OpenAI API key configured
- [ ] MinIO service running and accessible
- [ ] Database migrations applied
- [ ] Environment variables set (including Supabase)
- [ ] Rate limiting configured
- [ ] File upload limits set
- [ ] OpenAPI documentation generated
- [ ] Supabase JWT verification configured
- [ ] Idempotency headers implemented

### Backend Deployment
- [ ] Spring Boot application builds successfully
- [ ] All dependencies resolved (including Supabase JWT)
- [ ] Database connection tested
- [ ] MinIO connection tested
- [ ] OpenAI API accessible
- [ ] Supabase JWT verification working
- [ ] OpenAPI docs available at /api/docs
- [ ] Error envelope format validated
- [ ] Logging configured

### Frontend Deployment
- [ ] Next.js build successful
- [ ] TypeScript compilation clean
- [ ] React Query integration working
- [ ] Context API UI state management working
- [ ] All components render correctly (reusing existing components)
- [ ] API integration working with proper error handling
- [ ] Form validation with React Hook Form + Zod
- [ ] Material-UI theming consistent
- [ ] Responsive design verified

### Post-Deployment
- [ ] API endpoints responding
- [ ] File uploads working
- [ ] AI profile generation functional
- [ ] Photo uploads working
- [ ] Rate limiting active
- [ ] Error monitoring configured

## Monitoring & Maintenance

### 1. Logging
- **AI Requests**: Track API calls and responses
- **Rate Limiting**: Monitor user request patterns
- **Errors**: Comprehensive error logging
- **Performance**: Response time monitoring

### 2. Metrics
- **Usage**: Number of profiles generated
- **Costs**: OpenAI API usage and costs
- **Performance**: Response times and success rates
- **Storage**: MinIO usage and file counts

### 3. Maintenance
- **API Key Rotation**: Regular OpenAI key updates
- **Rate Limit Tuning**: Adjust based on usage patterns
- **Model Updates**: Upgrade to newer AI models
- **Storage Cleanup**: Remove old files and profiles

## Future Enhancements

### 1. AI Improvements
- **Model Upgrades**: GPT-4, Claude, or other models
- **Custom Prompts**: Industry-specific profile generation
- **Multi-language**: Support for non-English CVs
- **Photo Analysis**: AI-powered photo quality assessment

### 2. Feature Extensions
- **Profile Templates**: Industry-specific templates
- **Batch Processing**: Multiple CV processing
- **Export Options**: PDF, LinkedIn, etc.
- **Analytics**: Profile performance metrics

### 3. Integration Enhancements
- **LinkedIn API**: Direct profile import
- **Job Boards**: Integration with job platforms
- **CRM Systems**: Customer relationship management
- **Analytics**: Advanced user behavior tracking

---

## Implementation Timeline

### Phase 1: Core Infrastructure (Week 1)
- Database schema and migrations
- Basic entity and repository layers
- MinIO file storage setup
- OpenAI service integration

### Phase 2: AI Integration (Week 2)
- OpenAI GPT integration
- Prompt engineering and optimization
- Fallback mechanisms
- Error handling and logging

### Phase 3: Frontend Development (Week 3)
- React components and context
- UI/UX implementation
- API integration
- Type safety implementation

### Phase 4: Security & Validation (Week 4)
- Input validation and sanitization
- Rate limiting implementation
- Security testing
- Performance optimization

### Phase 5: Testing & Deployment (Week 5)
- Comprehensive testing
- Production configuration
- Deployment and monitoring
- Documentation and training

---

*This implementation plan provides a comprehensive roadmap for building a production-ready AI-powered profile generation feature using ChatGPT integration.*