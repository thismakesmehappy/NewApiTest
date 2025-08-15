package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.dynamodb.ITable;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * LambdaStack - Manages Lambda functions for API endpoints
 */
public class LambdaStack extends Stack {
    
    private final String environment;
    private final String resourcePrefix;
    private final Function publicFunction;
    private final Function authFunction;
    private final Function itemsFunction;
    private final Function developerFunction;
    
    public LambdaStack(final Construct scope, final String id, final StackProps props, 
                      final String environment, final ITable itemsTable, 
                      final CognitoStack.CognitoResources cognitoResources) {
        super(scope, id, props);
        
        this.environment = environment;
        this.resourcePrefix = "toyapi-" + environment;
        
        // Create Lambda functions
        this.publicFunction = createPublicLambda(itemsTable);
        this.authFunction = createAuthLambda(itemsTable, cognitoResources);
        this.itemsFunction = createItemsLambda(itemsTable, cognitoResources.userPool);
        this.developerFunction = createDeveloperLambdaIndependent(itemsTable);
    }
    
    /**
     * Creates Lambda function for public endpoints.
     */
    private Function createPublicLambda(ITable itemsTable) {
        return createLambdaFunction(
                "ToyApiLambdaPublic",
                "co.thismakesmehappy.toyapi.service.handlers.PublicHandler",
                "Handles public API endpoints",
                itemsTable,
                null
        );
    }

    /**
     * Creates Lambda function for authentication endpoints.
     */
    private Function createAuthLambda(ITable itemsTable, CognitoStack.CognitoResources cognitoResources) {
        return createLambdaFunctionWithClient(
                "ToyApiLambdaAuth", 
                "co.thismakesmehappy.toyapi.service.handlers.AuthHandler",
                "Handles authentication endpoints",
                itemsTable,
                cognitoResources
        );
    }

    /**
     * Creates Lambda function for items CRUD operations.
     */
    private Function createItemsLambda(ITable itemsTable, UserPool userPool) {
        return createLambdaFunction(
                "ToyApiLambdaItems",
                "co.thismakesmehappy.toyapi.service.handlers.ItemsHandler", 
                "Handles items CRUD operations",
                itemsTable,
                userPool
        );
    }

    /**
     * Creates Lambda function for developer key management that works independently of API Gateway.
     * Uses AWS API calls instead of CDK references to avoid circular dependencies.
     */
    private Function createDeveloperLambdaIndependent(ITable itemsTable) {
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
                .handler("co.thismakesmehappy.toyapi.service.handlers.DeveloperHandler")
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
                                         ITable itemsTable, UserPool userPool) {
        
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
                .timeout(getOptimizedTimeout(functionName))
                .memorySize(getOptimizedMemorySize(functionName))  // Performance-optimized memory allocation
                .description(description + " (" + this.environment + ")")
                .environment(environment)
                .build();

        // Grant DynamoDB permissions
        itemsTable.grantReadWriteData(function);
        
        // Grant permissions to query GSI (Global Secondary Index)
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("dynamodb:Query"))
                .resources(Arrays.asList(
                        itemsTable.getTableArn() + "/index/*"  // Grant access to all GSIs
                ))
                .build());

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
                                         ITable itemsTable, CognitoStack.CognitoResources cognitoResources) {
        
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
        
        // Grant permissions to query GSI (Global Secondary Index)
        function.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Arrays.asList("dynamodb:Query"))
                .resources(Arrays.asList(
                        itemsTable.getTableArn() + "/index/*"  // Grant access to all GSIs
                ))
                .build());

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
    
    // Getters for other stacks
    public Function getPublicFunction() {
        return publicFunction;
    }
    
    public Function getAuthFunction() {
        return authFunction;
    }
    
    public Function getItemsFunction() {
        return itemsFunction;
    }
    
    public Function getDeveloperFunction() {
        return developerFunction;
    }
    
    /**
     * Get optimized memory size based on function type and performance requirements.
     */
    private int getOptimizedMemorySize(String functionName) {
        String lowerName = functionName.toLowerCase();
        
        // High-memory functions for complex operations
        if (lowerName.contains("analytics") || lowerName.contains("report")) {
            return 1024;  // Analytics need more memory for data processing
        }
        
        // Medium-memory functions for moderate complexity
        if (lowerName.contains("items") || lowerName.contains("auth")) {
            return 768;   // API operations with caching and optimization
        }
        
        // Low-memory functions for simple operations
        if (lowerName.contains("public") || lowerName.contains("health")) {
            return 512;   // Simple public endpoints
        }
        
        // Default optimized memory for general functions
        return 640;  // Balanced performance for most operations
    }
    
    /**
     * Get optimized timeout based on function type.
     */
    private Duration getOptimizedTimeout(String functionName) {
        String lowerName = functionName.toLowerCase();
        
        // Longer timeouts for complex operations
        if (lowerName.contains("analytics") || lowerName.contains("report")) {
            return Duration.seconds(45);  // Analytics may need more time
        }
        
        // Medium timeouts for database operations
        if (lowerName.contains("items") || lowerName.contains("auth")) {
            return Duration.seconds(20);  // Most API operations should be fast
        }
        
        // Short timeouts for simple operations
        if (lowerName.contains("public") || lowerName.contains("health")) {
            return Duration.seconds(10);  // Public endpoints should be very fast
        }
        
        // Default timeout
        return Duration.seconds(30);
    }
    
    public String getResourcePrefix() {
        return resourcePrefix;
    }
}