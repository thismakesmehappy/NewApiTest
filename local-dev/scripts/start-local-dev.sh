#!/bin/bash

# TODO: Don't run unit tests when building locally, should Only run unit tests locally wjem the user asks

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

# Function to check if port is in use
check_port() {
    local port=$1
    local service_name=$2
    if lsof -ti:$port >/dev/null 2>&1; then
        echo -e "${RED}❌ Port $port is already in use (required for $service_name)${NC}"
        echo -e "${YELLOW}To free the port, run: lsof -ti:$port | xargs kill -9${NC}"
        return 1
    fi
    return 0
}

# Function to wait for service with timeout
wait_for_service() {
    local url=$1
    local service_name=$2
    local timeout=${3:-30}
    
    echo -e "${YELLOW}Waiting for $service_name to be ready...${NC}"
    for i in $(seq 1 $timeout); do
        if curl -s "$url" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ $service_name is ready${NC}"
            return 0
        fi
        if [ $i -eq $timeout ]; then
            echo -e "${RED}❌ $service_name failed to start after ${timeout} seconds${NC}"
            return 1
        fi
        sleep 1
    done
}

# Load environment variables
if [ -f "$LOCAL_DEV_DIR/.env.local" ]; then
    echo -e "${YELLOW}Loading local environment variables...${NC}"
    set -a  # automatically export all variables
    source "$LOCAL_DEV_DIR/.env.local"
    set +a
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

# Check if AWS CLI is available
if ! command -v aws &> /dev/null; then
    echo -e "${RED}❌ AWS CLI not found${NC}"
    echo "Please install AWS CLI"
    exit 1
fi
echo -e "${GREEN}✅ AWS CLI found${NC}"

# Check ports
echo -e "\n${YELLOW}Checking port availability...${NC}"
check_port 3000 "SAM Local API" || exit 1
check_port 8000 "DynamoDB Local" || exit 1
echo -e "${GREEN}✅ All required ports are available${NC}"

# Build the service if needed
echo -e "\n${YELLOW}Building service...${NC}"
cd "$PROJECT_ROOT/service"
if [ ! -f "target/toyapi-service-1.0-SNAPSHOT.jar" ] || [ "src/main/java" -nt "target/toyapi-service-1.0-SNAPSHOT.jar" ]; then
    echo -e "${YELLOW}Service JAR not found or source files newer, building...${NC}"
    mvn clean package -DskipTests -q
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

# Stop any existing containers first
docker-compose down --remove-orphans >/dev/null 2>&1

# Start services
docker-compose up -d
if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to start Docker services${NC}"
    echo -e "${YELLOW}Checking Docker logs:${NC}"
    docker-compose logs
    exit 1
fi
echo -e "${GREEN}✅ Docker services started${NC}"

# Wait for services to be ready
wait_for_service "http://localhost:8000" "DynamoDB Local" 30 || exit 1


# Set up DynamoDB table
echo -e "\n${YELLOW}Setting up DynamoDB table...${NC}"
if ! "$SCRIPT_DIR/setup-local-dynamodb.sh"; then
    echo -e "${RED}❌ Failed to set up DynamoDB table${NC}"
    exit 1
fi

# Validate environment setup
echo -e "\n${YELLOW}Validating environment setup...${NC}"
if [ ! -f "$LOCAL_DEV_DIR/env.json" ]; then
    echo -e "${RED}❌ env.json file not found${NC}"
    exit 1
fi

if [ ! -f "$PROJECT_ROOT/service/target/toyapi-service-1.0-SNAPSHOT.jar" ]; then
    echo -e "${RED}❌ Service JAR not found${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Environment validation passed${NC}"

# Start SAM Local
echo -e "\n${YELLOW}Starting SAM Local API...${NC}"
echo -e "${BLUE}SAM Local will start on http://localhost:${SAM_LOCAL_PORT:-3000}${NC}"
echo -e "${BLUE}DynamoDB Local running on http://localhost:8000${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Stopping local development environment...${NC}"
    # Kill SAM Local process if it's running
    if [ ! -z "$SAM_PID" ]; then
        kill $SAM_PID 2>/dev/null || true
    fi
    # Stop Docker services
    docker-compose down --remove-orphans
    echo -e "${GREEN}✅ Local development environment stopped${NC}"
}

# Set trap to cleanup on script exit
trap cleanup EXIT INT TERM

# Set SAM CLI telemetry off
export SAM_CLI_TELEMETRY=0

# Change to local-dev directory and start SAM
cd "$LOCAL_DEV_DIR"
sam local start-api --template template.yaml --port ${SAM_LOCAL_PORT:-3000} --host 0.0.0.0 --env-vars env.json --warm-containers EAGER &
SAM_PID=$!

# Wait for SAM Local to be ready
wait_for_service "http://localhost:${SAM_LOCAL_PORT:-3000}/public/message" "SAM Local API" 60 || exit 1

echo -e "\n${GREEN}🎉 Local development environment is ready!${NC}"
echo -e "${GREEN}🚀 API available at: http://localhost:${SAM_LOCAL_PORT:-3000}${NC}"
echo -e "${GREEN}🗄️  DynamoDB Local: http://localhost:8000${NC}"
echo -e "${GREEN}📖 Import Insomnia collection: ToyApi_Local_Insomnia_Collection.json${NC}"

# Keep the script running
wait $SAM_PID