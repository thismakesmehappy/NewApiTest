# 🏷️ ToyAPI Resource Naming Standards

## **Current Issues Identified**
- Mixed naming conventions across resources
- Inconsistent prefixes and suffixes  
- Generic names that could cause conflicts
- Varying levels of specificity

## **Standardized Naming Convention**

### **Format**: `{ServicePrefix}{ResourceType}{Purpose}{Environment?}`

### **Examples**:
```java
// ✅ CORRECT - Consistent and descriptive
Table.Builder.create(this, "ToyApiDynamoItems")           // Service + Type + Purpose
Function.Builder.create(this, "ToyApiLambdaDeveloper")    // Service + Type + Purpose  
Secret.Builder.create(this, "ToyApiSecretApiKeys")        // Service + Type + Purpose
UserPool.Builder.create(this, "ToyApiCognitoUsers")       // Service + Type + Purpose

// ❌ INCORRECT - Current inconsistent naming
Table.Builder.create(this, "ItemsTable")                 // Purpose + Type (reversed)
Function.Builder.create(this, "DeveloperFunction")       // Purpose + Type (reversed)
Secret.Builder.create(this, "ApiKeySecret")              // Purpose + Type (reversed)
UserPool.Builder.create(this, "UserPool")                // Too generic
```

## **Resource Type Mappings**

| AWS Service | Prefix | Example |
|-------------|--------|---------|
| DynamoDB | `ToyApiDynamo` | `ToyApiDynamoItems`, `ToyApiDynamoMetrics` |
| Lambda | `ToyApiLambda` | `ToyApiLambdaAuth`, `ToyApiLambdaAnalytics` |
| Cognito | `ToyApiCognito` | `ToyApiCognitoUsers`, `ToyApiCognitoClient` |
| API Gateway | `ToyApiGateway` | `ToyApiGatewayRest`, `ToyApiGatewayStage` |
| CloudWatch | `ToyApiCloudWatch` | `ToyApiCloudWatchDashboard`, `ToyApiCloudWatchAlarms` |
| S3 | `ToyApiS3` | `ToyApiS3Logs`, `ToyApiS3Assets` |
| IAM | `ToyApiIam` | `ToyApiIamRole`, `ToyApiIamPolicy` |
| VPC | `ToyApiVpc` | `ToyApiVpcCache`, `ToyApiVpcSecurityGroup` |
| ElastiCache | `ToyApiCache` | `ToyApiCacheRedis`, `ToyApiCacheSubnetGroup` |
| CloudFront | `ToyApiCdn` | `ToyApiCdnDistribution`, `ToyApiCdnPolicy` |
| WAF | `ToyApiWaf` | `ToyApiWafWebAcl`, `ToyApiWafRuleSet` |
| Secrets Manager | `ToyApiSecret` | `ToyApiSecretApiKeys`, `ToyApiSecretSigning` |
| SNS | `ToyApiSns` | `ToyApiSnsAlerts`, `ToyApiSnsNotifications` |
| Kinesis | `ToyApiKinesis` | `ToyApiKinesisAnalytics`, `ToyApiKinesisEvents` |

## **Specific Naming Rules**

### **1. Logical Grouping**
```java
// Security-related resources
ToyApiSecretApiKeys
ToyApiSecretSigning  
ToyApiWafWebAcl
ToyApiWafRuleMalicious

// Analytics-related resources
ToyApiKinesisAnalytics
ToyApiLambdaAnalyticsProcessor
ToyApiLambdaAnalyticsReporter
ToyApiDynamoAnalyticsMetrics

// Caching-related resources
ToyApiCacheRedis
ToyApiCacheVpc
ToyApiCacheSecurityGroup
ToyApiDynamoDax
```

### **2. Environment Handling**
- Environment is handled via `resourcePrefix` variable
- CDK construct names should NOT include environment (handled at runtime)
- Resource names (actual AWS resources) include environment via `resourcePrefix`

```java
// ✅ CORRECT
Table.Builder.create(this, "ToyApiDynamoItems")
    .tableName(resourcePrefix + "-items")  // toyapi-dev-items, toyapi-prod-items

// ❌ INCORRECT  
Table.Builder.create(this, "ToyApiDynamoItemsDev")  // Don't hardcode environment
```

### **3. Special Cases**

#### **Lambda Functions**
```java
ToyApiLambdaAuth          // Authentication handler
ToyApiLambdaItems         // Items CRUD handler
ToyApiLambdaPublic        // Public endpoints handler
ToyApiLambdaDeveloper     // Developer key management
ToyApiLambdaAnalytics     // Analytics processing
ToyApiLambdaRotation      // Key rotation handler
```

#### **CloudWatch Alarms**
```java
ToyApiAlarmHighUsage      // Usage monitoring  
ToyApiAlarmLowUsage       // Usage monitoring
ToyApiAlarmWafBlocked     // Security monitoring
ToyApiAlarmCacheMemory    // Performance monitoring
```

#### **Security Groups**
```java
ToyApiSecurityGroupCache      // ElastiCache access
ToyApiSecurityGroupLambda     // Lambda VPC access  
ToyApiSecurityGroupDax        // DAX cluster access
```

## **Implementation Priority**

### **Phase 1: Critical Security Resources** ⚠️
1. `ApiKeySecret` → `ToyApiSecretApiKeys`
2. `RequestSigningSecret` → `ToyApiSecretSigning`
3. `CacheSecurityGroup` → `ToyApiSecurityGroupCache`
4. `LambdaCacheSecurityGroup` → `ToyApiSecurityGroupLambda`

### **Phase 2: Core Infrastructure** 
1. `ItemsTable` → `ToyApiDynamoItems`
2. `UserPool` → `ToyApiCognitoUsers`
3. `UserPoolClient` → `ToyApiCognitoClient`

### **Phase 3: Monitoring & Analytics**
1. `AnalyticsStream` → `ToyApiKinesisAnalytics`
2. `UsageMetricsTable` → `ToyApiDynamoMetrics`
3. `AnalyticsDashboard` → `ToyApiCloudWatchDashboard`

## **Benefits of Standardization**

### **🔍 Improved Discoverability**
- Easy to find related resources
- Clear service boundaries
- Logical grouping in AWS Console

### **🛡️ Reduced Naming Conflicts**
- Unique prefixes prevent collisions
- Clear ownership of resources
- Environment isolation maintained

### **📊 Better Monitoring**
- Consistent tagging strategies
- Easier automated resource management
- Clear cost allocation

### **🔧 Simplified Maintenance**
- Predictable naming patterns
- Easier infrastructure documentation
- Reduced cognitive load for developers

## **Next Steps**

1. **Update high-priority security resources first**
2. **Test deployment in dev environment**
3. **Gradually migrate other resources**
4. **Update documentation and scripts**
5. **Establish naming validation in CI/CD**

---

*This document should be updated as new resource types are added to the infrastructure.*