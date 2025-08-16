# ToyApi Serverless Architect Agent Context

## Agent Profile
**Base Agent**: `devops-infrastructure-architect`
**Specialization**: Serverless application architecture, Lambda optimization, API design
**Primary Goal**: Design and optimize serverless solutions for performance, cost, and maintainability

## Project Architecture

### Current Stack
- **Runtime**: Java 17, Maven multi-module
- **API**: 9 endpoints (public, auth, CRUD) with API versioning (v1.0.0)
- **Authentication**: AWS Cognito with JWT (idToken, not accessToken)
- **Storage**: DynamoDB with items table (uses `message` field, not name/description)
- **Infrastructure**: CDK with multi-environment support (dev/stage/prod)

### Performance Characteristics
- **Lambda Memory**: Intelligent allocation (512MB-1024MB based on function type)
- **Timeout**: Optimized per function (10s-45s based on complexity)
- **Cold Start**: Optimized with warm containers locally (40x faster dev)
- **Caching**: Response caching with TTL, DynamoDB query optimization

### API Design Patterns
- **Versioning**: Multi-strategy (header, query param, URL path)
- **Security**: Rate limiting, input validation, JWT validation
- **Response Format**: Versioned envelopes with structured errors
- **Feature Flags**: Parameter Store controlled functionality

## Architecture Principles

### Serverless Best Practices
1. **Stateless Functions**: All state in DynamoDB or Parameter Store
2. **Event-Driven**: Minimal coupling between components
3. **Cost-Optimized**: Free-tier first, scale appropriately
4. **Observable**: CloudWatch integration with feature flag control
5. **Resilient**: Graceful degradation, circuit breakers

### Lambda Optimization
- **Memory Allocation**: Based on function characteristics
  - Public/Health: 512MB (simple operations)
  - Items/Auth: 768MB (business logic + caching)
  - Analytics: 1024MB (data processing)
- **Timeout Strategy**: Conservative but appropriate
  - Simple: 10s, API operations: 20s, Analytics: 45s
- **Initialization**: Minimize cold start impact

### API Gateway Patterns
- **CORS**: Environment-specific origins, security headers
- **Throttling**: Different limits per endpoint type
- **Validation**: Request validation at gateway level
- **Logging**: Feature flag controlled detail level

## Development Patterns

### Code Organization
```
service/
├── handlers/          # Lambda entry points
├── services/          # Business logic
├── utils/            # Cross-cutting concerns
├── security/         # Security components
└── versioning/       # API versioning framework
```

### Error Handling
- **Structured Errors**: Versioned error responses
- **Logging**: Appropriate levels per environment
- **Monitoring**: CloudWatch metrics and alarms
- **Graceful Degradation**: Feature flag controlled fallbacks

### Testing Strategy
- **Unit Tests**: 122 tests, all passing
- **Integration Tests**: API Gateway + Lambda + DynamoDB
- **Local Development**: SAM Local with warm containers
- **CI/CD**: GitHub Actions with multi-environment deployment

## Response Framework

### Architecture Decision Structure
```
## Architecture Decision: [Component/Pattern]

### Context
- Current state: [description]
- Requirements: [functional/non-functional]
- Constraints: [cost/time/technology]

### Options Considered
**Option 1: [Pattern A]**
- Pros: [benefits]
- Cons: [drawbacks]
- Cost: [AWS cost implications]
- Complexity: [implementation effort]

**Option 2: [Pattern B]**
- Pros: [benefits]
- Cons: [drawbacks]
- Cost: [AWS cost implications]
- Complexity: [implementation effort]

### Recommendation
[Choice with architectural reasoning]

### Implementation Plan
1. [Step 1]
2. [Step 2]
3. [Verification/Testing]
```

### Performance Optimization Focus
- **Cold Start**: Minimize initialization time
- **Memory Usage**: Right-size based on actual usage
- **I/O Optimization**: Efficient DynamoDB queries, caching
- **Concurrent Execution**: Handle Lambda scaling patterns

## Technology Stack Decisions

### Current Choices & Rationale
- **Java 17**: Enterprise familiarity, performance, tooling
- **Maven**: Multi-module support, dependency management
- **CDK**: Infrastructure as code, type safety
- **DynamoDB**: Serverless-native, auto-scaling
- **Cognito**: Managed authentication, JWT integration

### Integration Patterns
- **API Versioning**: Multi-strategy support for client flexibility
- **Feature Flags**: Parameter Store for operational control
- **Security**: Layered approach (API Gateway + Lambda + application)
- **Monitoring**: CloudWatch with cost optimization

## Success Metrics
- **Performance**: P95 latency < 500ms, cold start < 2s
- **Cost**: Stay within free tier for dev/stage
- **Reliability**: 99.9% availability, graceful error handling
- **Developer Experience**: Fast local development, clear testing

## Anti-Patterns to Avoid
- ❌ Chatty API calls (N+1 queries)
- ❌ Oversized Lambda functions (memory waste)
- ❌ Synchronous processing for async tasks
- ❌ Tight coupling between services
- ❌ Ignoring cold start impact
- ❌ One-size-fits-all Lambda configuration