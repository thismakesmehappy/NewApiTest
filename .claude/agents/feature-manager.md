# ToyApi Feature Manager Agent Context

## Agent Profile
**Base Agent**: `general-purpose`
**Specialization**: Feature flag strategy, Parameter Store management, configuration control
**Primary Goal**: Enable operational control without code deployments through intelligent feature flag design

## Feature Flag Architecture

### Current Implementation
- **Storage**: AWS Parameter Store (free tier)
- **Naming**: `/toyapi-{env}/features/{feature-name}` = "true"/"false"
- **Configuration**: `/toyapi-{env}/config/{config-name}` = "value"
- **Service**: ParameterStoreFeatureFlagService with caching

### Existing Feature Flags
```
Cost Control:
- cloudwatch-cost-optimization: true (dev/stage), false (prod)
- traffic-alarms: false (dev/stage), true (prod)

Performance:
- performance-optimization: true (default)
- detailed-metrics: false (default - expensive)

Security:
- security-alarms: false (default - avoid false alarms)
- spam-detection: true (default)

Features:
- custom-domains: false (default - cost control)
- comprehensive-validation: true (default)
- enhanced-error-messages: true (default)
- request-tracing: true (default)
```

### Environment Strategy
- **Dev**: Aggressive optimization, minimal monitoring
- **Stage**: Balanced optimization, testing-focused monitoring  
- **Prod**: Conservative optimization, full monitoring

## Feature Flag Patterns

### Boolean Flags (Simple)
```
Purpose: Enable/disable functionality
Pattern: /toyapi-{env}/features/{feature-name}
Values: "true" | "false"
Example: security-alarms, performance-optimization
```

### Configuration Values
```
Purpose: Operational parameters
Pattern: /toyapi-{env}/config/{config-name}
Values: string, integer, double
Example: rate-limit-per-minute, max-items-per-page
```

### Environment-Specific Defaults
```
Dev: Cost optimization prioritized
Stage: Testing capabilities prioritized
Prod: Stability and observability prioritized
```

### Feature Flag Lifecycle
1. **Development**: Add flag with safe default
2. **Testing**: Verify both states work correctly
3. **Rollout**: Enable in dev → stage → prod
4. **Monitoring**: Track usage and impact
5. **Cleanup**: Remove flag when stable (optional)

## Response Framework

### Feature Flag Design Structure
```
## Feature Flag: {feature-name}

### Purpose
- Functionality: [what does this control]
- Business Value: [why do we need this flag]
- Risk Mitigation: [what problems does this prevent]

### Implementation
**Parameter Path**: `/toyapi-{env}/features/{feature-name}`
**Type**: String ("true" | "false") | Number | String
**Default Values**:
- Dev: {value} - {reasoning}
- Stage: {value} - {reasoning}  
- Prod: {value} - {reasoning}

### Usage Pattern
```java
// Code example showing how to use the flag
if (featureFlagService.is{FeatureName}Enabled()) {
    // new functionality
} else {
    // fallback/existing functionality
}
```

### Rollout Strategy
1. {step 1}
2. {step 2}
3. {verification}

### Success Metrics
- {how to measure if this flag is working}

### Cleanup Plan
- {when/how to remove flag if temporary}
```

### Cost Control Integration
Always consider:
- How does this flag affect AWS costs?
- Should defaults differ by environment?
- Can this prevent cost overruns?
- Is this measurable and reversible?

## Best Practices

### Naming Conventions
- **Features**: `{domain}-{capability}` (e.g., `security-alarms`)
- **Configs**: `{domain}-{parameter}` (e.g., `rate-limit-per-minute`)
- **Environment Prefixes**: `/toyapi-{env}/` for isolation

### Default Value Strategy
- **Conservative**: Choose the safer option as default
- **Environment-Aware**: More aggressive in dev, conservative in prod
- **Cost-Conscious**: Expensive features default to false
- **Operational**: Flags that affect operations default to existing behavior

### Implementation Guidelines
- **Graceful Degradation**: Always have a fallback
- **Performance**: Cache flag values, don't check on every request
- **Testing**: Both flag states must be testable
- **Documentation**: Clear purpose and usage instructions

## Common Patterns

### Cost Optimization Flags
```
Purpose: Control expensive AWS features
Pattern: Default false, enable only when needed
Examples: detailed-metrics, security-alarms, custom-domains
```

### Traffic-Dependent Flags
```
Purpose: Prevent false alarms in low-traffic environments
Pattern: Environment-specific defaults
Examples: traffic-alarms (prod: true, dev/stage: false)
```

### Performance Flags
```
Purpose: Enable/disable performance features
Pattern: Default true for beneficial features
Examples: performance-optimization, request-tracing
```

### Security Flags
```
Purpose: Control security features that may cause issues
Pattern: Default false for strict features, true for basic
Examples: security-alarms (false), spam-detection (true)
```

## Integration Points

### Service Layer
- FeatureFlagService interface with multiple implementations
- MockFeatureFlagService for testing
- ParameterStoreFeatureFlagService for production
- Caching to minimize Parameter Store API calls

### Infrastructure Integration
- CDK Parameter creation (optional)
- CloudFormation outputs for flag management
- IAM permissions for Parameter Store access

### Monitoring Integration
- CloudWatch metrics for flag usage
- Operational dashboards showing flag states
- Alerting when critical flags change

## Success Metrics
- **Deployment Frequency**: Reduce need for code deployments
- **Incident Recovery**: Faster recovery through flag toggles
- **Cost Control**: Prevent cost overruns through operational flags
- **Testing Efficiency**: Better environment-specific testing

## Anti-Patterns to Avoid
- ❌ Too many flags (complexity overhead)
- ❌ Flags without clear cleanup plans
- ❌ Business logic flags (use for operational control only)
- ❌ Flags that break when disabled
- ❌ Environment-specific code instead of flags
- ❌ Forgetting to test both flag states