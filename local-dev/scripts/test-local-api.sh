#!/bin/bash

# ToyApi Local API Testing Script
# This script tests all endpoints in the local development environment

set -e  # Exit on any error

echo "🧪 Testing ToyApi Local Development API..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
BASE_URL="http://localhost:3000"
MOCK_TOKEN="local-dev-mock-token-12345"

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Function to test an endpoint
test_endpoint() {
    local method=$1
    local endpoint=$2
    local expected_status=$3
    local headers=$4
    local body=$5
    local description=$6
    
    echo -e "\n${BLUE}Testing: ${description}${NC}"
    echo -e "${YELLOW}${method} ${BASE_URL}${endpoint}${NC}"
    
    if [ "$method" == "GET" ]; then
        if [ -n "$headers" ]; then
            response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X GET "$BASE_URL$endpoint" -H "$headers")
        else
            response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X GET "$BASE_URL$endpoint")
        fi
    elif [ "$method" == "POST" ]; then
        if [ -n "$headers" ] && [ -n "$body" ]; then
            response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST "$BASE_URL$endpoint" -H "$headers" -d "$body")
        else
            response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST "$BASE_URL$endpoint")
        fi
    fi
    
    # Extract HTTP status and body
    http_status=$(echo $response | sed -E 's/.*HTTPSTATUS:([0-9]{3})$/\1/')
    response_body=$(echo $response | sed -E 's/HTTPSTATUS:[0-9]{3}$//')
    
    if [ "$http_status" == "$expected_status" ]; then
        echo -e "${GREEN}✅ PASS - Status: $http_status${NC}"
        if [ -n "$response_body" ]; then
            echo -e "${GREEN}Response: $response_body${NC}"
        fi
        ((TESTS_PASSED++))
    else
        echo -e "${RED}❌ FAIL - Expected: $expected_status, Got: $http_status${NC}"
        if [ -n "$response_body" ]; then
            echo -e "${RED}Response: $response_body${NC}"
        fi
        ((TESTS_FAILED++))
    fi
}

# Check if local API is running
echo -e "${YELLOW}Checking if local API is running...${NC}"
if ! curl -s "$BASE_URL" > /dev/null; then
    echo -e "${RED}❌ Local API is not running at $BASE_URL${NC}"
    echo -e "${YELLOW}Please start it first with: ./scripts/start-local-dev.sh${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Local API is running${NC}"

echo -e "\n${BLUE}Starting API endpoint tests...${NC}"

# Test 1: Public endpoint
test_endpoint "GET" "/public/message" "200" "" "" "Public message endpoint"

# Test 2: Login with mock authentication  
test_endpoint "POST" "/auth/login" "200" "Content-Type: application/json" '{"username":"localuser","password":"localpass"}' "Mock authentication login"

# Test 3: Authenticated message with mock token
test_endpoint "GET" "/auth/message" "200" "Authorization: Bearer $MOCK_TOKEN" "" "Authenticated message with mock token"

# Test 4: User-specific message
test_endpoint "GET" "/auth/user/local-user-12345/message" "200" "Authorization: Bearer $MOCK_TOKEN" "" "User-specific message"

# Test 5: List items (should be empty initially)
test_endpoint "GET" "/items" "200" "Authorization: Bearer $MOCK_TOKEN" "" "List items (authenticated)"

# Test 6: Create an item
test_endpoint "POST" "/items" "201" "Content-Type: application/json Authorization: Bearer $MOCK_TOKEN" '{"message":"Test item from local development"}' "Create item"

# Test Results Summary
echo -e "\n${BLUE}=== Test Results Summary ===${NC}"
echo -e "${GREEN}Tests Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Tests Failed: $TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "\n${GREEN}🎉 All tests passed! Local development environment is working correctly.${NC}"
    exit 0
else
    echo -e "\n${RED}❌ Some tests failed. Please check the local development setup.${NC}"
    exit 1
fi