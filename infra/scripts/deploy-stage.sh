#!/bin/bash

# ToyApi Staging Environment Deployment Script
# This script deploys the ToyApi infrastructure to the staging environment

set -e  # Exit on any error

echo "🚀 Starting ToyApi deployment to STAGING environment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Environment configuration
ENVIRONMENT="stage"
STACK_NAME="ToyApiStack-${ENVIRONMENT}"

echo -e "${BLUE}Environment: ${ENVIRONMENT}${NC}"
echo -e "${BLUE}Stack Name: ${STACK_NAME}${NC}"

# Staging deployment confirmation (skip in CI/CD)
if [[ "${CI}" != "true" ]]; then
    echo -e "\n${YELLOW}⚠️  You are about to deploy to the STAGING environment!${NC}"
    echo -e "${YELLOW}This will create production-like resources that may incur costs.${NC}"
    read -p "Are you sure you want to continue? (yes/no): " confirmation

    if [[ $confirmation != "yes" ]]; then
        echo -e "${YELLOW}Deployment cancelled.${NC}"
        exit 0
    fi
else
    echo -e "\n${BLUE}Running in CI/CD mode - proceeding with staging deployment${NC}"
fi

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

# Check if AWS CLI is configured
if ! aws sts get-caller-identity >/dev/null 2>&1; then
    echo -e "${RED}❌ AWS CLI is not configured or credentials are invalid${NC}"
    echo "Please run 'aws configure' to set up your credentials"
    exit 1
fi

echo -e "${GREEN}✅ AWS CLI configured${NC}"

# Check if CDK is installed
if ! command -v cdk &> /dev/null; then
    echo -e "${RED}❌ CDK CLI not found${NC}"
    echo "Please install CDK: npm install -g aws-cdk"
    exit 1
fi

echo -e "${GREEN}✅ CDK CLI found${NC}"

# Build all modules from root (ensures correct dependency order)
echo -e "\n${YELLOW}Building all modules...${NC}"
cd ../..
mvn clean package -q
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ All modules built successfully${NC}"

# Return to infra directory
cd infra

# Bootstrap CDK if needed
echo -e "\n${YELLOW}Checking CDK bootstrap...${NC}"
if ! cdk bootstrap --context environment=${ENVIRONMENT} >/dev/null 2>&1; then
    echo -e "${YELLOW}Bootstrapping CDK...${NC}"
    cdk bootstrap --context environment=${ENVIRONMENT}
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ CDK bootstrap failed${NC}"
        exit 1
    fi
fi
echo -e "${GREEN}✅ CDK bootstrap complete${NC}"

# Show diff before deployment
echo -e "\n${YELLOW}Showing infrastructure changes...${NC}"
cdk diff ${STACK_NAME} --context environment=${ENVIRONMENT}

# Confirm deployment (skip in CI/CD)
if [[ "${CI}" != "true" ]]; then
    echo -e "\n${YELLOW}Ready to deploy to staging environment.${NC}"
    read -p "Proceed with deployment? (yes/no): " final_confirmation

    if [[ $final_confirmation != "yes" ]]; then
        echo -e "${YELLOW}Deployment cancelled.${NC}"
        exit 0
    fi
else
    echo -e "\n${BLUE}CI/CD mode - proceeding with deployment${NC}"
fi

# Deploy the stack
echo -e "\n${YELLOW}Deploying infrastructure...${NC}"
cdk deploy ${STACK_NAME} --context environment=${ENVIRONMENT} --require-approval never
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Deployment failed${NC}"
    exit 1
fi

echo -e "\n${GREEN}🎉 Deployment to ${ENVIRONMENT} completed successfully!${NC}"

# Get and display stack outputs
echo -e "\n${BLUE}Stack Outputs:${NC}"
aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query 'Stacks[0].Outputs' --output table

echo -e "\n${GREEN}✅ ToyApi ${ENVIRONMENT} environment is ready!${NC}"
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Run integration tests against the staging API"
echo "2. Validate all endpoints work correctly"
echo "3. Check performance and error rates"
echo "4. If everything looks good, deploy to production"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo "- View stack details: aws cloudformation describe-stacks --stack-name ${STACK_NAME}"
echo "- View API logs: aws logs tail /aws/lambda/toyapi-stage-publicfunction --follow"
echo "- Run integration tests: cd ../integration-tests && mvn test -Dapi.base.url=<API_URL>"
echo "- Destroy stack: cdk destroy ${STACK_NAME} --context environment=${ENVIRONMENT}"