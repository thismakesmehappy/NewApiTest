# ToyApi - Enterprise-Grade Serverless API with CI/CD 🚀

**Status: PRODUCTION READY + DEVELOPER ONBOARDING** - Complete CI/CD pipeline with API key management

## ✨ What's New

- 🔑 **Developer API Key Management** - Self-service API key registration and management
- 🌐 **Developer Portal** - Web-based onboarding interface
- 📊 **Usage Plans & Rate Limiting** - 100 req/sec, 10,000/day quotas
- 🚀 **Full CI/CD Pipeline** - GitHub Actions with automated deployment
- 📈 **Monitoring & Alerting** - CloudWatch dashboards and cost monitoring
- 🛡️ **Security Scanning** - OWASP dependency check with automated updates

## 🚀 Quick Start

```bash
# Test the live API
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/public/message"

# Login (get real JWT tokens)
curl -X POST "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"TestPassword123"}'

# Use idToken from login response for protected endpoints
curl "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/auth/message" \
  -H "Authorization: Bearer <idToken>"
```

## 🔑 Developer Onboarding

1. **Register as Developer**: Open `developer-portal/index.html` in your browser
2. **Get API Key**: Create your developer profile and generate API keys
3. **Start Building**: Use your API key with rate-limited access (100 req/sec)

## 📋 API Endpoints (14 total)

### Core API (9 endpoints)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/public/message` | None | Public message |
| POST | `/auth/login` | None | Get JWT tokens |
| GET | `/auth/message` | JWT | Protected message |
| GET | `/auth/user/{id}/message` | JWT | User-specific message |
| GET | `/items` | JWT | List user's items |
| POST | `/items` | JWT | Create item (`{"message":"content"}`) |
| GET | `/items/{id}` | JWT | Get specific item |
| PUT | `/items/{id}` | JWT | Update item (`{"message":"content"}`) |
| DELETE | `/items/{id}` | JWT | Delete item |

### Developer Management (5 endpoints)
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/developer/register` | None | Register as developer |
| GET | `/developer/profile` | Dev ID | Get developer profile |
| PUT | `/developer/profile` | Dev ID | Update developer profile |
| POST | `/developer/api-key` | Dev ID | Create new API key |
| GET | `/developer/api-keys` | Dev ID | List your API keys |
| DELETE | `/developer/api-key/{keyId}` | Dev ID | Delete API key |

## 🔑 Key Info

- **Live API**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/`
- **Test User**: `testuser` / `TestPassword123`
- **Auth**: Use `idToken` (not accessToken) for API requests
- **Items**: Use `{"message":"content"}` format (not name/description)

## 🏗️ Architecture

**Multi-Environment Serverless Architecture**
- **AWS Lambda** - Java 17 serverless functions
- **API Gateway** - RESTful endpoints with CORS support
- **DynamoDB** - NoSQL data storage with GSI
- **Cognito** - JWT authentication and user management
- **CloudWatch** - Logging, metrics, dashboards, and alerts
- **API Keys & Usage Plans** - Rate limiting and developer management

## 🚀 CI/CD Pipeline

**Automated GitHub Actions Workflow:**
- ✅ **Deployment Approval Gate** - Manual approval for production releases
- ✅ **Build & Test** - Maven compilation with caching optimization
- ✅ **Security Scanning** - OWASP dependency check with NVD resilience  
- ✅ **Multi-Environment Deploy** - Automatic dev → staging → production
- ✅ **Dependabot Integration** - Weekly dependency updates with grouping

## 🔧 Local Development

```bash
# Start local development environment
cd local-dev && ./scripts/start-local-dev.sh

# Test local endpoints
curl http://localhost:3000/public/message
```

**Features:**
- 🐳 Docker Compose with DynamoDB Local
- ⚡ SAM Local with warm containers (40x faster)
- 🔄 Hot reload without container restarts
- 📊 All 14 endpoints available locally

## 📖 Documentation

- **[CLAUDE.md](CLAUDE.md)** - Complete project status and context
- **[API_TESTING_GUIDE.md](API_TESTING_GUIDE.md)** - Full API documentation with examples  
- **[DEVELOPER_ONBOARDING_GUIDE.md](DEVELOPER_ONBOARDING_GUIDE.md)** - Developer onboarding and API key management
- **[developer-portal/index.html](developer-portal/index.html)** - Web-based developer portal
- **ToyApi_*_Insomnia_Collection.json** - Ready-to-import API testing collections

## 🚀 Deployment

### Automated (Recommended)
```bash
# Push to trigger CI/CD pipeline
git push origin main
# → Triggers automatic deployment to dev → staging → production
```

### Manual Deployment
```bash
# Dev environment
cd infra && ./scripts/deploy-dev.sh

# Stage environment  
cd infra && ./scripts/deploy-stage.sh

# Production environment (requires approval)
cd infra && ./scripts/deploy-prod.sh
```

## 📊 Monitoring & Costs

- **CloudWatch Dashboards** - Real-time API metrics and performance
- **Cost Monitoring** - $10/month budget with 50%, 75%, 85%, 95% alerts
- **Security Alerts** - Automated vulnerability scanning and updates
- **Usage Analytics** - API key usage tracking and rate limit monitoring

## 🎯 Production Features

✅ **Enterprise-Ready**
- Multi-environment deployment (dev/stage/prod)
- Automated CI/CD with approval gates
- Comprehensive monitoring and alerting
- Security scanning and dependency management

✅ **Developer Experience**  
- Self-service API key management
- Interactive web portal for onboarding
- Rate limiting and usage analytics
- Local development with hot reload

✅ **Scalability & Performance**
- Serverless auto-scaling architecture
- DynamoDB with GSI for efficient queries
- API Gateway caching and throttling
- Cost-optimized resource configuration

**Ready for production workloads and enterprise adoption! 🚀**