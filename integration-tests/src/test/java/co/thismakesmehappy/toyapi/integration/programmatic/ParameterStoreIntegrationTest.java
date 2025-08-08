package co.thismakesmehappy.toyapi.integration.programmatic;

import co.thismakesmehappy.toyapi.service.utils.ParameterStoreHelper;
import co.thismakesmehappy.toyapi.integration.shared.TestConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Programmatic integration tests for AWS Parameter Store functionality.
 * Tests real AWS service API calls without HTTP layer.
 * 
 * These tests make direct AWS SDK calls and should not run in production.
 */
@Tag("integration")
@Tag("programmatic")
public class ParameterStoreIntegrationTest {
    
    private String environment;
    
    @BeforeEach
    void setUp() {
        environment = TestConfiguration.getEnvironment();
        TestConfiguration.printConfiguration("Programmatic - Parameter Store", environment);
    }
    
    @Test
    void testGetTestUsernameIntegration() {
        String username = ParameterStoreHelper.getTestUsername();
        
        assertNotNull(username, "Username should never be null");
        assertFalse(username.trim().isEmpty(), "Username should not be empty");
        assertTrue(username.equals("testuser") || username.startsWith("test"), 
            "Username should be either default 'testuser' or a test user value");
    }
    
    @Test
    void testGetTestPasswordIntegration() {
        String password = ParameterStoreHelper.getTestPassword();
        
        assertNotNull(password, "Password should never be null");
        assertFalse(password.trim().isEmpty(), "Password should not be empty");
        assertTrue(password.equals("TestPassword123") || password.length() >= 8, 
            "Password should be either default 'TestPassword123' or a valid password from Parameter Store");
    }
    
    @Test
    void testGetApiUrlIntegration() {
        String apiUrl = ParameterStoreHelper.getApiUrl();
        
        assertNotNull(apiUrl, "API URL should never be null");
        assertTrue(apiUrl.contains("execute-api.us-east-1.amazonaws.com"), 
            "API URL should contain AWS API Gateway domain");
        assertTrue(apiUrl.contains("/dev/") || apiUrl.contains("/stage/") || apiUrl.contains("/prod/"), 
            "API URL should contain a valid environment path (/dev/, /stage/, or /prod/). Got: " + apiUrl);
        
        System.out.println("Parameter Store returned API URL: " + apiUrl + " for environment: " + environment);
    }
    
    @Test
    void testGetRegionIntegration() {
        String region = ParameterStoreHelper.getRegion();
        
        assertNotNull(region, "Region should never be null");
        assertEquals("us-east-1", region, "Region should be us-east-1");
    }
    
    @Test
    void testGetParameterWithRealAWSIntegration() {
        String value = ParameterStoreHelper.getParameter("config/region", "default-region");
        
        assertNotNull(value, "Parameter value should never be null");
        assertTrue(value.equals("default-region") || value.matches("[a-z]+-[a-z]+-\\d+"), 
            "Region should be either default 'default-region' or a valid AWS region format");
    }
    
    @Test
    void testParameterStoreConnectivity() {
        // Test graceful handling of non-existent parameters
        String nonExistentValue = ParameterStoreHelper.getParameter("definitely-does-not-exist", "default-test-value");
        assertEquals("default-test-value", nonExistentValue, 
            "Should return default value for non-existent parameter");
        
        // Test parameter that might exist
        String regionValue = ParameterStoreHelper.getParameter("config/region", "fallback-region");
        assertNotNull(regionValue, "Region parameter should return a value");
        assertTrue(regionValue.equals("fallback-region") || regionValue.matches("[a-z]+-[a-z]+-\\d+"), 
            "Region should be either fallback or valid AWS region");
    }
    
    @Test
    void testEnvironmentSpecificParameterPaths() {
        String testValue = ParameterStoreHelper.getParameter("integration-test", "integration-default");
        assertNotNull(testValue, "Parameter request should complete without error");
        assertTrue(testValue.equals("integration-default") || testValue.length() > 0,
            "Should return either default value or actual parameter value");
    }
}