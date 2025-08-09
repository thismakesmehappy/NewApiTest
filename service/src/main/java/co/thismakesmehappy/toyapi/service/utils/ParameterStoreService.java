package co.thismakesmehappy.toyapi.service.utils;

/**
 * Interface for accessing configuration parameters from various sources.
 * Supports dependency injection and easy mocking for unit tests.
 */
public interface ParameterStoreService {
    
    /**
     * Retrieves a parameter value.
     * 
     * @param parameterName The parameter name (without prefix)
     * @param defaultValue The default value to return if parameter is not found
     * @return The parameter value or default value
     */
    String getParameter(String parameterName, String defaultValue);
    
    /**
     * Gets the test username from configuration.
     * 
     * @return The test username
     */
    String getTestUsername();
    
    /**
     * Gets the test password from configuration.
     * 
     * @return The test password
     */
    String getTestPassword();
    
    /**
     * Gets the API base URL from configuration.
     * 
     * @return The API base URL
     */
    String getApiUrl();
    
    /**
     * Gets the AWS region from configuration.
     * 
     * @return The AWS region
     */
    String getRegion();
}