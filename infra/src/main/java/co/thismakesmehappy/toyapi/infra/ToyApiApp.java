package co.thismakesmehappy.toyapi.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * CDK Application entry point for ToyApi infrastructure.
 * 
 * This application creates infrastructure stacks for different environments:
 * - dev: Development environment for testing
 * - stage: Staging environment for pre-production validation  
 * - prod: Production environment for live traffic
 */
public class ToyApiApp {
    public static void main(final String[] args) {
        App app = new App();

        // Get environment from context or default to 'dev'
        String environment = (String) app.getNode().tryGetContext("environment");
        if (environment == null) {
            environment = "dev";
        }

        // AWS environment configuration
        Environment awsEnvironment = Environment.builder()
                .account("375004071203")  // Your AWS account ID
                .region("us-east-1")      // All environments in us-east-1
                .build();

        // Create stack with environment-specific naming
        String stackName = "ToyApiStack-" + environment;
        
        ToyApiStack stack = new ToyApiStack(app, stackName, StackProps.builder()
                .env(awsEnvironment)
                .description("ToyApi serverless infrastructure for " + environment + " environment")
                .build(), environment);

        // Add tags to all resources
        software.amazon.awscdk.Tags.of(stack).add("Project", "ToyApi");
        software.amazon.awscdk.Tags.of(stack).add("Environment", environment);
        software.amazon.awscdk.Tags.of(stack).add("Owner", "thismakesmehappy");
        software.amazon.awscdk.Tags.of(stack).add("CostCenter", "ToyApi-" + environment);

        app.synth();
    }
}