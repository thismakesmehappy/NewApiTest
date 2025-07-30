# ToyApi - AWS Serverless API Project

## 🎯 Status: ENTERPRISE READY + APPROVAL GATES ✅

**Complete serverless API with enterprise-grade CI/CD and deployment controls**

- **Live APIs**: 
  - Dev: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/`
  - Staging: `https://8dida7flbl.execute-api.us-east-1.amazonaws.com/stage/`
  - Production: `https://55g7hsw2c1.execute-api.us-east-1.amazonaws.com/prod/`
- **CI/CD**: GitHub Actions with approval gates and multi-environment deployment
- **Authentication**: Real AWS Cognito JWT tokens across all environments
- **Infrastructure**: Fully isolated multi-environment CDK stacks
- **Testing**: Comprehensive API testing with detailed reports

## 🔧 CRITICAL SESSION CONTEXT

### Working Features:
- ✅ **9 API endpoints** (public, auth, CRUD) with JWT security
- ✅ **GitHub Actions CI/CD** with automated deployment pipeline
- ✅ **Local development** with warm containers (40x faster)
- ✅ **Security scanning** with OWASP dependency check
- ✅ **Multi-environment** deployment (dev/stage/prod)

### Key Implementation Notes:
- **Items use `message` field** (not name/description per OpenAPI)
- **Use `idToken`** for API requests (not accessToken)
- **Test user**: `testuser` / `TestPassword123`
- **GitHub repo**: `https://github.com/thismakesmehappy/NewApiTest`

### Quick Commands:
```bash
# Test API
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" -d '{"username":"testuser","password":"TestPassword123"}'

# Local development
cd local-dev && ./scripts/start-local-dev.sh

# Deploy
cd infra && ./scripts/deploy-stage.sh
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

## 🚀 CI/CD Pipeline Status

### GitHub Actions Workflows:
- ✅ **Build & Test** - Maven multi-module builds with caching
- ✅ **Security Scan** - OWASP dependency check with NVD resilience
- ✅ **Multi-env Deploy** - Automated dev→staging→production pipeline
- ✅ **PR Validation** - Code quality, security, CDK synthesis checks
- ✅ **Dependabot** - Weekly dependency updates with grouping

### Local Development:
- ✅ **SAM Local** with warm containers (40x performance boost)
- ✅ **Mock Database** with persistent sessions
- ✅ **Docker Compose** - DynamoDB Local, auto-setup
- ✅ **Hot Reload** - No container restarts needed

## 🎯 Available Actions

```bash
# CI/CD (automated via GitHub)
git push origin main  # → staging → production

# Local development  
./local-dev/scripts/start-local-dev.sh

# Manual deployment
./infra/scripts/deploy-stage.sh
./infra/scripts/deploy-prod.sh

# API testing
# Import: ToyApi_*_Insomnia_Collection.json
```

## 🔑 Critical AWS Information

- **AWS Account**: 375004071203  
- **Region**: us-east-1
- **API Base URL**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/`
- **User Pool ID**: `us-east-1_rtm5EgiL1`
- **Client ID**: `e7tntsf3vrde93qcakghlibfo`  
- **DynamoDB Table**: `toyapi-dev-items`
- **Test User**: username: `testuser`, password: `TestPassword123`

## 📋 Current State Summary

**✅ Enterprise-Ready Serverless API**
- **9 endpoints** with Cognito JWT auth
- **CI/CD pipeline** with GitHub Actions  
- **Multi-environment** (dev/stage/prod)
- **Local development** with 40x faster warm containers
- **Security scanning** with automated dependency updates

**🔧 For Troubleshooting:**
- Items API uses `message` field (not OpenAPI spec)
- Always use `idToken` (not `accessToken`) 
- GitHub Actions: `https://github.com/thismakesmehappy/NewApiTest/actions`
- Test user: `testuser` / `TestPassword123`

**📚 Documentation:**
- `API_TESTING_GUIDE.md` - Complete API docs
- `ToyApi_*_Insomnia_Collection.json` - Ready-to-import collections
- `.github/README.md` - CI/CD setup guide
- `local-dev/DEVELOPMENT_GUIDE.md` - Local development guide

**Last Updated**: 2025-07-28  
**Status**: Production-ready with automated CI/CD 