# 🧪 Team Sharing Testing Guide

This comprehensive guide provides step-by-step testing scenarios to validate the team sharing functionality across all user roles and environments.

## 📋 Prerequisites

Before running these tests, ensure you have:

1. **Test Users Created**: All 4 test users in the target environment (see `team-sharing-test-credentials.md`)
2. **API Testing Tool**: Insomnia, Postman, or curl commands ready
3. **Environment Setup**: Base URL and credentials configured for your target environment
4. **Clean State**: No existing test items that might interfere

## 🎯 Test Matrix Overview

| Test User | Role | Team | Can Create | Can Read | Can Modify |
|-----------|------|------|------------|----------|------------|
| standarduser | USER | team-engineering | Own + team-engineering | Own + team-engineering + public | Own items only |
| engteamadmin | TEAM_ADMIN | team-engineering | Own + team-engineering | Own + team-engineering + public | Own + any team-engineering |
| marketingteamadmin | TEAM_ADMIN | team-marketing | Own + team-marketing | Own + team-marketing + public | Own + any team-marketing |
| globaladmin | ADMIN | All teams | Any team | All items | All items |

---

## 🔥 Critical Test Scenarios

### Test Suite 1: Standard User Access Control

**Test User**: `standarduser` (USER role, team-engineering member)

#### Test 1.1: Login and Token Validation
```bash
# Step 1: Login
curl -X POST "{base_url}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"standarduser","password":"TeamShare123"}'

# Expected: Success with idToken, accessToken, refreshToken
# Save the idToken for subsequent tests
```

#### Test 1.2: Create Individual Item
```bash
# Step 2: Create personal item  
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"My personal item"}'

# Expected: Success (201 Created)
# Note the itemId for later tests
```

#### Test 1.3: Create Team Item (Authorized)
```bash
# Step 3: Create team-engineering item
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Engineering team item","teamId":"team-engineering"}'

# Expected: Success (201 Created)
```

#### Test 1.4: Create Team Item (Unauthorized)
```bash
# Step 4: Try to create team-marketing item
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Marketing item - should fail","teamId":"team-marketing"}'

# Expected: Failure (403 Forbidden) - Not a member of team-marketing
```

#### Test 1.5: Read Own Items
```bash
# Step 5: Get all items (should see own items)
curl -X GET "{base_url}/items" \
  -H "Authorization: Bearer {idToken}"

# Expected: Success - sees own items and team-engineering items
```

#### Test 1.6: Modify Own Item
```bash
# Step 6: Update own item
curl -X PUT "{base_url}/items/{ownItemId}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Updated my personal item"}'

# Expected: Success (200 OK)
```

---

### Test Suite 2: Team Admin Cross-Team Isolation

**Test User**: `engteamadmin` (TEAM_ADMIN role, team-engineering admin)

#### Test 2.1: Login and Setup
```bash
# Step 1: Login as engineering team admin
curl -X POST "{base_url}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"engteamadmin","password":"TeamShare123"}'
```

#### Test 2.2: Create Team Item in Own Team
```bash
# Step 2: Create engineering team item
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Admin created engineering item","teamId":"team-engineering"}'

# Expected: Success (201 Created)
```

#### Test 2.3: Try Cross-Team Creation (Should Fail)
```bash
# Step 3: Try to create marketing team item
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Cross-team item - should fail","teamId":"team-marketing"}'

# Expected: Failure (403 Forbidden) - Not a member of team-marketing
```

#### Test 2.4: Modify Team Item Created by Standard User
```bash
# Step 4: Modify team-engineering item created by standarduser
curl -X PUT "{base_url}/items/{standardUserTeamItemId}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Admin modified standard user item"}'

# Expected: Success (200 OK) - Team admin can modify any team item
```

#### Test 2.5: Try to Read Marketing Team Items
```bash
# Step 5: Try to access marketing team items
curl -X GET "{base_url}/items?teamId=team-marketing" \
  -H "Authorization: Bearer {idToken}"

# Expected: Empty result or filtered results (no marketing items visible)
```

---

### Test Suite 3: Marketing Team Admin Isolation

**Test User**: `marketingteamadmin` (TEAM_ADMIN role, team-marketing admin)

#### Test 3.1: Login and Create Marketing Item
```bash
# Step 1: Login as marketing team admin
curl -X POST "{base_url}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"marketingteamadmin","password":"TeamShare123"}'

# Step 2: Create marketing team item
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Marketing team campaign","teamId":"team-marketing"}'

# Expected: Success (201 Created)
```

