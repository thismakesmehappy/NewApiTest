// CDK v2.91.0 RequestAuthorizer Complete Implementation Example
// This shows the correct pattern for using RequestAuthorizer in CDK v2

import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.Duration;
import java.util.Arrays;

public class CDKv2RequestAuthorizerExample {
    
    private void createRequestAuthorizerExample(RestApi api, Function authorizerFunction) {
        
        // 1. Create the RequestAuthorizer with proper CDK v2 pattern
        RequestAuthorizer requestAuthorizer = RequestAuthorizer.Builder.create(this, "CustomRequestAuthorizer")
                .handler(authorizerFunction)
                .identitySources(Arrays.asList(
                        IdentitySource.header("x-api-key"),
                        IdentitySource.context("identity.sourceIp"),
                        IdentitySource.queryString("token")  // Optional: additional sources
                ))
                .resultsCacheTtl(Duration.minutes(5))  // Cache for better performance
                .authorizerName("custom-request-authorizer")
                .build();
        
        // 2. Create MethodOptions that use the authorizer
        MethodOptions authorizedMethodOptions = MethodOptions.builder()
                .authorizer(requestAuthorizer)
                .build();
        
        // 3. Apply the authorizer to specific API methods
        Resource protectedResource = api.getRoot().addResource("protected");
        
        // Apply to GET method
        protectedResource.addMethod("GET", 
                LambdaIntegration.Builder.create(someFunction).build(),
                authorizedMethodOptions);
        
        // Apply to POST method  
        protectedResource.addMethod("POST",
                LambdaIntegration.Builder.create(someFunction).build(), 
                authorizedMethodOptions);
                
        // 4. Optional: Create a resource that requires the authorizer
        Resource rateLimitedResource = api.getRoot().addResource("rate-limited");
        rateLimitedResource.addMethod("GET",
                LambdaIntegration.Builder.create(someFunction).build(),
                authorizedMethodOptions);
    }
    
    // Lambda Authorizer Function Structure (for reference)
    private void createAuthorizerLambda() {
        /*
        Your Lambda authorizer function should return a policy document like:
        
        {
            "principalId": "user123",
            "policyDocument": {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Action": "execute-api:Invoke",
                        "Effect": "Allow",  // or "Deny"
                        "Resource": "arn:aws:execute-api:region:account:api-id/stage/method/resource"
                    }
                ]
            },
            "context": {
                "rateLimitExceeded": "false",
                "requestsRemaining": "95"
            }
        }
        */
    }
}