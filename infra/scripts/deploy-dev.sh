#!/bin/bash

# ToyApi Development Environment Deployment Script
# This script deploys the ToyApi infrastructure to the dev environment

set -e  # Exit on any error

echo "🚀 Starting ToyApi deployment to DEV environment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Environment configuration
ENVIRONMENT="dev"
STACK_NAME="ToyApiStack-${ENVIRONMENT}"

echo -e "${BLUE}Environment: ${ENVIRONMENT}${NC}"
echo -e "${BLUE}Stack Name: ${STACK_NAME}${NC}"

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
# Get the project root directory (parent of infra)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
echo -e "${BLUE}Project root: ${PROJECT_ROOT}${NC}"
cd "${PROJECT_ROOT}"
mvn clean package -q
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Build failed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ All modules built successfully${NC}"

# Return to infra directory
cd "${PROJECT_ROOT}/infra"

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

# Synthesize the stack
echo -e "\n${YELLOW}Synthesizing CDK stack...${NC}"
cdk synth ${STACK_NAME} --context environment=${ENVIRONMENT} >/dev/null
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ CDK synthesis failed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ CDK synthesis successful${NC}"

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
echo "1. Test the API endpoints using the API URL above"
echo "2. Check CloudWatch logs for Lambda function execution"
echo "3. Monitor costs in AWS Billing dashboard"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo "- View stack details: aws cloudformation describe-stacks --stack-name ${STACK_NAME}"
echo "- View API logs: aws logs tail /aws/lambda/toyapi-dev-publicfunction --follow"
echo "- Destroy stack: cdk destroy ${STACK_NAME} --context environment=${ENVIRONMENT}"