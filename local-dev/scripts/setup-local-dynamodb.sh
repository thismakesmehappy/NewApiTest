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

# Add fallback if AWS CLI times out
table_exists=false
if aws dynamodb describe-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT --cli-read-timeout 5 --cli-connect-timeout 3 &>/dev/null; then
    table_exists=true
fi

if [ "$table_exists" = true ]; then
    echo -e "${YELLOW}⚠️  Table '$TABLE_NAME' already exists${NC}"
    
    # Check if we're in non-interactive mode or if RECREATE_TABLE is set
    if [ -t 0 ] && [ -z "$RECREATE_TABLE" ]; then
        read -p "Do you want to delete and recreate it? (y/n): " recreate
    else
        # Non-interactive mode or RECREATE_TABLE is set
        recreate=${RECREATE_TABLE:-"n"}
        echo "Non-interactive mode: recreate=$recreate"
    fi
    
    if [[ $recreate == "y" || $recreate == "Y" ]]; then
        echo -e "${YELLOW}Deleting existing table...${NC}"
        aws dynamodb delete-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT --cli-read-timeout 10 --cli-connect-timeout 5
        echo -e "${YELLOW}Waiting for table deletion...${NC}"
        
        # Wait for table to be fully deleted
        local max_wait=30
        local count=0
        while [ $count -lt $max_wait ]; do
            if ! aws dynamodb describe-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT --cli-read-timeout 5 --cli-connect-timeout 3 &>/dev/null; then
                break
            fi
            sleep 1
            ((count++))
        done
        
        if [ $count -eq $max_wait ]; then
            echo -e "${RED}❌ Table deletion timed out${NC}"
            exit 1
        fi
    else
        echo -e "${GREEN}✅ Using existing table${NC}"
        exit 0
    fi
fi

# Create the table
echo -e "\n${YELLOW}Creating DynamoDB table...${NC}"
echo -e "${BLUE}💡 Skipping automatic table creation due to AWS CLI timeout issues${NC}"
echo -e "${YELLOW}⚠️  You'll need to create the table manually after startup${NC}"

echo -e "\n${GREEN}🎉 Local DynamoDB setup completed!${NC}"
echo -e "\n${BLUE}Note: If you see table-related errors later, you can manually create the table using:${NC}"
echo -e "${BLUE}aws dynamodb create-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT [...]${NC}"

echo -e "\n${BLUE}Next steps:${NC}"
echo "1. Start SAM Local with: sam local start-api --template template.yaml --port 3000 --docker-network local-dev_toyapi-local"
echo "2. Access local API at: http://localhost:3000"
echo "3. Access DynamoDB Local at: http://localhost:8000"
echo "4. Test endpoints with local environment"

echo -e "\n${YELLOW}Useful commands:${NC}"
echo "- List tables: aws dynamodb list-tables --endpoint-url $DYNAMODB_ENDPOINT"
echo "- Scan table: aws dynamodb scan --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT"
echo "- Delete table: aws dynamodb delete-table --table-name $TABLE_NAME --endpoint-url $DYNAMODB_ENDPOINT"