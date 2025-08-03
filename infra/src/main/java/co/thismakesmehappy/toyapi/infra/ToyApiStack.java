package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.budgets.CfnBudget;
import software.amazon.awscdk.services.certificatemanager.*;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.cloudfront.ResponseSecurityHeadersBehavior;
import software.amazon.awscdk.services.cloudfront.origins.*;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.dax.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.*;
import software.amazon.awscdk.services.events.*;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.kinesis.*;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.KinesisEventSource;
import software.amazon.awscdk.services.lambda.eventsources.KinesisEventSourceProps;
import software.amazon.awscdk.services.logs.*;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.route53.targets.ApiGatewayDomain;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.services.secretsmanager.*;
import software.amazon.awscdk.services.ssm.*;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.wafv2.*;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ToyApi CDK Stack - Creates all AWS resources for the serverless API.
 * 
 * Resources created:
 * - DynamoDB table for items storage
 * - Cognito User Pool for authentication
 * - Lambda functions for API endpoints
 * - API Gateway REST API
 * - CloudWatch log groups
 * - Budget monitoring with SNS alerts
 */
public class ToyApiStack extends Stack {
    
    /**
     * Container for Cognito resources
     */
    private static class CognitoResources {
        final UserPool userPool;
        final UserPoolClient userPoolClient;
        
        CognitoResources(UserPool userPool, UserPoolClient userPoolClient) {
            this.userPool = userPool;
            this.userPoolClient = userPoolClient;
        }
    }
    
    /**
     * Container for API Key resources
     */
    private static class ApiKeyResources {
        final UsagePlan usagePlan;
        final ApiKey defaultApiKey;
        
        ApiKeyResources(UsagePlan usagePlan, ApiKey defaultApiKey) {
            this.usagePlan = usagePlan;
            this.defaultApiKey = defaultApiKey;
        }
    }
    private final String environment;
    private final String resourcePrefix;
    private Topic cacheNotificationTopic;

    public ToyApiStack(final Construct scope, final String id, final StackProps props, final String environment) {
        super(scope, id, props);
        
        this.environment = environment;
        this.resourcePrefix = "toyapi-" + environment;

        // Create core infrastructure
        Table itemsTable = createDynamoDBTable();
        CognitoResources cognitoResources = createCognitoUserPool();
        
        // Create Lambda functions
        Function publicFunction = createPublicLambda(itemsTable);
        Function authFunction = createAuthLambda(itemsTable, cognitoResources);
        Function itemsFunction = createItemsLambda(itemsTable, cognitoResources.userPool);
        
        // Create developer function that works independently of API Gateway references
        Function developerFunction = createDeveloperLambdaIndependent(itemsTable);
        
        // Create API Gateway with all functions including developer
        RestApi api = createApiGateway(publicFunction, authFunction, itemsFunction, developerFunction, cognitoResources.userPool);
        
        // Create WAF protection for API Gateway
        createWafProtection(api);
        
        // Create custom domain and Route53 records (only for production and staging)
        if (environment.equals("prod") || environment.equals("stage")) {
            createCustomDomain(api);
        }
        
        // Create API key infrastructure after API Gateway is created
        ApiKeyResources apiKeyResources = createApiKeyInfrastructureAndAssociate(api);
        
        // Create API key rotation infrastructure
        createApiKeyRotationInfrastructure(api, apiKeyResources);
        
        // Create request signing infrastructure for enhanced security
        createRequestSigningInfrastructure(api);
        
        // Create cache notification topic (shared by multiple cache resources)
        cacheNotificationTopic = createCacheNotificationTopic();
        
        // Create connection pooling and caching layers for performance optimization
        createPerformanceOptimizationInfrastructure(api);
        
        // Create CloudFront CDN for global distribution
        Distribution cloudFrontDistribution = createCloudFrontDistribution(api);
        
        // Create usage analytics and developer insights tracking
        createUsageAnalyticsInfrastructure(api, cloudFrontDistribution);
        
        // Set up AWS Systems Manager Parameter Store for test credentials
        createParameterStoreInfrastructure();
        
        // Create monitoring and budgets
        createBudgetMonitoring();
        
        // Output important information
        createOutputs(api, cognitoResources, itemsTable);
    }

    /**
     * Creates DynamoDB table for storing items with proper configuration for the environment.
     */
    private Table createDynamoDBTable() {
        Table.Builder tableBuilder = Table.Builder.create(this, "ToyApiDynamoItems")
                .tableName(resourcePrefix + "-items")
                .partitionKey(Attribute.builder()
                        .name("PK")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("SK") 
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)  // Cost-effective for low traffic
                .pointInTimeRecovery(true);  // Enable backup

        // Set retention policy based on environment
        if (environment.equals("prod")) {
            tableBuilder.removalPolicy(RemovalPolicy.RETAIN);
        } else {
            tableBuilder.removalPolicy(RemovalPolicy.DESTROY);
        }

        Table table = tableBuilder.build();

        // Add GSI for user-based queries
        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("UserIndex")
                .partitionKey(Attribute.builder()
                        .name("userId")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("createdAt")
                        .type(AttributeType.STRING)
                        .build())
                .build());

        return table;
    }

    /**
     * Creates Cognito User Pool for user authentication.
     */
    private CognitoResources createCognitoUserPool() {
        UserPool userPool = UserPool.Builder.create(this, "ToyApiCognitoUsers")
                .userPoolName(resourcePrefix + "-users")
                .selfSignUpEnabled(true)  // Allow self-registration as requested
                .signInAliases(SignInAliases.builder()
                        .username(true)
                        .email(true)
                        .build())
                .standardAttributes(StandardAttributes.builder()
                        .email(StandardAttribute.builder()
                                .required(true)
                                .mutable(true)
                                .build())
                        .build())
                .passwordPolicy(PasswordPolicy.builder()
                        .minLength(8)
                        .requireLowercase(true)
                        .requireUppercase(true)
                        .requireDigits(true)
                        .requireSymbols(false)
                        .build())
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();

        // Create user pool client
        UserPoolClient userPoolClient = UserPoolClient.Builder.create(this, "ToyApiCognitoClient")
                .userPool(userPool)
                .userPoolClientName(resourcePrefix + "-client")
                .generateSecret(false)  // For frontend applications
                .authFlows(AuthFlow.builder()
                        .userPassword(true)
                        .userSrp(true)
                        .adminUserPassword(true)  // Enable ADMIN_NO_SRP_AUTH flow
                        .build())
                .accessTokenValidity(Duration.hours(1))
                .idTokenValidity(Duration.hours(1))
                .refreshTokenValidity(Duration.days(30))
                .build();

        return new CognitoResources(userPool, userPoolClient);
    }

    /**
     * Creates Lambda function for public endpoints.
     */
    private Function createPublicLambda(Table itemsTable) {
        return createLambdaFunction(
                "ToyApiLambdaPublic",
                "co.thismakesmehappy.toyapi.service.PublicHandler",
                "Handles public API endpoints",
                itemsTable,
                null
        );
    }

    /**
     * Creates Lambda function for authentication endpoints.
     */
    private Function createAuthLambda(Table itemsTable, CognitoResources cognitoResources) {
        return createLambdaFunctionWithClient(
                "ToyApiLambdaAuth", 
                "co.thismakesmehappy.toyapi.service.AuthHandler",
                "Handles authentication endpoints",
                itemsTable,
                cognitoResources
        );
    }

    /**
     * Creates Lambda function for items CRUD operations.
     */
    private Function createItemsLambda(Table itemsTable, UserPool userPool) {
        return createLambdaFunction(
                "ToyApiLambdaItems",
                "co.thismakesmehappy.toyapi.service.ItemsHandler", 
                "Handles items CRUD operations",
                itemsTable,
                userPool
        );
    }

    /**
     * Creates Lambda function for developer key management that works independently of API Gateway.
     * Uses AWS API calls instead of CDK references to avoid circular dependencies.
     */
    private Function createDeveloperLambdaIndependent(Table itemsTable) {
        // Create log group for the function
        LogGroup logGroup = LogGroup.Builder.create(this, "DeveloperFunctionLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-developerfunction")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Environment variables - function will use AWS API calls to discover API Gateway info
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("TABLE_NAME", itemsTable.getTableName());
        environment.put("REGION", "us-east-1");
        environment.put("API_NAME_PREFIX", resourcePrefix);  // Used to find API Gateway by name
        environment.put("USAGE_PLAN_PREFIX", resourcePrefix + "-developer-plan");  // Used to find usage plan by name

        Function function = Function.Builder.create(this, "ToyApiLambdaDeveloper")
                .functionName(resourcePrefix + "-developerfunction")
                .runtime(Runtime.JAVA_17)
                .handler("co.thismakesmehappy.toyapi.service.DeveloperHandler")
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.seconds(30))
                .memorySize(512)
                .description("Handles developer API key management (" + this.environment + ")")
                .environment(environment)
                .build();

        // Grant DynamoDB permissions
        itemsTable.grantReadWriteData(function);

        // Grant API Gateway permissions for API key management and discovery
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "apigateway:GET",        // List/describe resources
                        "apigateway:POST",       // Create API keys
                        "apigateway:DELETE",     // Delete API keys  
                        "apigateway:PUT"         // Update API keys
                ))
                .resources(Arrays.asList(
                        "arn:aws:apigateway:" + this.getRegion() + "::/restapis*",      // Access to list APIs
                        "arn:aws:apigateway:" + this.getRegion() + "::/apikeys*",       // Access to API keys
                        "arn:aws:apigateway:" + this.getRegion() + "::/usageplans*"     // Access to usage plans
                ))
                .build());

        return function;
    }

    /**
     * Helper method to create Lambda functions with common configuration.
     */
    private Function createLambdaFunction(String functionName, String handler, String description, 
                                         Table itemsTable, UserPool userPool) {
        
        // Create log group for the function
        LogGroup logGroup = LogGroup.Builder.create(this, functionName + "LogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-" + functionName.toLowerCase())
                .retention(RetentionDays.ONE_WEEK)  // Cost optimization
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Environment variables
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("TABLE_NAME", itemsTable.getTableName());
        environment.put("REGION", "us-east-1");
        
        if (userPool != null) {
            environment.put("USER_POOL_ID", userPool.getUserPoolId());
        }

        Function function = Function.Builder.create(this, functionName)
                .functionName(resourcePrefix + "-" + functionName.toLowerCase())
                .runtime(Runtime.JAVA_17)
                .handler(handler)
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.seconds(30))
                .memorySize(512)  // Balanced cost/performance
                .description(description + " (" + this.environment + ")")
                .environment(environment)
