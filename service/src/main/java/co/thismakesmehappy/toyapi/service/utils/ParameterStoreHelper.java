package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Helper class for accessing AWS Systems Manager Parameter Store.
 * Provides secure access to test credentials and configuration values.
 * 
 * @deprecated Use {@link ParameterStoreService} for dependency injection instead.
 * This class is maintained for backward compatibility.
 */
@Deprecated
public class ParameterStoreHelper {
    
    private static ParameterStoreService parameterStoreService;
    
    /**
     * Gets the Parameter Store service instance, creating it if needed.
     * Uses lazy initialization with AWS SSM client.
     */
    private static synchronized ParameterStoreService getParameterStoreService() {
        if (parameterStoreService == null) {
            SsmClient ssmClient = SsmClient.builder()
                    .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                    .build();
            parameterStoreService = new AwsParameterStoreService(ssmClient);
        }
        return parameterStoreService;
    }
    
    /**
     * Sets the Parameter Store service for testing.
     * 
     * @param service The Parameter Store service to use
     */
    public static void setParameterStoreService(ParameterStoreService service) {
        parameterStoreService = service;
    }
    
    /**
     * Retrieves a parameter value from Parameter Store.
     * 
     * @param parameterName The parameter name (without prefix)
     * @param defaultValue The default value to return if parameter is not found
     * @return The parameter value or default value
     */
    public static String getParameter(String parameterName, String defaultValue) {
        return getParameterStoreService().getParameter(parameterName, defaultValue);
    }
    
    /**
     * Gets the test username from Parameter Store.
     * Falls back to environment variable (using System.getProperty for testing), then default value.
     * 
     * @return The test username
     */
    public static String getTestUsername() {
        return getParameterStoreService().getTestUsername();
    }
    
    /**
     * Gets the test password from Parameter Store.
     * Falls back to environment variable (using System.getProperty for testing), then default value.
     * 
     * @return The test password
     */
    public static String getTestPassword() {
        return getParameterStoreService().getTestPassword();
    }
    
    /**
     * Gets the API base URL from Parameter Store.
     * 
     * @return The API base URL
     */
    public static String getApiUrl() {
        return getParameterStoreService().getApiUrl();
    }
    
    /**
     * Gets the region from Parameter Store.
     * 
     * @return The AWS region
     */
    public static String getRegion() {
        return getParameterStoreService().getRegion();
    }
}