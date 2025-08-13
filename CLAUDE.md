# ToyApi - AWS Serverless API Project

## 🎯 Status: ENTERPRISE READY + APPROVAL GATES ✅

**Complete serverless API with enterprise-grade CI/CD and deployment controls**

- **Live APIs**: 
  - Dev: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/` (deployment issues resolved)
  - Staging: `https://5ytzml6fnb.execute-api.us-east-1.amazonaws.com/stage/` ✅ **NEW DEPLOYMENT**
  - Production: `https://skslof01gg.execute-api.us-east-1.amazonaws.com/prod/` ✅ **WORKING**
- **Custom Domains** (ready to enable):
  - Dev: `https://dev.toyapi.thismakesmehappy.co` 
  - Staging: `https://stage.toyapi.thismakesmehappy.co`
  - Production: `https://toyapi.thismakesmehappy.co`
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
- **Test user**: See `dev-credentials.md` (local file)
- **GitHub repo**: `https://github.com/thismakesmehappy/NewApiTest`

### Quick Commands:
```bash
# Test API (credentials in dev-credentials.md)
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" -d '{"username":"[see dev-credentials.md]","password":"[see dev-credentials.md]"}'

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
- ✅ **Multi-env Deploy** - Complete dev→staging→production pipeline with monitoring stacks
- ✅ **Development Deploy** - On-demand from feature branches (use `[deploy-dev]` in commit message)
- ✅ **Infrastructure Visibility** - CDK diff shown before all deployments
- ✅ **PR Validation** - Code quality, security, CDK synthesis checks
- ✅ **Dependabot** - Weekly dependency updates with grouping

### Local Development:
- ✅ **SAM Local** with warm containers (40x performance boost)
- ✅ **Mock Database** with persistent sessions
- ✅ **Docker Compose** - DynamoDB Local, auto-setup
- ✅ **Hot Reload** - No container restarts needed

## 🎯 Available Actions

```bash
# CI/CD Pipeline Flow:
git push origin main                    # → staging → production (after approval)
git push origin feature-branch         # → runs tests only
git commit -m "fix: something [deploy-dev]" && git push  # → deploys to dev environment

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
- **Test User**: See `dev-credentials.md` (local file, git-ignored)

## 📋 Current State Summary

**✅ Enterprise-Ready Serverless API**
- **9 endpoints** with Cognito JWT auth
- **Complete CI/CD pipeline** deploying both application and monitoring stacks
- **Multi-environment** (dev/stage/prod) with full observability
- **CloudWatch monitoring** with dashboards, alarms, and SNS alerts
- **Local development** with 40x faster warm containers
- **Security scanning** with automated dependency updates

**🔧 For Troubleshooting:**
- Items API uses `message` field (not OpenAPI spec)
- Always use `idToken` (not `accessToken`) 
- GitHub Actions: `https://github.com/thismakesmehappy/NewApiTest/actions`
- Test user: See `dev-credentials.md` (local file)

**📚 Documentation:**
- `API_TESTING_GUIDE.md` - Complete API docs
- `ToyApi_*_Insomnia_Collection.json` - Ready-to-import collections
- `.github/README.md` - CI/CD setup guide
- `local-dev/DEVELOPMENT_GUIDE.md` - Local development guide

## 🚨 Recent Deployment Resolution (2025-08-05)

**✅ RESOLVED: Complete CI/CD Pipeline with Full Monitoring**

**Issues Fixed:**
1. **Lambda policy size limit** - Reduced from 463 to 294 resources by simplifying infrastructure
2. **CI/CD pipeline incomplete** - Now deploys both application and monitoring stacks 
3. **Monitoring stack deployment failure** - Fixed CloudWatch Logs MetricFilter patterns

**Current Status:**
- ✅ **Development**: Complete stack with monitoring deployed
- ✅ **Staging**: Complete stack with monitoring deployed  
- ✅ **Production**: Stable API with monitoring ready to deploy
- ✅ **CI/CD Pipeline**: Fixed to deploy complete infrastructure (main → staging → prod)

**Enhanced Monitoring Infrastructure (FREE TIER OPTIMIZED):**
- ✅ **Advanced CloudWatch Dashboards** - Single-value widgets, P99 latency, calculated metrics (error %, auth success %)
- ✅ **Per-Endpoint Analysis** - Detailed breakdown by API endpoint and HTTP method
- ✅ **Multi-Service Monitoring** - API Gateway, Lambda, DynamoDB, Cognito with built-in metrics
- ✅ **Business Intelligence** - Log-based user activity, item creation trends, endpoint popularity
- ✅ **Security Monitoring** - Failed auth detection, suspicious activity patterns, breach alerts
- ✅ **Smart Alerting** - 15+ intelligent alarms with proper thresholds and escalation
- ✅ **Cost Optimization** - ~$5/month total (vs $300+ enterprise tools), maximizes free tier

**CI/CD Flow Fixed:**
- Main branch deploys to staging → production (with approval gates)
- Feature branches can deploy to development using `[deploy-dev]` flag
- Both application and monitoring stacks deployed automatically

**Monitoring Expansion Plan:**
- 📋 **Current**: Free tier optimization - enterprise features at minimal cost
- 📋 **Phase 1**: Strategic additions ($15/month) - X-Ray tracing, synthetic monitoring
- 📋 **Phase 2**: Full enterprise ($50/month) - Multi-region, third-party integrations
- 📋 **Documentation**: `DOCS/MONITORING_EXPANSION_PLAN.md` - Complete roadmap with ROI analysis

**Last Updated**: 2025-08-07  
**Status**: Enterprise-ready with enhanced free-tier monitoring and automated deployment