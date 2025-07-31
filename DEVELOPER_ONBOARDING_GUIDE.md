# 🚀 ToyApi Developer Onboarding Guide

Complete guide to getting started with ToyApi, from registration to making your first API calls.

## 📋 Table of Contents

1. [Quick Start](#quick-start)
2. [Developer Registration](#developer-registration)
3. [API Key Management](#api-key-management)
4. [Authentication Methods](#authentication-methods)
5. [Making API Calls](#making-api-calls)
6. [Rate Limits & Quotas](#rate-limits--quotas)
7. [Error Handling](#error-handling)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Support](#support)

## 🎯 Quick Start

Get up and running in 5 minutes:

1. **Register**: Visit the [Developer Portal](developer-portal/index.html)
2. **Get API Key**: Create your first API key
3. **Test**: Make your first API call
4. **Build**: Integrate into your application

```bash
# Test your setup
curl -H "X-API-Key: your-api-key-here" \
     https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message
```

## 👤 Developer Registration

### Using the Developer Portal

1. **Visit the Portal**: Open `developer-portal/index.html` in your browser
2. **Fill Registration Form**:
   - **Email**: Your primary contact email
   - **Name**: Full name for communications
   - **Organization**: Company or personal project name
   - **Purpose**: Brief description of your intended use

3. **Submit**: Click "Register as Developer"
4. **Confirmation**: You'll see a success message and can immediately create API keys

### Using the API Directly

```bash
curl -X POST https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "developer@example.com",
    "name": "John Developer",
    "organization": "Example Corp",
    "purpose": "Building mobile app integration"
  }'
```

**Response:**
```json
{
  "developerId": "uuid-here",
  "email": "developer@example.com",
  "name": "John Developer",
  "status": "ACTIVE",
  "message": "Developer registered successfully"
}
```

## 🔑 API Key Management

### Creating API Keys

#### Via Developer Portal
1. Switch to "Manage Keys" tab
2. Enter your registered email
3. Click "Load My Data"
4. Enter a name for your key (e.g., "Production App", "Testing")
5. Click "Create New API Key"
6. **Important**: Copy the API key value immediately - it's only shown once!

#### Via API
```bash
curl -X POST https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-key \
  -H "Content-Type: application/json" \
  -d '{
    "email": "developer@example.com",
    "name": "My App Key"
  }'
```

**Response:**
```json
{
  "apiKeyId": "key-id-here",
  "apiKey": "actual-key-value-store-securely",
  "keyName": "My App Key",
  "status": "ACTIVE",
  "message": "API key created successfully"
}
```

### Managing API Keys

#### List Your Keys
```bash
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-keys?email=developer@example.com"
```

#### Delete a Key
```bash
curl -X DELETE "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/developer/api-key/{keyId}?email=developer@example.com"
```

### API Key Security Best Practices

✅ **Do:**
- Store API keys securely (environment variables, secure vaults)
- Use different keys for different environments (dev/staging/prod)
- Rotate keys regularly
- Delete unused keys immediately

❌ **Don't:**
- Commit API keys to version control
- Share keys in emails or chat
- Use the same key across multiple applications
- Hardcode keys in client-side code

## 🔐 Authentication Methods

ToyApi supports two authentication methods:

### 1. API Key Only (Public Endpoints)
For public endpoints, only an API key is required:

```bash
curl -H "X-API-Key: your-api-key-here" \
     https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message
```

### 2. JWT + API Key (Protected Endpoints)
For protected endpoints, you need both an API key and a JWT token:

```bash
# Step 1: Login to get JWT token
curl -X POST https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "TestPassword123"
  }'

# Step 2: Use both API key and JWT token
curl -H "X-API-Key: your-api-key-here" \
     -H "Authorization: Bearer your-jwt-token-here" \
     https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items
```

## 🌐 Making API Calls

### Base URL
```
https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev
```

### Headers
```
X-API-Key: your-api-key-here
Authorization: Bearer your-jwt-token-here (for protected endpoints)
Content-Type: application/json (for POST/PUT requests)
```

### Available Endpoints

#### Public Endpoints (API Key Only)
- `GET /public/message` - Get public message
- `POST /auth/login` - User login
- `POST /developer/register` - Developer registration

#### Protected Endpoints (JWT + API Key Required)
- `GET /auth/message` - Get authenticated message
- `GET /auth/user/{userId}/message` - Get user-specific message
- `GET /items` - List all items
- `POST /items` - Create new item
- `GET /items/{id}` - Get specific item
- `PUT /items/{id}` - Update item
- `DELETE /items/{id}` - Delete item

#### Developer Management (API Key Only)
- `GET /developer/profile` - Get developer profile
- `PUT /developer/profile` - Update developer profile
- `POST /developer/api-key` - Create API key
- `GET /developer/api-keys` - List API keys
- `DELETE /developer/api-key/{keyId}` - Delete API key

### Example Requests

#### JavaScript/Fetch
```javascript
// Public endpoint
const response = await fetch('https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message', {
  headers: {
    'X-API-Key': 'your-api-key-here'
  }
});

// Protected endpoint
const protectedResponse = await fetch('https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items', {
  headers: {
    'X-API-Key': 'your-api-key-here',
    'Authorization': 'Bearer your-jwt-token-here'
  }
});

// Create item
const createResponse = await fetch('https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items', {
  method: 'POST',
  headers: {
    'X-API-Key': 'your-api-key-here',
    'Authorization': 'Bearer your-jwt-token-here',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    message: 'Hello from my app!'
  })
});
```

#### Python/Requests
```python
import requests

# Configuration
API_BASE = "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev"
API_KEY = "your-api-key-here"
JWT_TOKEN = "your-jwt-token-here"

# Headers
headers = {
    "X-API-Key": API_KEY,
    "Authorization": f"Bearer {JWT_TOKEN}",
    "Content-Type": "application/json"
}

# Get items
response = requests.get(f"{API_BASE}/items", headers=headers)
items = response.json()

# Create item
new_item = {
    "message": "Hello from Python!"
}
response = requests.post(f"{API_BASE}/items", json=new_item, headers=headers)
```

#### Node.js/Axios
```javascript
const axios = require('axios');

const api = axios.create({
  baseURL: 'https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev',
  headers: {
    'X-API-Key': 'your-api-key-here',
    'Authorization': 'Bearer your-jwt-token-here'
  }
});

// Get items
const items = await api.get('/items');

// Create item
const newItem = await api.post('/items', {
  message: 'Hello from Node.js!'
});
```

## ⚡ Rate Limits & Quotas

### Current Limits
- **Rate Limit**: 100 requests per second
- **Burst Limit**: 200 requests (short bursts)
- **Daily Quota**: 10,000 requests per day
- **Key Limit**: 10 API keys per developer

### Rate Limit Headers
API responses include rate limit information:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640995200
```

### Handling Rate Limits
```javascript
async function makeApiCall(url) {
  try {
    const response = await fetch(url, {
      headers: { 'X-API-Key': 'your-key' }
    });
    
    if (response.status === 429) {
      // Rate limited - wait and retry
      const retryAfter = response.headers.get('Retry-After') || 60;
      console.log(`Rate limited. Retrying after ${retryAfter} seconds`);
      await new Promise(resolve => setTimeout(resolve, retryAfter * 1000));
      return makeApiCall(url); // Retry
    }
    
    return response.json();
  } catch (error) {
    console.error('API call failed:', error);
    throw error;
  }
}
```

## 🚨 Error Handling

### Common HTTP Status Codes

| Code | Meaning | Description |
|------|---------|-------------|
| 200 | OK | Request successful |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid request data |
| 401 | Unauthorized | Missing or invalid JWT token |
| 403 | Forbidden | Missing or invalid API key |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Resource already exists |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

### Error Response Format
```json
{
  "error": "Invalid API key",
  "statusCode": 403
}
```

### Handling Errors
```javascript
async function handleApiResponse(response) {
  if (!response.ok) {
    const error = await response.json();
    
    switch (response.status) {
      case 403:
        console.error('API key invalid or missing');
        // Redirect to key management
        break;
      case 429:
        console.error('Rate limit exceeded');
        // Implement backoff strategy
        break;
      case 500:
        console.error('Server error');
        // Retry with exponential backoff
        break;
      default:
        console.error('API error:', error.error);
    }
    
    throw new Error(error.error || 'API call failed');
  }
  
  return response.json();
}
```

## 💡 Best Practices

### API Key Management
- **Environment Variables**: Store keys in environment variables
- **Key Rotation**: Rotate keys monthly or after security incidents
- **Monitoring**: Monitor key usage for unusual patterns
- **Scoping**: Use different keys for different applications/environments

### Request Optimization
- **Caching**: Cache responses when appropriate
- **Batching**: Batch multiple operations when possible
- **Compression**: Use gzip compression for large payloads
- **Pagination**: Handle paginated responses properly

### Error Handling
- **Retry Logic**: Implement exponential backoff for retries
- **Graceful Degradation**: Handle API failures gracefully
- **Logging**: Log API errors for debugging
- **User Feedback**: Provide meaningful error messages to users

### Security
- **HTTPS Only**: Always use HTTPS for API calls
- **Input Validation**: Validate all input data
- **Token Refresh**: Implement JWT token refresh logic
- **Secure Storage**: Store tokens and keys securely

## 🔧 Troubleshooting

### Common Issues

#### "Invalid API Key" (403 Forbidden)
- Verify key is correct and active
- Check X-API-Key header format
- Ensure key hasn't been deleted

#### "Unauthorized" (401)
- JWT token missing or expired
- Login again to get fresh token
- Check Authorization header format

#### "Rate Limited" (429)
- Slow down requests
- Implement backoff strategy
- Check daily quota usage

#### "Not Found" (404)
- Verify endpoint URL
- Check resource ID exists
- Confirm API base URL

### Debug Checklist
- [ ] API key is valid and active
- [ ] Headers are properly formatted
- [ ] JWT token is not expired
- [ ] Request body is valid JSON
- [ ] Endpoint URL is correct
- [ ] Rate limits not exceeded

### Testing Tools
```bash
# Test API key validity
curl -v -H "X-API-Key: your-key" \
  https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message

# Test with verbose output
curl -v -H "X-API-Key: your-key" \
     -H "Authorization: Bearer your-token" \
     https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items

# Check rate limit headers
curl -I -H "X-API-Key: your-key" \
  https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message
```

## 📞 Support

### Self-Service Resources
1. **Developer Portal**: Use the web portal for key management
2. **API Documentation**: Complete reference in portal docs tab
3. **Status Page**: Check for service interruptions
4. **Example Code**: See code samples above

### Getting Help
- **GitHub Issues**: Report bugs and feature requests
- **Email Support**: Contact for account issues
- **Community Forum**: Connect with other developers

### Support Response Times
- **Critical Issues**: 2-4 hours
- **General Questions**: 24-48 hours
- **Feature Requests**: 1-2 weeks

---

## 🚀 Next Steps

1. **Register**: Complete developer registration
2. **Create Keys**: Set up API keys for your environments
3. **Test**: Make your first API calls
4. **Build**: Integrate into your application
5. **Monitor**: Track usage and performance
6. **Scale**: Optimize for production workloads

Happy coding! 🎉