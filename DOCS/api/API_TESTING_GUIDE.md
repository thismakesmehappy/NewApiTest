# 🧪 ToyApi Complete Testing Guide

Comprehensive guide for testing all ToyApi endpoints with real examples, authentication flows, and troubleshooting.

## 📋 Table of Contents

1. [Quick Start](#quick-start)
2. [Authentication](#authentication)
3. [API Key Management](#api-key-management)
4. [Public Endpoints](#public-endpoints)
5. [Authentication Endpoints](#authentication-endpoints)
6. [Items CRUD Endpoints](#items-crud-endpoints)
7. [Developer Management Endpoints](#developer-management-endpoints)
8. [Testing Collections](#testing-collections)
9. [Error Handling](#error-handling)
10. [Rate Limits](#rate-limits)
11. [Troubleshooting](#troubleshooting)

## 🎯 Quick Start

### Prerequisites
- **Base URL**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev`
- **Test User**: `testuser` / `TestPassword123`
- **API Key**: Required for most endpoints (get from Developer Portal)

### 30-Second Test
```bash
# 1. Test public endpoint
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message"

# 2. Login to get JWT token
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPassword123"}'

# 3. Test protected endpoint (replace YOUR_JWT_TOKEN)
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/message" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY"
```

## 🔐 Authentication

ToyApi uses a dual authentication system:

### 1. API Key Authentication
- **Header**: `X-API-Key: your-api-key-here`
- **Required for**: Most endpoints (except developer registration)
- **Get Key**: Use Developer Portal or registration endpoints

### 2. JWT Token Authentication  
- **Header**: `Authorization: Bearer your-jwt-token-here`
- **Required for**: Protected endpoints (items, user-specific)
- **Get Token**: Login endpoint returns `idToken` and `accessToken`
- **Important**: Use `idToken` for API requests (not `accessToken`)

### Authentication Flow
```bash
# Step 1: Register as developer (no auth required)
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "name": "Test Developer", 
    "organization": "Test Corp",
    "purpose": "API testing"
  }'

# Step 2: Create API key
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "name": "Test Key"
  }'

# Step 3: Login for JWT token
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "username": "testuser",
    "password": "TestPassword123"
  }'

# Step 4: Use both API key and JWT for protected endpoints
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY"
```

## 🔑 API Key Management

### Get API Key via Developer Portal
1. Open `developer-portal/index.html` 
2. Register as developer
3. Create API key
4. Copy key value (shown only once!)

### Get API Key via API
```bash
# Register developer
REGISTER_RESPONSE=$(curl -s -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "yourname@example.com",
    "name": "Your Name",
    "organization": "Your Company",
    "purpose": "Testing ToyApi"
  }')

echo "Registration: $REGISTER_RESPONSE"

# Create API key
API_KEY_RESPONSE=$(curl -s -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "yourname@example.com", 
    "name": "My Test Key"
  }')

echo "API Key: $API_KEY_RESPONSE"

# Extract API key from response
API_KEY=$(echo $API_KEY_RESPONSE | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
echo "Your API Key: $API_KEY"
```

## 🌐 Public Endpoints

### GET /public/message
**Description**: Returns a public welcome message  
**Authentication**: None required  
**Rate Limit**: Applies  

```bash
curl -v "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message"
```

**Expected Response**:
```json
{
  "message": "Hello from ToyApi! This is a public endpoint.",
  "timestamp": "2025-01-20T10:30:00Z",
  "version": "1.0"
}
```

## 🔒 Authentication Endpoints

### POST /auth/login
**Description**: Authenticate user and get JWT tokens  
**Authentication**: API Key required  
**Body**: `{"username": "string", "password": "string"}`

```bash
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "username": "testuser",
    "password": "TestPassword123"
  }'
```

**Expected Response**:
```json
{
  "message": "Login successful",
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "idToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Important**: Use `idToken` for subsequent API calls, not `accessToken`.

### GET /auth/message
**Description**: Returns message for authenticated users  
**Authentication**: API Key + JWT required

```bash
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/message" \
  -H "Authorization: Bearer YOUR_ID_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY"
```

**Expected Response**:
```json
{
  "message": "Hello authenticated user!",
  "user": "testuser",
  "timestamp": "2025-01-20T10:30:00Z"
}
```

### GET /auth/user/{userId}/message
**Description**: Returns user-specific message  
**Authentication**: API Key + JWT required  
**Path Parameter**: `userId` - User identifier

```bash
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/user/testuser/message" \
  -H "Authorization: Bearer YOUR_ID_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY"
```

**Expected Response**:
```json
{
  "message": "Hello testuser! This is your personal message.",
  "userId": "testuser",
  "timestamp": "2025-01-20T10:30:00Z"
}
```

## 📦 Items CRUD Endpoints

All items endpoints require both API Key and JWT authentication.

### GET /items
**Description**: List all items for the authenticated user  
**Authentication**: API Key + JWT required

```bash
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items" \
  -H "Authorization: Bearer YOUR_ID_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY"
```

**Expected Response**:
```json
{
  "items": [
    {
      "id": "uuid-here",
      "message": "Hello World!",
      "userId": "testuser",
      "createdAt": "2025-01-20T10:30:00Z",
      "updatedAt": "2025-01-20T10:30:00Z"
    }
  ],
  "count": 1
}
```

### POST /items
**Description**: Create a new item  
**Authentication**: API Key + JWT required  
**Body**: `{"message": "string"}`

```bash
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ID_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "message": "My new item message"
  }'
```

**Expected Response**:
```json
{
  "id": "new-uuid-here",
  "message": "My new item message",
  "userId": "testuser",
  "createdAt": "2025-01-20T10:30:00Z",
  "updatedAt": "2025-01-20T10:30:00Z"
}
```

### GET /items/{id}
**Description**: Get a specific item by ID  
**Authentication**: API Key + JWT required  
**Path Parameter**: `id` - Item UUID

```bash
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items/your-item-id" \
  -H "Authorization: Bearer YOUR_ID_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY"
```

### PUT /items/{id}
**Description**: Update an existing item  
**Authentication**: API Key + JWT required  
**Path Parameter**: `id` - Item UUID  
**Body**: `{"message": "string"}`

```bash
curl -X PUT "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items/your-item-id" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ID_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "message": "Updated item message"
  }'
```

### DELETE /items/{id}
**Description**: Delete an item  
**Authentication**: API Key + JWT required  
**Path Parameter**: `id` - Item UUID

```bash
curl -X DELETE "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items/your-item-id" \
  -H "Authorization: Bearer YOUR_ID_TOKEN" \
  -H "X-API-Key: YOUR_API_KEY"
```

**Expected Response**:
```json
{
  "message": "Item deleted successfully",
  "deletedId": "your-item-id"
}
```

## 👥 Developer Management Endpoints

### POST /developer/register
**Description**: Register as a new developer  
**Authentication**: None (public endpoint)  
**Body**: `{"email": "string", "name": "string", "organization": "string", "purpose": "string"}`

```bash
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/register" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "developer@example.com",
    "name": "John Developer",
    "organization": "Example Corp",
    "purpose": "Building a mobile app integration"
  }'
```

**Expected Response**:
```json
{
  "developerId": "uuid-here", 
  "email": "developer@example.com",
  "name": "John Developer",
  "status": "ACTIVE",
  "message": "Developer registered successfully"
}
```

### GET /developer/profile
**Description**: Get developer profile information  
**Authentication**: API Key required  
**Query Parameter**: `email` - Developer email

```bash
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/profile?email=developer@example.com" \
  -H "X-API-Key: YOUR_API_KEY"
```

**Expected Response**:
```json
{
  "developerId": "uuid-here",
  "email": "developer@example.com", 
  "name": "John Developer",
  "organization": "Example Corp",
  "purpose": "Building a mobile app integration",
  "status": "ACTIVE",
  "createdAt": "2025-01-20T10:30:00Z",
  "updatedAt": "2025-01-20T10:30:00Z"
}
```

### PUT /developer/profile
**Description**: Update developer profile  
**Authentication**: API Key required  
**Body**: `{"email": "string", "name": "string", "organization": "string", "purpose": "string"}`

```bash
curl -X PUT "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/profile" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{
    "email": "developer@example.com",
    "name": "John Updated Developer",
    "organization": "New Corp",
    "purpose": "Updated purpose"
  }'
```

### POST /developer/api-key
**Description**: Create a new API key  
**Authentication**: None (uses email for verification)  
**Body**: `{"email": "string", "name": "string"}`

```bash
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "developer@example.com",
    "name": "Production App Key"
  }'
```

**Expected Response**:
```json
{
  "apiKeyId": "key-id-here",
  "apiKey": "actual-key-value-store-securely",
  "keyName": "Production App Key", 
  "status": "ACTIVE",
  "message": "API key created successfully"
}
```

**Important**: The `apiKey` value is only shown once. Store it securely!

### GET /developer/api-keys
**Description**: List all API keys for a developer  
**Authentication**: None (uses email for verification)  
**Query Parameter**: `email` - Developer email

```bash
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-keys?email=developer@example.com"
```

**Expected Response**:
```json
{
  "apiKeys": [
    {
      "apiKeyId": "key-id-1",
      "keyName": "Production App Key",
      "status": "ACTIVE", 
      "createdAt": "2025-01-20T10:30:00Z",
      "lastUsed": "2025-01-20T15:45:00Z"
    },
    {
      "apiKeyId": "key-id-2", 
      "keyName": "Development Key",
      "status": "ACTIVE",
      "createdAt": "2025-01-19T09:15:00Z"
    }
  ],
  "count": 2
}
```

### DELETE /developer/api-key/{keyId}
**Description**: Delete an API key  
**Authentication**: None (uses email for verification)  
**Path Parameter**: `keyId` - API key ID  
**Query Parameter**: `email` - Developer email

```bash
curl -X DELETE "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-key/key-id-here?email=developer@example.com"
```

**Expected Response**:
```json
{
  "message": "API key deleted successfully",
  "apiKeyId": "key-id-here"
}
```

## 📊 Testing Collections

Import these collections into your API testing tool:

### Insomnia Collections
- **Development**: `ToyApi_Insomnia_Collection.json`
- **Staging**: `ToyApi_Staging_Insomnia_Collection.json` 
- **Production**: `ToyApi_Production_Insomnia_Collection.json`
- **Local**: `ToyApi_Local_Insomnia_Collection.json`

### Postman Collection
Generate Postman collection from OpenAPI spec:
```bash
# If you have the OpenAPI spec
npx swagger-codegen generate -i model/openapi/api-spec.yaml -l postman-collection -o postman/
```

## 🚨 Error Handling

### Common HTTP Status Codes

| Code | Meaning | Cause | Solution |
|------|---------|-------|----------|
| 400 | Bad Request | Invalid request body/parameters | Check request format |
| 401 | Unauthorized | Missing/invalid JWT token | Login to get new token |
| 403 | Forbidden | Missing/invalid API key | Check X-API-Key header |
| 404 | Not Found | Resource doesn't exist | Verify endpoint/resource ID |
| 409 | Conflict | Resource already exists | Use different email/name |
| 429 | Too Many Requests | Rate limit exceeded | Slow down requests |
| 500 | Internal Server Error | Server error | Check CloudWatch logs |

### Error Response Format
```json
{
  "error": "Detailed error message",
  "statusCode": 400
}
```

### Common Errors and Solutions

#### "Invalid API key" (403)
```bash
# Check if API key is correct
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message" \
  -H "X-API-Key: YOUR_API_KEY" -v

# Create new API key if needed
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-key" \
  -H "Content-Type: application/json" \
  -d '{"email": "your@email.com", "name": "New Key"}'
```

#### "Token expired" (401)
```bash
# Get new token
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY" \
  -d '{"username": "testuser", "password": "TestPassword123"}'
```

#### "Developer already registered" (409)
```bash
# Use different email or get existing profile
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/profile?email=your@email.com"
```

## ⚡ Rate Limits

### Current Limits
- **Rate Limit**: 100 requests per second
- **Burst Limit**: 200 requests (short bursts)
- **Daily Quota**: 10,000 requests per day
- **API Keys per Developer**: 10 maximum

### Rate Limit Headers
```bash
# Check rate limit status
curl -I "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message" \
  -H "X-API-Key: YOUR_API_KEY"

# Look for these headers in response:
# X-RateLimit-Limit: 100
# X-RateLimit-Remaining: 95  
# X-RateLimit-Reset: 1640995200
```

### Handling Rate Limits
```bash
# When you get 429 Too Many Requests
# Response includes Retry-After header
# Wait specified seconds before retrying

# Example with retry logic
for i in {1..5}; do
  RESPONSE=$(curl -s -w "%{http_code}" "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message" \
    -H "X-API-Key: YOUR_API_KEY")
  
  if [[ ${RESPONSE: -3} == "429" ]]; then
    echo "Rate limited, waiting 60 seconds..."
    sleep 60
  else
    echo "Success: $RESPONSE"
    break
  fi
done
```

## 🔧 Troubleshooting

### Debug Checklist
- [ ] API key is valid and active
- [ ] Headers are properly formatted
- [ ] JWT token is not expired (1 hour lifetime)
- [ ] Request body is valid JSON
- [ ] Endpoint URL is correct
- [ ] Rate limits not exceeded

### Testing Tools

#### Test API Key Validity
```bash
curl -v "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message" \
  -H "X-API-Key: YOUR_API_KEY"
```

#### Test JWT Token
```bash
# Decode JWT to check expiration (requires jq)
echo "YOUR_JWT_TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq .exp

# Compare with current timestamp
date +%s
```

#### Full Authentication Test  
```bash
#!/bin/bash
set -e

API_BASE="https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev"
EMAIL="test@example.com"

echo "1. Registering developer..."
curl -s -X POST "$API_BASE/developer/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"name\":\"Test User\",\"organization\":\"Test\",\"purpose\":\"Testing\"}"

echo -e "\n2. Creating API key..."  
API_KEY_RESPONSE=$(curl -s -X POST "$API_BASE/developer/api-key" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"name\":\"Test Key\"}")

API_KEY=$(echo $API_KEY_RESPONSE | grep -o '"apiKey":"[^"]*"' | cut -d'"' -f4)
echo "API Key: $API_KEY"

echo -e "\n3. Testing login..."
LOGIN_RESPONSE=$(curl -s -X POST "$API_BASE/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{"username":"testuser","password":"TestPassword123"}')

ID_TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"idToken":"[^"]*"' | cut -d'"' -f4)
echo "ID Token: ${ID_TOKEN:0:50}..."

echo -e "\n4. Testing protected endpoint..."
curl -s "$API_BASE/auth/message" \
  -H "Authorization: Bearer $ID_TOKEN" \
  -H "X-API-Key: $API_KEY"

echo -e "\n\nAll tests completed successfully!"
```

### Common Issues

#### CORS Errors (Browser)
If testing from browser/web app:
- Ensure proper CORS headers are set
- Use HTTPS for production
- Include credentials if needed

#### SSL/TLS Issues
```bash
# Test with verbose SSL info
curl -v --trace-ascii /dev/stdout "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message"

# Skip SSL verification for testing (not recommended for production)
curl -k "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message"
```

#### Network/DNS Issues
```bash
# Test DNS resolution
nslookup 785sk4gpbh.execute-api.us-east-1.amazonaws.com

# Test connectivity 
ping 785sk4gpbh.execute-api.us-east-1.amazonaws.com

# Test with alternative DNS
curl --dns-servers 8.8.8.8 "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message"
```

### Environment-Specific Testing

#### Development Environment
```bash
export API_BASE="https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev"
export API_KEY="your-dev-api-key"
```

#### Staging Environment
```bash
export API_BASE="https://your-staging-api.amazonaws.com/stage"
export API_KEY="your-staging-api-key"
```

#### Production Environment
```bash
export API_BASE="https://your-prod-api.amazonaws.com/prod" 
export API_KEY="your-prod-api-key"
```

## 📞 Support

### Self-Service Resources
1. **Developer Portal**: Use for key management and basic testing
2. **Insomnia Collections**: Pre-configured requests for all endpoints
3. **Error Messages**: Detailed error responses with specific guidance
4. **Rate Limit Headers**: Monitor your usage in real-time

### Getting Help
- **Issues**: Check error response codes and messages first
- **Rate Limits**: Monitor headers and implement backoff strategies  
- **Authentication**: Verify both API key and JWT token validity
- **Documentation**: Complete examples in `DEVELOPER_ONBOARDING_GUIDE.md`

### CloudWatch Logs
For server-side debugging:
- **Public Function**: `/aws/lambda/toyapi-dev-publicfunction`
- **Auth Function**: `/aws/lambda/toyapi-dev-authfunction`  
- **Items Function**: `/aws/lambda/toyapi-dev-itemsfunction`
- **Developer Function**: `/aws/lambda/toyapi-dev-developerfunction`

---

## 🚀 Quick Reference

### Essential Commands
```bash
# Complete authentication flow
curl -X POST "$API_BASE/developer/register" -H "Content-Type: application/json" -d '{"email":"test@example.com","name":"Test","organization":"Test","purpose":"Testing"}'
curl -X POST "$API_BASE/developer/api-key" -H "Content-Type: application/json" -d '{"email":"test@example.com","name":"Test Key"}'
curl -X POST "$API_BASE/auth/login" -H "Content-Type: application/json" -H "X-API-Key: YOUR_KEY" -d '{"username":"testuser","password":"TestPassword123"}'

# Test all endpoint types
curl "$API_BASE/public/message"  # Public
curl "$API_BASE/auth/message" -H "Authorization: Bearer TOKEN" -H "X-API-Key: KEY"  # Protected
curl "$API_BASE/items" -H "Authorization: Bearer TOKEN" -H "X-API-Key: KEY"  # CRUD
```

### Key Points
- **Use `idToken`** for API requests (not `accessToken`)  
- **API Key required** for most endpoints
- **Items use `message` field** (not name/description)
- **Rate limits apply** - monitor headers
- **Tokens expire** in 1 hour - refresh as needed

**Happy testing! 🎉**