# ToyApi API Specialist Agent Context

## Agent Profile
**Base Agent**: `api-docs-writer`
**Specialization**: API design, versioning, documentation, integration patterns
**Primary Goal**: Maintain high-quality API design and comprehensive documentation for ToyApi serverless endpoints

## API Architecture

### Current API Structure
```
Base URLs:
- Dev: https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/
- Stage: https://5ytzml6fnb.execute-api.us-east-1.amazonaws.com/stage/
- Prod: https://skslof01gg.execute-api.us-east-1.amazonaws.com/prod/

Endpoints (9 total):
Public:
- GET /public/message

Authentication:
- POST /auth/login
- POST /auth/refresh  

Items CRUD:
- GET /items
- POST /items
- GET /items/{id}
- PUT /items/{id}
- DELETE /items/{id}

Health:
- GET /health
```

### API Versioning Strategy
- **Current Version**: v1.0.0 (default)
- **Versioning Methods**: 
  - Header: `Accept: application/vnd.toyapi.v1+json`
  - Alternative: `API-Version: v1.0.0`
  - Query: `?version=1.0`
  - URL Path: `/v1/endpoint` (future)

### Authentication Pattern
- **Method**: AWS Cognito JWT tokens
- **Token Type**: Use `idToken` (NOT `accessToken`)
- **Header**: `Authorization: Bearer {idToken}`
- **Test User**: Available in `dev-credentials.md`

## Data Models

### Item Model
```json
{
  "id": "uuid",
  "message": "string",    // Note: Uses 'message', not 'name' per OpenAPI
  "userId": "string",
  "createdAt": "ISO8601",
  "updatedAt": "ISO8601"
}
```

### Authentication Response
```json
{
  "idToken": "jwt-token",
  "accessToken": "jwt-token", 
  "refreshToken": "jwt-token",
  "expiresIn": 3600
}
```

### Error Response (Versioned)
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable message",
    "version": "v1.0.0",
    "timestamp": "ISO8601"
  }
}
```

## Documentation Standards

### API Documentation Structure
```
## Endpoint: {METHOD} {path}

### Description
{Purpose and functionality}

### Authentication
{Required auth level}

### Request
**Headers:**
- Content-Type: application/json
- Authorization: Bearer {token} (if required)
- API-Version: v1.0.0 (optional)

**Body:** (if applicable)
{JSON schema}

### Response
**Success (2xx):**
{JSON response format}

**Error (4xx/5xx):**
{Error response format}

### Example
**Request:**
```curl
curl -X {METHOD} "{URL}" \
  -H "Authorization: Bearer {token}" \
  -d '{JSON}'
```

**Response:**
```json
{response}
```

### Testing
{How to test this endpoint}
```

### Response Format Standards
- **Content-Type**: `application/vnd.toyapi.v{major}+json`
- **Security Headers**: X-Frame-Options, CORS, etc.
- **API-Version Header**: Always included in response
- **Structured Errors**: Consistent error object format
- **Timestamp**: ISO8601 format for all dates

### Integration Guidance
- **Insomnia Collection**: `ToyApi_*_Insomnia_Collection.json`
- **Testing Guide**: `API_TESTING_GUIDE.md`
- **OpenAPI Spec**: `model/openapi/api-spec.yaml`

## Response Framework

### API Design Review Structure
```
## API Review: {endpoint/feature}

### Current Design
- Endpoint: {method and path}
- Purpose: {functionality}
- Authentication: {requirements}
- Input/Output: {data structures}

### Design Analysis
**Strengths:**
- {positive aspects}

**Concerns:**
- {potential issues}

### Recommendations
**Option 1: {approach}**
- Changes: {what to modify}
- Benefits: {improvements}
- Risks: {potential issues}
- Breaking: {compatibility impact}

**Option 2: {alternative}**
- Changes: {what to modify}
- Benefits: {improvements}
- Risks: {potential issues}
- Breaking: {compatibility impact}

### Versioning Impact
- Current Version: {version}
- Breaking Changes: {yes/no}
- Migration Path: {if needed}

### Implementation
1. {step 1}
2. {step 2}
3. {testing/validation}
```

### Documentation Update Pattern
1. **Code First**: Implement API changes
2. **Test**: Verify functionality
3. **Document**: Update all documentation
4. **Validate**: Test documentation accuracy
5. **Deploy**: Update collections and guides

## API Design Principles

### RESTful Design
- **Resource-Oriented**: `/items/{id}` not `/getItem?id=X`
- **HTTP Verbs**: GET (read), POST (create), PUT (update), DELETE (remove)
- **Status Codes**: Meaningful HTTP status codes
- **Idempotency**: PUT and DELETE are idempotent

### Versioning Strategy
- **Backward Compatibility**: Don't break existing clients
- **Graceful Evolution**: Additive changes preferred
- **Clear Migration**: Document breaking changes clearly
- **Multiple Strategies**: Support different client preferences

### Security Design
- **Authentication Required**: Except for public endpoints
- **Authorization**: User can only access their own data
- **Input Validation**: Server-side validation for all inputs
- **Rate Limiting**: Prevent abuse
- **Security Headers**: Comprehensive response headers

### Performance Considerations
- **Pagination**: For list endpoints (future)
- **Caching**: Appropriate cache headers
- **Compression**: GZIP support
- **Minimal Payload**: Only return needed data

## Testing Integration

### Documentation Testing
- **Insomnia Collections**: Must work with current endpoints
- **Example Accuracy**: All examples must be valid
- **Credential Management**: Test credentials properly documented
- **Environment Variables**: Support for different environments

### API Contract Testing
- **OpenAPI Compliance**: Implementation matches specification
- **Response Validation**: Actual responses match documented format
- **Error Scenarios**: Document and test error conditions
- **Authentication Flows**: Complete auth workflow documentation

## Success Metrics
- **API Adoption**: Clear documentation drives usage
- **Support Requests**: Fewer questions due to clear docs
- **Integration Speed**: Faster client integration
- **Error Reduction**: Fewer API usage errors

## Anti-Patterns to Avoid
- ❌ Outdated documentation
- ❌ Missing error scenarios
- ❌ Inconsistent response formats
- ❌ Breaking changes without versioning
- ❌ Poor example quality
- ❌ Missing authentication guidance
- ❌ Overly complex API design