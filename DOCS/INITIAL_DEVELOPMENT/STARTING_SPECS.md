# AWS API Project

A serverless API project using AWS free tier services and OpenAPI for API definition.

## Project Structure

```
aws-api-project/
├── infra/               # AWS CDK infrastructure code
├── model/               # OpenAPI model definitions and generated code
├── service/            # Lambda service implementation
├── integration-tests/  # API integration tests
├── local-dev/         # Local development utilities
└── scripts/           # Development and deployment scripts
```

## Technology Stack

- **API Definition**: OpenAPI 3.0.3
- **Infrastructure**: AWS CDK
- **Runtime**: AWS Lambda with Java 17
- **Database**: Amazon DynamoDB
- **Authentication**: Amazon Cognito
- **CI/CD**: GitHub Actions
- **AWS Region**: us-east-1 (hardcoded in the application)

## Accounts
- See `local-secrets.md` for sensitive configuration values


## Specifications
- We sould use free resources
- Compute shold  be lambda-based
- As an engineer, I should be able to update the model to generate new endpoints
- As an engineer, I should be able to use the model as a base to create services
- As an engineer, i want to use free resources from AWS
- As an engineer, I would like to have alarms for budget (max budget $10 a month)
- As an engineer, I should be able to commit to Github and it should build, run tests, then deploy to AWS
- Once in AWS, we should have a dev, a stage, and a prod region
- As an engineer, I should be able to test locally
- As an engineer, I should be able to test against dev, stage, and prod
- As a user, I should be able to access public endpoints
- As a user, I should not be able to access authenticated content
- As a user, I should be able to authenticate
- As an authenticated user, I should be able to access authenticated endpoints and only get the information I have access to
- As an authenticated user, I should not be able to access information I don't have access to
