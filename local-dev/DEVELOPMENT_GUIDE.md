# ToyApi Local Development Guide

This guide shows you how to use the local development environment for fast, efficient ToyApi development.

## 🚀 Getting Started

### 1. Start Local Environment
```bash
cd local-dev
./scripts/start-local-dev.sh
```

This command will:
- Build your service JAR
- Start local DynamoDB with Docker
- Create the database table
- Start SAM Local API Gateway
- Make your API available at `http://localhost:3000`

### 2. Test the Setup
```bash
# In another terminal
./scripts/test-local-api.sh
```

### 3. Import Insomnia Collection
- Open Insomnia
- Import `ToyApi_Local_Insomnia_Collection.json`
- Start testing endpoints immediately

## 🔄 Development Workflow

### Making Code Changes

1. **Edit Service Code**
   ```bash
   # Edit files in service/src/main/java/
   vim service/src/main/java/co/thismakesmehappy/toyapi/service/AuthHandler.java
   ```

2. **Rebuild Service**
   ```bash
   cd service
   mvn clean package
   ```

3. **Restart SAM Local**
   - Stop SAM Local (Ctrl+C)
   - Restart: `./scripts/start-local-dev.sh`
   - Or just restart SAM: `sam local start-api --template template.yaml --port 3000`

4. **Test Changes**
   ```bash
   ./scripts/test-local-api.sh
   # Or use Insomnia/curl
   ```

### Hot Reload (Experimental)

For faster development, you can use SAM's built-in hot reload:

```bash
# Start with hot reload enabled
sam local start-api --template template.yaml --port 3000 --warm-containers EAGER
```

## 📊 Database Management

### View Data
- **Web UI**: http://localhost:8001 (DynamoDB Admin)
- **CLI**: 
  ```bash
  aws dynamodb scan --table-name toyapi-local-items --endpoint-url http://localhost:8000
  ```

### Sample Data Operations
```bash
# List all tables
aws dynamodb list-tables --endpoint-url http://localhost:8000

# Add sample data
aws dynamodb put-item \
  --table-name toyapi-local-items \
  --item '{"PK":{"S":"ITEM#test-123"},"SK":{"S":"ITEM"},"message":{"S":"Sample item"},"userId":{"S":"local-user-12345"}}' \
  --endpoint-url http://localhost:8000

# Query user items
aws dynamodb query \
  --table-name toyapi-local-items \
  --index-name UserIndex \
  --key-condition-expression "userId = :userId" \
  --expression-attribute-values '{":userId":{"S":"local-user-12345"}}' \
  --endpoint-url http://localhost:8000
```

### Reset Database
```bash
# Clean slate
docker-compose down
docker-compose up -d dynamodb-local
./scripts/setup-local-dynamodb.sh
```

## 🔧 Configuration

### Environment Variables (.env.local)
```bash
# Core settings
ENVIRONMENT=local
MOCK_AUTHENTICATION=true
USE_LOCAL_DYNAMODB=true

# Database
DYNAMODB_ENDPOINT=http://localhost:8000
TABLE_NAME=toyapi-local-items

# Authentication
LOCAL_TEST_USER_ID=local-user-12345
SKIP_COGNITO_VALIDATION=true

# Debug
DEBUG=true
LOG_LEVEL=DEBUG
```

### Mock Authentication Details

Local development uses simplified authentication:

- **Any username/password** works for login
- **Any Bearer token** works for protected endpoints  
- **Default user ID**: `local-user-12345`
- **Mock token**: `local-dev-mock-token-12345`

## 🧪 Testing Strategies

### Unit Testing
```bash
cd service
mvn test
```

### Integration Testing
```bash
# Start local environment
./scripts/start-local-dev.sh

# Run integration tests (in another terminal)
./scripts/test-local-api.sh

# Or test specific endpoints
curl http://localhost:3000/public/message
curl -H "Authorization: Bearer mock-token" http://localhost:3000/items
```

### Load Testing (Optional)
```bash
# Install hey (HTTP load testing tool)
brew install hey

# Test public endpoint
hey -n 100 -c 10 http://localhost:3000/public/message

# Test authenticated endpoint
hey -n 100 -c 10 -H "Authorization: Bearer mock-token" http://localhost:3000/items
```

## 🐛 Debugging

### Enable Debug Logging
Set `LOG_LEVEL=DEBUG` in `.env.local` and restart.

### Common Issues

**Port conflicts**:
```bash
# Kill processes using required ports
lsof -ti:3000,8000,8001 | xargs kill -9
```

**Database connection**:
```bash
# Check DynamoDB container
docker-compose ps
docker-compose logs dynamodb-local
```

**Service build errors**:
```bash
# Clean rebuild
cd service
mvn clean package -U -X
```

**SAM Local issues**:
```bash
# Check SAM version and update
sam --version
pip install --upgrade aws-sam-cli

# Clear SAM cache
rm -rf ~/.aws-sam
```

## 📝 Development Tips

### 1. Use Environment-Specific Code
```java
// In your service code
private final boolean isLocal = "local".equals(System.getenv("ENVIRONMENT"));

if (isLocal) {
    // Local development behavior
    logger.debug("Running in local development mode");
} else {
    // AWS environment behavior
}
```

### 2. Mock External Services
```java
// Example: Mock external API calls in local development
if (mockAuthentication) {
    return createMockResponse();
} else {
    return callRealService();
}
```

### 3. Database Seeding
Create a script to populate test data:
```bash
#!/bin/bash
# seed-local-data.sh
aws dynamodb batch-write-item \
  --request-items file://test-data.json \
  --endpoint-url http://localhost:8000
```

### 4. API Documentation
Keep your local Insomnia collection updated as you add endpoints.

## 🚀 Deployment Workflow

When ready to deploy to AWS:

```bash
# 1. Test locally first
./scripts/test-local-api.sh

# 2. Deploy to dev environment
cd infra
./scripts/deploy-dev.sh

# 3. Test dev environment
# Use ToyApi_Insomnia_Collection.json (dev environment)

# 4. Deploy to staging when ready
./scripts/deploy-stage.sh

# 5. Deploy to production when approved
./scripts/deploy-prod.sh
```

## 📊 Performance Comparison

| Environment | Startup Time | Response Time | Cost |
|-------------|--------------|---------------|------|
| **Local** | ~30 seconds | <50ms | $0 |
| **AWS Dev** | ~2 minutes | ~200ms | ~$0.01/day |
| **AWS Prod** | ~3 minutes | ~100ms | Variable |

## 🎯 Next Steps

1. **Master Local Development**: Get comfortable with the local workflow
2. **Add New Features**: Use local environment to develop faster
3. **Set up CI/CD**: Automate the dev→stage→prod pipeline
4. **Add Frontend**: Create a UI that consumes your API

The local development environment gives you the fastest possible feedback loop for ToyApi development!