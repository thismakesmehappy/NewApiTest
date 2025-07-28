#!/bin/bash

# ToyApi Production Environment Deployment Script
# This script deploys the ToyApi infrastructure to the production environment

set -e  # Exit on any error

echo "🚀 Starting ToyApi deployment to PRODUCTION environment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Environment configuration
ENVIRONMENT="prod"
STACK_NAME="ToyApiStack-${ENVIRONMENT}"

echo -e "${BLUE}Environment: ${ENVIRONMENT}${NC}"
echo -e "${BLUE}Stack Name: ${STACK_NAME}${NC}"

# Production deployment warnings (skip in CI/CD)
if [[ "${CI}" != "true" ]]; then
    echo -e "\n${RED}🚨 PRODUCTION DEPLOYMENT WARNING! 🚨${NC}"
    echo -e "${RED}You are about to deploy to the LIVE PRODUCTION environment!${NC}"
    echo -e "${YELLOW}This will create production resources with RETAIN policies.${NC}"
    echo -e "${YELLOW}Resources will NOT be automatically deleted if the stack is destroyed.${NC}"
    echo ""
    echo -e "${YELLOW}Pre-deployment checklist:${NC}"
    echo "1. ✅ Code has been tested in dev and staging environments"
    echo "2. ✅ Integration tests pass successfully"  
    echo "3. ✅ Performance testing completed"
    echo "4. ✅ Security review completed"
    echo "5. ✅ Database migration scripts tested (if applicable)"
    echo "6. ✅ Rollback plan is prepared"
    echo ""
    read -p "Have you completed the pre-deployment checklist? (yes/no): " checklist_confirmation

    if [[ $checklist_confirmation != "yes" ]]; then
        echo -e "${RED}Please complete the pre-deployment checklist before deploying to production.${NC}"
        exit 1
    fi

    echo -e "\n${YELLOW}⚠️  FINAL CONFIRMATION REQUIRED ⚠️${NC}"
    echo "Type 'DEPLOY-TO-PRODUCTION' to confirm you want to deploy to production:"
    read -p "> " production_confirmation

    if [[ $production_confirmation != "DEPLOY-TO-PRODUCTION" ]]; then
        echo -e "${YELLOW}Production deployment cancelled.${NC}"
        exit 0
    fi
else
    echo -e "\n${BLUE}Running in CI/CD mode - deploying to production${NC}"
    echo -e "${BLUE}Pre-deployment checklist assumed completed in automated pipeline${NC}"
fi

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

# Check if AWS CLI is configured
if ! aws sts get-caller-identity >/dev/null 2>&1; then
    echo -e "${RED}❌ AWS CLI is not configured or credentials are invalid${NC}"
    echo "Please run 'aws configure' to set up your credentials"
    exit 1
fi

# Verify we're deploying to the correct AWS account
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
EXPECTED_ACCOUNT="375004071203"

if [[ $ACCOUNT_ID != $EXPECTED_ACCOUNT ]]; then
    echo -e "${RED}❌ Wrong AWS account! Expected: ${EXPECTED_ACCOUNT}, Got: ${ACCOUNT_ID}${NC}"
    exit 1
fi

echo -e "${GREEN}✅ AWS CLI configured for correct account (${ACCOUNT_ID})${NC}"

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

# Final deployment confirmation (skip in CI/CD)
if [[ "${CI}" != "true" ]]; then
    echo -e "\n${RED}🚨 LAST CHANCE TO CANCEL! 🚨${NC}"
    echo -e "${YELLOW}About to deploy to PRODUCTION with the changes shown above.${NC}"
    read -p "Type 'PROCEED' to continue with production deployment: " final_confirmation

    if [[ $final_confirmation != "PROCEED" ]]; then
        echo -e "${YELLOW}Production deployment cancelled.${NC}"
        exit 0
    fi
else
    echo -e "\n${BLUE}CI/CD mode - proceeding with production deployment${NC}"
fi

# Deploy the stack
echo -e "\n${YELLOW}🚀 Deploying to production...${NC}"
cdk deploy ${STACK_NAME} --context environment=${ENVIRONMENT} --require-approval never
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Production deployment failed${NC}"
    echo -e "${YELLOW}Check the error above and investigate before retrying.${NC}"
    exit 1
fi

echo -e "\n${GREEN}🎉 PRODUCTION DEPLOYMENT COMPLETED SUCCESSFULLY! 🎉${NC}"

# Get and display stack outputs
echo -e "\n${BLUE}Production Stack Outputs:${NC}"
aws cloudformation describe-stacks --stack-name ${STACK_NAME} --query 'Stacks[0].Outputs' --output table

echo -e "\n${GREEN}✅ ToyApi PRODUCTION environment is live!${NC}"
echo -e "\n${BLUE}Post-deployment checklist:${NC}"
echo "1. 🔍 Test all API endpoints"
echo "2. 📊 Monitor CloudWatch metrics and logs"
echo "3. 💰 Check AWS billing dashboard"
echo "4. 🚨 Verify budget alerts are configured"
echo "5. 📧 Confirm you receive budget alert emails"
echo "6. 🔐 Test authentication flows"
echo "7. 📱 Test from client applications"
echo ""
echo -e "${BLUE}Monitoring commands:${NC}"
echo "- View stack details: aws cloudformation describe-stacks --stack-name ${STACK_NAME}"
echo "- View API logs: aws logs tail /aws/lambda/toyapi-prod-publicfunction --follow"
echo "- Monitor all logs: aws logs tail --follow /aws/lambda/toyapi-prod-*"
echo ""
echo -e "${RED}⚠️  PRODUCTION RESOURCES CREATED ⚠️${NC}"
echo -e "${YELLOW}Remember: Production resources have RETAIN policies and won't be deleted automatically.${NC}"
echo -e "${YELLOW}Only destroy the production stack if you're absolutely sure!${NC}"