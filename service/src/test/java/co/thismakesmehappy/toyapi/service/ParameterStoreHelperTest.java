package co.thismakesmehappy.toyapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ParameterStoreHelper functionality.
 * Tests both Parameter Store integration and fallback behavior.
 */
public class ParameterStoreHelperTest {
    
    @BeforeEach
    void setUp() {
        // Clear environment variables before each test
        System.clearProperty("TEST_USERNAME");
        System.clearProperty("TEST_PASSWORD");
        System.clearProperty("ENVIRONMENT");
    }
    
    @AfterEach
    void tearDown() {
        // Clean up after each test
        System.clearProperty("TEST_USERNAME");
        System.clearProperty("TEST_PASSWORD");
        System.clearProperty("ENVIRONMENT");
    }
    
    @Test
    void testGetTestUsernameWithEnvironmentFallback() {
        // Set environment variable
        System.setProperty("TEST_USERNAME", "testuser-from-env");
        
        // Should get value from environment variable when Parameter Store is not available
        String username = ParameterStoreHelper.getTestUsername();
        assertEquals("testuser-from-env", username);
    }
    
    @Test
    void testGetTestPasswordWithEnvironmentFallback() {
        // Set environment variable
        System.setProperty("TEST_PASSWORD", "password-from-env");
        
        // Should get value from environment variable when Parameter Store is not available
        String password = ParameterStoreHelper.getTestPassword();
        assertEquals("password-from-env", password);
    }
    
    @Test
    void testGetTestUsernameWithDefaultFallback() {
        // No environment variables set
        
        // Should get default value when both Parameter Store and environment are not available
        String username = ParameterStoreHelper.getTestUsername();
        assertEquals("testuser", username);
    }
    
    @Test
    void testGetTestPasswordWithDefaultFallback() {
        // No environment variables set
        
        // Should get default value when both Parameter Store and environment are not available
        String password = ParameterStoreHelper.getTestPassword();
        assertEquals("TestPassword123", password);
    }
    
    @Test
    void testGetApiUrlWithDefault() {
        // Should return default API URL pattern when Parameter Store is not available
        String apiUrl = ParameterStoreHelper.getApiUrl();
        assertTrue(apiUrl.contains("execute-api.us-east-1.amazonaws.com"));
        assertTrue(apiUrl.contains("/dev/") || apiUrl.contains("/stage/") || apiUrl.contains("/prod/"));
    }
    
    @Test
    void testGetRegionWithDefault() {
        // Should return default region when Parameter Store is not available
        String region = ParameterStoreHelper.getRegion();
        assertEquals("us-east-1", region);
    }
    
    @Test
    void testGetParameterWithDefault() {
        // Test the generic parameter method with a non-existent parameter
        String value = ParameterStoreHelper.getParameter("non-existent/parameter", "default-value");
        assertEquals("default-value", value);
    }
    
    @Test
    void testParameterStoreHelperWithDifferentEnvironments() {
        // Test with different environment settings
        System.setProperty("ENVIRONMENT", "test");
        
        // Should still return valid defaults
        String username = ParameterStoreHelper.getTestUsername();
        String password = ParameterStoreHelper.getTestPassword();
        
        assertNotNull(username);
        assertNotNull(password);
        assertEquals("testuser", username);
        assertEquals("TestPassword123", password);
    }
}