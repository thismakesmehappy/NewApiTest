#!/bin/bash

# ToyApi Local Development Startup Script
# This script sets up and starts the complete local development environment

set -e  # Exit on any error

echo "🚀 Starting ToyApi Local Development Environment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DEV_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_ROOT="$(dirname "$LOCAL_DEV_DIR")"

echo -e "${BLUE}Script Directory: ${SCRIPT_DIR}${NC}"
echo -e "${BLUE}Local Dev Directory: ${LOCAL_DEV_DIR}${NC}"
echo -e "${BLUE}Project Root: ${PROJECT_ROOT}${NC}"

# Load environment variables
if [ -f "$LOCAL_DEV_DIR/.env.local" ]; then
    echo -e "${YELLOW}Loading local environment variables...${NC}"
    export $(cat "$LOCAL_DEV_DIR/.env.local" | grep -v '^#' | xargs)
    echo -e "${GREEN}✅ Environment variables loaded${NC}"
else
    echo -e "${RED}❌ .env.local file not found${NC}"
    exit 1
fi

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running${NC}"
    echo "Please start Docker Desktop and try again"
    exit 1
fi
echo -e "${GREEN}✅ Docker is running${NC}"

# Check if SAM CLI is installed
if ! command -v sam &> /dev/null; then
    echo -e "${RED}❌ SAM CLI not found${NC}"
    echo "Please install SAM CLI: https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html"
    exit 1
fi
echo -e "${GREEN}✅ SAM CLI found${NC}"

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven not found${NC}"
    echo "Please install Maven"
    exit 1
fi
echo -e "${GREEN}✅ Maven found${NC}"

# Build the service if needed
echo -e "\n${YELLOW}Building service...${NC}"
cd "$PROJECT_ROOT/service"
if [ ! -f "target/toyapi-service-1.0-SNAPSHOT.jar" ] || [ "src/main/java" -nt "target/toyapi-service-1.0-SNAPSHOT.jar" ]; then
    echo -e "${YELLOW}Service JAR not found or source files newer, building...${NC}"
    mvn clean package -q
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Service build failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Service built successfully${NC}"
else
    echo -e "${GREEN}✅ Service JAR is up to date${NC}"
fi

# Start Docker services
echo -e "\n${YELLOW}Starting Docker services...${NC}"
cd "$LOCAL_DEV_DIR"
docker-compose up -d
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to start Docker services${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Docker services started${NC}"

# Wait for DynamoDB to be ready
echo -e "\n${YELLOW}Waiting for DynamoDB Local to be ready...${NC}"
for i in {1..30}; do
    if curl -s "http://localhost:8000" > /dev/null; then
        echo -e "${GREEN}✅ DynamoDB Local is ready${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}❌ DynamoDB Local failed to start${NC}"
        exit 1
    fi
    sleep 1
done

# Set up DynamoDB table
echo -e "\n${YELLOW}Setting up DynamoDB table...${NC}"
"$SCRIPT_DIR/setup-local-dynamodb.sh"

# Start SAM Local
echo -e "\n${YELLOW}Starting SAM Local API...${NC}"
echo -e "${BLUE}SAM Local will start on http://localhost:${SAM_LOCAL_PORT:-3000}${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Stopping local development environment...${NC}"
    docker-compose down
    echo -e "${GREEN}✅ Local development environment stopped${NC}"
}

# Set trap to cleanup on script exit
trap cleanup EXIT

# Change to local-dev directory and start SAM
cd "$LOCAL_DEV_DIR"
sam local start-api --template template.yaml --port ${SAM_LOCAL_PORT:-3000} --host 0.0.0.0 --env-vars .env.local.json