#### Test 3.2: Verify Engineering Team Isolation
```bash
# Step 3: Try to access engineering team items
curl -X GET "{base_url}/items?teamId=team-engineering" \
  -H "Authorization: Bearer {idToken}"

# Expected: Empty result (no engineering items visible)
```

#### Test 3.3: Try to Modify Engineering Item
```bash
# Step 4: Try to modify engineering team item
curl -X PUT "{base_url}/items/{engineeringItemId}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Cross-team modification attempt"}'

# Expected: Failure (403 Forbidden) or (404 Not Found)
```

---

### Test Suite 4: Global Admin Full Access

**Test User**: `globaladmin` (ADMIN role, all teams)

#### Test 4.1: Login and Verify Full Access
```bash
# Step 1: Login as global admin
curl -X POST "{base_url}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"globaladmin","password":"TeamShare123"}'
```

#### Test 4.2: Create Items for Multiple Teams
```bash
# Step 2: Create engineering item
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Admin engineering item","teamId":"team-engineering"}'

# Step 3: Create marketing item  
curl -X POST "{base_url}/items" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Admin marketing item","teamId":"team-marketing"}'

# Expected: Both succeed (201 Created)
```

#### Test 4.3: Read All Items
```bash
# Step 4: Get all items (should see everything)
curl -X GET "{base_url}/items" \
  -H "Authorization: Bearer {idToken}"

# Expected: Success - sees all items from all teams and users
```

#### Test 4.4: Modify Any Item
```bash
# Step 5: Modify items created by other users
curl -X PUT "{base_url}/items/{anyItemId}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {idToken}" \
  -d '{"message":"Global admin can modify anything"}'

# Expected: Success (200 OK) - Admin can modify any item
```

#### Test 4.5: Delete Any Item
```bash
# Step 6: Delete items created by other users
curl -X DELETE "{base_url}/items/{anyItemId}" \
  -H "Authorization: Bearer {idToken}"

# Expected: Success (200 OK or 204 No Content) - Admin can delete any item
```

---

## 🔍 Detailed Validation Scenarios

### Scenario A: End-to-End Workflow Test

**Objective**: Validate complete team sharing workflow across all user types

1. **Setup Phase** (Global Admin):
   - Login as `globaladmin`
   - Create sample items for both teams
   - Create public items
   - Note all item IDs

2. **Standard User Phase**:
   - Login as `standarduser`
   - Verify can see team-engineering items
   - Verify cannot see team-marketing items
   - Create own team item
   - Try to modify admin's team item (should fail)

3. **Team Admin Phase**:
   - Login as `engteamadmin`
   - Verify can modify any team-engineering item
   - Verify cannot access team-marketing items
   - Delete standard user's item (should succeed)

4. **Cross-Team Verification**:
   - Login as `marketingteamadmin`
   - Verify complete isolation from engineering team
   - Create marketing items
   - Verify engineering admin cannot access

5. **Cleanup Phase** (Global Admin):
   - Login as `globaladmin`
   - Delete all test items
   - Verify all users see clean state

### Scenario B: Security Boundary Testing

**Objective**: Ensure no unauthorized cross-team access

1. **Data Setup**:
   - Create items as each user type
   - Note which user created which items

2. **Unauthorized Access Attempts**:
   - Try to access items from wrong teams
   - Try to modify items without permissions
   - Try to delete items without permissions

3. **Token Boundary Testing**:
   - Use expired tokens
   - Use tokens from different users
   - Use malformed tokens

### Scenario C: Edge Case Testing

**Objective**: Test boundary conditions and error handling

1. **Invalid Team IDs**:
   - Try to create items with non-existent team IDs
   - Try to access items with invalid team filters

2. **Missing/Invalid Data**:
   - Create items without required fields
   - Use empty or null team IDs
   - Send malformed JSON

3. **State Transition Testing**:
   - Create item as individual, try to assign to team
   - Change team membership and verify access changes

---

## 📊 Expected Test Results Matrix

### User Access Matrix

| Operation | standarduser | engteamadmin | marketingteamadmin | globaladmin |
|-----------|--------------|--------------|-------------------|-------------|
| Create individual item | ✅ | ✅ | ✅ | ✅ |
| Create team-engineering item | ✅ | ✅ | ❌ | ✅ |
| Create team-marketing item | ❌ | ❌ | ✅ | ✅ |
| Read own items | ✅ | ✅ | ✅ | ✅ |
| Read team-engineering items | ✅ | ✅ | ❌ | ✅ |
| Read team-marketing items | ❌ | ❌ | ✅ | ✅ |
| Read public items | ✅ | ✅ | ✅ | ✅ |
| Modify own items | ✅ | ✅ | ✅ | ✅ |
| Modify team-engineering items | ❌ | ✅ | ❌ | ✅ |
| Modify team-marketing items | ❌ | ❌ | ✅ | ✅ |
| Delete any item | ❌ | ✅ (team only) | ✅ (team only) | ✅ |

