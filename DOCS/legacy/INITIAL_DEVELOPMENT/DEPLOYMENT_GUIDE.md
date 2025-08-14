# ToyApi Deployment Guide

## 🚀 Quick Start - Deploy to AWS

Your project is now ready for deployment! Follow these steps to get your API running on AWS.

### Prerequisites

1. **AWS CLI configured** with appropriate permissions
2. **CDK CLI installed**: `npm install -g aws-cdk`
3. **Java 17** installed and configured

### Step 1: Verify Build

```bash
# Ensure everything compiles
mvn clean compile

# Package the service for deployment
mvn clean package -pl service
```

### Step 2: Deploy Infrastructure

```bash
# Navigate to infrastructure directory
cd infra

# Deploy to development environment
./scripts/deploy-dev.sh
```

This will:
- Bootstrap CDK (if needed)
- Build and package your Lambda functions
- Deploy all AWS resources (API Gateway, Lambda, DynamoDB, Cognito)
- Output the API URL and resource information

### Step 3: Test Your API

After deployment, you'll get an API URL like: `https://abc123.execute-api.us-east-1.amazonaws.com/dev`

#### Test Public Endpoint
```bash
curl https://YOUR_API_URL/public/message
```

Expected response:
```json
{
  "message": "Hello from ToyApi public endpoint! Environment: dev",
  "timestamp": "2025-07-24T14:00:00Z"
}
```

#### Test Authentication
```bash
# Login to get a token
curl -X POST https://YOUR_API_URL/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "testpass"}'
```

Expected response:
```json
{
  "token": "mock-jwt-token-1234567890",
  "userId": "user-12345",
  "expiresIn": 3600
}
```

#### Test Authenticated Endpoints
```bash
# Use the token from login response
curl https://YOUR_API_URL/auth/message \
  -H "Authorization: Bearer mock-jwt-token-1234567890"
```

#### Test Items CRUD
```bash
# Create an item
curl -X POST https://YOUR_API_URL/items \
  -H "Authorization: Bearer mock-jwt-token-1234567890" \
  -H "Content-Type: application/json" \
  -d '{"message": "My first item"}'

# List items
curl https://YOUR_API_URL/items \
  -H "Authorization: Bearer mock-jwt-token-1234567890"

# Get specific item
curl https://YOUR_API_URL/items/ITEM_ID \
  -H "Authorization: Bearer mock-jwt-token-1234567890"

# Update item
curl -X PUT https://YOUR_API_URL/items/ITEM_ID \
  -H "Authorization: Bearer mock-jwt-token-1234567890" \
  -H "Content-Type: application/json" \
  -d '{"message": "Updated item message"}'

# Delete item
curl -X DELETE https://YOUR_API_URL/items/ITEM_ID \
  -H "Authorization: Bearer mock-jwt-token-1234567890"
```

## 🔧 Environment Management

### Deploy to Different Environments

```bash
# Development (default)
./scripts/deploy-dev.sh

# Staging
./scripts/deploy-stage.sh

# Production (requires confirmation)
./scripts/deploy-prod.sh
```

### Environment Differences

- **Dev**: `toyapi-dev-*` resources, auto-destroy on stack deletion
- **Stage**: `toyapi-stage-*` resources, auto-destroy on stack deletion  
- **Prod**: `toyapi-prod-*` resources, retained on stack deletion

## 📊 Monitoring & Cost Control

### Budget Alerts
Your deployment includes budget monitoring with email alerts at:
- 50% of $10 monthly budget
- 75% of $10 monthly budget
- 85% of $10 monthly budget
- 95% of $10 monthly budget (forecasted)

**Important**: Update the email address in `ToyApiStack.java` line 245 before deployment!

### CloudWatch Logs
Monitor your Lambda functions:
- `/aws/lambda/toyapi-dev-public`
- `/aws/lambda/toyapi-dev-auth`
- `/aws/lambda/toyapi-dev-items`

### DynamoDB Table
- Table name: `toyapi-dev-items`
- Pay-per-request billing (cost-effective for low traffic)
- Point-in-time recovery enabled

## 🛠️ Development Workflow

### Making Changes

1. **Update code** in `service/src/main/java/`
2. **Rebuild**: `mvn clean package -pl service`
3. **Redeploy**: `cd infra && ./scripts/deploy-dev.sh`

### Update API Specification

1. **Edit** `model/openapi/api-spec.yaml`
2. **Regenerate models**: `mvn clean compile -pl model`
3. **Update handlers** in service module as needed
4. **Redeploy**: Follow development workflow above

## 🔍 Troubleshooting

### Common Issues

1. **CDK Bootstrap Error**
   ```bash
   cdk bootstrap --context environment=dev
   ```

2. **Lambda Package Not Found**
   ```bash
   mvn clean package -pl service
   ```

3. **Permission Errors**
   - Ensure AWS CLI has appropriate permissions
   - Check IAM roles and policies

4. **Build Failures**
   ```bash
   mvn clean compile
   ```

### Useful Commands

```bash
# Check CDK diff before deployment
cdk diff ToyApiStack-dev --context environment=dev

# View CDK synthesized template
cdk synth ToyApiStack-dev --context environment=dev

# Destroy stack (be careful!)
cdk destroy ToyApiStack-dev --context environment=dev
```

## 📋 Next Steps

After successful deployment:

1. **Set up local development** (Phase 5)
2. **Implement real Cognito authentication** (replace mock JWT)
3. **Add integration tests** (Phase 5)
4. **Set up CI/CD pipeline** (Phase 6)
5. **Add monitoring dashboards** (Phase 7)

## 🎯 Success Criteria

✅ API Gateway endpoint accessible  
✅ Public endpoint returns message  
✅ Authentication endpoint returns token  
✅ Items CRUD operations work  
✅ DynamoDB stores data correctly  
✅ Budget monitoring configured  
✅ CloudWatch logs available  

## 🆘 Need Help?

- Check CloudWatch logs for Lambda errors
- Review CDK deployment outputs
- Verify AWS CLI configuration
- Ensure all dependencies are installed

Your serverless API is ready to go! 🚀
