# Project Clarification Questions

Before creating the detailed implementation plan, please answer the following questions to ensure the solution meets your specific needs.

## 1. API Domain & Functionality
- **What type of data/business domain will your API handle?** (e.g., user management, todo items, blog posts, inventory, etc.)
This is a toy project, I haven't thought much about it. It will be simple data. keep it flexible, but simple.

- **What are some example endpoints you'd like to implement initially?** (This will help define the OpenAPI spec structure)
Let's implement a toy private endpoint (that just returns a message to anyone), a toy authenticated endpoint (that returns a message only if the user is authenticated), a toy authenticated message for a specific user (we need a specific user's credentials to get a custom message), then CRUD operations for a simple item (it should have an ID and a message), and an endpoint to authenticate

## 2. Authentication & Authorization
- **For the access control requirements, do you want role-based access (admin/user) or resource-based access (users can only see their own data)?**
both
- 
- **Should users be able to self-register, or do you want admin-only user creation?**
self-register

## 3. Development Environment Setup
- **Do you have AWS CLI configured locally with your account (375004071203)?**
no
- 
- **Do you have Node.js/npm installed for CDK development?**
yes

- **Do you have Java 17 installed for Lambda development?**
yes

## 4. GitHub Integration
- **Should I help you set up the repository structure and GitHub Actions workflows?**
yes

- **Do you want separate branches for dev/stage/prod deployments, or prefer a different branching strategy?**
same branch. should be a pipeline, first dev, then stage, then prod from main. Eventually I would like to remove dev from the pipeline and only do manual deployment to dev, but for now include it in the pipeline

## 5. Multi-Environment Setup
- **When you mention "dev, stage, and prod region" - did you mean environments within us-east-1, or actual different AWS regions?**
let's put them all in us-east-1

- **How do you want to handle environment-specific configurations?** (e.g., different DynamoDB table names, Cognito pools, etc.)
please advise

## 6. Budget & Monitoring
- **Besides the $10/month budget alarm, do you want other CloudWatch alarms?** (error rates, latency, throttling, etc.)
yes
- **What should happen when the budget alarm triggers?** (email notification, SNS topic, etc.)
email (bernardo+toyAPI@thismakesmehappy.co). There should be a warning for 50%, 75%, 85%, 95%)

## 7. Local Development & Testing
- **Do you want to use AWS SAM for local testing, or prefer a different approach?** (Docker, LocalStack, etc.)
AWS SAM works. Please document

- **For integration tests, should they run against real AWS resources or mocked services?**
By default mock services, but I should be able to specify a different stage for the tests (so I should be able to deploy to dev and test against it, or test agains the latest in stage or prod)

## 8. OpenAPI Code Generation
- **Do you have a preference for the OpenAPI code generation tool?** (OpenAPI Generator, AWS SDK, custom tooling)
please suggest

- **Should the generated code include validation, serialization, and client SDKs?**
please suggest

---

## Instructions
Please answer these questions by editing this document directly, or create a separate `PROJECT_ANSWERS.md` file. Once you provide the answers, I'll create a comprehensive implementation plan and start building your serverless API project.
