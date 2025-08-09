package co.thismakesmehappy.toyapi.service.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Mock implementation of ParameterStoreService for unit testing.
 * Allows setting predefined values without AWS dependencies.
 */
public class MockParameterStoreService implements ParameterStoreService {
    
    private final Map<String, String> parameters = new HashMap<>();
    private String testUsername = "mockuser";
    private String testPassword = "mockpass";
    private String apiUrl = "https://mock-api.example.com/test/";
    private String region = "us-east-1";
    
    /**
     * Sets a parameter value for testing.
     * 
     * @param parameterName The parameter name
     * @param value The parameter value
     */
    public void setParameter(String parameterName, String value) {
        parameters.put(parameterName, value);
    }
    
    /**
     * Sets the test username.
     * 
     * @param username The test username
     */
    public void setTestUsername(String username) {
        this.testUsername = username;
    }
    
    /**
     * Sets the test password.
     * 
     * @param password The test password
     */
    public void setTestPassword(String password) {
        this.testPassword = password;
    }
    
    /**
     * Sets the API URL.
     * 
     * @param apiUrl The API URL
     */
    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }
    
    /**
     * Sets the region.
     * 
     * @param region The AWS region
     */
    public void setRegion(String region) {
        this.region = region;
    }
    
    @Override
    public String getParameter(String parameterName, String defaultValue) {
        return parameters.getOrDefault(parameterName, defaultValue);
    }
    
    @Override
    public String getTestUsername() {
        return testUsername;
    }
    
    @Override
    public String getTestPassword() {
        return testPassword;
    }
    
    @Override
    public String getApiUrl() {
        return apiUrl;
    }
    
    @Override
    public String getRegion() {
        return region;
    }
}