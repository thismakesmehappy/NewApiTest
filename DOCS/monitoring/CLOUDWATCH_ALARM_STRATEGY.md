# CloudWatch Alarm Optimization Strategy

## Overview

This document explains our CloudWatch alarm optimization strategy designed to stay within AWS free tier limits while ensuring proper monitoring across environments.

## Problem Statement

**AWS Free Tier Limit**: 10 CloudWatch alarms per account  
**Current Usage**: 9/10 alarms used (90% of limit)  
**Challenge**: Need monitoring across 3 environments (dev, staging, production) without exceeding limits

## Solution: Tiered Alarm Strategy

### Alarm Allocation

| Environment | Alarm Count | Purpose | Justification |
|-------------|-------------|---------|---------------|
| **Production** | 6 alarms | Critical user-facing issues | Most important for business continuity |
| **Staging** | 3 alarms | Pre-production validation | Ensures safe promotion to production |
| **Development** | 0 alarms | Development only | Dashboards provide sufficient visibility |
| **Account-wide** | 1 alarm | Cost monitoring | Prevents budget overruns |
| **Total** | **10 alarms** | **Free tier limit** | **Maximizes coverage within constraints** |

### Production Alarms (6 Critical Alarms)

These alarms monitor the most critical user-facing issues:

1. **API High Latency** (`toyapi-prod-api-high-latency`)
   - **Threshold**: > 2000ms average latency
   - **Why Critical**: Directly impacts user experience
   - **Action**: Immediate investigation required

2. **API High Client Errors** (`toyapi-prod-api-high-client-errors`)
   - **Threshold**: > 20 4XX errors in 5 minutes
   - **Why Critical**: Indicates authentication/authorization issues
   - **Action**: Check auth service health

3. **API Server Errors** (`toyapi-prod-api-server-errors`)
   - **Threshold**: ≥ 1 5XX error
   - **Why Critical**: Any server error indicates system failure
   - **Action**: Immediate response required

4. **Lambda Errors** (`toyapi-prod-lambda-errors`)
   - **Threshold**: ≥ 5 errors across all functions
   - **Why Critical**: Application logic failures
   - **Action**: Check function logs and rollback if needed

5. **DynamoDB Throttling** (`toyapi-prod-dynamodb-throttles`)
   - **Threshold**: ≥ 1 throttle event
   - **Why Critical**: Database unavailability affects all operations
   - **Action**: Scale capacity or investigate query patterns

6. **Security Breach** (`toyapi-prod-security-breach`)
   - **Threshold**: > 50 failed authentications in 5 minutes
   - **Why Critical**: Potential security attack
   - **Action**: Security incident response

### Staging Alarms (3 Essential Alarms)

These alarms ensure staging is safe for production promotion:

1. **Availability** (`toyapi-stage-availability`)
   - **Threshold**: ≥ 1 5XX error
   - **Why Important**: Any server error indicates deployment issues
   - **Action**: Block production promotion until resolved

2. **Performance Regression** (`toyapi-stage-performance-regression`)
   - **Threshold**: > 3000ms average latency
   - **Why Important**: Prevents performance regressions from reaching production
   - **Action**: Investigate performance changes

3. **High Error Rate** (`toyapi-stage-high-error-rate`)
   - **Threshold**: > 50 4XX errors in 5 minutes
   - **Why Important**: Indicates configuration or deployment issues
   - **Action**: Review deployment and configuration

### Development Environment (0 Alarms)

**Why No Alarms?**
- Development environment is for testing and experimentation
- Dashboards provide sufficient visibility for developers
- Saves alarm quota for production-critical monitoring
- Developers can manually check metrics when needed

**What's Available Instead?**
- Complete CloudWatch dashboards with all metrics
- Real-time monitoring widgets
- Log aggregation and search capabilities
- Manual metric inspection tools

### Account-wide Monitoring (1 Alarm)

1. **Cost Alert** (`toyapi-cost-alert`)
   - **Threshold**: > $8 estimated monthly charges
   - **Why Critical**: Prevents unexpected billing
   - **Action**: Review resource usage and optimize costs

## Enterprise Scaling Path

### Current State (Free Tier Optimized)
- **Cost**: ~$5/month
- **Alarms**: 10 (free tier limit)
- **Coverage**: Critical issues only

### Phase 1: Strategic Additions ($15/month)
If budget allows $15/month, add:
- **Per-Lambda function alarms** (error, duration, throttle)
- **X-Ray tracing** for detailed performance analysis
- **Synthetic monitoring** for uptime validation
- **Additional 20 alarms** (~$10/month)

### Phase 2: Full Enterprise ($50/month)
For production-ready applications with $50+/month budget:
- **Detailed SLA monitoring** per endpoint
- **Infrastructure alarms** (EC2, ELB, RDS)
- **Security monitoring** (WAF, GuardDuty integration)
- **Business metrics** (user activity, conversion rates)
- **Log-based anomaly detection**
- **Multi-region monitoring**
- **Third-party integrations** (PagerDuty, Slack)
- **Estimated cost**: $50-200/month

## Implementation Details

### Code Location
```
/infra/src/main/java/co/thismakesmehappy/toyapi/infra/MonitoringStack.java
```

