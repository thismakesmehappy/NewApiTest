# ToyApi Local Development Guide

This guide shows you how to use the local development environment for fast, efficient ToyApi development.

## 🚀 Getting Started

### 1. Start Local Environment
```bash
cd local-dev
./scripts/start-local-dev.sh
```

This enhanced startup script will:
- ✅ **Validate prerequisites** (Docker, SAM CLI, Maven, AWS CLI)
- ✅ **Check port availability** (3000, 8000, 8001) with conflict resolution
- ✅ **Build your service JAR** (only if needed or source changed)
- ✅ **Start local DynamoDB with Docker** using persistent volumes
- ✅ **Wait for services to be ready** with configurable timeouts
- ✅ **Start SAM Local API Gateway** with proper environment configuration
- ✅ **Provide comprehensive status** and next steps

**Note**: Due to AWS CLI timeout issues with local DynamoDB, the table creation step is skipped during startup. You'll need to create the table manually after startup (see step 2b below).

### 2a. Monitor Environment Health
```bash
# Check all services status
./scripts/health-check.sh
```

The health check script validates:
- Docker services status (DynamoDB Local)
- Service connectivity and response
- Database table existence
- Required files (JAR, environment config)
- Provides actionable troubleshooting guidance

### 2b. Create Database Table (Required)
Due to AWS CLI timeout issues, you need to manually create the DynamoDB table after startup:

```bash
# Easy one-command table creation
./scripts/create-table-manual.sh
```

