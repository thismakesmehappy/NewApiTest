# ToyApi Local Development Environment

This directory contains everything needed to run ToyApi locally for fast development and testing.

## 🚀 Quick Start

```bash
# Start the complete local development environment
cd local-dev
./scripts/start-local-dev.sh
```

This single command will:
- ✅ Build the service JAR if needed
- ✅ Start local DynamoDB with Docker
- ✅ Create the DynamoDB table structure
- ✅ Start SAM Local API Gateway
- ✅ Set up all environment variables

## 📋 Prerequisites

- **Docker Desktop** - For local DynamoDB
- **SAM CLI** - For local API Gateway simulation
- **Maven** - For building the service
- **AWS CLI** - For DynamoDB operations

### Install SAM CLI

```bash
# macOS
brew install aws-sam-cli

# Windows/Linux
# See: https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html
```

## 🔧 Local Environment Details

### Services Running Locally

| Service | URL | Purpose |
|---------|-----|---------|
| **API Gateway** | `http://localhost:3000` | Main API endpoints |
| **DynamoDB** | `http://localhost:8000` | Local database |
| **DynamoDB Admin** | `http://localhost:8001` | Database management UI |

### Environment Configuration

The local environment uses:
- **Mock Authentication** - No real Cognito, simplified for development
- **Local DynamoDB** - Isolated from AWS, data persists in Docker volume
- **Hot Reload** - SAM Local automatically picks up JAR changes

## 🧪 Testing the Local API

### Using Insomnia
1. Import `ToyApi_Local_Insomnia_Collection.json`
2. Use the "Local Development Environment" 
3. No authentication needed - uses mock tokens

### Using cURL

```bash
# Test public endpoint
curl http://localhost:3000/public/message

# Test mock authentication
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "localuser", "password": "localpass"}'

# Test protected endpoint with mock token
curl http://localhost:3000/auth/message \
  -H "Authorization: Bearer local-dev-mock-token-12345"

# Test items endpoints
curl http://localhost:3000/items \
  -H "Authorization: Bearer local-dev-mock-token-12345"
```

## 📁 Directory Structure

```
local-dev/
├── README.md                    # This file
├── template.yaml               # SAM template for local API
├── docker-compose.yml          # Local DynamoDB setup
├── .env.local                  # Environment variables
└── scripts/
    ├── start-local-dev.sh      # Main startup script
    └── setup-local-dynamodb.sh # DynamoDB table setup
```

## 🔄 Development Workflow

### 1. Make Code Changes
Edit files in `service/src/main/java/`

### 2. Rebuild Service (if needed)
```bash
cd service
mvn clean package
```

### 3. Restart SAM Local
SAM Local will automatically pick up the new JAR file.

### 4. Test Changes
Use Insomnia or cURL to test your changes immediately.

## 🗄️ Database Management

### View Data
- **Admin UI**: http://localhost:8001
- **CLI**: `aws dynamodb scan --table-name toyapi-local-items --endpoint-url http://localhost:8000`

### Reset Database
```bash
# Stop and restart with fresh data
docker-compose down
docker-compose up -d
./scripts/setup-local-dynamodb.sh
```

## 🛠️ Troubleshooting

### Port Already in Use
```bash
# Kill processes on ports
lsof -ti:3000,8000,8001 | xargs kill -9
```

### DynamoDB Connection Issues
```bash
# Restart DynamoDB container
docker-compose restart dynamodb-local
```

### Service Build Issues
```bash
# Clean rebuild
cd service
mvn clean package -U
```

### SAM Local Issues
```bash
# Check SAM version
sam --version

# Clear SAM cache
rm -rf ~/.aws-sam
```

## 🔀 Differences from AWS Environment

| Feature | Local | AWS |
|---------|-------|-----|
| **Authentication** | Mock tokens | Real Cognito JWT |
| **Database** | Local DynamoDB | AWS DynamoDB |
| **CORS** | Permissive | Configured |
| **Logging** | Console output | CloudWatch |
| **Scaling** | Single instance | Auto-scaling |

## 📝 Environment Variables

Key variables in `.env.local`:

```bash
# Core Configuration
ENVIRONMENT=local
TABLE_NAME=toyapi-local-items
DYNAMODB_ENDPOINT=http://localhost:8000

# Mock Authentication
MOCK_AUTHENTICATION=true
SKIP_COGNITO_VALIDATION=true
LOCAL_TEST_USER_ID=local-user-12345

# Development Flags
DEBUG=true
USE_LOCAL_DYNAMODB=true
ENABLE_CORS=true
```

## 🚨 Important Notes

- **Data Persistence**: Local DynamoDB data persists between restarts
- **Mock Auth**: Authentication is simplified - all requests with any Bearer token work
- **No Budget Limits**: No AWS costs, but also no production constraints
- **Environment Isolation**: Completely separate from dev/stage/prod environments

## 🎯 Next Steps

1. **Start Development**: `./scripts/start-local-dev.sh`
2. **Import Insomnia Collection**: Use `ToyApi_Local_Insomnia_Collection.json`
3. **Begin Coding**: Edit service files and test immediately
4. **Deploy to AWS**: When ready, use `cd infra && ./scripts/deploy-dev.sh`