### Key Methods
- `createProductionAlarms()` - Creates 6 production alarms
- `createStagingAlarms()` - Creates 3 staging alarms  
- `createMonitoringFeatureFlags()` - Parameter Store configuration
- Environment detection: `isProduction`, `isStaging`, `enableAlarms`

### Feature Flags
The monitoring system uses Parameter Store feature flags for configuration:

```
/toyapi-{environment}/features/detailed-monitoring = "true"/"false"
/toyapi-{environment}/config/alarm-sensitivity = "low"/"medium"/"high"
/toyapi-{environment}/config/dashboard-complexity = "basic"/"standard"/"advanced"
/toyapi-{environment}/config/log-retention-days = "7"/"30"/"90"
```

## Dashboards vs. Alarms

### What We Keep in All Environments
- **CloudWatch Dashboards** - Free and comprehensive
- **Real-time metrics widgets** - No cost impact
- **Log aggregation** - Minimal cost with reasonable retention
- **Custom metrics** - Log-based metrics are free
- **Performance tracking** - Available in all environments

### What We Optimize (Alarms Only)
- **SNS notifications** - Only for critical issues
- **Immediate alerting** - Only where action is required
- **PagerDuty integration** - Only for production incidents

## Monitoring Without Alarms

### Development Environment Strategy
Since dev has no alarms, developers use:

1. **Dashboard Monitoring**
   - Real-time metric widgets
   - Error rate tracking
   - Performance visualization

2. **Log-based Debugging**
   - CloudWatch Logs Insights queries
   - Error pattern detection
   - Performance bottleneck identification

3. **Manual Inspection**
   - On-demand metric review
   - Local testing validation
   - Integration test results

## Cost Analysis

### Current Free Tier Usage
| Service | Usage | Cost |
|---------|-------|------|
| CloudWatch Alarms | 10/10 | $0 (free tier) |
| CloudWatch Dashboards | 3 | $0 (up to 3 free) |
| CloudWatch Metrics | Standard | $0 (first 10 metrics free) |
| CloudWatch Logs | ~100MB/month | ~$0.50 |
| SNS Notifications | ~50/month | ~$0.10 |
| Parameter Store | ~20 parameters | ~$0.05 |
| **Total** | | **~$0.65/month** |

### Enterprise Comparison
| Solution | Monthly Cost | Alarm Count | Features |
|----------|--------------|-------------|----------|
| **Our Optimized** | ~$0.65 | 10 | Critical monitoring + dashboards |
| **Full CloudWatch** | ~$50 | 100+ | Complete monitoring |
| **DataDog** | ~$150 | Unlimited | APM + monitoring |
| **New Relic** | ~$200 | Unlimited | Full observability |

## Benefits of This Approach

### ✅ Immediate Benefits
- **Stay within free tier** - No unexpected charges
- **Focus on critical issues** - Alarms only for actionable problems
- **Comprehensive dashboards** - Full visibility without cost
- **Environment-appropriate monitoring** - Different needs per environment

### ✅ Strategic Benefits
- **Scalable architecture** - Easy to add alarms as budget grows
- **Feature flag controlled** - Toggle monitoring levels via Parameter Store
- **Enterprise-ready foundation** - Built for future scaling
- **Cost transparency** - Clear upgrade path and costs

### ✅ Operational Benefits
- **Reduced noise** - Only critical alerts interrupt teams
- **Faster response** - Clear priority on production issues
- **Better focus** - Staging alarms prevent production issues

## Decision Rationale

### Why Production Gets 6 Alarms
- **User-facing impact** - Direct business consequences
- **Revenue protection** - Downtime affects business
- **SLA compliance** - Contractual obligations
- **Security requirements** - Immediate threat response

### Why Staging Gets 3 Alarms
- **Quality gate** - Prevents bad deployments
- **Early detection** - Catches issues before production
- **Safe promotion** - Ensures staging → production confidence
- **Minimal viable monitoring** - Essential checks only

### Why Development Gets 0 Alarms
- **Non-critical environment** - No user impact
- **Experimentation space** - Expected to have issues
- **Developer responsibility** - Team monitors their own work
- **Cost optimization** - Maximum alarm quota for critical environments

## Future Considerations

### When to Add More Alarms
1. **Revenue growth** - When business can afford $15+/month
2. **Team growth** - When 24/7 response team exists
3. **SLA requirements** - When contractual obligations require more monitoring
4. **Compliance needs** - When regulations require detailed monitoring

### Upgrade Path
1. **First**: Add per-Lambda function monitoring ($5-10/month)
2. **Second**: Add synthetic monitoring for uptime ($10-20/month)
3. **Third**: Add infrastructure monitoring ($20-50/month)
4. **Fourth**: Add APM and full observability ($50-200/month)

## Monitoring This Strategy

### How to Track Alarm Usage
```bash
# Check current alarm count
aws cloudwatch describe-alarms --query 'length(MetricAlarms)'

# List all alarms by environment
aws cloudwatch describe-alarms --alarm-name-prefix "toyapi-prod"
aws cloudwatch describe-alarms --alarm-name-prefix "toyapi-stage"
```

### How to Monitor Costs
- Monthly CloudWatch cost review
- AWS Cost Explorer for monitoring services
- Budget alerts at $5, $10, $15 thresholds

---

**Last Updated**: 2025-08-12  
**Strategy Owner**: ToyApi Development Team  
**Review Schedule**: Monthly cost review, quarterly strategy review