# ToyApi Integration Tests

This module contains integration tests organized by test type and execution method. Tests can be run independently or together, depending on your needs.

## Test Structure

```
integration-tests/src/test/java/co/thismakesmehappy/toyapi/integration/
├── programmatic/               # Direct AWS SDK calls
│   └── ParameterStoreIntegrationTest.java
├── http/
│   ├── aws/                   # AWS API Gateway URLs
│   │   └── AwsApiGatewayIntegrationTest.java
│   └── custom/                # Custom domain URLs
│       └── CustomDomainIntegrationTest.java
├── shared/                    # Common utilities
│   ├── TestConfiguration.java
│   └── AuthenticationHelper.java
└── deprecated/                # Old tests (kept for reference)
    ├── ApiIntegrationTest.java
    └── ParameterStoreIntegrationTest.java
```

## Test Types

### 1. Programmatic Tests
- **Location**: `integration/programmatic/`
- **Purpose**: Test direct AWS service integration (Parameter Store, etc.)
- **Tags**: `@Tag("programmatic")`
- **Network**: AWS SDK calls only

### 2. HTTP Tests - AWS API Gateway
- **Location**: `integration/http/aws/`
- **Purpose**: Test API through AWS-generated API Gateway URLs
- **Tags**: `@Tag("http")`, `@Tag("aws")`
- **Network**: HTTPS to `*.execute-api.us-east-1.amazonaws.com`

### 3. HTTP Tests - Custom Domains
- **Location**: `integration/http/custom/`
- **Purpose**: Test API through custom domain URLs
- **Tags**: `@Tag("http")`, `@Tag("custom")`
- **Network**: HTTPS to `*.thismakesmehappy.co`
- **Prerequisites**: DNS propagation complete, custom domains configured

## Running Tests

### All Integration Tests (Default)
```bash
mvn test -Pintegration-test -Dtest.environment=dev
```

### Programmatic Tests Only
```bash
mvn test -Pprogrammatic-test -Dtest.environment=dev
```

### HTTP Tests with AWS API Gateway
```bash
mvn test -Phttp-aws-test -Dtest.environment=stage
```

### HTTP Tests with Custom Domains
```bash
mvn test -Phttp-custom-test -Dtest.environment=prod -Dtest.custom.domains.enabled=true
```

### Smoke Tests (Quick Health Checks)
```bash
mvn test -Psmoke-test -Dtest.environment=dev
```

## Environment Configuration

### Supported Environments
- `dev` (default)
- `stage`
- `prod`

### Environment URLs
- **Dev**: `https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev`
- **Stage**: `https://5ytzml6fnb.execute-api.us-east-1.amazonaws.com/stage`
- **Production**: `https://skslof01gg.execute-api.us-east-1.amazonaws.com/prod`

### Custom Domain URLs
- **Dev**: `https://dev.api.thismakesmehappy.co`
- **Stage**: `https://stage.api.thismakesmehappy.co`
- **Production**: `https://api.thismakesmehappy.co`

## Test Tags

Tests are organized with JUnit 5 tags for flexible execution:

- `@Tag("integration")` - All integration tests
- `@Tag("programmatic")` - Direct AWS SDK calls
- `@Tag("http")` - HTTP-based tests
- `@Tag("aws")` - AWS API Gateway URLs
- `@Tag("custom")` - Custom domain URLs
- `@Tag("smoke")` - Quick health checks
- `@Tag("health")` - Health/status tests
- `@Tag("auth")` - Authentication tests
- `@Tag("crud")` - CRUD operation tests
- `@Tag("performance")` - Performance validation tests

## Configuration Properties

### System Properties
- `test.environment` - Target environment (dev/stage/prod)
- `test.custom.domains.enabled` - Enable custom domain tests (true/false)
- `test.username` - Test user username (defaults to "testuser")
- `test.password` - Test user password (defaults to "TestPassword123")

### Example with Custom Properties
```bash
mvn test -Phttp-aws-test \
  -Dtest.environment=stage \
  -Dtest.username=myuser \
  -Dtest.password=mypassword
```

## CI/CD Integration

### GitHub Actions Usage
```yaml
# Run programmatic tests (no network dependencies)
- name: Run Programmatic Tests
  run: mvn test -Pprogrammatic-test -Dtest.environment=dev

# Run HTTP tests against staging
- name: Run HTTP Tests - Staging
  run: mvn test -Phttp-aws-test -Dtest.environment=stage

# Run smoke tests for quick validation
- name: Run Smoke Tests
  run: mvn test -Psmoke-test -Dtest.environment=prod
```

### Local Development
```bash
# Quick smoke test during development
mvn test -Psmoke-test

# Full HTTP test against local/dev environment
mvn test -Phttp-aws-test -Dtest.environment=dev

# Test Parameter Store integration
mvn test -Pprogrammatic-test
```

## Dependencies

### Required Dependencies
- ToyApi Service module (for ParameterStoreHelper)
- ToyApi Model module (for API contracts)
- REST Assured (for HTTP testing)
- JUnit 5 (for test execution)

### Optional Dependencies
- AWS SDK (already included via service module)
- Jackson (for JSON processing)

## Development Guidelines

### Adding New Tests

1. **Choose the correct package**:
   - Direct AWS calls → `programmatic/`
   - HTTP via AWS URLs → `http/aws/`
   - HTTP via custom domains → `http/custom/`

2. **Use appropriate tags**:
   ```java
   @Tag("integration")
   @Tag("http")  // or "programmatic"
   @Tag("aws")   // or "custom"
   ```

3. **Use shared utilities**:
   - `TestConfiguration` for environment setup
   - `AuthenticationHelper` for JWT token management

4. **Follow naming conventions**:
   - `*IntegrationTest.java` for test classes
   - Descriptive test method names

### Test Isolation

Each test type is designed to run independently:
- **Programmatic tests** don't require HTTP connectivity
- **AWS HTTP tests** work with any environment
- **Custom domain tests** require DNS and SSL setup

This allows for:
- Faster CI/CD pipelines (run only relevant tests)
- Better debugging (isolate network vs. logic issues)
- Flexible deployment validation (test each layer separately)

## Troubleshooting

### Custom Domain Tests Skipped
If custom domain tests are skipped with "Custom domains not enabled":
```bash
mvn test -Phttp-custom-test -Dtest.custom.domains.enabled=true
```

### Authentication Failures
Verify test credentials:
```bash
mvn test -Dtest.username=youruser -Dtest.password=yourpass
```

### Environment Issues
Check environment configuration:
```bash
mvn test -Dtest.environment=stage -X  # Debug logging
```

### Network Connectivity
Test with different profiles:
```bash
# Test programmatic only (no HTTP)
mvn test -Pprogrammatic-test

# Test HTTP with different environment
mvn test -Phttp-aws-test -Dtest.environment=dev
```