# ToyApi - Production Ready Serverless API ✅

**Status: FULLY OPERATIONAL** - Real Cognito JWT authentication working

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

## 📋 API Endpoints (9 total)

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

## 🔑 Key Info

- **Live API**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev/`
- **Test User**: `testuser` / `TestPassword123`
- **Auth**: Use `idToken` (not accessToken) for API requests
- **Items**: Use `{"message":"content"}` format (not name/description)

## 📖 Documentation

- **CLAUDE.md** - Complete project status and context
- **API_TESTING_GUIDE.md** - Full API documentation with examples  
- **ToyApi_Insomnia_Collection.json** - Import for testing

## 🚀 Deploy

```bash
# Dev environment
cd infra && ./scripts/deploy-dev.sh

# Stage environment  
cd infra && ./scripts/deploy-stage.sh

# Production environment
cd infra && ./scripts/deploy-prod.sh
```

**Ready for production or additional features!**