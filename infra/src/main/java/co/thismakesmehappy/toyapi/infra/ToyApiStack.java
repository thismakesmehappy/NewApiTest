package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.budgets.CfnBudget;
import software.amazon.awscdk.services.cognito.*;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.sns.Topic;
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
        
        // Create API Gateway with API key management
        ApiKeyResources apiKeyResources = createApiKeyInfrastructure();
        
        // Create API Gateway first with placeholder for developer function
        RestApi api = createApiGatewayWithoutDeveloper(publicFunction, authFunction, itemsFunction, cognitoResources.userPool, apiKeyResources);
        
        // Now create developer function with API Gateway references
        Function developerFunction = createDeveloperLambdaWithReferences(itemsTable, api, apiKeyResources);
        
        // Add developer endpoints to the existing API
        addDeveloperEndpoints(api, developerFunction);
        
        // Create monitoring and budgets
        createBudgetMonitoring();
        
        // Output important information
        createOutputs(api, cognitoResources, itemsTable);
    }

    /**
     * Creates DynamoDB table for storing items with proper configuration for the environment.
     */
    private Table createDynamoDBTable() {
        Table.Builder tableBuilder = Table.Builder.create(this, "ItemsTable")
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
        UserPool userPool = UserPool.Builder.create(this, "UserPool")
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
        UserPoolClient userPoolClient = UserPoolClient.Builder.create(this, "UserPoolClient")
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
                "PublicFunction",
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
                "AuthFunction", 
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
                "ItemsFunction",
                "co.thismakesmehappy.toyapi.service.ItemsHandler", 
                "Handles items CRUD operations",
                itemsTable,
                userPool
        );
    }

    /**
     * Creates Lambda function for developer key management with API Gateway references.
     */
    private Function createDeveloperLambdaWithReferences(Table itemsTable, RestApi api, ApiKeyResources apiKeyResources) {
        // Create log group for the function
        LogGroup logGroup = LogGroup.Builder.create(this, "DeveloperFunctionLogGroup")
                .logGroupName("/aws/lambda/" + resourcePrefix + "-developerfunction")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Environment variables with API Gateway references
        Map<String, String> environment = new HashMap<>();
        environment.put("ENVIRONMENT", this.environment);
        environment.put("TABLE_NAME", itemsTable.getTableName());
        environment.put("REGION", "us-east-1");
        environment.put("REST_API_ID", api.getRestApiId());
        environment.put("USAGE_PLAN_ID", apiKeyResources.usagePlan.getUsagePlanId());

        Function function = Function.Builder.create(this, "DeveloperFunction")
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

        // Grant API Gateway permissions for API key management
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList(
                        "apigateway:POST",
                        "apigateway:GET",
                        "apigateway:DELETE",
                        "apigateway:PUT"
                ))
                .resources(Arrays.asList("arn:aws:apigateway:" + this.getRegion() + "::/apikeys*",
                                       "arn:aws:apigateway:" + this.getRegion() + "::/usageplans*"))
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

        return function;
    }

    /**
     * Creates API key infrastructure including usage plans and default API key.
     */
    private ApiKeyResources createApiKeyInfrastructure() {
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

        return new ApiKeyResources(usagePlan, defaultApiKey);
    }


    /**
     * Creates API Gateway REST API without developer endpoints to avoid circular dependency.
     */
    private RestApi createApiGatewayWithoutDeveloper(Function publicFunction, Function authFunction, 
                                                   Function itemsFunction, UserPool userPool, ApiKeyResources apiKeyResources) {
        
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

        // Public endpoints
        Resource publicResource = api.getRoot().addResource("public");
        publicResource.addResource("message")
                .addMethod("GET", new LambdaIntegration(publicFunction));

        // Auth endpoints  
        Resource authResource = api.getRoot().addResource("auth");
        authResource.addResource("login")
                .addMethod("POST", new LambdaIntegration(authFunction));
        authResource.addResource("message")
                .addMethod("GET", new LambdaIntegration(authFunction), 
                        MethodOptions.builder().authorizer(authorizer).build());

        // User-specific endpoint
        authResource.addResource("user")
                .addResource("{userId}")
                .addResource("message")
                .addMethod("GET", new LambdaIntegration(authFunction),
                        MethodOptions.builder().authorizer(authorizer).build());

        // Items endpoints (all authenticated)
        Resource itemsResource = api.getRoot().addResource("items");
        MethodOptions authMethodOptions = MethodOptions.builder().authorizer(authorizer).build();
        
        itemsResource.addMethod("GET", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemsResource.addMethod("POST", new LambdaIntegration(itemsFunction), authMethodOptions);
        
        Resource itemResource = itemsResource.addResource("{id}");
        itemResource.addMethod("GET", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemResource.addMethod("PUT", new LambdaIntegration(itemsFunction), authMethodOptions);
        itemResource.addMethod("DELETE", new LambdaIntegration(itemsFunction), authMethodOptions);

        // Associate usage plan with API stage
        apiKeyResources.usagePlan.addApiStage(UsagePlanPerApiStage.builder()
                .api(api)
                .stage(api.getDeploymentStage())
                .build());

        return api;
    }
    
    /**
     * Adds developer endpoints to the existing API Gateway
     */
    private void addDeveloperEndpoints(RestApi api, Function developerFunction) {
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
}