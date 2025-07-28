#!/bin/bash

# ToyApi Local Development Health Check Script
# This script checks the health of all local development services

echo "🏥 ToyApi Local Development Health Check..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DEV_DIR="$(dirname "$SCRIPT_DIR")"

# Health check results
HEALTH_ISSUES=0

# Function to check service health
check_service() {
    local url=$1
    local service_name=$2
    local timeout=${3:-5}
    
    echo -n -e "${YELLOW}Checking $service_name... ${NC}"
    
    if curl -s --max-time $timeout "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Healthy${NC}"
    else
        echo -e "${RED}❌ Unhealthy${NC}"
        ((HEALTH_ISSUES++))
    fi
}

# Function to check port
check_port() {
    local port=$1
    local service_name=$2
    
    echo -n -e "${YELLOW}Checking $service_name (port $port)... ${NC}"
    
    if lsof -ti:$port >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Running${NC}"
    else
        echo -e "${RED}❌ Not running${NC}"
        ((HEALTH_ISSUES++))
    fi
}

# Check Docker services
echo -e "\n${BLUE}=== Docker Services ===${NC}"
cd "$LOCAL_DEV_DIR"

if [ -f docker-compose.yml ]; then
    docker-compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
    echo ""
else
    echo -e "${RED}❌ docker-compose.yml not found${NC}"
    ((HEALTH_ISSUES++))
fi

# Check individual services
echo -e "${BLUE}=== Service Health Checks ===${NC}"

# DynamoDB Local
check_service "http://localhost:8000" "DynamoDB Local"


# SAM Local API - Public endpoint
check_service "http://localhost:3000/public/message" "SAM Local API (Public)"

# SAM Local API - Auth endpoint
check_service "http://localhost:3000/auth/login" "SAM Local API (Auth)" 10

# Check if DynamoDB table exists (with timeout)
echo -n -e "${YELLOW}Checking DynamoDB table... ${NC}"
if timeout 10 aws dynamodb describe-table --table-name toyapi-local-items --endpoint-url http://localhost:8000 --cli-read-timeout 5 --cli-connect-timeout 3 >/dev/null 2>&1; then
    echo -e "${GREEN}✅ Table exists${NC}"
else
    echo -e "${YELLOW}⚠️  Table check timed out or table missing${NC}"
    echo -e "${BLUE}💡 Run: ./scripts/setup-local-dynamodb.sh${NC}"
    ((HEALTH_ISSUES++))
fi

# Check service JAR
echo -n -e "${YELLOW}Checking service JAR... ${NC}"
if [ -f "$LOCAL_DEV_DIR/../service/target/toyapi-service-1.0-SNAPSHOT.jar" ]; then
    echo -e "${GREEN}✅ JAR exists${NC}"
else
    echo -e "${RED}❌ JAR missing${NC}"
    ((HEALTH_ISSUES++))
fi

# Check environment files
echo -n -e "${YELLOW}Checking environment files... ${NC}"
if [ -f "$LOCAL_DEV_DIR/.env.local" ] && [ -f "$LOCAL_DEV_DIR/env.json" ]; then
    echo -e "${GREEN}✅ Environment files exist${NC}"
else
    echo -e "${RED}❌ Environment files missing${NC}"
    ((HEALTH_ISSUES++))
fi

# Final health summary
echo -e "\n${BLUE}=== Health Summary ===${NC}"
if [ $HEALTH_ISSUES -eq 0 ]; then
    echo -e "${GREEN}🎉 All systems healthy! Environment is ready for development.${NC}"
    echo -e "${GREEN}🚀 API: http://localhost:3000${NC}"
    echo -e "${GREEN}🗄️  DynamoDB Local: http://localhost:8000${NC}"
    exit 0
else
    echo -e "${RED}❌ Found $HEALTH_ISSUES health issue(s)${NC}"
    echo -e "${YELLOW}💡 Try running: ./scripts/start-local-dev.sh${NC}"
    exit 1
fi