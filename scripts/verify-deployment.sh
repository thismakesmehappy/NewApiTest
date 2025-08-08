#!/bin/bash

# Deployment Verification Script
# Verifies that deployed Lambda functions can find their handler classes
# and that integration tests pass after deployment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
ENVIRONMENTS=("dev" "stage" "prod")
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo -e "${BLUE}🔍 ToyApi Deployment Verification${NC}"
echo "======================================"

# Function to verify handler classes exist in JAR
verify_jar_handlers() {
    echo -e "\n${YELLOW}📦 Verifying JAR contains handler classes...${NC}"
    
    local jar_path="${PROJECT_ROOT}/service/target/toyapi-service-1.0-SNAPSHOT.jar"
    
    if [[ ! -f "$jar_path" ]]; then
        echo -e "${RED}❌ Service JAR not found at: $jar_path${NC}"
        echo -e "${YELLOW}💡 Run: mvn clean package -DskipTests${NC}"
        return 1
    fi
    
    # Check for required handler classes in new package structure
    local handlers=(
        "co/thismakesmehappy/toyapi/service/handlers/PublicHandler.class"
        "co/thismakesmehappy/toyapi/service/handlers/AuthHandler.class" 
        "co/thismakesmehappy/toyapi/service/handlers/ItemsHandler.class"
        "co/thismakesmehappy/toyapi/service/handlers/DeveloperHandler.class"
        "co/thismakesmehappy/toyapi/service/analytics/AnalyticsHandler.class"
        "co/thismakesmehappy/toyapi/service/analytics/AnalyticsReportHandler.class"
    )
    
    echo "Checking handlers in JAR..."
    for handler in "${handlers[@]}"; do
        if jar tf "$jar_path" | grep -q "$handler"; then
            echo -e "${GREEN}✅ Found: $handler${NC}"
        else
            echo -e "${RED}❌ Missing: $handler${NC}"
            return 1
        fi
    done
    
    echo -e "${GREEN}✅ All handler classes found in JAR${NC}"
}

# Function to verify infrastructure references match JAR contents
verify_infrastructure_consistency() {
    echo -e "\n${YELLOW}🏗️  Verifying infrastructure handler references...${NC}"
    
    local infra_files=(
        "${PROJECT_ROOT}/infra/src/main/java/co/thismakesmehappy/toyapi/infra/ToyApiStack.java"
        "${PROJECT_ROOT}/infra/src/main/java/co/thismakesmehappy/toyapi/infra/LambdaStack.java"
    )
    
    # Check for old package references that would cause 502 errors
    local old_patterns=(
        "co.thismakesmehappy.toyapi.service.PublicHandler"
        "co.thismakesmehappy.toyapi.service.AuthHandler"
        "co.thismakesmehappy.toyapi.service.ItemsHandler"
        "co.thismakesmehappy.toyapi.service.DeveloperHandler"
        "co.thismakesmehappy.toyapi.service.AnalyticsHandler"
        "co.thismakesmehappy.toyapi.service.AnalyticsReportHandler"
    )
    
    local found_old_refs=false
    
    for file in "${infra_files[@]}"; do
        if [[ -f "$file" ]]; then
            echo "Checking $(basename "$file")..."
            for pattern in "${old_patterns[@]}"; do
                if grep -q "$pattern" "$file"; then
                    echo -e "${RED}❌ Found old handler reference: $pattern in $(basename "$file")${NC}"
                    found_old_refs=true
                fi
            done
        fi
    done
    
    if $found_old_refs; then
        echo -e "${RED}❌ Infrastructure contains old handler references that will cause 502 errors${NC}"
        echo -e "${YELLOW}💡 Update handler references to use new package structure:${NC}"
        echo -e "   co.thismakesmehappy.toyapi.service.handlers.* (for main handlers)"
        echo -e "   co.thismakesmehappy.toyapi.service.analytics.* (for analytics)"
        return 1
    fi
    
    echo -e "${GREEN}✅ No old handler references found${NC}"
}

