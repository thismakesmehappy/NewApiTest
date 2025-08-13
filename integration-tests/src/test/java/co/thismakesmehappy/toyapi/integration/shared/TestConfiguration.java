package co.thismakesmehappy.toyapi.integration.shared;

/**
 * Shared configuration for integration tests across different test types.
 * Provides consistent environment setup and URL resolution.
 */
public class TestConfiguration {
    
    /**
     * Get the current test environment from system property
     */
    public static String getEnvironment() {
        return System.getProperty("test.environment", "dev");
    }
    
    /**
     * Get AWS API Gateway URL for the environment
     */
    public static String getAwsApiUrl(String environment) {
        switch (environment.toLowerCase()) {
            case "prod":
            case "production":
                return "https://skslof01gg.execute-api.us-east-1.amazonaws.com/prod";
            case "stage":
            case "staging":
                return "https://5ytzml6fnb.execute-api.us-east-1.amazonaws.com/stage";
            case "dev":
            case "development":
            default:
                return "https://785sk4gpbh.execute-api.us-east-1.amazonaws.com/dev";
        }
    }
    
    /**
     * Get custom domain URL for the environment (when custom domains are enabled)
     */
    public static String getCustomApiUrl(String environment) {
        switch (environment.toLowerCase()) {
            case "prod":
            case "production":
                return "https://toyapi.thismakesmehappy.co";
            case "stage":
            case "staging":
                return "https://stage.toyapi.thismakesmehappy.co";
            case "dev":
            case "development":
            default:
                return "https://dev.toyapi.thismakesmehappy.co";
        }
    }
    
    /**
     * Check if custom domains are available for testing
     */
    public static boolean areCustomDomainsAvailable() {
        return Boolean.parseBoolean(System.getProperty("test.custom.domains.enabled", "false"));
    }
    
    /**
     * Print test configuration info
     */
    public static void printConfiguration(String testType, String environment) {
        System.out.println("==========================================");
        System.out.println("Integration Test Configuration");
        System.out.println("==========================================");
        System.out.println("Test Type: " + testType);
        System.out.println("Environment: " + environment);
        System.out.println("AWS API URL: " + getAwsApiUrl(environment));
        System.out.println("Custom API URL: " + getCustomApiUrl(environment));
        System.out.println("Custom Domains Available: " + areCustomDomainsAvailable());
        System.out.println("==========================================");
    }
}