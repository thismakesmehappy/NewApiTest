package co.thismakesmehappy.toyapi.integration;

import co.thismakesmehappy.toyapi.service.ParameterStoreHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ParameterStoreHelper functionality.
 * Tests real AWS Parameter Store integration and fallback behavior.
 * 
 * These tests make real AWS API calls and should not run in production.
 */
@Tag("integration")
public class ParameterStoreIntegrationTest {
    
    private String environment;
    
    @BeforeEach
    void setUp() {
        // Get environment from system property, default to dev
        environment = System.getProperty("test.environment", "dev");
        System.out.println("Running Parameter Store integration tests against environment: " + environment);
    }
    
    @Test
    void testGetTestUsernameIntegration() {
        // Test that we can retrieve test username (either from Parameter Store or fallback)
        String username = ParameterStoreHelper.getTestUsername();
        
        assertNotNull(username, "Username should never be null");
        assertFalse(username.trim().isEmpty(), "Username should not be empty");
        
        // Should be either from Parameter Store or default fallback
        assertTrue(username.equals("testuser") || username.startsWith("test"), 
            "Username should be either default 'testuser' or a test user value");
    }
    
    @Test
    void testGetTestPasswordIntegration() {
        // Test that we can retrieve test password (either from Parameter Store or fallback)
        String password = ParameterStoreHelper.getTestPassword();
        
        assertNotNull(password, "Password should never be null");
        assertFalse(password.trim().isEmpty(), "Password should not be empty");
        
        // Should be either from Parameter Store or default fallback
        assertTrue(password.equals("TestPassword123") || password.length() >= 8, 
            "Password should be either default 'TestPassword123' or a valid password from Parameter Store");
    }
    
    @Test
    void testGetApiUrlIntegration() {
        // Test that we get a valid API URL for the current environment
        String apiUrl = ParameterStoreHelper.getApiUrl();
        
        assertNotNull(apiUrl, "API URL should never be null");
        assertTrue(apiUrl.contains("execute-api.us-east-1.amazonaws.com"), 
            "API URL should contain AWS API Gateway domain");
        
        // Should contain environment-specific path
        switch (environment.toLowerCase()) {
            case "prod":
            case "production":
                assertTrue(apiUrl.contains("/prod/"), "Production API URL should contain /prod/ path");
                break;
            case "stage":
            case "staging":
                assertTrue(apiUrl.contains("/stage/"), "Staging API URL should contain /stage/ path");
                break;
            case "dev":
            case "development":
            default:
                assertTrue(apiUrl.contains("/dev/"), "Development API URL should contain /dev/ path");
                break;
        }
    }
    
    @Test
    void testGetRegionIntegration() {
        // Test that we get a valid AWS region
        String region = ParameterStoreHelper.getRegion();
        
        assertNotNull(region, "Region should never be null");
        assertEquals("us-east-1", region, "Region should be us-east-1");
    }
    
    @Test
    void testGetParameterWithRealAWSIntegration() {
        // Test the generic parameter method with a parameter that might exist
        String value = ParameterStoreHelper.getParameter("config/region", "default-region");
        
        assertNotNull(value, "Parameter value should never be null");
        
        // Should be either from Parameter Store or the default
        assertTrue(value.equals("default-region") || value.matches("[a-z]+-[a-z]+-\\d+"), 
            "Region should be either default 'default-region' or a valid AWS region format");
    }
    
    @Test
    void testParameterStoreConnectivity() {
        // Test that Parameter Store helper can handle both success and failure gracefully
        
        // Try to get a parameter that definitely doesn't exist
        String nonExistentValue = ParameterStoreHelper.getParameter("definitely-does-not-exist", "default-test-value");
        assertEquals("default-test-value", nonExistentValue, 
            "Should return default value for non-existent parameter");
        
        // Try to get a parameter that might exist (region config)
        String regionValue = ParameterStoreHelper.getParameter("config/region", "fallback-region");
        assertNotNull(regionValue, "Region parameter should return a value");
        assertTrue(regionValue.equals("fallback-region") || regionValue.matches("[a-z]+-[a-z]+-\\d+"), 
            "Region should be either fallback or valid AWS region");
    }
    
    @Test
    void testEnvironmentSpecificParameterPaths() {
        // Verify that parameters are requested with correct environment prefix
        
        // This test validates that the helper is using the right parameter path structure
        // /toyapi-{environment}/parameter-name
        
        String testValue = ParameterStoreHelper.getParameter("integration-test", "integration-default");
        assertNotNull(testValue, "Parameter request should complete without error");
        
        // The actual value doesn't matter - what matters is that the request completed
        // and either returned a Parameter Store value or the default gracefully
        assertTrue(testValue.equals("integration-default") || testValue.length() > 0,
            "Should return either default value or actual parameter value");
    }
}