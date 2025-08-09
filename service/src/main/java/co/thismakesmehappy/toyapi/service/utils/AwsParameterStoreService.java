package co.thismakesmehappy.toyapi.service.utils;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * AWS Systems Manager Parameter Store implementation.
 * Provides secure access to test credentials and configuration values.
 */
public class AwsParameterStoreService implements ParameterStoreService {
    
    private static final Logger logger = Logger.getLogger(AwsParameterStoreService.class.getName());
    
    private final SsmClient ssmClient;
    private final String environment;
    private final String parameterPrefix;
    
    public AwsParameterStoreService(SsmClient ssmClient) {
        this.ssmClient = ssmClient;
        this.environment = System.getenv("ENVIRONMENT");
        this.parameterPrefix = "/toyapi-" + (environment != null ? environment : "dev");
    }
    
    /**
     * Retrieves a parameter value from Parameter Store.
     * 
     * @param parameterName The parameter name (without prefix)
     * @param defaultValue The default value to return if parameter is not found
     * @return The parameter value or default value
     */
    @Override
    public String getParameter(String parameterName, String defaultValue) {
        try {
            String fullParameterName = parameterPrefix + "/" + parameterName;
            
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(fullParameterName)
                    .withDecryption(true)  // Decrypt if it's a SecureString
                    .build();
            
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
            
        } catch (SsmException e) {
            // Log warning but don't fail - return default value
            logger.log(Level.WARNING, "Could not retrieve parameter {0}: {1}", new Object[]{parameterName, e.getMessage()});
            return defaultValue;
        } catch (Exception e) {
            // For any other exception, log and return default
            logger.log(Level.SEVERE, "Error accessing Parameter Store for parameter {0}: {1}", new Object[]{parameterName, e.getMessage()});
            return defaultValue;
        }
    }
    
    /**
     * Gets the test username from configuration.
     * Falls back to environment variable (using System.getProperty for testing), then default value.
     * 
     * @return The test username
     */
    @Override
    public String getTestUsername() {
        // First try Parameter Store
        String username = getParameter("test-credentials/username", null);
        
        // Fall back to system property (for testing)
        if (username == null) {
            username = System.getProperty("TEST_USERNAME");
        }
        
        // Fall back to environment variable
        if (username == null) {
            username = System.getenv("TEST_USERNAME");
        }
        
        // Final fallback to default
        if (username == null) {
            username = "testuser";
        }
        
        return username;
    }
    
    /**
     * Gets the test password from configuration.
     * Falls back to environment variable (using System.getProperty for testing), then default value.
     * 
     * @return The test password
     */
    @Override
    public String getTestPassword() {
        // First try Parameter Store
        String password = getParameter("test-credentials/password", null);
        
        // Fall back to system property (for testing)
        if (password == null) {
            password = System.getProperty("TEST_PASSWORD");
        }
        
        // Fall back to environment variable
        if (password == null) {
            password = System.getenv("TEST_PASSWORD");
        }
        
        // Final fallback to default
        if (password == null) {
            password = "TestPassword123";
        }
        
        return password;
    }
    
    /**
     * Gets the API base URL from configuration.
     * 
     * @return The API base URL
     */
    @Override
    public String getApiUrl() {
        String env = environment != null ? environment : "dev";
        return getParameter("config/api-url", "https://placeholder.execute-api.us-east-1.amazonaws.com/" + env + "/");
    }
    
    /**
     * Gets the AWS region from configuration.
     * 
     * @return The AWS region
     */
    @Override
    public String getRegion() {
        return getParameter("config/region", "us-east-1");
    }
}