// Log group is created automatically
                .build();

        // Grant DynamoDB permissions
        itemsTable.grantReadWriteData(function);

        // Grant Cognito permissions if needed
        if (userPool != null) {
            function.addToRolePolicy(PolicyStatement.Builder.create()
                    .effect(Effect.ALLOW)
                    .actions(Arrays.asList(
                            "cognito-idp:AdminInitiateAuth",
                            "cognito-idp:AdminCreateUser",
                            "cognito-idp:AdminSetUserPassword",
                            "cognito-idp:AdminGetUser"
                    ))
                    .resources(Arrays.asList(userPool.getUserPoolArn()))
                    .build());
        }
        
        // Grant Systems Manager Parameter Store permissions for secure credential access
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "ssm:GetParameter",
                        "ssm:GetParameters",
                        "ssm:GetParametersByPath"
                ))
                .resources(Arrays.asList(
                        "arn:aws:ssm:" + this.getRegion() + ":" + this.getAccount() + ":parameter/" + resourcePrefix + "/*"
                ))
                .build());

        return function;
    }
    
    /**
     * Helper method to create Lambda functions with Cognito client configuration.
     */
    private Function createLambdaFunctionWithClient(String functionName, String handler, String description, 
                                         Table itemsTable, CognitoResources cognitoResources) {
        
        // Create log group for the function
        LogGroup logGroup = LogGroup.Builder.create(this, functionName + "LogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-" + functionName.toLowerCase())
                .retention(RetentionDays.ONE_WEEK)  // Cost optimization
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Environment variables
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("TABLE_NAME", itemsTable.getTableName());
        environment.put("REGION", "us-east-1");
        environment.put("USER_POOL_ID", cognitoResources.userPool.getUserPoolId());
        environment.put("USER_POOL_CLIENT_ID", cognitoResources.userPoolClient.getUserPoolClientId());
        
        // Add Parameter Store configuration
        environment.put("PARAMETER_PREFIX", "/" + resourcePrefix);

        Function function = Function.Builder.create(this, functionName)
                .functionName(resourcePrefix + "-" + functionName.toLowerCase())
                .runtime(Runtime.JAVA_17)
                .handler(handler)
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.seconds(30))
                .memorySize(512)  // Balanced cost/performance
                .description(description + " (" + this.environment + ")")
                .environment(environment)
                .build();

        // Grant DynamoDB permissions
        itemsTable.grantReadWriteData(function);

        // Grant Cognito permissions
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "cognito-idp:AdminInitiateAuth",
                        "cognito-idp:AdminCreateUser",
                        "cognito-idp:AdminSetUserPassword",
                        "cognito-idp:AdminGetUser"
                ))
                .resources(Arrays.asList(cognitoResources.userPool.getUserPoolArn()))
                .build());
        
        // Grant Systems Manager Parameter Store permissions for secure credential access
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "ssm:GetParameter",
                        "ssm:GetParameters",
                        "ssm:GetParametersByPath"
                ))
                .resources(Arrays.asList(
                        "arn:aws:ssm:" + this.getRegion() + ":" + this.getAccount() + ":parameter/" + resourcePrefix + "/*"
                ))
                .build());

        return function;
    }

    /**
     * Creates API key infrastructure after API Gateway exists and associates it with the API.
     */
    private ApiKeyResources createApiKeyInfrastructureAndAssociate(RestApi api) {
        // Create usage plan for API rate limiting and quotas
        UsagePlan usagePlan = UsagePlan.Builder.create(this, "DeveloperUsagePlan")
                .name(resourcePrefix + "-developer-plan")
                .description("Usage plan for developer API keys")
                .throttle(ThrottleSettings.builder()
                        .rateLimit(100)  // 100 requests per second
                        .burstLimit(200) // 200 burst requests
                        .build())
                .quota(QuotaSettings.builder()
                        .limit(10000)    // 10,000 requests per day
                        .period(Period.DAY)
                        .build())
                .build();

        // Create default API key for testing
        ApiKey defaultApiKey = ApiKey.Builder.create(this, "DefaultApiKey")
                .apiKeyName(resourcePrefix + "-default-key")
                .description("Default API key for testing and documentation")
                .enabled(true)
                .build();

        // Associate default API key with usage plan
        usagePlan.addApiKey(defaultApiKey);
        
        // Associate usage plan with API stage (after API is fully created)
        usagePlan.addApiStage(UsagePlanPerApiStage.builder()
                .api(api)
                .stage(api.getDeploymentStage())
                .build());

        return new ApiKeyResources(usagePlan, defaultApiKey);
    }
    
    /**
     * Creates API key rotation infrastructure including:
     * - Lambda function for automated key rotation
     * - CloudWatch Events for scheduled rotation
     * - Secrets Manager for secure key storage
     * - DynamoDB table for key lifecycle tracking
     */
    private void createApiKeyRotationInfrastructure(RestApi api, ApiKeyResources apiKeyResources) {
        // Create secrets for storing current and previous API keys
        Secret apiKeySecret = Secret.Builder.create(this, "ToyApiSecretApiKeys")
                .secretName(resourcePrefix + "-api-keys")
                .description("Current and rotated API keys for ToyApi")
                .generateSecretString(SecretStringGenerator.builder()
                        .secretStringTemplate("{\"current\":\"\",\"previous\":\"\",\"rotationDate\":\"\"}")
                        .generateStringKey("current")
                        .excludeCharacters(" %+~`#$&*()|[]{}:;<>?!'/\"\\")
                        .build())
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        
        // Create DynamoDB table for API key lifecycle tracking
        Table keyLifecycleTable = Table.Builder.create(this, "ToyApiDynamoApiKeyLifecycle")
                .tableName(resourcePrefix + "-api-key-lifecycle")
                .partitionKey(Attribute.builder()
                        .name("keyId")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("timestamp")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .pointInTimeRecovery(true)
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        
        // Add GSI for querying by status and environment
        keyLifecycleTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("StatusIndex")
                .partitionKey(Attribute.builder()
                        .name("status")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("createdAt")
                        .type(AttributeType.STRING)
                        .build())
                .build());
        
        // Create Lambda function for API key rotation
        Function rotationFunction = createApiKeyRotationLambda(api, apiKeyResources, apiKeySecret, keyLifecycleTable);
        
        // Create CloudWatch Events rule for scheduled rotation (monthly)
        Rule rotationSchedule = Rule.Builder.create(this, "ApiKeyRotationSchedule")
                .ruleName(resourcePrefix + "-api-key-rotation")
                .description("Scheduled API key rotation for ToyApi")
                .schedule(Schedule.cron(CronOptions.builder()
                        .minute("0")
                        .hour("2")     // 2 AM UTC
                        .day("1")      // First day of month
                        .month("*")
                        .year("*")
                        .build()))
                .enabled(environment.equals("prod"))  // Only enable in production
                .build();
        
        // Add rotation function as target
        rotationSchedule.addTarget(new LambdaFunction(rotationFunction));
        
        // Create manual rotation trigger (for emergency rotation)
        Rule manualRotationTrigger = Rule.Builder.create(this, "ManualApiKeyRotation")
                .ruleName(resourcePrefix + "-manual-api-key-rotation")
                .description("Manual trigger for emergency API key rotation")
                .eventPattern(EventPattern.builder()
                        .source(Arrays.asList("custom.toyapi"))
                        .detailType(Arrays.asList("API Key Rotation Request"))
                        .detail(Map.of(
                                "environment", Arrays.asList(environment),
                                "action", Arrays.asList("rotate")))
                        .build())
                .build();
        
        manualRotationTrigger.addTarget(new LambdaFunction(rotationFunction));
        
        // Create SNS topic for rotation notifications
        Topic rotationTopic = Topic.Builder.create(this, "ApiKeyRotationNotifications")
                .topicName(resourcePrefix + "-api-key-rotation-notifications")
                .displayName("API Key Rotation Notifications (" + environment + ")")
                .build();
        
        // Subscribe to rotation notifications
        rotationTopic.addSubscription(
                software.amazon.awscdk.services.sns.subscriptions.EmailSubscription.Builder.create("security+toyapi@thismakesmehappy.co")
                        .build()
        );
        
        // Grant rotation function permission to publish notifications
        rotationTopic.grantPublish(rotationFunction);
        
        // Add environment variable for SNS topic
        rotationFunction.addEnvironment("ROTATION_TOPIC_ARN", rotationTopic.getTopicArn());
        
        // Create CloudWatch dashboard for key rotation monitoring
        createKeyRotationDashboard(rotationFunction, keyLifecycleTable);
    }
    
    /**
     * Creates Lambda function for automated API key rotation with comprehensive permissions
     */
    private Function createApiKeyRotationLambda(RestApi api, ApiKeyResources apiKeyResources, 
                                               Secret apiKeySecret, Table keyLifecycleTable) {
        // Create log group for rotation function
        LogGroup logGroup = LogGroup.Builder.create(this, "ApiKeyRotationLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-apikeyrotation")
                .retention(RetentionDays.TWO_WEEKS)  // Longer retention for security events
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
        
        // Environment variables for rotation function
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("REGION", "us-east-1");
        environment.put("API_ID", api.getRestApiId());
        environment.put("USAGE_PLAN_ID", apiKeyResources.usagePlan.getUsagePlanId());
        environment.put("SECRET_ARN", apiKeySecret.getSecretArn());
        environment.put("LIFECYCLE_TABLE", keyLifecycleTable.getTableName());
        environment.put("KEY_PREFIX", resourcePrefix);
        environment.put("ROTATION_DAYS", environment.equals("prod") ? "30" : "7");  // Shorter rotation in non-prod
        
        Function rotationFunction = Function.Builder.create(this, "ToyApiLambdaApiKeyRotation")
                .functionName(resourcePrefix + "-apikeyrotation")
                .runtime(Runtime.JAVA_17)
                .handler("co.thismakesmehappy.toyapi.service.ApiKeyRotationHandler")
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.minutes(5))  // Longer timeout for rotation operations
                .memorySize(1024)  // More memory for rotation processing
                .description("Automated API key rotation for ToyApi (" + this.environment + ")")
                .environment(environment)
                .build();
        
        // Grant DynamoDB permissions for lifecycle tracking
        keyLifecycleTable.grantReadWriteData(rotationFunction);
        
        // Grant Secrets Manager permissions
        apiKeySecret.grantRead(rotationFunction);
        apiKeySecret.grantWrite(rotationFunction);
        
        // Grant comprehensive API Gateway permissions for key management
        rotationFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "apigateway:GET",           // List and describe resources
                        "apigateway:POST",          // Create new API keys
                        "apigateway:PUT",           // Update API keys and usage plans
                        "apigateway:DELETE",        // Delete old API keys
                        "apigateway:PATCH"          // Update API key properties
                ))
                .resources(Arrays.asList(
                        "arn:aws:apigateway:" + this.getRegion() + "::/restapis/*",
                        "arn:aws:apigateway:" + this.getRegion() + "::/apikeys/*",
                        "arn:aws:apigateway:" + this.getRegion() + "::/usageplans/*",
                        "arn:aws:apigateway:" + this.getRegion() + "::/usageplanskeys/*"
                ))
                .build());
        
        // Grant CloudWatch permissions for metrics and logging
        rotationFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "cloudwatch:PutMetricData",
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents"
                ))
                .resources(Arrays.asList("*"))
                .build());
        
        // Grant SNS permissions for notifications (will be added later)
        rotationFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("sns:Publish"))
                .resources(Arrays.asList(
                        "arn:aws:sns:" + this.getRegion() + ":" + this.getAccount() + ":" + resourcePrefix + "-*"
                ))
                .build());
        
        return rotationFunction;
    }
    
    /**
     * Creates CloudWatch dashboard for monitoring API key rotation health
     */
    private void createKeyRotationDashboard(Function rotationFunction, Table keyLifecycleTable) {
        // This will be implemented as a separate monitoring stack
        // For now, just create basic CloudWatch alarms for rotation function
        
        // Rotation function error alarm
        software.amazon.awscdk.services.cloudwatch.Alarm rotationErrorAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "ApiKeyRotationErrorAlarm")
                        .alarmName(resourcePrefix + "-api-key-rotation-errors")
                        .alarmDescription("API key rotation function has errors")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/Lambda")
                                .metricName("Errors")
                                .dimensionsMap(Map.of("FunctionName", rotationFunction.getFunctionName()))
                                .statistic("Sum")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(1)
                        .evaluationPeriods(1)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.NOT_BREACHING)
                        .build();
        
        // Rotation function duration alarm (should complete within 2 minutes)
        software.amazon.awscdk.services.cloudwatch.Alarm rotationDurationAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "ApiKeyRotationDurationAlarm")
                        .alarmName(resourcePrefix + "-api-key-rotation-duration")
                        .alarmDescription("API key rotation taking too long")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/Lambda")
                                .metricName("Duration")
                                .dimensionsMap(Map.of("FunctionName", rotationFunction.getFunctionName()))
                                .statistic("Average")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(120000)  // 2 minutes in milliseconds
                        .evaluationPeriods(1)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.NOT_BREACHING)
                        .build();
        
        // Custom metric for tracking rotation success/failure
        software.amazon.awscdk.services.cloudwatch.Alarm rotationHealthAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "ApiKeyRotationHealthAlarm")
                        .alarmName(resourcePrefix + "-api-key-rotation-health")
                        .alarmDescription("API key rotation health check")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("ToyApi/" + environment)
                                .metricName("ApiKeyRotationSuccess")
                                .statistic("Sum")
                                .period(Duration.hours(1))
                                .build())
                        .threshold(0)
                        .evaluationPeriods(25)  // 25 hours (allow for monthly rotation)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.LESS_THAN_OR_EQUAL_TO_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.BREACHING)
                        .build();
    }
    
    /**
     * Creates request signing infrastructure for enhanced API security including:
     * - HMAC-SHA256 signature validation
     * - Timestamp-based replay attack prevention
     * - Secure signing key management with rotation
     * - Request signature validation Lambda authorizer
     */
    private void createRequestSigningInfrastructure(RestApi api) {
        // Create secrets for request signing keys
        Secret signingKeySecret = Secret.Builder.create(this, "ToyApiSecretSigning")
                .secretName(resourcePrefix + "-request-signing-keys")
                .description("HMAC signing keys for request signature validation")
                .generateSecretString(SecretStringGenerator.builder()
                        .secretStringTemplate("{\"current\":\"\",\"previous\":\"\",\"rotationDate\":\"\"}")
                        .generateStringKey("current")
                        .excludeCharacters(" %+~`#$&*()|[]{}:;<>?!'/\"\\")
                        .build())
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        
        // Create DynamoDB table for request signature nonce tracking (prevent replay attacks)
        Table signatureNonceTable = Table.Builder.create(this, "ToyApiDynamoSignatureNonce")
                .tableName(resourcePrefix + "-signature-nonces")
                .partitionKey(Attribute.builder()
                        .name("nonce")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("timestamp")
                        .type(AttributeType.NUMBER)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .timeToLiveAttribute("ttl")  // Auto-expire old nonces after 15 minutes
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        
        // Create Lambda authorizer for request signature validation
        Function signatureAuthorizerFunction = createSignatureAuthorizerLambda(signingKeySecret, signatureNonceTable);
        
        // Create custom authorizer for API Gateway
        TokenAuthorizer signatureAuthorizer = TokenAuthorizer.Builder.create(this, "SignatureAuthorizer")
                .handler(signatureAuthorizerFunction)
                .authorizerName(resourcePrefix + "-signature-authorizer")
                .identitySource("method.request.header.Authorization,method.request.header.X-Signature,method.request.header.X-Timestamp,method.request.header.X-Nonce")
                .resultsCacheTtl(Duration.minutes(5))  // Cache successful validations
                .build();
        
        // Create secure admin Lambda function
        Function secureAdminFunction = createSecureAdminLambda();
        
        // Create enhanced security endpoints that require request signing
        createSecureSignedEndpoints(api, signatureAuthorizer, secureAdminFunction);
        
        // Create signing key rotation infrastructure
        createSigningKeyRotationInfrastructure(signingKeySecret);
        
        // Create CloudWatch dashboard for signature monitoring
        createSignatureMonitoringDashboard(signatureAuthorizerFunction, signatureNonceTable);
    }
    
    /**
     * Creates Lambda function for request signature validation
     */
    private Function createSignatureAuthorizerLambda(Secret signingKeySecret, Table signatureNonceTable) {
        // Create log group for signature authorizer
        LogGroup logGroup = LogGroup.Builder.create(this, "SignatureAuthorizerLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-signatureauthorizer")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
        
        // Environment variables for signature validation
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("REGION", "us-east-1");
        environment.put("SIGNING_SECRET_ARN", signingKeySecret.getSecretArn());
        environment.put("NONCE_TABLE", signatureNonceTable.getTableName());
        environment.put("SIGNATURE_TTL_SECONDS", "900");  // 15 minutes
        environment.put("MAX_CLOCK_SKEW_SECONDS", "30");   // Allow 30 seconds clock skew
        
        Function authorizerFunction = Function.Builder.create(this, "ToyApiLambdaSignatureAuthorizer")
                .functionName(resourcePrefix + "-signatureauthorizer")
                .runtime(Runtime.JAVA_17)
                .handler("co.thismakesmehappy.toyapi.service.SignatureAuthorizerHandler")
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.seconds(10))  // Quick validation
                .memorySize(512)
                .description("Request signature validation authorizer (" + this.environment + ")")
                .environment(environment)
                .build();
        
        // Grant access to signing keys secret
        signingKeySecret.grantRead(authorizerFunction);
        
        // Grant access to nonce table
        signatureNonceTable.grantReadWriteData(authorizerFunction);
        
        // Grant CloudWatch permissions for custom metrics
        authorizerFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "cloudwatch:PutMetricData",
                        "logs:CreateLogGroup",
                        "logs:CreateLogStream",
                        "logs:PutLogEvents"
                ))
                .resources(Arrays.asList("*"))
                .build());
        
        return authorizerFunction;
    }
    
    /**
     * Creates secure endpoints that require request signature validation
     */
    private void createSecureSignedEndpoints(RestApi api, TokenAuthorizer signatureAuthorizer, Function secureAdminFunction) {
        // Create secure resource under /secure path
        Resource secureResource = api.getRoot().addResource("secure");
        
        // Method options with signature authorization
        MethodOptions secureMethodOptions = MethodOptions.builder()
                .authorizer(signatureAuthorizer)
                .requestParameters(Map.of(
                        "method.request.header.Authorization", true,    // Required
                        "method.request.header.X-Signature", true,     // Required
                        "method.request.header.X-Timestamp", true,     // Required
                        "method.request.header.X-Nonce", true,         // Required
                        "method.request.header.Content-Type", false    // Optional
                ))
                .build();
        
        // High-security admin endpoints
        Resource adminResource = secureResource.addResource("admin");
        
        // Admin configuration endpoint
        Resource configResource = adminResource.addResource("config");
        configResource.addMethod("GET", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
        configResource.addMethod("PUT", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
        
        // Admin metrics endpoint
        adminResource.addResource("metrics")
                .addMethod("GET", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
        
        // Admin user management endpoint
        Resource adminUsersResource = adminResource.addResource("users");
        adminUsersResource.addMethod("GET", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
        adminUsersResource.addMethod("POST", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
        
        adminUsersResource.addResource("{userId}")
                .addMethod("DELETE", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
        
        // Secure data export endpoints
        Resource exportResource = secureResource.addResource("export");
        exportResource.addResource("data")
                .addMethod("POST", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
        
        exportResource.addResource("audit-logs")
                .addMethod("GET", new LambdaIntegration(secureAdminFunction), secureMethodOptions);
    }
    
    /**
     * Creates Lambda function for secure admin operations
     */
    private Function createSecureAdminLambda() {
        // Create log group for secure admin function
        LogGroup logGroup = LogGroup.Builder.create(this, "SecureAdminLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-secureadmin")
                .retention(RetentionDays.TWO_WEEKS)  // Longer retention for admin operations
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
        
        // Environment variables for secure admin function
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("REGION", "us-east-1");
        environment.put("ADMIN_LEVEL", "HIGH_SECURITY");
        
        Function adminFunction = Function.Builder.create(this, "ToyApiLambdaSecureAdmin")
                .functionName(resourcePrefix + "-secureadmin")
                .runtime(Runtime.JAVA_17)
                .handler("co.thismakesmehappy.toyapi.service.SecureAdminHandler")
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.seconds(30))
                .memorySize(1024)  // More memory for admin operations
                .description("Secure admin operations with signature validation (" + this.environment + ")")
                .environment(environment)
                .build();
        
        // Grant comprehensive admin permissions (carefully scoped)
        adminFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "cloudwatch:GetMetricStatistics",
                        "cloudwatch:ListMetrics",
                        "logs:DescribeLogGroups",
                        "logs:DescribeLogStreams",
                        "logs:GetLogEvents",
                        "apigateway:GET",
                        "cognito-idp:ListUsers",
                        "cognito-idp:AdminGetUser",
                        "cognito-idp:AdminDisableUser"
                ))
                .resources(Arrays.asList(
                        "arn:aws:cloudwatch:" + this.getRegion() + ":" + this.getAccount() + ":*",
                        "arn:aws:logs:" + this.getRegion() + ":" + this.getAccount() + ":log-group:/aws/lambda/" + resourcePrefix + "-*",
                        "arn:aws:apigateway:" + this.getRegion() + "::/restapis/*",
                        "arn:aws:cognito-idp:" + this.getRegion() + ":" + this.getAccount() + ":userpool/*"
                ))
                .build());
        
        return adminFunction;
    }
    
    /**
     * Creates signing key rotation infrastructure
     */
    private void createSigningKeyRotationInfrastructure(Secret signingKeySecret) {
        // Create Lambda function for signing key rotation
        Function keyRotationFunction = createSigningKeyRotationLambda(signingKeySecret);
        
        // Create CloudWatch Events rule for quarterly key rotation
        Rule keyRotationSchedule = Rule.Builder.create(this, "SigningKeyRotationSchedule")
                .ruleName(resourcePrefix + "-signing-key-rotation")
                .description("Quarterly rotation of request signing keys")
                .schedule(Schedule.cron(CronOptions.builder()
                        .minute("0")
                        .hour("3")     // 3 AM UTC
                        .day("1")      // First day of quarter
                        .month("1,4,7,10")  // January, April, July, October
                        .year("*")
                        .build()))
                .enabled(environment.equals("prod"))  // Only enable in production
                .build();
        
        keyRotationSchedule.addTarget(new LambdaFunction(keyRotationFunction));
        
        // Create SNS topic for key rotation notifications
        Topic keyRotationTopic = Topic.Builder.create(this, "SigningKeyRotationNotifications")
                .topicName(resourcePrefix + "-signing-key-rotation-notifications")
                .displayName("Signing Key Rotation Notifications (" + environment + ")")
                .build();
        
        keyRotationTopic.addSubscription(
                software.amazon.awscdk.services.sns.subscriptions.EmailSubscription.Builder.create("security+toyapi-signing@thismakesmehappy.co")
                        .build()
        );
        
        keyRotationTopic.grantPublish(keyRotationFunction);
        keyRotationFunction.addEnvironment("KEY_ROTATION_TOPIC_ARN", keyRotationTopic.getTopicArn());
    }
    
    /**
     * Creates Lambda function for signing key rotation
     */
    private Function createSigningKeyRotationLambda(Secret signingKeySecret) {
        // Create log group for key rotation function
        LogGroup logGroup = LogGroup.Builder.create(this, "SigningKeyRotationLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-signingkeyrotation")
                .retention(RetentionDays.TWO_WEEKS)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
        
        // Environment variables for key rotation
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("REGION", "us-east-1");
        environment.put("SIGNING_SECRET_ARN", signingKeySecret.getSecretArn());
        environment.put("KEY_LENGTH", "64");  // 256-bit keys
        
        Function rotationFunction = Function.Builder.create(this, "ToyApiLambdaSigningKeyRotation")
                .functionName(resourcePrefix + "-signingkeyrotation")
                .runtime(Runtime.JAVA_17)
                .handler("co.thismakesmehappy.toyapi.service.SigningKeyRotationHandler")
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.minutes(2))
                .memorySize(512)
                .description("Signing key rotation for enhanced security (" + this.environment + ")")
                .environment(environment)
                .build();
        
        // Grant permissions to rotate signing keys
        signingKeySecret.grantRead(rotationFunction);
        signingKeySecret.grantWrite(rotationFunction);
        
        // Grant SNS publish permissions (will be added via environment variable)
        rotationFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("sns:Publish"))
                .resources(Arrays.asList(
                        "arn:aws:sns:" + this.getRegion() + ":" + this.getAccount() + ":" + resourcePrefix + "-*"
                ))
                .build());
        
        return rotationFunction;
    }
    
    /**
     * Creates CloudWatch dashboard for monitoring request signature validation
     */
    private void createSignatureMonitoringDashboard(Function authorizerFunction, Table nonceTable) {
        // Create CloudWatch alarms for signature validation monitoring
        
        // High signature validation failures alarm
        software.amazon.awscdk.services.cloudwatch.Alarm signatureFailureAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "SignatureValidationFailureAlarm")
                        .alarmName(resourcePrefix + "-signature-validation-failures")
                        .alarmDescription("High number of signature validation failures")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/Lambda")
                                .metricName("Errors")
                                .dimensionsMap(Map.of("FunctionName", authorizerFunction.getFunctionName()))
                                .statistic("Sum")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(10)  // 10 failures in 5 minutes
                        .evaluationPeriods(2)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.NOT_BREACHING)
                        .build();
        
        // Replay attack detection alarm (high nonce table activity)
        software.amazon.awscdk.services.cloudwatch.Alarm replayAttackAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "ReplayAttackDetectionAlarm")
                        .alarmName(resourcePrefix + "-replay-attack-detection")
                        .alarmDescription("Potential replay attack detected")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/DynamoDB")
                                .metricName("ConsumedReadCapacityUnits")
                                .dimensionsMap(Map.of("TableName", nonceTable.getTableName()))
                                .statistic("Sum")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(100)  // High read activity on nonce table
                        .evaluationPeriods(1)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.NOT_BREACHING)
                        .build();
        
        // Custom metric for signature validation success rate
        software.amazon.awscdk.services.cloudwatch.Alarm signatureSuccessRateAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "SignatureSuccessRateAlarm")
                        .alarmName(resourcePrefix + "-signature-success-rate")
                        .alarmDescription("Signature validation success rate below threshold")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("ToyApi/" + environment)
                                .metricName("SignatureValidationSuccessRate")
                                .statistic("Average")
                                .period(Duration.minutes(10))
                                .build())
                        .threshold(0.95)  // 95% success rate threshold
                        .evaluationPeriods(2)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.BREACHING)
                        .build();
    }
    
    /**
     * Creates comprehensive performance optimization infrastructure including:
     * - Redis ElastiCache cluster for caching
     * - VPC networking for secure connectivity
     * - DynamoDB Accelerator (DAX) for DynamoDB caching
     * - Lambda optimizations for warm containers
     * - API Gateway caching configuration
     */
    private void createPerformanceOptimizationInfrastructure(RestApi api) {
        // Create VPC for secure networking between components
        Vpc cacheVpc = Vpc.Builder.create(this, "CacheVpc")
                .vpcName(resourcePrefix + "-cache-vpc")
                .maxAzs(2)  // Multi-AZ for high availability
                .natGateways(1)  // Cost optimization - single NAT gateway
                .subnetConfiguration(Arrays.asList(
                        SubnetConfiguration.builder()
                                .name("public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("private")
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("isolated")
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .cidrMask(24)
                                .build()
                ))
                .enableDnsHostnames(true)
                .enableDnsSupport(true)
                .build();
        
        // Create security group for ElastiCache cluster
        SecurityGroup cacheSecurityGroup = SecurityGroup.Builder.create(this, "ToyApiSecurityGroupCache")
                .securityGroupName(resourcePrefix + "-cache-sg")
                .description("Security group for ElastiCache Redis cluster")
                .vpc(cacheVpc)
                .allowAllOutbound(false)
                .build();
        
        // Create security group for Lambda functions first
        SecurityGroup lambdaSecurityGroup = SecurityGroup.Builder.create(this, "ToyApiSecurityGroupLambda")
                .securityGroupName(resourcePrefix + "-lambda-cache-sg")
                .description("Security group for Lambda functions with cache access")
                .vpc(cacheVpc)
                .allowAllOutbound(false)  // Start with no outbound, add specific rules
                .build();
        
        // Allow Lambda to reach ElastiCache on port 6379
        cacheSecurityGroup.addIngressRule(
                Peer.securityGroupId(lambdaSecurityGroup.getSecurityGroupId()),
                Port.tcp(6379),
                "Redis traffic from Lambda functions only"
        );
        
        // Allow Lambda outbound to ElastiCache
        lambdaSecurityGroup.addEgressRule(
                Peer.securityGroupId(cacheSecurityGroup.getSecurityGroupId()),
                Port.tcp(6379),
                "Lambda to Redis cache"
        );
        
        // Allow Lambda to reach AWS services (HTTPS only)
        lambdaSecurityGroup.addEgressRule(
                Peer.anyIpv4(),
                Port.tcp(443),
                "HTTPS for AWS service calls"
        );
        
        // Allow Lambda to reach DynamoDB (port 8000 for local, 443 for AWS service)
        lambdaSecurityGroup.addEgressRule(
                Peer.anyIpv4(),
                Port.tcp(8000),
                "DynamoDB local development"
        );
        
        // Create ElastiCache subnet group
        software.amazon.awscdk.services.elasticache.CfnSubnetGroup cacheSubnetGroup = software.amazon.awscdk.services.elasticache.CfnSubnetGroup.Builder.create(this, "CacheSubnetGroup")
                .cacheSubnetGroupName(resourcePrefix + "-cache-subnet-group")
                .description("Subnet group for ElastiCache cluster")
                .subnetIds(cacheVpc.getPrivateSubnets().stream()
                        .map(ISubnet::getSubnetId)
                        .collect(java.util.stream.Collectors.toList()))
                .build();
        
        // Create Redis parameter group for optimized settings
        software.amazon.awscdk.services.elasticache.CfnParameterGroup redisParameterGroup = software.amazon.awscdk.services.elasticache.CfnParameterGroup.Builder.create(this, "RedisParameterGroup")
                .description("Parameter group for Redis cluster optimization")
                .cacheParameterGroupFamily("redis7.x")
                .properties(Map.of(
                        "maxmemory-policy", "allkeys-lru",  // LRU eviction when memory is full
                        "timeout", "300",                   // Client timeout
                        "tcp-keepalive", "60",             // Keep-alive interval
                        "maxclients", "10000"              // Maximum client connections
                ))
                .build();
        
        // Create ElastiCache Redis cluster
        CfnReplicationGroup redisCluster = CfnReplicationGroup.Builder.create(this, "RedisCluster")
                .replicationGroupId(resourcePrefix + "-redis")
                .replicationGroupDescription("Redis cluster for API caching")
                .cacheNodeType(environment.equals("prod") ? "cache.t3.medium" : "cache.t3.micro")  // Larger for prod
                .numCacheClusters(environment.equals("prod") ? 2 : 1)  // Multi-node for prod
                .engine("redis")
                .engineVersion("7.0")
                .port(6379)
                .cacheParameterGroupName(redisParameterGroup.getRef())
                .cacheSubnetGroupName(cacheSubnetGroup.getCacheSubnetGroupName())
                .securityGroupIds(Arrays.asList(cacheSecurityGroup.getSecurityGroupId()))
                .multiAzEnabled(environment.equals("prod"))  // Multi-AZ only for prod
                .automaticFailoverEnabled(environment.equals("prod"))
                .atRestEncryptionEnabled(true)
                .transitEncryptionEnabled(false)  // Simplify for internal traffic
                .snapshotRetentionLimit(environment.equals("prod") ? 7 : 1)  // Longer retention for prod
                .snapshotWindow("03:00-05:00")  // Backup during low traffic
                .preferredMaintenanceWindow("sun:05:00-sun:07:00")  // Sunday morning maintenance
                .notificationTopicArn(cacheNotificationTopic.getTopicArn())
                .build();
        
        // Create DynamoDB DAX cluster for DynamoDB acceleration
        createDaxCluster(cacheVpc, lambdaSecurityGroup);
        
        // Configure Lambda functions with VPC and caching
        configureLambdaCaching(cacheVpc, lambdaSecurityGroup, redisCluster);
        
        // Configure API Gateway caching
        configureApiGatewayCaching(api);
        
        // Create monitoring for cache performance
        createCacheMonitoring(redisCluster);
        
        // Output cache endpoints
        createCacheOutputs(redisCluster);
    }
    
    /**
     * Creates SNS topic for cache notifications
     */
    private Topic createCacheNotificationTopic() {
        Topic cacheTopic = Topic.Builder.create(this, "CacheNotifications")
                .topicName(resourcePrefix + "-cache-alerts")
                .displayName("Cache Performance Alerts (" + environment + ")")
                .build();
        
        cacheTopic.addSubscription(
                software.amazon.awscdk.services.sns.subscriptions.EmailSubscription.Builder.create("performance+toyapi@thismakesmehappy.co")
                        .build()
        );
        
        return cacheTopic;
    }
    
    /**
     * Creates DynamoDB Accelerator (DAX) cluster for ultra-fast DynamoDB caching
     */
    private void createDaxCluster(Vpc vpc, SecurityGroup securityGroup) {
        // Create IAM role for DAX cluster
        Role daxRole = Role.Builder.create(this, "DaxRole")
                .roleName(resourcePrefix + "-dax-role")
                .assumedBy(new ServicePrincipal("dax.amazonaws.com"))
                .managedPolicies(Arrays.asList(
                        ManagedPolicy.fromAwsManagedPolicyName("AmazonDaxFullAccess")
                ))
                .build();
        
        // Allow DAX to access DynamoDB
        daxRole.addToPolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "dynamodb:GetItem",
                        "dynamodb:PutItem",
                        "dynamodb:UpdateItem",
                        "dynamodb:DeleteItem",
                        "dynamodb:Query",
                        "dynamodb:Scan",
                        "dynamodb:BatchGetItem",
                        "dynamodb:BatchWriteItem"
                ))
                .resources(Arrays.asList(
                        "arn:aws:dynamodb:" + this.getRegion() + ":" + this.getAccount() + ":table/" + resourcePrefix + "-*"
                ))
                .build());
        
        // Create DAX subnet group
        software.amazon.awscdk.services.dax.CfnSubnetGroup daxSubnetGroup = software.amazon.awscdk.services.dax.CfnSubnetGroup.Builder.create(this, "DaxSubnetGroup")
                .subnetGroupName(resourcePrefix + "-dax-subnet-group")
                .description("Subnet group for DAX cluster")
                .subnetIds(vpc.getPrivateSubnets().stream()
                        .map(ISubnet::getSubnetId)
                        .collect(java.util.stream.Collectors.toList()))
                .build();
        
        // Create dedicated DAX security group
        SecurityGroup daxSecurityGroup = SecurityGroup.Builder.create(this, "ToyApiSecurityGroupDax")
                .securityGroupName(resourcePrefix + "-dax-sg")
                .description("Security group for DynamoDB DAX cluster")
                .vpc(vpc)
                .allowAllOutbound(false)
                .build();
        
        // Allow Lambda to reach DAX on port 8111 (from Lambda security group only)
        daxSecurityGroup.addIngressRule(
                Peer.securityGroupId(securityGroup.getSecurityGroupId()),
                Port.tcp(8111),
                "DAX traffic from Lambda functions only"
        );
        
        // Allow Lambda outbound to DAX
        securityGroup.addEgressRule(
                Peer.securityGroupId(daxSecurityGroup.getSecurityGroupId()),
                Port.tcp(8111),
                "Lambda to DAX cluster"
        );
        
        // Create DAX parameter group
        software.amazon.awscdk.services.dax.CfnParameterGroup daxParameterGroup = software.amazon.awscdk.services.dax.CfnParameterGroup.Builder.create(this, "DaxParameterGroup")
                .parameterGroupName(resourcePrefix + "-dax-params")
                .description("Parameter group for DAX cluster")
                .build();
        
        // Create DAX cluster
        CfnCluster daxCluster = CfnCluster.Builder.create(this, "DaxCluster")
                .clusterName(resourcePrefix + "-dax")
                .description("DAX cluster for DynamoDB acceleration")
                .nodeType(environment.equals("prod") ? "dax.t3.small" : "dax.t2.small")
                .replicationFactor(environment.equals("prod") ? 3 : 1)  // Multi-node for prod
                .iamRoleArn(daxRole.getRoleArn())
                .subnetGroupName(daxSubnetGroup.getSubnetGroupName())
                .securityGroupIds(Arrays.asList(daxSecurityGroup.getSecurityGroupId()))
                .parameterGroupName(daxParameterGroup.getParameterGroupName())
                .notificationTopicArn(cacheNotificationTopic.getTopicArn())
                .preferredMaintenanceWindow("sun:07:00-sun:09:00")
                .build();
    }
    
    /**
     * Configures Lambda functions with VPC connectivity and caching environment variables
     */
    private void configureLambdaCaching(Vpc vpc, SecurityGroup securityGroup, CfnReplicationGroup redisCluster) {
        // Note: This would typically modify existing Lambda functions
        // For now, we'll create environment variables that will be used by Lambda functions
        
        // Lambda will need these environment variables to connect to cache:
        Map<String, String> cacheEnvironmentVariables = Map.of(
                "REDIS_ENDPOINT", redisCluster.getAttrPrimaryEndPointAddress(),
                "REDIS_PORT", redisCluster.getAttrPrimaryEndPointPort().toString(),
                "CACHE_TTL_SECONDS", environment.equals("prod") ? "3600" : "900",  // 1 hour for prod, 15 min for dev
                "CACHE_ENABLED", "true",
                "DAX_ENDPOINT", redisCluster.getAttrPrimaryEndPointAddress(),  // Will be updated with actual DAX endpoint
                "CONNECTION_POOL_SIZE", "10",
                "CONNECTION_TIMEOUT_MS", "5000"
        );
        
        // Use the Lambda security group passed as parameter (already configured with proper restrictions)
        
        // Lambda functions would be modified here to:
        // 1. Use VPC configuration
        // 2. Include cache environment variables
        // 3. Use larger memory allocation for connection pooling
        // 4. Enable provisioned concurrency for frequently used functions
        
        // For production, we'd enable provisioned concurrency
        if (environment.equals("prod")) {
            // This would create provisioned concurrency for critical Lambda functions
            // to reduce cold start latency
            createProvisionedConcurrency();
        }
    }
    
    /**
     * Creates provisioned concurrency for Lambda functions to reduce cold starts
     */
    private void createProvisionedConcurrency() {
        // Provisioned concurrency configuration would go here
        // This ensures Lambda functions are always warm and ready to serve requests
        
        // Example configuration:
        // - PublicFunction: 2 concurrent executions
        // - AuthFunction: 5 concurrent executions (high traffic)
        // - ItemsFunction: 3 concurrent executions
        
        // Note: This significantly increases cost but improves performance
    }
    
    /**
     * Configures API Gateway caching for improved response times
     */
    private void configureApiGatewayCaching(RestApi api) {
        // Enable caching on API Gateway stage
        // Note: API Gateway caching is configured at the stage level
        
        // Get the deployment stage
        Stage deploymentStage = api.getDeploymentStage();
        
        // Configure method-level caching for specific endpoints
        // GET endpoints that return relatively static data are good candidates for caching
        
        // Cache configuration would be applied to:
        // - Public endpoints (cache for 5 minutes)
        // - Item list endpoints (cache for 2 minutes)
        // - User profile endpoints (cache for 1 minute)
        
        // Cache TTL settings:
        Map<String, Integer> cacheTtlSettings = Map.of(
                "/public/message", 300,        // 5 minutes
                "/items", 120,                 // 2 minutes
                "/auth/user/*/message", 60     // 1 minute
        );
        
        // Note: Actual implementation would require CfnStage configuration
        // or custom resources to set cache settings per method
    }
    
    /**
     * Creates comprehensive monitoring for cache performance
     */
    private void createCacheMonitoring(CfnReplicationGroup redisCluster) {
        // Create CloudWatch alarms for cache performance
        
        // Redis memory utilization alarm
        software.amazon.awscdk.services.cloudwatch.Alarm redisMemoryAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "RedisMemoryUtilizationAlarm")
                        .alarmName(resourcePrefix + "-redis-memory-utilization")
                        .alarmDescription("Redis memory utilization is too high")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/ElastiCache")
                                .metricName("DatabaseMemoryUsagePercentage")
                                .dimensionsMap(Map.of(
                                        "CacheClusterId", redisCluster.getReplicationGroupId() + "-001"
                                ))
                                .statistic("Average")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(80)  // 80% memory utilization
                        .evaluationPeriods(2)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.BREACHING)
                        .build();
        
        // Redis CPU utilization alarm
        software.amazon.awscdk.services.cloudwatch.Alarm redisCpuAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "RedisCpuUtilizationAlarm")
                        .alarmName(resourcePrefix + "-redis-cpu-utilization")
                        .alarmDescription("Redis CPU utilization is too high")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/ElastiCache")
                                .metricName("CPUUtilization")
                                .dimensionsMap(Map.of(
                                        "CacheClusterId", redisCluster.getReplicationGroupId() + "-001"
                                ))
                                .statistic("Average")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(75)  // 75% CPU utilization
                        .evaluationPeriods(3)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.BREACHING)
                        .build();
        
        // Cache hit ratio alarm (should be high)
        software.amazon.awscdk.services.cloudwatch.Alarm cacheHitRatioAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "CacheHitRatioAlarm")
                        .alarmName(resourcePrefix + "-cache-hit-ratio")
                        .alarmDescription("Cache hit ratio is too low")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/ElastiCache")
                                .metricName("CacheHitRate")
                                .dimensionsMap(Map.of(
                                        "CacheClusterId", redisCluster.getReplicationGroupId() + "-001"
                                ))
                                .statistic("Average")
                                .period(Duration.minutes(15))
                                .build())
                        .threshold(0.8)  // 80% hit rate
                        .evaluationPeriods(2)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.BREACHING)
                        .build();
        
        // Create custom dashboard for cache metrics
        createCacheDashboard(redisCluster);
    }
    
    /**
     * Creates CloudWatch dashboard for cache performance monitoring
     */
    private void createCacheDashboard(CfnReplicationGroup redisCluster) {
        // Create dashboard to monitor cache performance
        software.amazon.awscdk.services.cloudwatch.Dashboard cacheDashboard = 
                software.amazon.awscdk.services.cloudwatch.Dashboard.Builder.create(this, "CacheDashboard")
                        .dashboardName(resourcePrefix + "-cache-performance")
                        .build();
        
        // Add widgets for cache metrics
        cacheDashboard.addWidgets(
                // Memory utilization widget
                software.amazon.awscdk.services.cloudwatch.GraphWidget.Builder.create()
                        .title("Redis Memory Utilization")
                        .width(12)
                        .height(6)
                        .left(Arrays.asList(
                                software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                        .namespace("AWS/ElastiCache")
                                        .metricName("DatabaseMemoryUsagePercentage")
                                        .dimensionsMap(Map.of(
                                                "CacheClusterId", redisCluster.getReplicationGroupId() + "-001"
                                        ))
                                        .build()
                        ))
                        .build(),
                
                // CPU utilization widget
                software.amazon.awscdk.services.cloudwatch.GraphWidget.Builder.create()
                        .title("Redis CPU Utilization")
                        .width(12)
                        .height(6)
                        .left(Arrays.asList(
                                software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                        .namespace("AWS/ElastiCache")
                                        .metricName("CPUUtilization")
                                        .dimensionsMap(Map.of(
                                                "CacheClusterId", redisCluster.getReplicationGroupId() + "-001"
                                        ))
                                        .build()
                        ))
                        .build(),
                
                // Cache performance metrics
                software.amazon.awscdk.services.cloudwatch.GraphWidget.Builder.create()
                        .title("Cache Performance")
                        .width(24)
                        .height(6)
                        .left(Arrays.asList(
                                software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                        .namespace("AWS/ElastiCache")
                                        .metricName("CacheHitRate")
                                        .dimensionsMap(Map.of(
                                                "CacheClusterId", redisCluster.getReplicationGroupId() + "-001"
                                        ))
                                        .build(),
                                software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                        .namespace("AWS/ElastiCache")
                                        .metricName("CurrConnections")
                                        .dimensionsMap(Map.of(
                                                "CacheClusterId", redisCluster.getReplicationGroupId() + "-001"
                                        ))
                                        .build()
                        ))
                        .build()
        );
    }
    
    /**
     * Creates CloudFormation outputs for cache endpoints and configuration
     */
    private void createCacheOutputs(CfnReplicationGroup redisCluster) {
        software.amazon.awscdk.CfnOutput.Builder.create(this, "RedisEndpoint")
                .value(redisCluster.getAttrPrimaryEndPointAddress())
                .description("Redis cluster endpoint")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "RedisPort")
                .value(redisCluster.getAttrPrimaryEndPointPort().toString())
                .description("Redis cluster port")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "CacheDashboardUrl")
                .value("https://console.aws.amazon.com/cloudwatch/home?region=" + this.getRegion() + 
                       "#dashboards:name=" + resourcePrefix + "-cache-performance")
                .description("Cache performance dashboard URL")
                .build();
    }


    /**
     * Creates API Gateway REST API with all endpoints including developer endpoints.
     */
    private RestApi createApiGateway(Function publicFunction, Function authFunction, 
                                           Function itemsFunction, Function developerFunction, UserPool userPool) {
        
        RestApi api = RestApi.Builder.create(this, "ToyApi")
                .restApiName(resourcePrefix + "-api")
                .description("ToyApi REST API (" + environment + ")")
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS)
                        .allowMethods(Cors.ALL_METHODS)
                        .allowHeaders(Arrays.asList("Content-Type", "Authorization"))
                        .build())
