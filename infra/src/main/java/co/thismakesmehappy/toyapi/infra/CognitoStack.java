package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cognito.*;
import software.constructs.Construct;

/**
 * CognitoStack - Manages Cognito User Pool and authentication infrastructure
 */
public class CognitoStack extends Stack {
    
    private final String environment;
    private final String resourcePrefix;
    private final CognitoResources cognitoResources;
    
    /**
     * Container for Cognito resources
     */
    public static class CognitoResources {
        public final UserPool userPool;
        public final UserPoolClient userPoolClient;
        
        public CognitoResources(UserPool userPool, UserPoolClient userPoolClient) {
            this.userPool = userPool;
            this.userPoolClient = userPoolClient;
        }
    }
    
    public CognitoStack(final Construct scope, final String id, final StackProps props, final String environment) {
        super(scope, id, props);
        
        this.environment = environment;
        this.resourcePrefix = "toyapi-" + environment;
        
        // Create Cognito User Pool for authentication
        this.cognitoResources = createCognitoUserPool();
        
        // Create outputs
        createOutputs();
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
     * Creates CloudFormation outputs for Cognito resources.
     */
    private void createOutputs() {
        software.amazon.awscdk.CfnOutput.Builder.create(this, "UserPoolId")
                .value(cognitoResources.userPool.getUserPoolId())
                .description("Cognito User Pool ID")
                .exportName(resourcePrefix + "-user-pool-id")
                .build();
                
        software.amazon.awscdk.CfnOutput.Builder.create(this, "UserPoolClientId")
                .value(cognitoResources.userPoolClient.getUserPoolClientId())
                .description("Cognito User Pool Client ID")
                .exportName(resourcePrefix + "-user-pool-client-id")
                .build();
    }
    
    // Getters for other stacks
    public CognitoResources getCognitoResources() {
        return cognitoResources;
    }
    
    public String getResourcePrefix() {
        return resourcePrefix;
    }
}