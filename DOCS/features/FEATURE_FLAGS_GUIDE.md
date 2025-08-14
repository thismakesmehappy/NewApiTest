# Feature Flags with Parameter Store

## Overview

Feature flags are now implemented using your existing AWS Parameter Store infrastructure. **Zero additional cost** - uses your current setup.

## How It Works

### Parameter Structure
```
/toyapi-{environment}/features/{feature-name} = "true"/"false"
/toyapi-{environment}/config/{config-name} = "value"
```

### Examples
```
/toyapi-prod/features/comprehensive-validation = "true"
/toyapi-prod/features/spam-detection = "false"  
/toyapi-prod/config/max-items-per-page = "25"
/toyapi-prod/config/spam-threshold = "0.9"
```

## Manual Control Options

### 1. AWS Console
1. Go to **Systems Manager → Parameter Store**
2. Find parameter: `/toyapi-prod/features/spam-detection`
3. Click **Edit** 
4. Change value: `false` → `true`
5. **Save** → Takes effect in ~30 seconds

### 2. AWS CLI
```bash
# Enable feature
aws ssm put-parameter \
  --name "/toyapi-prod/features/comprehensive-validation" \
  --value "true" \
  --overwrite

# Adjust configuration  
aws ssm put-parameter \
  --name "/toyapi-prod/config/max-items-per-page" \
  --value "25" \
  --overwrite

# Disable feature (instant rollback)
aws ssm put-parameter \
  --name "/toyapi-prod/features/spam-detection" \
  --value "false" \
  --overwrite
```

### 3. CDK Infrastructure (Permanent Settings)
```typescript
new StringParameter(stack, 'SpamDetectionFlag', {
  parameterName: '/toyapi-prod/features/spam-detection',
  stringValue: 'true'
});

new StringParameter(stack, 'MaxItemsConfig', {
  parameterName: '/toyapi-prod/config/max-items-per-page', 
  stringValue: '50'
});
```

## Available Feature Flags

### Features (true/false)
| Flag | Parameter | Default | Description |
|------|-----------|---------|-------------|
| Comprehensive Validation | `features/comprehensive-validation` | `true` | Collect all validation errors vs fail-fast |
| Enhanced Error Messages | `features/enhanced-error-messages` | `true` | Include detailed error context |
| Spam Detection | `features/spam-detection` | `true` | Enable spam content filtering |
| Request Tracing | `features/request-tracing` | `true` | Enable request ID tracking |
| Detailed Metrics | `features/detailed-metrics` | `false` | Collect expensive metrics |

### Configuration (numeric/string values)
| Setting | Parameter | Default | Description |
|---------|-----------|---------|-------------|
| Max Items Per Page | `config/max-items-per-page` | `50` | Pagination limit |
| Rate Limit | `config/rate-limit-per-minute` | `100` | Requests per user per minute |
| Max Message Length | `config/max-message-length` | `1000` | Characters in item message |
| Spam Threshold | `config/spam-threshold` | `0.8` | Spam detection sensitivity (0.0-1.0) |

## Usage in Code

### Basic Usage
```java
// In your service constructor
FeatureFlagService flags = new ParameterStoreFeatureFlagService(parameterStore);

// Use feature flags
if (flags.isSpamDetectionEnabled()) {
    double threshold = flags.getSpamDetectionThreshold();
    // Run spam detection with threshold
}

int maxItems = flags.getMaxItemsPerPage();
// Use in validation: "Limit cannot exceed " + maxItems
```

### Integration Example
```java
public class ItemValidationService {
    private final FeatureFlagService featureFlags;
    
    public void validateMessage(String message) throws ValidationException {
        int maxLength = featureFlags.getConfigValueAsInt("max-message-length", 1000);
        
        if (message.length() > maxLength) {
            throw new ValidationException("Message cannot exceed " + maxLength + " characters");
        }
    }
}
```

## Real-World Scenarios

### Scenario 1: Performance Issue
```bash
# Problem: API is slow under high load
# Solution: Reduce limits instantly

aws ssm put-parameter --name "/toyapi-prod/config/max-items-per-page" --value "25" --overwrite
aws ssm put-parameter --name "/toyapi-prod/config/rate-limit-per-minute" --value "50" --overwrite
aws ssm put-parameter --name "/toyapi-prod/features/detailed-metrics" --value "false" --overwrite
```

### Scenario 2: New Feature Rollout
```bash
# Stage 1: Test new validation in dev
aws ssm put-parameter --name "/toyapi-dev/features/comprehensive-validation" --value "true" --overwrite

# Stage 2: Deploy to production (disabled) 
aws ssm put-parameter --name "/toyapi-prod/features/comprehensive-validation" --value "false" --overwrite

# Stage 3: Enable in production
aws ssm put-parameter --name "/toyapi-prod/features/comprehensive-validation" --value "true" --overwrite

# Stage 4: Rollback if needed (instant)
aws ssm put-parameter --name "/toyapi-prod/features/comprehensive-validation" --value "false" --overwrite
```

### Scenario 3: Security Incident
```bash
# Increase security measures immediately
aws ssm put-parameter --name "/toyapi-prod/features/spam-detection" --value "true" --overwrite
aws ssm put-parameter --name "/toyapi-prod/config/spam-threshold" --value "0.7" --overwrite
aws ssm put-parameter --name "/toyapi-prod/features/enhanced-error-messages" --value "false" --overwrite
```

## Cost Analysis

### Parameter Store Costs
- **API calls**: ~$0.05 per 10,000 requests
- **Storage**: $0.05 per 10,000 parameters per month
- **Your usage**: ~$2-5/month total

### Compared to Alternatives
- **AppConfig**: $15-50/month (overkill for your scale)
- **LaunchDarkly**: $200+/month (enterprise)
- **Split.io**: $50+/month (SaaS)

## Benefits

✅ **Zero new infrastructure** - extends existing Parameter Store
✅ **Practically free** - pennies per month  
✅ **Instant changes** - 30 second propagation
✅ **Environment isolation** - different values per env
✅ **AWS Console control** - non-technical team members can toggle
✅ **CLI automation** - scriptable deployments
✅ **Secure** - encryption at rest, IAM controlled
✅ **Proven reliability** - you're already using it successfully

## Testing

Use `MockFeatureFlagService` in tests:

```java
@Test
void testWithFeatureEnabled() {
    MockFeatureFlagService flags = new MockFeatureFlagService();
    flags.setFeatureFlag("spam-detection", true);
    flags.setConfigValue("spam-threshold", "0.9");
    
    // Test with flags enabled
    ItemValidationService validator = new ItemValidationService(flags);
    // ... test validation behavior
}
```

## Next Steps

1. **Deploy feature flag infrastructure** (extend existing Parameter Store)
2. **Set initial parameters** via AWS Console or CDK
3. **Integrate into services** gradually
4. **Monitor and adjust** as needed

Ready to implement? The infrastructure is already there! 🚀