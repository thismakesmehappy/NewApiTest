#!/bin/bash

# ToyApi Local Development Shutdown Script
# This script gracefully stops all local development services

echo "🛑 Stopping ToyApi Local Development Environment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_DEV_DIR="$(dirname "$SCRIPT_DIR")"

# Function to kill processes on specific ports
kill_port_processes() {
    local port=$1
    local service_name=$2
    
    echo -e "${YELLOW}Checking for processes on port $port ($service_name)...${NC}"
    local pids=$(lsof -ti:$port 2>/dev/null)
    
    if [ ! -z "$pids" ]; then
        echo -e "${YELLOW}Stopping $service_name processes on port $port...${NC}"
        echo "$pids" | xargs kill -TERM 2>/dev/null
        sleep 2
        
        # Force kill if still running
        local remaining_pids=$(lsof -ti:$port 2>/dev/null)
        if [ ! -z "$remaining_pids" ]; then
            echo -e "${YELLOW}Force stopping remaining processes...${NC}"
            echo "$remaining_pids" | xargs kill -9 2>/dev/null
        fi
        echo -e "${GREEN}✅ $service_name stopped${NC}"
    else
        echo -e "${GREEN}✅ No $service_name processes found${NC}"
    fi
}

# Stop SAM Local processes
kill_port_processes 3000 "SAM Local API"

# Stop Docker services
echo -e "\n${YELLOW}Stopping Docker services...${NC}"
cd "$LOCAL_DEV_DIR"

if [ -f docker-compose.yml ]; then
    docker-compose down --remove-orphans
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Docker services stopped${NC}"
    else
        echo -e "${RED}❌ Error stopping Docker services${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  docker-compose.yml not found${NC}"
fi

# Stop DynamoDB processes (backup)
kill_port_processes 8000 "DynamoDB Local"

# Clean up any remaining SAM processes
echo -e "\n${YELLOW}Cleaning up SAM processes...${NC}"
pkill -f "sam local" 2>/dev/null || true

# Remove SAM cache if requested
if [ "$1" = "--clean" ]; then
    echo -e "\n${YELLOW}Cleaning SAM cache...${NC}"
    rm -rf ~/.aws-sam/cache 2>/dev/null || true
    echo -e "${GREEN}✅ SAM cache cleaned${NC}"
fi

echo -e "\n${GREEN}🎉 Local development environment stopped successfully!${NC}"
echo -e "${BLUE}To restart: ./scripts/start-local-dev.sh${NC}"