### Error Response Matrix

| Scenario | Expected HTTP Status | Expected Error |
|----------|---------------------|----------------|
| Unauthorized team access | 403 Forbidden | "Access denied to team" |
| Invalid team ID | 400 Bad Request | "Invalid team ID" |
| Expired token | 401 Unauthorized | "Token expired" |
| Missing token | 401 Unauthorized | "Authorization required" |
| Malformed request | 400 Bad Request | "Invalid request format" |

---

## 🛠️ Automated Testing Scripts

### Basic Smoke Test Script
```bash
#!/bin/bash
# team-sharing-smoke-test.sh

BASE_URL="https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev"
PASS_COUNT=0
FAIL_COUNT=0

# Test 1: Standard user can login
echo "Testing standard user login..."
RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"standarduser","password":"TeamShare123"}')

if echo "$RESPONSE" | grep -q "idToken"; then
  echo "✅ Standard user login: PASS"
  ((PASS_COUNT++))
else
  echo "❌ Standard user login: FAIL"
  ((FAIL_COUNT++))
fi

# Add more automated tests here...

echo "Results: $PASS_COUNT passed, $FAIL_COUNT failed"
```

### Postman Collection Test Runner
```javascript
// Pre-request Script for Postman Collection
pm.environment.set("timestamp", Date.now());

// Test for login response
pm.test("Login successful", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json()).to.have.property('idToken');
    
    // Store token for subsequent requests
    const responseJson = pm.response.json();
    pm.environment.set("idToken", responseJson.idToken);
});

// Test for team access control
pm.test("User can access own team items", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.items).to.be.an('array');
    
    // Verify only authorized team items are visible
    jsonData.items.forEach(item => {
        if (item.teamId) {
            pm.expect(item.teamId).to.be.oneOf(['team-engineering']); // for standarduser
        }
    });
});
```

---

## 🚨 Common Issues & Troubleshooting

### Issue 1: User Login Failures
**Symptoms**: 
- "User does not exist" error
- "Incorrect username or password" error

**Solutions**:
1. Verify username is exact (case-sensitive)
2. Confirm password is `TeamShare123!`
3. Check you're using correct environment
4. Verify user exists in Cognito pool

### Issue 2: Authorization Failures
**Symptoms**:
- 403 Forbidden on team operations
- Cannot access expected team items

**Solutions**:
1. Check user's team membership in AuthorizationService
2. Verify JWT token contains correct user ID
3. Confirm team ID is spelled correctly
4. Check if user pattern matching is working

### Issue 3: Token Issues
**Symptoms**:
- "Invalid token" errors
- Intermittent authorization failures

**Solutions**:
1. Use `idToken` not `accessToken`
2. Check token expiration (1 hour limit)
3. Refresh token if needed
4. Verify Bearer token format

### Issue 4: Cross-Environment Issues
**Symptoms**:
- Users don't exist in different environments
- Different behavior across env

**Solutions**:
1. Verify you're testing correct environment
2. Check all users created in target environment
3. Confirm API URLs match environment
4. Validate User Pool IDs

---

## 📈 Test Reporting Template

### Test Execution Report

**Date**: [Date]  
**Environment**: [dev/stage/prod]  
**Tester**: [Name]  
**Build/Version**: [Version]

#### Test Summary
- **Total Tests**: [Number]
- **Passed**: [Number] 
- **Failed**: [Number]
- **Skipped**: [Number]
- **Pass Rate**: [Percentage]

#### Failed Tests
| Test ID | Test Name | Expected | Actual | Notes |
|---------|-----------|----------|--------|-------|
| 1.4 | Standard user team isolation | 403 Forbidden | 200 OK | User could access marketing team |

#### Environment Details
- **API Base URL**: [URL]
- **User Pool ID**: [ID]
- **Test Duration**: [Duration]
- **Network Issues**: [Any connectivity problems]

#### Recommendations
- [Action items for failed tests]
- [Performance observations]
- [Security concerns]

---

**Created**: 2025-08-17  
**Last Updated**: 2025-08-17  
**Version**: 1.0  
**Status**: Ready for testing

This guide should be used in conjunction with `team-sharing-test-credentials.md` for complete team sharing validation.