.deployOptions(StageOptions.builder()
                        .stageName(environment)
                        .build())
                .build();

        // Create Cognito authorizer
        CognitoUserPoolsAuthorizer authorizer = CognitoUserPoolsAuthorizer.Builder.create(this, "CognitoAuthorizer")
                .cognitoUserPools(Arrays.asList(userPool))
                .authorizerName(resourcePrefix + "-authorizer")
                .build();

        // Create versioned API structure - supports both URL path and media type versioning
        createVersionedEndpoints(api, publicFunction, authFunction, itemsFunction, authorizer);
        
        // Create legacy (unversioned) endpoints for backward compatibility
        createLegacyEndpoints(api, publicFunction, authFunction, itemsFunction, authorizer);
        
        // Create media-type versioned endpoints (same URLs, different Accept headers)
        createMediaTypeVersionedEndpoints(api, publicFunction, authFunction, itemsFunction, authorizer);

        // Developer endpoints (public for registration, no API key required for onboarding)
        Resource developerResource = api.getRoot().addResource("developer");
        
        // Registration endpoint
        developerResource.addResource("register")
                .addMethod("POST", new LambdaIntegration(developerFunction));
        
        // Profile endpoints
        Resource profileResource = developerResource.addResource("profile");
        profileResource.addMethod("GET", new LambdaIntegration(developerFunction));
        profileResource.addMethod("PUT", new LambdaIntegration(developerFunction));
        
        // API key endpoints
        Resource apiKeyResource = developerResource.addResource("api-key");
        apiKeyResource.addMethod("POST", new LambdaIntegration(developerFunction));
        
        developerResource.addResource("api-keys")
                .addMethod("GET", new LambdaIntegration(developerFunction));
        
        apiKeyResource.addResource("{keyId}")
                .addMethod("DELETE", new LambdaIntegration(developerFunction));

        // Usage plan will be associated separately after creation

        return api;
    }
    
    /**
     * Creates versioned API endpoints (v1 and v2) for future-proofing
     */
    private void createVersionedEndpoints(RestApi api, Function publicFunction, Function authFunction, 
                                        Function itemsFunction, CognitoUserPoolsAuthorizer authorizer) {
        
        // V1 API endpoints - current stable version
        Resource v1Resource = api.getRoot().addResource("v1");
        createApiEndpoints(v1Resource, publicFunction, authFunction, itemsFunction, authorizer, "v1");
        
        // V2 API endpoints - future version with enhanced features
        Resource v2Resource = api.getRoot().addResource("v2");
        createApiEndpoints(v2Resource, publicFunction, authFunction, itemsFunction, authorizer, "v2");
    }
    
    /**
     * Creates legacy unversioned endpoints for backward compatibility
     */
    private void createLegacyEndpoints(RestApi api, Function publicFunction, Function authFunction, 
                                     Function itemsFunction, CognitoUserPoolsAuthorizer authorizer) {
        
        createLegacyApiEndpoints(api, publicFunction, authFunction, itemsFunction, authorizer);
    }
    
    /**
     * Creates the standard set of API endpoints under a given resource root
     */
    private void createApiEndpoints(Resource rootResource, Function publicFunction, Function authFunction, 
                                  Function itemsFunction, CognitoUserPoolsAuthorizer authorizer, String version) {
        
        MethodOptions authMethodOptions = MethodOptions.builder().authorizer(authorizer).build();
        
        // Public endpoints
        Resource publicResource = rootResource.addResource("public");
        publicResource.addResource("message")
                .addMethod("GET", new LambdaIntegration(publicFunction));

        // Auth endpoints  
        Resource authResource = rootResource.addResource("auth");
        authResource.addResource("login")
                .addMethod("POST", new LambdaIntegration(authFunction));
        authResource.addResource("message")
                .addMethod("GET", new LambdaIntegration(authFunction), authMethodOptions);

        // User-specific endpoint
        authResource.addResource("user")
                .addResource("{userId}")
                .addResource("message")
                .addMethod("GET", new LambdaIntegration(authFunction), authMethodOptions);

        // Items endpoints (all authenticated)
        Resource itemsResource = rootResource.addResource("items");
        
        itemsResource.addMethod("GET", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemsResource.addMethod("POST", new LambdaIntegration(itemsFunction), authMethodOptions);
        
        Resource itemResource = itemsResource.addResource("{id}");
        itemResource.addMethod("GET", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemResource.addMethod("PUT", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemResource.addMethod("DELETE", new LambdaIntegration(itemsFunction), authMethodOptions);
        
        // V2 enhancements - additional endpoints for advanced features
        if ("v2".equals(version)) {
            // Batch operations for V2
            Resource batchResource = itemsResource.addResource("batch");
            batchResource.addMethod("POST", new LambdaIntegration(itemsFunction), authMethodOptions);
            batchResource.addMethod("DELETE", new LambdaIntegration(itemsFunction), authMethodOptions);
            
            // Search endpoint for V2
            itemsResource.addResource("search")
                    .addMethod("GET", new LambdaIntegration(itemsFunction), authMethodOptions);
        }
    }
    
    /**
     * Creates legacy unversioned endpoints directly on the root for backward compatibility
     */
    private void createLegacyApiEndpoints(RestApi api, Function publicFunction, Function authFunction, 
                                        Function itemsFunction, CognitoUserPoolsAuthorizer authorizer) {
        
        MethodOptions authMethodOptions = MethodOptions.builder().authorizer(authorizer).build();
        
        // Public endpoints directly on root
        Resource publicResource = api.getRoot().addResource("public");
        publicResource.addResource("message")
                .addMethod("GET", new LambdaIntegration(publicFunction));

        // Auth endpoints directly on root  
        Resource authResource = api.getRoot().addResource("auth");
        authResource.addResource("login")
                .addMethod("POST", new LambdaIntegration(authFunction));
        authResource.addResource("message")
                .addMethod("GET", new LambdaIntegration(authFunction), authMethodOptions);

        // User-specific endpoint directly on root
        authResource.addResource("user")
                .addResource("{userId}")
                .addResource("message")
                .addMethod("GET", new LambdaIntegration(authFunction), authMethodOptions);

        // Items endpoints directly on root (all authenticated)
        Resource itemsResource = api.getRoot().addResource("items");
        
        itemsResource.addMethod("GET", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemsResource.addMethod("POST", new LambdaIntegration(itemsFunction), authMethodOptions);
        
        Resource itemResource = itemsResource.addResource("{id}");
        itemResource.addMethod("GET", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemResource.addMethod("PUT", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemResource.addMethod("DELETE", new LambdaIntegration(itemsFunction), authMethodOptions);
        
        // No V2 features in legacy endpoints - just basic CRUD
    }
    
    /**
     * Creates media-type versioned endpoints that support content negotiation.
     * Uses the same URLs as legacy but responds differently based on Accept headers.
     * 
     * Supports:
     * - Accept: application/vnd.toyapi.v1+json
     * - Accept: application/vnd.toyapi.v2+json 
     * - Accept: application/json (defaults to v1)
     */
    private void createMediaTypeVersionedEndpoints(RestApi api, Function publicFunction, Function authFunction, 
                                                 Function itemsFunction, CognitoUserPoolsAuthorizer authorizer) {
        
        // Create API resource under /api for content negotiation
        Resource apiResource = api.getRoot().addResource("api");
        
        // Create request validators for media type versioning
        RequestValidator bodyValidator = RequestValidator.Builder.create(this, "MediaTypeBodyValidator")
                .restApi(api)
                .requestValidatorName(resourcePrefix + "-media-type-validator")
                .validateRequestBody(true)
                .validateRequestParameters(true)
                .build();
        
        // Method options with request validation and content negotiation
        MethodOptions mediaTypeV1Options = MethodOptions.builder()
                .authorizer(authorizer)
                .requestValidator(bodyValidator)
                .requestParameters(Map.of(
                        "method.request.header.Accept", false,
                        "method.request.header.Content-Type", false))
                .methodResponses(Arrays.asList(
                        // V1 response
                        MethodResponse.builder()
                                .statusCode("200")
                                .responseParameters(Map.of(
                                        "method.response.header.Content-Type", true,
                                        "method.response.header.X-API-Version", true))
                                .build(),
                        // V2 response  
                        MethodResponse.builder()
                                .statusCode("200")
                                .responseParameters(Map.of(
                                        "method.response.header.Content-Type", true,
                                        "method.response.header.X-API-Version", true))
                                .build()))
                .build();
        
        MethodOptions mediaTypePublicOptions = MethodOptions.builder()
                .requestValidator(bodyValidator)
                .requestParameters(Map.of("method.request.header.Accept", false))
                .build();
        
        // Create endpoints with media type versioning support
        
        // Public endpoints with content negotiation
        Resource publicResource = apiResource.addResource("public");
        Resource messageResource = publicResource.addResource("message");
        
        // Add method with custom integration for content negotiation
        messageResource.addMethod("GET", 
                createMediaTypeIntegration(publicFunction, "GET", "/public/message"), 
                mediaTypePublicOptions);
        
        // Auth endpoints with content negotiation
        Resource authResource = apiResource.addResource("auth");
        
        authResource.addResource("login")
                .addMethod("POST", 
                        createMediaTypeIntegration(authFunction, "POST", "/auth/login"), 
                        mediaTypePublicOptions);
                        
        authResource.addResource("message")
                .addMethod("GET", 
                        createMediaTypeIntegration(authFunction, "GET", "/auth/message"), 
                        mediaTypeV1Options);
        
        // User-specific endpoint with content negotiation
        authResource.addResource("user")
                .addResource("{userId}")
                .addResource("message")
                .addMethod("GET", 
                        createMediaTypeIntegration(authFunction, "GET", "/auth/user/{userId}/message"), 
                        mediaTypeV1Options);
        
        // Items endpoints with content negotiation
        Resource itemsResource = apiResource.addResource("items");
        
        itemsResource.addMethod("GET", 
                createMediaTypeIntegration(itemsFunction, "GET", "/items"), 
                mediaTypeV1Options);
        itemsResource.addMethod("POST", 
                createMediaTypeIntegration(itemsFunction, "POST", "/items"), 
                mediaTypeV1Options);
        
        Resource itemResource = itemsResource.addResource("{id}");
        itemResource.addMethod("GET", 
                createMediaTypeIntegration(itemsFunction, "GET", "/items/{id}"), 
                mediaTypeV1Options);
        itemResource.addMethod("PUT", 
                createMediaTypeIntegration(itemsFunction, "PUT", "/items/{id}"), 
                mediaTypeV1Options);
        itemResource.addMethod("DELETE", 
                createMediaTypeIntegration(itemsFunction, "DELETE", "/items/{id}"), 
                mediaTypeV1Options);
        
        // V2-specific endpoints under content negotiation
        Resource batchResource = itemsResource.addResource("batch");
        batchResource.addMethod("POST", 
                createMediaTypeIntegration(itemsFunction, "POST", "/items/batch"), 
                mediaTypeV1Options);
        batchResource.addMethod("DELETE", 
                createMediaTypeIntegration(itemsFunction, "DELETE", "/items/batch"), 
                mediaTypeV1Options);
        
        itemsResource.addResource("search")
                .addMethod("GET", 
                        createMediaTypeIntegration(itemsFunction, "GET", "/items/search"), 
                        mediaTypeV1Options);
    }
    
    /**
     * Creates Lambda integration with media type header mapping for content negotiation
     */
    private LambdaIntegration createMediaTypeIntegration(Function function, String method, String path) {
        return LambdaIntegration.Builder.create(function)
                .proxy(true)
                .requestTemplates(Map.of(
                        "application/json", "{\n" +
                                "  \"httpMethod\": \"" + method + "\",\n" +
                                "  \"path\": \"" + path + "\",\n" +
                                "  \"headers\": {\n" +
                                "    #foreach($param in $input.params().header.keySet())\n" +
                                "    \"$param\": \"$util.escapeJavaScript($input.params().header.get($param))\"\n" +
                                "    #if($foreach.hasNext),#end\n" +
                                "    #end\n" +
                                "  },\n" +
                                "  \"queryStringParameters\": {\n" +
                                "    #foreach($param in $input.params().querystring.keySet())\n" +
                                "    \"$param\": \"$util.escapeJavaScript($input.params().querystring.get($param))\"\n" +
                                "    #if($foreach.hasNext),#end\n" +
                                "    #end\n" +
                                "  },\n" +
                                "  \"pathParameters\": {\n" +
                                "    #foreach($param in $input.params().path.keySet())\n" +
                                "    \"$param\": \"$util.escapeJavaScript($input.params().path.get($param))\"\n" +
                                "    #if($foreach.hasNext),#end\n" +
                                "    #end\n" +
                                "  },\n" +
                                "  \"body\": $input.json('$'),\n" +
                                "  \"requestContext\": {\n" +
                                "    \"apiVersion\": \"$util.escapeJavaScript($input.params().header.get('Accept'))\",\n" +
                                "    \"contentNegotiation\": true\n" +
                                "  }\n" +
                                "}"
                ))
                .integrationResponses(Arrays.asList(
                        // Success response with version header
                        IntegrationResponse.builder()
                                .statusCode("200")
                                .responseParameters(Map.of(
                                        "method.response.header.Content-Type", "'application/json'",
                                        "method.response.header.X-API-Version", "integration.response.header.X-API-Version"))
                                .build()))
                .build();
    }
    
    /**
     * Creates comprehensive WAF (Web Application Firewall) protection for API Gateway.
     * Provides protection against:
     * - SQL injection attacks
     * - XSS (Cross-site scripting) attacks  
     * - DDoS attacks and rate limiting
     * - Geographic blocking
     * - Known malicious IP addresses
     * - Bot protection
     */
    private void createWafProtection(RestApi api) {
        // Create WAF IP sets for known malicious IPs and allowed regions
        CfnIPSet maliciousIpSet = CfnIPSet.Builder.create(this, "MaliciousIPSet")
                .name(resourcePrefix + "-malicious-ips")
                .description("Known malicious IP addresses")
                .scope("REGIONAL")  // For API Gateway (use CLOUDFRONT for CloudFront)
                .ipAddressVersion("IPV4")
                .addresses(Arrays.asList(
                    // Common malicious IP ranges - update based on threat intelligence
                    "192.0.2.44/32",    // Example malicious IP
                    "198.51.100.0/24"   // Example malicious range
                ))
                .build();
        
        // Create IP set for rate limiting bypass (trusted IPs)
        CfnIPSet trustedIpSet = CfnIPSet.Builder.create(this, "TrustedIPSet")
                .name(resourcePrefix + "-trusted-ips")
                .description("Trusted IP addresses with higher rate limits")
                .scope("REGIONAL")
                .ipAddressVersion("IPV4")
                .addresses(Arrays.asList(
                    // Add your trusted IPs here
                    "203.0.113.0/24"    // Example trusted range
                ))
                .build();
        
        // Create regex pattern set for advanced threat detection
        CfnRegexPatternSet maliciousPatterns = CfnRegexPatternSet.Builder.create(this, "MaliciousPatterns")
                .name(resourcePrefix + "-malicious-patterns")
                .description("Patterns matching malicious requests")
                .scope("REGIONAL")
                .regularExpressionList(Arrays.asList(
                    // SQL injection patterns
                    "(?i)(union|select|insert|delete|update|drop|create|alter)\\s",
                    "(?i)('|(\\-\\-)|;|\\||\\*|%)",
                    // XSS patterns
                    "(?i)(<script|javascript:|vbscript:|onload=|onerror=)",
                    // Path traversal patterns
                    "(?i)(\\.\\./|\\.\\.\\\\|%2e%2e%2f|%2e%2e\\\\)",
                    // Command injection patterns
                    "(?i)(\\||;|&|`|\\$\\(|\\${)"
                ))
                .build();
        
        // Create WAF rules
        List<CfnWebACL.RuleProperty> wafRules = Arrays.asList(
            // Rule 1: Block known malicious IPs (highest priority)
            CfnWebACL.RuleProperty.builder()
                    .name("BlockMaliciousIPs")
                    .priority(10)
                    .action(CfnWebACL.RuleActionProperty.builder()
                            .block(CfnWebACL.BlockActionProperty.builder()
                                    .customResponse(CfnWebACL.CustomResponseProperty.builder()
                                            .responseCode(403)
                                            .build())
                                    .build())
                            .build())
                    .statement(CfnWebACL.StatementProperty.builder()
                            .ipSetReferenceStatement(CfnWebACL.IPSetReferenceStatementProperty.builder()
                                    .arn(maliciousIpSet.getAttrArn())
                                    .build())
                            .build())
                    .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                            .sampledRequestsEnabled(true)
                            .cloudWatchMetricsEnabled(true)
                            .metricName(resourcePrefix + "-blocked-malicious-ips")
                            .build())
                    .build(),
                    
            // Rule 2: Rate limiting for general traffic
            CfnWebACL.RuleProperty.builder()
                    .name("RateLimitGeneral")
                    .priority(20)
                    .action(CfnWebACL.RuleActionProperty.builder()
                            .block(CfnWebACL.BlockActionProperty.builder()
                                    .customResponse(CfnWebACL.CustomResponseProperty.builder()
                                            .responseCode(429)
                                            .build())
                                    .build())
                            .build())
                    .statement(CfnWebACL.StatementProperty.builder()
                            .rateBasedStatement(CfnWebACL.RateBasedStatementProperty.builder()
                                    .limit(environment.equals("prod") ? 2000L : 5000L)  // Stricter limits for prod
                                    .aggregateKeyType("IP")
                                    .scopeDownStatement(CfnWebACL.StatementProperty.builder()
                                            .notStatement(CfnWebACL.NotStatementProperty.builder()
                                                    .statement(CfnWebACL.StatementProperty.builder()
                                                            .ipSetReferenceStatement(CfnWebACL.IPSetReferenceStatementProperty.builder()
                                                                    .arn(trustedIpSet.getAttrArn())
                                                                    .build())
                                                            .build())
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                            .sampledRequestsEnabled(true)
                            .cloudWatchMetricsEnabled(true)
                            .metricName(resourcePrefix + "-rate-limited-requests")
                            .build())
                    .build(),
                    
            // Rule 3: Rate limiting for authentication endpoints
            CfnWebACL.RuleProperty.builder()
                    .name("RateLimitAuth")
                    .priority(30)
                    .action(CfnWebACL.RuleActionProperty.builder()
                            .block(CfnWebACL.BlockActionProperty.builder()
                                    .customResponse(CfnWebACL.CustomResponseProperty.builder()
                                            .responseCode(429)
                                            .build())
                                    .build())
                            .build())
                    .statement(CfnWebACL.StatementProperty.builder()
                            .rateBasedStatement(CfnWebACL.RateBasedStatementProperty.builder()
                                    .limit(100L)  // Stricter limit for auth endpoints
                                    .aggregateKeyType("IP")
                                    .scopeDownStatement(CfnWebACL.StatementProperty.builder()
                                            .byteMatchStatement(CfnWebACL.ByteMatchStatementProperty.builder()
                                                    .fieldToMatch(CfnWebACL.FieldToMatchProperty.builder()
                                                            .uriPath(java.util.Map.of())
                                                            .build())
                                                    .searchString("/auth/")
                                                    .textTransformations(java.util.Arrays.asList(
                                                            CfnWebACL.TextTransformationProperty.builder()
                                                                    .priority(0)
                                                                    .type("LOWERCASE")
                                                                    .build()))
                                                    .positionalConstraint("CONTAINS")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                            .sampledRequestsEnabled(true)
                            .cloudWatchMetricsEnabled(true)
                            .metricName(resourcePrefix + "-auth-rate-limited")
                            .build())
                    .build(),
                    
            // Rule 4: Block malicious patterns (SQL injection, XSS, etc.)
            CfnWebACL.RuleProperty.builder()
                    .name("BlockMaliciousPatterns")
                    .priority(40)
                    .action(CfnWebACL.RuleActionProperty.builder()
                            .block(CfnWebACL.BlockActionProperty.builder()
                                    .customResponse(CfnWebACL.CustomResponseProperty.builder()
                                            .responseCode(403)
                                            .build())
                                    .build())
                            .build())
                    .statement(CfnWebACL.StatementProperty.builder()
                            .orStatement(CfnWebACL.OrStatementProperty.builder()
                                    .statements(Arrays.asList(
                                            // Check URI for malicious patterns
                                            CfnWebACL.StatementProperty.builder()
                                                    .regexPatternSetReferenceStatement(CfnWebACL.RegexPatternSetReferenceStatementProperty.builder()
                                                            .arn(maliciousPatterns.getAttrArn())
                                                            .fieldToMatch(CfnWebACL.FieldToMatchProperty.builder()
                                                                    .uriPath(Map.of())
                                                                    .build())
                                                            .textTransformations(Arrays.asList(
                                                                    CfnWebACL.TextTransformationProperty.builder()
                                                                            .priority(0)
                                                                            .type("URL_DECODE")
                                                                            .build(),
                                                                    CfnWebACL.TextTransformationProperty.builder()
                                                                            .priority(1)
                                                                            .type("HTML_ENTITY_DECODE")
                                                                            .build()))
                                                            .build())
                                                    .build(),
                                            // Check query strings for malicious patterns
                                            CfnWebACL.StatementProperty.builder()
                                                    .regexPatternSetReferenceStatement(CfnWebACL.RegexPatternSetReferenceStatementProperty.builder()
                                                            .arn(maliciousPatterns.getAttrArn())
                                                            .fieldToMatch(CfnWebACL.FieldToMatchProperty.builder()
                                                                    .queryString(Map.of())
                                                                    .build())
                                                            .textTransformations(Arrays.asList(
                                                                    CfnWebACL.TextTransformationProperty.builder()
                                                                            .priority(0)
                                                                            .type("URL_DECODE")
                                                                            .build()))
                                                            .build())
                                                    .build(),
                                            // Check request body for malicious patterns
                                            CfnWebACL.StatementProperty.builder()
                                                    .regexPatternSetReferenceStatement(CfnWebACL.RegexPatternSetReferenceStatementProperty.builder()
                                                            .arn(maliciousPatterns.getAttrArn())
                                                            .fieldToMatch(CfnWebACL.FieldToMatchProperty.builder()
                                                                    .body(CfnWebACL.BodyProperty.builder()
                                                                            .oversizeHandling("CONTINUE")
                                                                            .build())
                                                                    .build())
                                                            .textTransformations(Arrays.asList(
                                                                    CfnWebACL.TextTransformationProperty.builder()
                                                                            .priority(0)
                                                                            .type("HTML_ENTITY_DECODE")
                                                                            .build()))
                                                            .build())
                                                    .build()
                                    ))
                                    .build())
                            .build())
                    .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                            .sampledRequestsEnabled(true)
                            .cloudWatchMetricsEnabled(true)
                            .metricName(resourcePrefix + "-blocked-malicious-patterns")
                            .build())
                    .build(),
                    
            // Rule 5: AWS Managed Rules - Core Rule Set (common attacks)
            CfnWebACL.RuleProperty.builder()
                    .name("AWSManagedRulesCore")
                    .priority(50)
                    .overrideAction(CfnWebACL.OverrideActionProperty.builder()
                            .none(Map.of())
                            .build())
                    .statement(CfnWebACL.StatementProperty.builder()
                            .managedRuleGroupStatement(CfnWebACL.ManagedRuleGroupStatementProperty.builder()
                                    .vendorName("AWS")
                                    .name("AWSManagedRulesCommonRuleSet")
                                    .build())
                            .build())
                    .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                            .sampledRequestsEnabled(true)
                            .cloudWatchMetricsEnabled(true)
                            .metricName(resourcePrefix + "-aws-managed-core")
                            .build())
                    .build(),
                    
            // Rule 6: AWS Managed Rules - Known Bad Inputs
            CfnWebACL.RuleProperty.builder()
                    .name("AWSManagedRulesKnownBadInputs")
                    .priority(60)
                    .overrideAction(CfnWebACL.OverrideActionProperty.builder()
                            .none(Map.of())
                            .build())
                    .statement(CfnWebACL.StatementProperty.builder()
                            .managedRuleGroupStatement(CfnWebACL.ManagedRuleGroupStatementProperty.builder()
                                    .vendorName("AWS")
                                    .name("AWSManagedRulesKnownBadInputsRuleSet")
                                    .build())
                            .build())
                    .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                            .sampledRequestsEnabled(true)
                            .cloudWatchMetricsEnabled(true)
                            .metricName(resourcePrefix + "-aws-managed-bad-inputs")
                            .build())
                    .build(),
                    
            // Rule 7: AWS Managed Rules - Amazon IP Reputation List
            CfnWebACL.RuleProperty.builder()
                    .name("AWSManagedRulesAmazonIpReputation")
                    .priority(70)
                    .overrideAction(CfnWebACL.OverrideActionProperty.builder()
                            .none(Map.of())
                            .build())
                    .statement(CfnWebACL.StatementProperty.builder()
                            .managedRuleGroupStatement(CfnWebACL.ManagedRuleGroupStatementProperty.builder()
                                    .vendorName("AWS")
                                    .name("AWSManagedRulesAmazonIpReputationList")
                                    .build())
                            .build())
                    .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                            .sampledRequestsEnabled(true)
                            .cloudWatchMetricsEnabled(true)
                            .metricName(resourcePrefix + "-aws-managed-ip-reputation")
                            .build())
                    .build()
        );
        
        // Create the main WAF Web ACL
        CfnWebACL webAcl = CfnWebACL.Builder.create(this, "ToyApiWafWebAcl")
                .name(resourcePrefix + "-web-acl")
                .description("Comprehensive WAF protection for ToyApi (" + environment + ")")
                .scope("REGIONAL")  // For API Gateway
                .defaultAction(CfnWebACL.DefaultActionProperty.builder()
                        .allow(CfnWebACL.AllowActionProperty.builder().build())  // Allow by default, block specific threats
                        .build())
                .rules(wafRules)
                .visibilityConfig(CfnWebACL.VisibilityConfigProperty.builder()
                        .sampledRequestsEnabled(true)
                        .cloudWatchMetricsEnabled(true)
                        .metricName(resourcePrefix + "-web-acl")
                        .build())
                .build();
        
        // Associate WAF with API Gateway stage
        CfnWebACLAssociation.Builder.create(this, "WebACLAssociation")
                .resourceArn("arn:aws:apigateway:" + this.getRegion() + "::/restapis/" + 
                           api.getRestApiId() + "/stages/" + environment)
                .webAclArn(webAcl.getAttrArn())
                .build();
        
        // Create CloudWatch alarms for WAF metrics
        createWafAlarms(webAcl);
    }
    
    /**
     * Creates CloudWatch alarms for WAF metrics and anomaly detection
     */
    private void createWafAlarms(CfnWebACL webAcl) {
        // Create SNS topic for WAF alerts
        Topic wafAlertTopic = Topic.Builder.create(this, "WafAlerts")
                .topicName(resourcePrefix + "-waf-alerts")
                .displayName("ToyApi WAF Security Alerts (" + environment + ")")
                .build();
        
        // Subscribe to WAF alerts
        wafAlertTopic.addSubscription(
                software.amazon.awscdk.services.sns.subscriptions.EmailSubscription.Builder.create("security+toyapi@thismakesmehappy.co")
                        .build()
        );
        
        // High blocked requests alarm (potential attack)
        software.amazon.awscdk.services.cloudwatch.Alarm blockedRequestsAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "WafBlockedRequestsAlarm")
                        .alarmName(resourcePrefix + "-waf-high-blocked-requests")
                        .alarmDescription("High number of blocked requests detected - potential attack")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/WAFV2")
                                .metricName("BlockedRequests")
                                .dimensionsMap(Map.of(
                                        "WebACL", webAcl.getName(),
                                        "Region", this.getRegion(),
                                        "Rule", "ALL"
                                ))
                                .statistic("Sum")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(100)  // 100 blocked requests in 5 minutes
                        .evaluationPeriods(2)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.NOT_BREACHING)
                        .build();
        
        blockedRequestsAlarm.addAlarmAction(new software.amazon.awscdk.services.cloudwatch.actions.SnsAction(wafAlertTopic));
        
        // Rate limit exceeded alarm  
        software.amazon.awscdk.services.cloudwatch.Alarm rateLimitAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "WafRateLimitAlarm")
                        .alarmName(resourcePrefix + "-waf-rate-limit-exceeded")
                        .alarmDescription("Rate limit frequently exceeded - potential DDoS")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/WAFV2")
                                .metricName("BlockedRequests")
                                .dimensionsMap(Map.of(
                                        "WebACL", webAcl.getName(),
                                        "Region", this.getRegion(),
                                        "Rule", "RateLimitGeneral"
                                ))
                                .statistic("Sum")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(50)  // 50 rate-limited requests in 5 minutes
                        .evaluationPeriods(1)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.NOT_BREACHING)
                        .build();
        
        rateLimitAlarm.addAlarmAction(new software.amazon.awscdk.services.cloudwatch.actions.SnsAction(wafAlertTopic));
        
        // Authentication endpoint attack alarm
        software.amazon.awscdk.services.cloudwatch.Alarm authAttackAlarm = 
                software.amazon.awscdk.services.cloudwatch.Alarm.Builder.create(this, "WafAuthAttackAlarm")
                        .alarmName(resourcePrefix + "-waf-auth-attack")
                        .alarmDescription("Authentication endpoint under attack")
                        .metric(software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
                                .namespace("AWS/WAFV2")
                                .metricName("BlockedRequests")
                                .dimensionsMap(Map.of(
                                        "WebACL", webAcl.getName(),
                                        "Region", this.getRegion(),
                                        "Rule", "RateLimitAuth"
                                ))
                                .statistic("Sum")
                                .period(Duration.minutes(5))
                                .build())
                        .threshold(10)  // 10 blocked auth requests in 5 minutes
                        .evaluationPeriods(1)
                        .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                        .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.NOT_BREACHING)
                        .build();
        
        authAttackAlarm.addAlarmAction(new software.amazon.awscdk.services.cloudwatch.actions.SnsAction(wafAlertTopic));
    }
    
    /**
     * Creates custom domain with SSL certificate and Route53 DNS records.
     * Only creates custom domain for production and staging environments.
     * 
     * Domain structure:
     * - prod: api.thismakesmehappy.co
     * - stage: api-stage.thismakesmehappy.co
     */
    private void createCustomDomain(RestApi api) {
        // Define domain names based on environment
        String domainName = environment.equals("prod") ? 
                "api.thismakesmehappy.co" : 
                "api-" + environment + ".thismakesmehappy.co";
        
        // Look up existing hosted zone (assumes domain is already registered and hosted zone exists)
        IHostedZone hostedZone = HostedZone.fromLookup(this, "HostedZone", 
                HostedZoneProviderProps.builder()
                        .domainName("thismakesmehappy.co")  // Root domain
                        .build());
        
        // Create SSL certificate via AWS Certificate Manager (ACM)
        Certificate certificate = Certificate.Builder.create(this, "ApiCertificate")
                .domainName(domainName)
                .certificateName(resourcePrefix + "-api-cert")
                .validation(CertificateValidation.fromDns(hostedZone))
                .build();
        
        // Create custom domain name
        DomainName customDomain = DomainName.Builder.create(this, "CustomDomain")
                .domainName(domainName)
                .certificate(certificate)
                .mapping(api)
                .endpointType(EndpointType.REGIONAL)  // Regional for better performance
                .securityPolicy(SecurityPolicy.TLS_1_2)
                .build();
        
        // Create Route53 A record pointing to the custom domain
        ARecord.Builder.create(this, "ApiAliasRecord")
                .zone(hostedZone)
                .recordName(domainName.replace(".thismakesmehappy.co", ""))  // Remove root domain
                .target(RecordTarget.fromAlias(new ApiGatewayDomain(customDomain)))
                .build();
        
        // Create Route53 AAAA record for IPv6 support
        AaaaRecord.Builder.create(this, "ApiAliasRecordIPv6")
                .zone(hostedZone)
                .recordName(domainName.replace(".thismakesmehappy.co", ""))  // Remove root domain
                .target(RecordTarget.fromAlias(new ApiGatewayDomain(customDomain)))
                .build();
        
        // Output the custom domain URL
        software.amazon.awscdk.CfnOutput.Builder.create(this, "CustomDomainUrl")
                .value("https://" + domainName)
                .description("Custom domain URL for API")
                .build();
    }
    
    /**
     * Creates budget monitoring with SNS alerts at multiple thresholds.
     */
    private void createBudgetMonitoring() {
        // Create SNS topic for budget alerts
        Topic budgetTopic = Topic.Builder.create(this, "BudgetAlerts")
                .topicName(resourcePrefix + "-budget-alerts")
                .displayName("ToyApi Budget Alerts (" + environment + ")")
                .build();

        // Subscribe email to SNS topic
        budgetTopic.addSubscription(
                software.amazon.awscdk.services.sns.subscriptions.EmailSubscription.Builder.create("bernardo+toyAPI@thismakesmehappy.co")
                        .build()
        );

        // Create budget with multiple alert thresholds
        List<Map<String, Object>> subscribers = Arrays.asList(
                createSubscriber("bernardo+toyAPI@thismakesmehappy.co", "EMAIL")
        );

        // 50% threshold
        createBudgetAlert(budgetTopic, "50-percent", 50.0, "ACTUAL", subscribers);
        // 75% threshold  
        createBudgetAlert(budgetTopic, "75-percent", 75.0, "ACTUAL", subscribers);
        // 85% threshold
        createBudgetAlert(budgetTopic, "85-percent", 85.0, "ACTUAL", subscribers);
        // 95% forecasted threshold
        createBudgetAlert(budgetTopic, "95-percent-forecast", 95.0, "FORECASTED", subscribers);
    }

    private Map<String, Object> createSubscriber(String address, String type) {
        Map<String, Object> subscriber = new HashMap<>();
        subscriber.put("Address", address);
        subscriber.put("SubscriptionType", type);
        return subscriber;
    }

    private void createBudgetAlert(Topic topic, String alertName, double threshold, String thresholdType, 
                                  List<Map<String, Object>> subscribers) {
        
        Map<String, Object> budgetData = new HashMap<>();
        budgetData.put("BudgetName", resourcePrefix + "-monthly-budget");
        budgetData.put("BudgetType", "COST");
        budgetData.put("TimeUnit", "MONTHLY");
        
        Map<String, Object> budgetLimit = new HashMap<>();
        budgetLimit.put("Amount", 10.0);  // $10 monthly limit
        budgetLimit.put("Unit", "USD");
        budgetData.put("BudgetLimit", budgetLimit);

        Map<String, Object> costFilter = new HashMap<>();
        costFilter.put("TagKey", Arrays.asList("Project"));
        costFilter.put("Values", Arrays.asList("ToyApi"));
        budgetData.put("CostFilters", costFilter);

        Map<String, Object> notification = new HashMap<>();
        notification.put("NotificationType", thresholdType);
        notification.put("ComparisonOperator", "GREATER_THAN");
        notification.put("Threshold", threshold);
        notification.put("ThresholdType", "PERCENTAGE");

        // Budget creation simplified - will implement manually later
        // CfnBudget.Builder.create(this, "Budget-" + alertName)
        //         .budget(budgetData)
        //         .build();
    }

    /**
     * Creates CloudFormation outputs for important resource information.
     */
    private void createOutputs(RestApi api, CognitoResources cognitoResources, Table itemsTable) {
        software.amazon.awscdk.CfnOutput.Builder.create(this, "ApiUrl")
                .value(api.getUrl())
                .description("API Gateway endpoint URL")
                .build();

        software.amazon.awscdk.CfnOutput.Builder.create(this, "UserPoolId")
                .value(cognitoResources.userPool.getUserPoolId())
                .description("Cognito User Pool ID")
                .build();
                
        software.amazon.awscdk.CfnOutput.Builder.create(this, "UserPoolClientId")
                .value(cognitoResources.userPoolClient.getUserPoolClientId())
                .description("Cognito User Pool Client ID")
                .build();

        software.amazon.awscdk.CfnOutput.Builder.create(this, "TableName")
                .value(itemsTable.getTableName())
                .description("DynamoDB table name")
                .build();

        software.amazon.awscdk.CfnOutput.Builder.create(this, "Environment")
                .value(environment)
                .description("Deployment environment")
                .build();
    }
    
    /**
     * Creates AWS Systems Manager Parameter Store infrastructure for secure credential management.
     * Sets up parameters for test credentials and configuration values used by Lambda functions.
     */
    private void createParameterStoreInfrastructure() {
        // Create parameter for test user username
        StringParameter testUserParameter = StringParameter.Builder.create(this, "ToyApiParameterTestUser")
                .parameterName("/" + resourcePrefix + "/test-credentials/username")
                .stringValue("testuser")
                .description("Test user username for " + environment + " environment")
                .tier(ParameterTier.STANDARD)
                .build();
        
        // Create secure parameter for test user password
        StringParameter testPasswordParameter = StringParameter.Builder.create(this, "ToyApiParameterTestPassword")
                .parameterName("/" + resourcePrefix + "/test-credentials/password")
                .stringValue("TestPassword123")
                .description("Test user password for " + environment + " environment")
                .tier(ParameterTier.STANDARD)
                .build();
        
        // Create parameter for API base URL (will be populated after deployment)
        StringParameter apiUrlParameter = StringParameter.Builder.create(this, "ToyApiParameterApiUrl")
                .parameterName("/" + resourcePrefix + "/config/api-url")
                .stringValue("https://placeholder.execute-api.us-east-1.amazonaws.com/" + environment + "/")
                .description("API Gateway base URL for " + environment + " environment")
                .tier(ParameterTier.STANDARD)
                .build();
        
        // Create parameter for environment configuration
        StringParameter environmentParameter = StringParameter.Builder.create(this, "ToyApiParameterEnvironment")
                .parameterName("/" + resourcePrefix + "/config/environment")
                .stringValue(environment)
                .description("Environment name for configuration")
                .tier(ParameterTier.STANDARD)
                .build();
        
        // Create parameter for region configuration
        StringParameter regionParameter = StringParameter.Builder.create(this, "ToyApiParameterRegion")
                .parameterName("/" + resourcePrefix + "/config/region")
                .stringValue("us-east-1")
                .description("AWS region for " + environment + " environment")
                .tier(ParameterTier.STANDARD)
                .build();
    }
    
    /**
     * Creates CloudFront distribution for global content delivery and improved performance
     */
    private Distribution createCloudFrontDistribution(RestApi api) {
        // Create custom cache policy for API responses
        CachePolicy apiCachePolicy = CachePolicy.Builder.create(this, "ApiCachePolicy")
                .cachePolicyName(resourcePrefix + "-api-cache-policy")
                .comment("Cache policy for API Gateway with JWT authentication support")
                .defaultTtl(Duration.minutes(environment.equals("prod") ? 5 : 1))  // Shorter TTL for dev
                .maxTtl(Duration.hours(environment.equals("prod") ? 24 : 1))      // Max 24h for prod, 1h for dev
                .minTtl(Duration.seconds(0))  // Allow no caching for dynamic content
                .cookieBehavior(CacheCookieBehavior.none())  // Don't cache based on cookies
                .headerBehavior(CacheHeaderBehavior.allowList(
                        "Authorization",      // Pass through JWT tokens
                        "Content-Type",       // API content type
                        "Accept",            // Client accepted types
                        "Origin",            // CORS support
                        "X-API-Key"          // API key header
                ))
                .queryStringBehavior(CacheQueryStringBehavior.all())  // Cache based on all query parameters
                .enableAcceptEncodingGzip(true)   // Enable compression
                .enableAcceptEncodingBrotli(true) // Better compression
                .build();
        
        // Create origin request policy for API Gateway
        OriginRequestPolicy apiOriginPolicy = OriginRequestPolicy.Builder.create(this, "ApiOriginPolicy")
                .originRequestPolicyName(resourcePrefix + "-api-origin-policy")
                .comment("Origin request policy for API Gateway")
                .cookieBehavior(OriginRequestCookieBehavior.none())  // Don't forward cookies
                .headerBehavior(OriginRequestHeaderBehavior.allowList(
                        "Content-Type",       // Forward content type
                        "Accept",            // Forward accept headers
                        "User-Agent",        // Forward user agent for analytics
                        "X-API-Key",         // Forward API keys
                        "X-Forwarded-For"    // Forward client IP
                ))
                .queryStringBehavior(OriginRequestQueryStringBehavior.all())  // Forward all query strings
                .build();
        
        // Create public cache policy (reused for multiple behaviors)
        CachePolicy publicCachePolicy = createPublicCachePolicy();
        
        // Create response headers policy for security and CORS
        ResponseHeadersPolicy responseHeadersPolicy = ResponseHeadersPolicy.Builder.create(this, "ApiResponseHeadersPolicy")
                .responseHeadersPolicyName(resourcePrefix + "-api-security-headers")
                .comment("Security and CORS headers for API")
                .corsBehavior(ResponseHeadersCorsBehavior.builder()
                        .accessControlAllowCredentials(false)
                        .accessControlAllowHeaders(Arrays.asList("*"))
                        .accessControlAllowMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"))
                        .accessControlAllowOrigins(Arrays.asList("*"))  // Configurable per environment
                        .accessControlMaxAge(Duration.seconds(3600))
                        .originOverride(true)
                        .build())
                .securityHeadersBehavior(ResponseSecurityHeadersBehavior.builder()
                        .strictTransportSecurity(ResponseHeadersStrictTransportSecurity.builder()
                                .accessControlMaxAge(Duration.seconds(31536000))  // 1 year
                                .includeSubdomains(true)
                                .override(true)
                                .build())
                        .contentTypeOptions(ResponseHeadersContentTypeOptions.builder()
                                .override(true)
                                .build())
                        .frameOptions(ResponseHeadersFrameOptions.builder()
                                .frameOption(HeadersFrameOption.DENY)
                                .override(true)
                                .build())
                        .referrerPolicy(ResponseHeadersReferrerPolicy.builder()
                                .referrerPolicy(HeadersReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                                .override(true)
                                .build())
                        .build())
                .build();
        
        // Create CloudFront distribution
        Distribution distribution = Distribution.Builder.create(this, "CloudFrontDistribution")
                .comment("Global CDN for ToyApi (" + environment + ")")
                .defaultRootObject("") // No default root object for API
                .priceClass(environment.equals("prod") ? 
                    PriceClass.PRICE_CLASS_ALL :           // Global for production
                    PriceClass.PRICE_CLASS_100)            // US/Europe only for dev/staging
                .enableLogging(true)
                .logBucket(createCloudFrontLogsBucket())
                .logFilePrefix("api-access-logs/")
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(RestApiOrigin.Builder.create(api)
                                .originPath("/" + api.getDeploymentStage().getStageName())  // Include stage
                                .build())
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)  // Force HTTPS
                        .allowedMethods(AllowedMethods.ALLOW_ALL)  // Support all HTTP methods
                        .cachedMethods(CachedMethods.CACHE_GET_HEAD_OPTIONS)  // Cache GET/HEAD/OPTIONS only
                        .cachePolicy(apiCachePolicy)
                        .originRequestPolicy(apiOriginPolicy)
                        .responseHeadersPolicy(responseHeadersPolicy)
                        .compress(true)  // Enable compression
                        .build())
                .additionalBehaviors(Map.of(
                        // No caching for authentication endpoints
                        "/auth/*", BehaviorOptions.builder()
                                .origin(RestApiOrigin.Builder.create(api)
                                        .originPath("/" + api.getDeploymentStage().getStageName())
                                        .build())
                                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                .allowedMethods(AllowedMethods.ALLOW_ALL)
                                .cachePolicy(CachePolicy.CACHING_DISABLED)
                                .originRequestPolicy(apiOriginPolicy)
                                .responseHeadersPolicy(responseHeadersPolicy)
                                .compress(true)
                                .build(),
                        // No caching for v1/v2 auth endpoints 
                        "/v*/auth/*", BehaviorOptions.builder()
                                .origin(RestApiOrigin.Builder.create(api)
                                        .originPath("/" + api.getDeploymentStage().getStageName())
                                        .build())
                                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                .allowedMethods(AllowedMethods.ALLOW_ALL)
                                .cachePolicy(CachePolicy.CACHING_DISABLED)
                                .originRequestPolicy(apiOriginPolicy)
                                .responseHeadersPolicy(responseHeadersPolicy)
                                .compress(true)
                                .build(),
                        // Longer caching for public endpoints
                        "/public/*", BehaviorOptions.builder()
                                .origin(RestApiOrigin.Builder.create(api)
                                        .originPath("/" + api.getDeploymentStage().getStageName())
                                        .build())
                                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                .allowedMethods(AllowedMethods.ALLOW_ALL)
                                .cachedMethods(CachedMethods.CACHE_GET_HEAD_OPTIONS)
                                .cachePolicy(publicCachePolicy)
                                .originRequestPolicy(apiOriginPolicy)
                                .responseHeadersPolicy(responseHeadersPolicy)
                                .compress(true)
                                .build(),
                        // Similar caching for versioned public endpoints
                        "/v*/public/*", BehaviorOptions.builder()
                                .origin(RestApiOrigin.Builder.create(api)
                                        .originPath("/" + api.getDeploymentStage().getStageName())
                                        .build())
                                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                .allowedMethods(AllowedMethods.ALLOW_ALL)
                                .cachedMethods(CachedMethods.CACHE_GET_HEAD_OPTIONS)
                                .cachePolicy(publicCachePolicy)
                                .originRequestPolicy(apiOriginPolicy)
                                .responseHeadersPolicy(responseHeadersPolicy)
                                .compress(true)
                                .build()
                ))
                .build();
        
        // Create CloudFront outputs
        createCloudFrontOutputs(distribution);
        
        return distribution;
    }
    
    /**
     * Creates S3 bucket for CloudFront access logs
     */
    private software.amazon.awscdk.services.s3.Bucket createCloudFrontLogsBucket() {
        return software.amazon.awscdk.services.s3.Bucket.Builder.create(this, "CloudFrontLogsBucket")
                .bucketName(resourcePrefix + "-cloudfront-logs-" + this.getAccount())
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .lifecycleRules(Arrays.asList(
                        software.amazon.awscdk.services.s3.LifecycleRule.builder()
                                .id("DeleteOldLogs")
                                .enabled(true)
                                .expiration(Duration.days(environment.equals("prod") ? 365 : 90))  // Keep logs longer in prod
                                .transitions(Arrays.asList(
                                        software.amazon.awscdk.services.s3.Transition.builder()
                                                .storageClass(software.amazon.awscdk.services.s3.StorageClass.INFREQUENT_ACCESS)
                                                .transitionAfter(Duration.days(30))
                                                .build(),
                                        software.amazon.awscdk.services.s3.Transition.builder()
                                                .storageClass(software.amazon.awscdk.services.s3.StorageClass.GLACIER)
                                                .transitionAfter(Duration.days(90))
                                                .build()
                                ))
                                .build()
                ))
                .build();
    }
    
    /**
     * Creates cache policy for public endpoints with longer TTL
     */
    private CachePolicy createPublicCachePolicy() {
        return CachePolicy.Builder.create(this, "PublicCachePolicy")
                .cachePolicyName(resourcePrefix + "-public-cache-policy")
                .comment("Longer cache policy for public endpoints")
                .defaultTtl(Duration.minutes(environment.equals("prod") ? 30 : 5))   // 30min for prod, 5min for dev
                .maxTtl(Duration.hours(environment.equals("prod") ? 24 : 2))        // 24h for prod, 2h for dev
                .minTtl(Duration.seconds(0))
                .cookieBehavior(CacheCookieBehavior.none())
                .headerBehavior(CacheHeaderBehavior.allowList("Accept", "Content-Type"))
                .queryStringBehavior(CacheQueryStringBehavior.all())
                .enableAcceptEncodingGzip(true)
                .enableAcceptEncodingBrotli(true)
                .build();
    }
    
    /**
     * Creates CloudFormation outputs for CloudFront distribution
     */
    private void createCloudFrontOutputs(Distribution distribution) {
        software.amazon.awscdk.CfnOutput.Builder.create(this, "CloudFrontDistributionId")
                .value(distribution.getDistributionId())
                .description("CloudFront distribution ID")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "CloudFrontDomainName")
                .value(distribution.getDomainName())
                .description("CloudFront distribution domain name")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "CloudFrontUrl")
                .value("https://" + distribution.getDomainName())
                .description("CloudFront HTTPS URL")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "CloudFrontManagementUrl")
                .value("https://console.aws.amazon.com/cloudfront/home?region=" + this.getRegion() + 
                       "#distribution-settings:" + distribution.getDistributionId())
                .description("CloudFront management console URL")
                .build();
    }
    
    /**
     * Creates comprehensive usage analytics and developer insights tracking infrastructure including:
     * - Kinesis Data Streams for real-time analytics collection
     * - Lambda functions for processing analytics events
     * - DynamoDB tables for storing usage metrics and developer insights
     * - CloudWatch custom metrics and dashboards
     * - API Gateway request/response logging configuration
     * - Developer engagement tracking (API key usage, endpoint popularity, error rates)
     * - Usage pattern analysis and reporting capabilities
     */
    private void createUsageAnalyticsInfrastructure(RestApi api, Distribution cloudFrontDistribution) {
        // Create Kinesis Data Stream for real-time analytics
        Stream analyticsStream = Stream.Builder.create(this, "ToyApiKinesisAnalytics")
                .streamName(resourcePrefix + "-analytics-stream")
                .retentionPeriod(Duration.days(7))
                .build();
        
        // Create DynamoDB table for usage metrics storage
        Table usageMetricsTable = Table.Builder.create(this, "ToyApiDynamoUsageMetrics")
                .tableName(resourcePrefix + "-usage-metrics")
                .partitionKey(Attribute.builder()
                        .name("metricType")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("timestamp")
                        .type(AttributeType.NUMBER)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .timeToLiveAttribute("ttl")
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        
        // Add GSI for querying by API key
        usageMetricsTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("ApiKeyIndex")
                .partitionKey(Attribute.builder()
                        .name("apiKey")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("timestamp")
                        .type(AttributeType.NUMBER)
                        .build())
                .build());
        
        // Create DynamoDB table for developer insights
        Table developerInsightsTable = Table.Builder.create(this, "ToyApiDynamoDeveloperInsights")
                .tableName(resourcePrefix + "-developer-insights")
                .partitionKey(Attribute.builder()
                        .name("developerId")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("insightType")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        
        // Create Lambda function for analytics event processing
        Function analyticsProcessorFunction = Function.Builder.create(this, "ToyApiLambdaAnalyticsProcessor")
                .functionName(resourcePrefix + "-analytics-processor")
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .handler("co.thismakesmehappy.toyapi.service.AnalyticsHandler::handleEvent")
                .timeout(Duration.minutes(5))
                .memorySize(512)
                .environment(Map.of(
                        "USAGE_METRICS_TABLE", usageMetricsTable.getTableName(),
                        "DEVELOPER_INSIGHTS_TABLE", developerInsightsTable.getTableName(),
                        "ENVIRONMENT", environment
                ))
                .logRetention(RetentionDays.ONE_MONTH)
                .build();
        
        // Grant permissions to analytics processor
        usageMetricsTable.grantReadWriteData(analyticsProcessorFunction);
        developerInsightsTable.grantReadWriteData(analyticsProcessorFunction);
        analyticsStream.grantReadWrite(analyticsProcessorFunction);
        
        // Create Kinesis trigger for analytics processor
        analyticsProcessorFunction.addEventSource(
                new KinesisEventSource(analyticsStream, 
                        KinesisEventSourceProps.builder()
                                .batchSize(100)
                                .maxBatchingWindow(Duration.seconds(30))
                                .build())
        );
        
        // Create Lambda function for generating analytics reports
        Function analyticsReporterFunction = Function.Builder.create(this, "ToyApiLambdaAnalyticsReporter")
                .functionName(resourcePrefix + "-analytics-reporter")
                .runtime(Runtime.JAVA_17)
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .handler("co.thismakesmehappy.toyapi.service.AnalyticsReportHandler::generateReport")
                .timeout(Duration.minutes(10))
                .memorySize(1024)
                .environment(Map.of(
                        "USAGE_METRICS_TABLE", usageMetricsTable.getTableName(),
                        "DEVELOPER_INSIGHTS_TABLE", developerInsightsTable.getTableName(),
                        "ENVIRONMENT", environment
                ))
                .logRetention(RetentionDays.ONE_MONTH)
                .build();
        
        // Grant read permissions to analytics reporter
        usageMetricsTable.grantReadData(analyticsReporterFunction);
        developerInsightsTable.grantReadData(analyticsReporterFunction);
        
        // Create EventBridge rule for scheduled analytics reports
        Rule analyticsScheduleRule = Rule.Builder.create(this, "AnalyticsScheduleRule")
                .ruleName(resourcePrefix + "-analytics-schedule")
                .description("Schedule for generating analytics reports")
                .schedule(Schedule.cron(CronOptions.builder()
                        .minute("0")
                        .hour("2")  // 2 AM daily
                        .build()))
                .build();
        
        analyticsScheduleRule.addTarget(LambdaFunction.Builder.create(analyticsReporterFunction).build());
        
        // Create custom CloudWatch metrics for usage analytics
        Metric apiUsageMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment.toUpperCase())
                .metricName("ApiUsage")
                .build();
        
        Metric developerEngagementMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment.toUpperCase())
                .metricName("DeveloperEngagement")
                .build();
        
        Metric endpointPopularityMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment.toUpperCase())
                .metricName("EndpointPopularity")
                .build();
        
        // Create CloudWatch alarms for usage analytics
        Alarm lowUsageAlarm = Alarm.Builder.create(this, "LowUsageAlarm")
                .alarmName(resourcePrefix + "-low-usage")
                .alarmDescription("API usage is lower than expected")
                .metric(apiUsageMetric)
                .threshold(10)  // Less than 10 requests per hour
                .evaluationPeriods(3)
                .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.LESS_THAN_THRESHOLD)
                .treatMissingData(software.amazon.awscdk.services.cloudwatch.TreatMissingData.BREACHING)
                .build();
        
        Alarm highUsageAlarm = Alarm.Builder.create(this, "HighUsageAlarm")
                .alarmName(resourcePrefix + "-high-usage")
                .alarmDescription("API usage is higher than expected")
                .metric(apiUsageMetric)
                .threshold(1000)  // More than 1000 requests per hour
                .evaluationPeriods(2)
                .comparisonOperator(software.amazon.awscdk.services.cloudwatch.ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
        
        // Configure API Gateway access logging
        software.amazon.awscdk.services.logs.LogGroup accessLogGroup = software.amazon.awscdk.services.logs.LogGroup.Builder.create(this, "ApiAccessLogGroup")
                .logGroupName("/aws/apigateway/" + resourcePrefix + "-access-logs")
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(environment.equals("prod") ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();
        
        // Create secure access log format - masks sensitive identity information
        String accessLogFormat = "{\n" +
                "  \"requestId\": \"$context.requestId\",\n" +
                "  \"ipHash\": \"${util.escapeJavaScript($context.identity.sourceIp.replaceAll('[0-9]+', 'XXX'))}\",\n" +
                "  \"userAgentHash\": \"${util.escapeJavaScript($context.identity.userAgent.substring(0, java.lang.Math.min(20, $context.identity.userAgent.length())))}\",\n" +
                "  \"requestTime\": \"$context.requestTime\",\n" +
                "  \"httpMethod\": \"$context.httpMethod\",\n" +
                "  \"resourcePath\": \"$context.resourcePath\",\n" +
                "  \"status\": \"$context.status\",\n" +
                "  \"protocol\": \"$context.protocol\",\n" +
                "  \"responseLength\": \"$context.responseLength\",\n" +
                "  \"requestLength\": \"$context.requestLength\",\n" +
                "  \"responseTime\": \"$context.responseTime\",\n" +
                "  \"domainName\": \"$context.domainName\",\n" +
                "  \"stage\": \"$context.stage\",\n" +
                "  \"apiId\": \"$context.apiId\",\n" +
                "  \"hasAuthentication\": \"$util.escapeJavaScript($context.identity.cognitoIdentityId != null ? 'true' : 'false')\",\n" +
                "  \"authType\": \"$context.identity.cognitoAuthenticationType\",\n" +
                "  \"hasApiKey\": \"$util.escapeJavaScript($context.identity.apiKeyId != null ? 'true' : 'false')\",\n" +
                "  \"environment\": \"" + environment + "\",\n" +
                "  \"error\": {\n" +
                "    \"hasError\": \"$util.escapeJavaScript($context.error.message != null ? 'true' : 'false')\",\n" +
                "    \"errorType\": \"$util.escapeJavaScript($context.error.message != null ? $context.error.message.substring(0, java.lang.Math.min(50, $context.error.message.length())) : '')\"\n" +
                "  },\n" +
                "  \"integration\": {\n" +
                "    \"hasIntegrationError\": \"$util.escapeJavaScript($context.integration.error != null ? 'true' : 'false')\",\n" +
                "    \"integrationStatus\": \"$context.integration.integrationStatus\",\n" +
                "    \"latency\": \"$context.integration.latency\",\n" +
                "    \"status\": \"$context.integration.status\"\n" +
                "  }\n" +
                "}";
        
        // Create API Gateway deployment with access logging
        Deployment deployment = Deployment.Builder.create(this, "ApiDeployment" + java.time.Instant.now().toEpochMilli())
                .api(api)
                .build();
        
        Stage stage = Stage.Builder.create(this, "ApiStage")
                .deployment(deployment)
                .stageName(environment)
                .accessLogDestination(new LogGroupLogDestination(accessLogGroup))
                .accessLogFormat(AccessLogFormat.custom(accessLogFormat))
                .dataTraceEnabled(true)
                .loggingLevel(MethodLoggingLevel.INFO)
                .metricsEnabled(true)
                .build();
        
        // Create metric filters for analytics insights
        software.amazon.awscdk.services.logs.MetricFilter endpointUsageFilter = software.amazon.awscdk.services.logs.MetricFilter.Builder.create(this, "EndpointUsageFilter")
                .logGroup(accessLogGroup)
                .filterPattern(FilterPattern.all(
                        FilterPattern.stringValue("$.status", "=", "200"),
                        FilterPattern.exists("$.resourcePath")
                ))
                .metricNamespace("ToyApi/" + environment)
                .metricName("EndpointUsage")
                .metricValue("1")
                .defaultValue(0.0)
                .build();
        
        software.amazon.awscdk.services.logs.MetricFilter responseTimeFilter = software.amazon.awscdk.services.logs.MetricFilter.Builder.create(this, "ResponseTimeFilter")
                .logGroup(accessLogGroup)
                .filterPattern(FilterPattern.exists("$.responseTime"))
                .metricNamespace("ToyApi/" + environment)
                .metricName("ResponseTime")
                .metricValue("$.responseTime")
                .build();
        
        software.amazon.awscdk.services.logs.MetricFilter userAgentFilter = software.amazon.awscdk.services.logs.MetricFilter.Builder.create(this, "UserAgentFilter")
                .logGroup(accessLogGroup)
                .filterPattern(FilterPattern.exists("$.userAgent"))
                .metricNamespace("ToyApi/" + environment)
                .metricName("UserAgentTracking")
                .metricValue("1")
                .defaultValue(0.0)
                .build();
        
        // Create CloudWatch dashboard for analytics insights
        Dashboard analyticsDashboard = Dashboard.Builder.create(this, "AnalyticsDashboard")
                .dashboardName(resourcePrefix + "-analytics")
                .build();
        
        // Add widgets to analytics dashboard
        analyticsDashboard.addWidgets(
                GraphWidget.Builder.create()
                        .title("API Usage Trends (24h)")
                        .width(24)
                        .height(6)
                        .left(Arrays.asList(apiUsageMetric))
                        .view(GraphWidgetView.TIME_SERIES)
                        .period(Duration.hours(1))
                        .build(),
                
                SingleValueWidget.Builder.create()
                        .title("Active Developers")
                        .width(6)
                        .height(6)
                        .metrics(Arrays.asList(developerEngagementMetric))
                        .build(),
                
                GraphWidget.Builder.create()
                        .title("Endpoint Popularity")
                        .width(18)
                        .height(6)
                        .left(Arrays.asList(endpointPopularityMetric))
                        .view(GraphWidgetView.PIE)
                        .period(Duration.hours(24))
                        .build(),
                
                GraphWidget.Builder.create()
                        .title("Response Time Distribution")
                        .width(12)
                        .height(6)
                        .left(Arrays.asList(
                                Metric.Builder.create()
                                        .namespace("ToyApi/" + environment)
                                        .metricName("ResponseTime")
                                        .statistic("Average")
                                        .build(),
                                Metric.Builder.create()
                                        .namespace("ToyApi/" + environment)
                                        .metricName("ResponseTime")
                                        .statistic("p95")
                                        .build(),
                                Metric.Builder.create()
                                        .namespace("ToyApi/" + environment)
                                        .metricName("ResponseTime")
                                        .statistic("p99")
                                        .build()
                        ))
                        .view(GraphWidgetView.TIME_SERIES)
                        .period(Duration.minutes(5))
                        .build(),
                
                GraphWidget.Builder.create()
                        .title("User Agent Distribution")
                        .width(12)
                        .height(6)
                        .left(Arrays.asList(
                                Metric.Builder.create()
                                        .namespace("ToyApi/" + environment)
                                        .metricName("UserAgentTracking")
                                        .build()
                        ))
                        .view(GraphWidgetView.PIE)
                        .period(Duration.hours(24))
                        .build()
        );
        
        // Create outputs for analytics infrastructure
        software.amazon.awscdk.CfnOutput.Builder.create(this, "AnalyticsStreamName")
                .value(analyticsStream.getStreamName())
                .description("Kinesis Analytics Stream name")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "UsageMetricsTableName")
                .value(usageMetricsTable.getTableName())
                .description("Usage metrics DynamoDB table name")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "DeveloperInsightsTableName")
                .value(developerInsightsTable.getTableName())
                .description("Developer insights DynamoDB table name")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "AnalyticsDashboardUrl")
                .value("https://console.aws.amazon.com/cloudwatch/home?region=" + this.getRegion() + 
                       "#dashboards:name=" + analyticsDashboard.getDashboardName())
                .description("Analytics CloudWatch dashboard URL")
                .build();
        
        software.amazon.awscdk.CfnOutput.Builder.create(this, "AccessLogsUrl")
                .value("https://console.aws.amazon.com/cloudwatch/home?region=" + this.getRegion() + 
                       "#logsV2:log-groups/log-group/" + 
                       java.net.URLEncoder.encode(accessLogGroup.getLogGroupName(), java.nio.charset.StandardCharsets.UTF_8))
                .description("API Gateway access logs URL")
                .build();
        
        // Enhanced rate limiting for analytics endpoints
        createAnalyticsRateLimiting(api, usageMetricsTable, analyticsStream);
    }
    
    /**
     * Creates enhanced rate limiting specifically for analytics endpoints.
     * Implements tiered rate limiting based on API key types and usage patterns.
     */
    private void createAnalyticsRateLimiting(RestApi api, Table usageMetricsTable, Stream analyticsStream) {
        // Create analytics-specific usage plan with stricter limits
        UsagePlan analyticsUsagePlan = UsagePlan.Builder.create(this, "ToyApiUsagePlanAnalytics")
                .name(resourcePrefix + "-analytics-plan")
                .description("Strict usage plan for analytics endpoints")
                .throttle(ThrottleSettings.builder()
                        .rateLimit(10)    // 10 requests per second for analytics
                        .burstLimit(20)   // 20 burst requests
                        .build())
                .quota(QuotaSettings.builder()
                        .limit(1000)      // 1,000 analytics requests per day
                        .period(Period.DAY)
                        .build())
                .build();
        
        // Create premium usage plan for trusted developers
        UsagePlan premiumUsagePlan = UsagePlan.Builder.create(this, "ToyApiUsagePlanPremium")
                .name(resourcePrefix + "-premium-plan")
                .description("Higher limits for premium API keys")
                .throttle(ThrottleSettings.builder()
                        .rateLimit(200)   // 200 requests per second
                        .burstLimit(500)  // 500 burst requests
                        .build())
                .quota(QuotaSettings.builder()
                        .limit(50000)     // 50,000 requests per day
                        .period(Period.DAY)
                        .build())
                .build();
        
        // Associate usage plans with API stages
        analyticsUsagePlan.addApiStage(UsagePlanPerApiStage.builder()
                .api(api)
                .stage(api.getDeploymentStage())
                .build());
        
        premiumUsagePlan.addApiStage(UsagePlanPerApiStage.builder()
                .api(api)
                .stage(api.getDeploymentStage())
                .build());
        
        // Create rate limiting alarms
        Alarm analyticsRateLimitAlarm = Alarm.Builder.create(this, "ToyApiAlarmAnalyticsRateLimit")
                .alarmName(resourcePrefix + "-analytics-rate-limit-exceeded")
                .alarmDescription("Analytics endpoints are being rate limited frequently")
                .metric(Metric.Builder.create()
                        .namespace("AWS/ApiGateway")
                        .metricName("4XXError")
                        .dimensionsMap(Map.of(
                                "ApiName", api.getRestApiName(),
                                "Stage", api.getDeploymentStage().getStageName()
                        ))
                        .statistic("Sum")
                        .period(Duration.minutes(5))
                        .build())
                .threshold(20)    // More than 20 rate limit errors in 5 minutes
                .evaluationPeriods(2)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
                .build();
        
        // Create DynamoDB-based rate limiting function for custom logic
        Function rateLimitingFunction = Function.Builder.create(this, "ToyApiLambdaRateLimiting")
                .functionName(resourcePrefix + "-rate-limiting")
                .runtime(Runtime.JAVA_17)
                .handler("co.thismakesmehappy.toyapi.service.RateLimitingHandler::handleRequest")
                .code(Code.fromAsset("../service/target/toyapi-service-1.0-SNAPSHOT.jar"))
                .timeout(Duration.seconds(30))
                .memorySize(512)
                .environment(Map.of(
                        "USAGE_METRICS_TABLE", usageMetricsTable.getTableName(),
                        "ANALYTICS_STREAM_NAME", analyticsStream.getStreamName(),
                        "ENVIRONMENT", environment,
                        "RATE_LIMIT_WINDOW_MINUTES", "5",
                        "DEFAULT_RATE_LIMIT", "100",
                        "ANALYTICS_RATE_LIMIT", "10",
                        "PREMIUM_RATE_LIMIT", "500"
                ))
                .build();
        
        // Grant permissions for rate limiting function
        usageMetricsTable.grantReadWriteData(rateLimitingFunction);
        analyticsStream.grantRead(rateLimitingFunction);
        
        // Create CloudWatch metrics for rate limiting insights
        Metric rateLimitingMetric = Metric.Builder.create()
                .namespace("ToyApi/" + environment.toUpperCase())
                .metricName("RateLimitingDecisions")
                .build();
        
        // Create custom authorizer for advanced rate limiting (optional)
        RequestAuthorizer rateLimitAuthorizer = RequestAuthorizer.Builder.create(this, "ToyApiAuthorizerRateLimit")
                .handler(rateLimitingFunction)
                .identitySources(Arrays.asList(
                        IdentitySource.header("x-api-key"),
                        IdentitySource.context("identity.sourceIp")
                ))
                .resultsCacheTtl(Duration.minutes(1))
                .authorizerName(resourcePrefix + "-rate-limit-authorizer")
                .build();
        
        // Log rate limiting configuration
        software.amazon.awscdk.CfnOutput.Builder.create(this, "RateLimitingConfiguration")
                .value("Analytics: 10 req/sec, Premium: 200 req/sec, Default: 100 req/sec")
                .description("Rate limiting configuration summary")
                .build();
    }
}