**Alternative manual command** (if the script doesn't work):
```bash
aws dynamodb create-table \
  --table-name toyapi-local-items \
  --attribute-definitions '[
    {"AttributeName":"PK","AttributeType":"S"},
    {"AttributeName":"SK","AttributeType":"S"},
    {"AttributeName":"userId","AttributeType":"S"},
    {"AttributeName":"createdAt","AttributeType":"S"}
  ]' \
  --key-schema '[
    {"AttributeName":"PK","KeyType":"HASH"},
    {"AttributeName":"SK","KeyType":"RANGE"}
  ]' \
  --global-secondary-indexes '[
    {
      "IndexName":"UserIndex",
      "KeySchema":[
        {"AttributeName":"userId","KeyType":"HASH"},
        {"AttributeName":"createdAt","KeyType":"RANGE"}
      ],
      "Projection":{"ProjectionType":"ALL"},
      "ProvisionedThroughput":{"ReadCapacityUnits":5,"WriteCapacityUnits":5}
    }
  ]' \
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
  --endpoint-url http://localhost:8000
```

**Verify table creation**:
```bash
aws dynamodb list-tables --endpoint-url http://localhost:8000
```

### 3. Test the Setup
```bash
# In another terminal (comprehensive API testing)
./scripts/test-local-api.sh
```

### 4. Import Insomnia Collection
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

### Quick 
Test Suite
```bash
# Start local environment
./scripts/start-local-dev.sh

# Run all endpoint tests (in another terminal)
./scripts/test-local-api.sh
```

### Manual Testing with cURL
```bash
# 1. Test public endpoint
curl http://localhost:3000/public/message

# 2. Test login (mock authentication)
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"localuser","password":"localpass"}'

# 3. Test authenticated endpoints
curl -H "Authorization: Bearer local-dev-mock-token-12345" \
  http://localhost:3000/auth/message

curl -H "Authorization: Bearer local-dev-mock-token-12345" \
  http://localhost:3000/auth/user/local-user-12345/message

# 4. Test items CRUD
# List items (initially empty)
curl -H "Authorization: Bearer local-dev-mock-token-12345" \
  http://localhost:3000/items

# Create an item
curl -X POST http://localhost:3000/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer local-dev-mock-token-12345" \
  -d '{"message":"My test item"}'

# List items again (should show 1 item)
curl -H "Authorization: Bearer local-dev-mock-token-12345" \
  http://localhost:3000/items
```

### Unit Testing
```bash
cd service
mvn test
```

### Integration Testing with Insomnia
1. Import `ToyApi_Local_Insomnia_Collection.json`
2. Select "Local Development Environment"
3. Collection provides complete coverage:
   - **Authentication & Messages folder**: Public, login, auth endpoints
   - **Items CRUD folder**: Complete item management workflow
4. All requests pre-configured with mock authentication
5. Environment variables automatically set for local development
6. Perfect for interactive testing and API exploration

### Load Testing (Optional)
```bash
# Install hey (HTTP load testing tool)
brew install hey

# Test public endpoint
hey -n 100 -c 10 http://localhost:3000/public/message

# Test authenticated endpoint
hey -n 100 -c 10 -H "Authorization: Bearer mock-token" http://localhost:3000/items
```

## 🛠️ Local Environment Operations

### Professional Startup/Shutdown
The local development environment now includes production-grade operational scripts:

#### Start Development Environment
```bash
./scripts/start-local-dev.sh
```
**Features:**
- Comprehensive prerequisite validation (Docker, SAM, Maven, AWS CLI)
- Port conflict detection with helpful resolution guidance
- Smart build detection (only rebuilds when source files change)
- Service health waiting with configurable timeouts
- Graceful error handling with detailed error messages
- Automatic cleanup on script interruption (Ctrl+C)

#### Stop Development Environment
```bash
./scripts/stop-local-dev.sh

# Clean shutdown with cache clearing
./scripts/stop-local-dev.sh --clean
```
**Features:**
- Graceful service termination (TERM → KILL if needed)
- Complete Docker service cleanup
- Port-specific process management
- SAM cache clearing option
- Comprehensive status reporting

#### Health Check & Monitoring
```bash
./scripts/health-check.sh
```
**Validates:**
- ✅ Docker services status and health
- ✅ DynamoDB Local connectivity
- ✅ SAM Local API responsiveness
- ✅ Database table existence
- ✅ Service JAR and environment files
- ✅ Provides actionable troubleshooting guidance

### Environment Management

#### Enhanced DynamoDB Setup
```bash
./scripts/setup-local-dynamodb.sh

# Non-interactive mode (for automation)
RECREATE_TABLE=y ./scripts/setup-local-dynamodb.sh
```
**Features:**
- Non-interactive mode support for CI/CD
- Improved table recreation with proper waiting
- Comprehensive error handling and validation
- Timeout handling for all operations

#### Docker Compose Improvements
- **Persistent volumes**: Data survives container restarts
- **Proper service dependencies**: Reliable startup order
- **Simplified configuration**: Removed problematic health checks
- **Enhanced networking**: Isolated network for clean separation

### Stability Features

The local development environment now includes enterprise-grade stability:

#### Error Recovery
- All scripts handle failures gracefully
- Detailed error messages with resolution guidance
- Automatic rollback on partial failures
- Comprehensive logging for troubleshooting

#### Process Management
- Proper cleanup of all services on exit/interrupt
- Port conflict detection and resolution
- Service dependency management
- Graceful shutdown procedures

#### Environment Validation
- Prerequisite checking before startup
- Configuration file validation
- Service readiness confirmation
- Health monitoring and reporting

## 🐛 Debugging

### Enable Debug Logging
Set `LOG_LEVEL=DEBUG` in `.env.local` and restart.

### Common Issues

**Environment not starting**:
```bash
# Run comprehensive health check first
./scripts/health-check.sh

# If startup fails, check logs
./scripts/start-local-dev.sh
# Script provides detailed error messages and resolution guidance
```

**Port conflicts**:
```bash
# The startup script now detects port conflicts automatically
# and provides specific resolution commands, but you can also:
lsof -ti:3000,8000,8001 | xargs kill -9

# Or use the professional shutdown script
./scripts/stop-local-dev.sh
```

**Database connection issues**:
```bash
# Check service status
./scripts/health-check.sh

# Inspect Docker services
docker-compose ps
docker-compose logs dynamodb-local

# Reset database completely
./scripts/stop-local-dev.sh
docker-compose down --volumes  # Remove persistent data
./scripts/start-local-dev.sh
```

**AWS CLI timeout issues with DynamoDB**:
```bash
# This is a known issue - AWS CLI sometimes hangs when connecting to DynamoDB Local
# Symptoms: Script gets stuck on "Checking if table exists..." or "Creating DynamoDB table..."

# Solution 1: Use the manual table creation script
./scripts/create-table-manual.sh

# Solution 2: Skip table creation entirely during startup
# The startup script now automatically skips problematic table creation

# Solution 3: Verify DynamoDB is responsive
curl -s http://localhost:8000  # Should return authentication error (this is normal)

# Solution 4: Check AWS CLI configuration
aws configure list  # Should show local credentials
```

**Table creation failures**:
```bash
# If table creation fails, manually create with simple command:
aws dynamodb create-table \
  --table-name toyapi-local-items \
  --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S \
  --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
  --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 \
  --endpoint-url http://localhost:8000

# Verify table exists
aws dynamodb list-tables --endpoint-url http://localhost:8000
```

**Service build errors**:
```bash
# The startup script rebuilds automatically when needed
# For manual rebuild:
cd service
mvn clean package -U -X

# Check if JAR exists
./scripts/health-check.sh
```

**SAM Local issues**:
```bash
# Check SAM version and update
sam --version
pip install --upgrade aws-sam-cli

# Clear SAM cache (built into stop script)
./scripts/stop-local-dev.sh --clean

# Check SAM telemetry (disabled automatically)
export SAM_CLI_TELEMETRY=0
```

**Items endpoints returning "Connection refused" errors**:
This happens when SAM Local Lambda containers can't access DynamoDB Local due to Docker networking isolation.

```bash
# ✅ SOLUTION: The scripts now automatically use Docker networking
# The startup script includes: --docker-network local-dev_toyapi-local
# Environment configured with: DYNAMODB_ENDPOINT=http://toyapi-dynamodb-local:8000

# Verify networking is working:
docker network ls | grep toyapi-local
docker inspect local-dev_toyapi-local

# If needed, restart with proper networking:
./scripts/stop-local-dev.sh
./scripts/start-local-dev.sh  # Uses updated networking automatically
```

**Services stuck or hanging**:
```bash
# Professional shutdown handles this automatically
./scripts/stop-local-dev.sh

# For stubborn processes, use force cleanup
./scripts/stop-local-dev.sh --clean

# Check if cleanup was successful
./scripts/health-check.sh
```

**Environment validation errors**:
```bash
# The startup script validates prerequisites automatically
# Common fixes for validation failures:

# Docker not running
open -a Docker

# Missing SAM CLI
brew install aws-sam-cli

# Missing Maven
brew install maven

# Missing AWS CLI
brew install awscli

# Missing environment files
cp .env.local.example .env.local  # Edit as needed
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