# Function to test API endpoints
test_api_endpoint() {
    local environment=$1
    local base_urls=(
        "dev:https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev"
        "stage:https://5ytzml6fnb.execute-api.us-east-1.amazonaws.com/stage" 
        "prod:https://skslof01gg.execute-api.us-east-1.amazonaws.com/prod"
    )
    
    local base_url=""
    for url_mapping in "${base_urls[@]}"; do
        if [[ $url_mapping == $environment:* ]]; then
            base_url="${url_mapping#*:}"
            break
        fi
    done
    
    if [[ -z "$base_url" ]]; then
        echo -e "${RED}❌ Unknown environment: $environment${NC}"
        return 1
    fi
    
    echo "Testing $environment: $base_url"
    
    # Test public endpoint (should always work)
    local response
    response=$(curl -s -w "%{http_code}" "$base_url/public/message")
    local http_code="${response: -3}"
    local body="${response%???}"
    
    if [[ "$http_code" == "200" ]]; then
        echo -e "${GREEN}✅ $environment public endpoint working${NC}"
        
        # Verify response contains expected environment
        if echo "$body" | grep -q "\"Environment\": \"$environment\"" || echo "$body" | grep -q "Environment: $environment"; then
            echo -e "${GREEN}✅ Response contains correct environment: $environment${NC}"
        else
            echo -e "${YELLOW}⚠️  Response doesn't contain expected environment identifier${NC}"
            echo "Response: $body"
        fi
    else
        echo -e "${RED}❌ $environment public endpoint failed (HTTP $http_code)${NC}"
        echo "Response: $body"
        return 1
    fi
}

# Function to run integration tests
run_integration_tests() {
    local environment=$1
    
    echo -e "\n${YELLOW}🧪 Running integration tests against $environment...${NC}"
    
    cd "${PROJECT_ROOT}/integration-tests"
    
    if mvn test -Dtest.environment="$environment" -q; then
        echo -e "${GREEN}✅ Integration tests passed for $environment${NC}"
    else
        echo -e "${RED}❌ Integration tests failed for $environment${NC}"
        return 1
    fi
}

# Main verification logic
main() {
    local environment="${1:-dev}"
    local run_tests="${2:-true}"
    
    echo "Verifying environment: $environment"
    echo "Run integration tests: $run_tests"
    echo ""
    
    # Step 1: Verify JAR contains expected handlers
    if ! verify_jar_handlers; then
        echo -e "\n${RED}💥 JAR verification failed${NC}"
        exit 1
    fi
    
    # Step 2: Verify infrastructure consistency  
    if ! verify_infrastructure_consistency; then
        echo -e "\n${RED}💥 Infrastructure consistency check failed${NC}"
        exit 1
    fi
    
    # Step 3: Test API endpoints
    echo -e "\n${YELLOW}🌐 Testing API endpoints...${NC}"
    if ! test_api_endpoint "$environment"; then
        echo -e "\n${RED}💥 API endpoint test failed${NC}"
        exit 1
    fi
    
    # Step 4: Run integration tests (optional)
    if [[ "$run_tests" == "true" ]]; then
        if ! run_integration_tests "$environment"; then
            echo -e "\n${RED}💥 Integration tests failed${NC}"
            exit 1
        fi
    else
        echo -e "\n${YELLOW}⏭️  Skipping integration tests${NC}"
    fi
    
    # Success!
    echo -e "\n${GREEN}🎉 Deployment verification successful for $environment!${NC}"
    echo -e "${GREEN}✅ JAR contains expected handlers${NC}"
    echo -e "${GREEN}✅ Infrastructure references are consistent${NC}" 
    echo -e "${GREEN}✅ API endpoints are responding${NC}"
    if [[ "$run_tests" == "true" ]]; then
        echo -e "${GREEN}✅ Integration tests pass${NC}"
    fi
}

# Help text
show_help() {
    echo "Usage: $0 [environment] [run_tests]"
    echo ""
    echo "Arguments:"
    echo "  environment  Environment to verify (dev|stage|prod) [default: dev]"
    echo "  run_tests    Run integration tests (true|false) [default: true]" 
    echo ""
    echo "Examples:"
    echo "  $0                    # Verify dev with integration tests"
    echo "  $0 stage              # Verify stage with integration tests"
    echo "  $0 prod false         # Verify prod without integration tests"
}

# Parse arguments
if [[ "$1" == "-h" || "$1" == "--help" ]]; then
    show_help
    exit 0
fi

# Run main verification
main "$@"