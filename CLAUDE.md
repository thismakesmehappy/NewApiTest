# ToyApi - AWS Serverless API Project

## 🎯 Current Status: PRODUCTION READY ✅

**FULLY OPERATIONAL** serverless API with real Cognito JWT authentication deployed to AWS.

- **Live API**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/`
- **Authentication**: Real AWS Cognito JWT tokens working
- **All Endpoints**: 9 endpoints fully implemented and tested
- **Infrastructure**: Complete CDK stack deployed to AWS
- **Documentation**: Comprehensive testing guides and API docs available

## 🚨 CRITICAL CONTEXT FOR FUTURE SESSIONS

### What's Currently Working:
1. **Real JWT Auth**: Login returns actual Cognito tokens, API Gateway validates them
2. **All Endpoints**: Public, auth, and items CRUD all working with proper security
3. **AWS Infrastructure**: DynamoDB, Lambda, API Gateway, Cognito all deployed
4. **Testing Tools**: Insomnia collection and comprehensive curl examples ready

### Key Implementation Details:
- **Items API uses `message` field** (not name/description/category as in OpenAPI spec)
- **Use `idToken` for auth** (not accessToken) - API Gateway Cognito authorizer requirement
- **Test user**: username: `testuser`, password: `TestPassword123`
- **Environment**: Currently on `dev` environment, ready for stage/prod

### If Authentication Fails:
1. Check if admin auth flow enabled: `ALLOW_ADMIN_USER_PASSWORD_AUTH`
2. User Pool ID: `us-east-1_rtm5EgiL1`, Client ID: `e7tntsf3vrde93qcakghlibfo`
3. Use idToken (not accessToken) for API requests

### Quick Test Commands:
```bash
# Login
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" -H "Content-Type: application/json" -d '{"username":"testuser","password":"TestPassword123"}'

# Test protected endpoint (use idToken from login)
curl -X GET "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/message" -H "Authorization: Bearer <idToken>"

# Create item (use idToken)
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/items" -H "Content-Type: application/json" -H "Authorization: Bearer <idToken>" -d '{"message":"test item"}'
```

## 📁 Current Project Structure

```
toyapi/
├── API_TESTING_GUIDE.md            # ⭐ COMPLETE API documentation with working examples
├── ToyApi_Insomnia_Collection.json # ⭐ Ready-to-import API testing collection
├── CLAUDE.md                       # ⭐ THIS FILE - Current status and context
├── pom.xml                         # Root Maven multi-module configuration
├── service/                        # ⭐ DEPLOYED Lambda handlers
│   ├── pom.xml                     # Cognito + DynamoDB + JWT dependencies
│   └── src/main/java/.../service/
│       ├── AuthHandler.java        # ⭐ REAL Cognito JWT auth working
│       ├── ItemsHandler.java       # ⭐ CRUD with DynamoDB working
│       └── PublicHandler.java      # ⭐ Public endpoints working
├── infra/                          # ⭐ DEPLOYED CDK infrastructure
│   ├── src/main/java/.../infra/
│   │   └── ToyApiStack.java        # Complete AWS stack deployed
│   └── scripts/deploy-*.sh         # Multi-environment deployment
└── model/openapi/api-spec.yaml     # OpenAPI 3.0.3 spec (items use 'message' field)
```

## ✅ FULLY COMPLETED PROJECT

### Current Working API Endpoints (9 total):
1. **GET /public/message** - ✅ Public access, no auth needed
2. **POST /auth/login** - ✅ Returns real Cognito JWT tokens
3. **GET /auth/message** - ✅ Protected, requires idToken
4. **GET /auth/user/{userId}/message** - ✅ User-specific, requires idToken  
5. **GET /items** - ✅ List user's items, requires idToken
6. **POST /items** - ✅ Create item ({"message":"content"}), requires idToken
7. **GET /items/{id}** - ✅ Get specific item, requires idToken
8. **PUT /items/{id}** - ✅ Update item ({"message":"content"}), requires idToken  
9. **DELETE /items/{id}** - ✅ Delete item, requires idToken

### Infrastructure Deployed:
- ✅ **API Gateway** with Cognito authorizer at `785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev`
- ✅ **3 Lambda Functions** (PublicHandler, AuthHandler, ItemsHandler) 
- ✅ **DynamoDB table** `toyapi-dev-items` with GSI
- ✅ **Cognito User Pool** `us-east-1_rtm5EgiL1` with admin auth flow enabled
- ✅ **CloudWatch Logs** with cost optimization
- ✅ **Budget monitoring** with email alerts

## 🎯 NEXT STEPS (Priority Order)

### Immediate Options:
1. **Deploy to Stage/Prod** - `./infra/scripts/deploy-stage.sh` or `deploy-prod.sh`
2. **Add More Features** - User registration, forgot password, item categories
3. **Local Development** - SAM local testing setup
4. **CI/CD Pipeline** - GitHub Actions for automated deployment
5. **Monitoring** - CloudWatch dashboards and alarms
6. **Performance** - Load testing and optimization

### Quick Deploy Commands:
```bash
# Deploy to staging
cd infra && ./scripts/deploy-stage.sh

# Deploy to production (with safety checks)
cd infra && ./scripts/deploy-prod.sh

# Re-deploy dev with changes
cd infra && ./scripts/deploy-dev.sh
```

## 🔑 Critical AWS Information

- **AWS Account**: 375004071203  
- **Region**: us-east-1
- **API Base URL**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/`
- **User Pool ID**: `us-east-1_rtm5EgiL1`
- **Client ID**: `e7tntsf3vrde93qcakghlibfo`  
- **DynamoDB Table**: `toyapi-dev-items`
- **Test User**: username: `testuser`, password: `TestPassword123`

## 📚 Key Documentation Files

- **API_TESTING_GUIDE.md** - Complete API documentation with working examples
- **ToyApi_Insomnia_Collection.json** - Ready-to-import API testing collection  
- **DEPLOYMENT_GUIDE.md** - Step-by-step deployment instructions
- **local-secrets.md** - AWS credentials and sensitive config (git-ignored)

---

## 🎉 PROJECT STATUS: PRODUCTION READY

**✅ Complete serverless API with enterprise-grade Cognito JWT authentication**

- 🔐 **Real Authentication**: Working Cognito integration
- 🌐 **All 9 Endpoints**: Fully functional and tested
- ☁️ **AWS Infrastructure**: Deployed and operational  
- 📖 **Documentation**: Complete with testing examples
- 🚀 **Ready for**: Stage/Prod deployment or feature additions

**Last Updated**: 2025-07-27  
**Git Status**: 16 meaningful commits representing full development progression