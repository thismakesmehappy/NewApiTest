#!/bin/bash

# ToyApi Local DynamoDB Setup Script
# This script creates the DynamoDB table structure locally

set -e  # Exit on any error

echo "🚀 Setting up local DynamoDB for ToyApi development..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DYNAMODB_ENDPOINT="http://localhost:8000"
TABLE_NAME="toyapi-local-items"
AWS_REGION="us-east-1"

# Set AWS credentials for local development
export AWS_ACCESS_KEY_ID=local
export AWS_SECRET_ACCESS_KEY=local
export AWS_DEFAULT_REGION=$AWS_REGION

echo -e "${BLUE}DynamoDB Endpoint: ${DYNAMODB_ENDPOINT}${NC}"
echo -e "${BLUE}Table Name: ${TABLE_NAME}${NC}"
echo -e "${BLUE}Region: ${AWS_REGION}${NC}"

# Check if DynamoDB Local is running
echo -e "\n${YELLOW}Checking if DynamoDB Local is running...${NC}"
if ! curl -s "$DYNAMODB_ENDPOINT" > /dev/null; then
    echo -e "${RED}❌ DynamoDB Local is not running${NC}"
    echo -e "${YELLOW}Please start it first with: docker-compose up -d dynamodb-local${NC}"
    exit 1
fi
echo -e "${GREEN}✅ DynamoDB Local is running${NC}"

# Check if table already exists
echo -e "\n${YELLOW}Checking if table exists...${NC}"
if aws dynamodb describe-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT &>/dev/null; then
    echo -e "${YELLOW}⚠️  Table '$TABLE_NAME' already exists${NC}"
    read -p "Do you want to delete and recreate it? (y/n): " recreate
    if [[ $recreate == "y" || $recreate == "Y" ]]; then
        echo -e "${YELLOW}Deleting existing table...${NC}"
        aws dynamodb delete-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT
        echo -e "${YELLOW}Waiting for table deletion...${NC}"
        sleep 3
    else
        echo -e "${GREEN}✅ Using existing table${NC}"
        exit 0
    fi
fi

# Create the table
echo -e "\n${YELLOW}Creating DynamoDB table...${NC}"
aws dynamodb create-table \
    --table-name $TABLE_NAME \
    --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
        AttributeName=userId,AttributeType=S \
        AttributeName=createdAt,AttributeType=S \
    --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
    --global-secondary-indexes \
        IndexName=UserIndex,KeySchema=[{AttributeName=userId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=ALL},ProvisionedThroughput={ReadCapacityUnits=5,WriteCapacityUnits=5} \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url $DYNAMODB_ENDPOINT

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Table created successfully${NC}"
else
    echo -e "${RED}❌ Failed to create table${NC}"
    exit 1
fi

# Wait for table to be active
echo -e "\n${YELLOW}Waiting for table to become active...${NC}"
sleep 2

# Verify table creation
echo -e "\n${YELLOW}Verifying table creation...${NC}"
aws dynamodb describe-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT --query 'Table.TableStatus' --output text

echo -e "\n${GREEN}🎉 Local DynamoDB setup completed successfully!${NC}"
echo -e "\n${BLUE}Table Details:${NC}"
aws dynamodb describe-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT --query 'Table.{TableName:TableName,TableStatus:TableStatus,ItemCount:ItemCount}' --output table

echo -e "\n${BLUE}Next steps:${NC}"
echo "1. Start SAM Local with: sam local start-api --template template.yaml --port 3000"
echo "2. Access local API at: http://localhost:3000"
echo "3. Access DynamoDB Admin UI at: http://localhost:8001"
echo "4. Test endpoints with local environment"

echo -e "\n${YELLOW}Useful commands:${NC}"
echo "- List tables: aws dynamodb list-tables --endpoint-url $DYNAMODB_ENDPOINT"
echo "- Scan table: aws dynamodb scan --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT"
echo "- Delete table: aws dynamodb delete-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT"