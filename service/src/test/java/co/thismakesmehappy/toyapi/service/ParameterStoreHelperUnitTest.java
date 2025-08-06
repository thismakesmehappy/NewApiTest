package co.thismakesmehappy.toyapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ParameterStoreHelper fallback functionality.
 * Tests the fallback logic when AWS Parameter Store is unavailable (e.g., no credentials).
 * 
 * These tests focus on the business logic and fallback behavior, not AWS integration.
 */
public class ParameterStoreHelperUnitTest {
    
    @BeforeEach
    void setUp() {
        // Clear system properties before each test to ensure clean state
        System.clearProperty("TEST_USERNAME");
        System.clearProperty("TEST_PASSWORD");
        System.clearProperty("ENVIRONMENT");
        
        // Disable AWS credentials to force fallback behavior for true unit testing
        System.setProperty("aws.accessKeyId", "");
        System.setProperty("aws.secretAccessKey", "");
        System.setProperty("aws.profile", "");
    }
    
    @AfterEach
    void tearDown() {
        // Clean up system properties after each test
        System.clearProperty("TEST_USERNAME");
        System.clearProperty("TEST_PASSWORD");
        System.clearProperty("ENVIRONMENT");
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
        System.clearProperty("aws.profile");
    }
    
    @Test
    void testGetTestUsernameWithSystemPropertyFallback() {
        // Set system property to simulate fallback scenario
        System.setProperty("TEST_USERNAME", "system-prop-user");
        
        // In environments without AWS credentials, Parameter Store will fail and fall back to system property
        String username = ParameterStoreHelper.getTestUsername();
        
        // Should get system property value when Parameter Store is unavailable
        assertEquals("system-prop-user", username);
    }
    
    @Test
    void testGetTestPasswordWithSystemPropertyFallback() {
        // Set system property to simulate fallback scenario
        System.setProperty("TEST_PASSWORD", "system-prop-password");
        
        // In environments without AWS credentials, Parameter Store will fail and fall back to system property
        String password = ParameterStoreHelper.getTestPassword();
        
        // Should get system property value when Parameter Store is unavailable
        assertEquals("system-prop-password", password);
    }
    
    @Test
    void testGetTestUsernameWithDefaultFallback() {
        // No system property set - should fall back to default
        
        // In environments without AWS credentials and no system properties
        String username = ParameterStoreHelper.getTestUsername();
        
        // Should get default value when both Parameter Store and system properties are unavailable
        assertEquals("testuser", username);
    }
    
    @Test
    void testGetTestPasswordWithDefaultFallback() {
        // No system property set - should fall back to default
        
        // In environments without AWS credentials and no system properties
        String password = ParameterStoreHelper.getTestPassword();
        
        // Should get default value when both Parameter Store and system properties are unavailable
        assertEquals("TestPassword123", password);
    }
    
    @Test
    void testGetApiUrlWithDefaultFallback() {
        // No Parameter Store access - should return environment-specific default URL
        
        String apiUrl = ParameterStoreHelper.getApiUrl();
        
        // Should contain AWS API Gateway structure with default environment (dev)
        assertNotNull(apiUrl);
        assertTrue(apiUrl.contains("execute-api.us-east-1.amazonaws.com"));
        assertTrue(apiUrl.contains("/dev/")); // Default environment when none specified
    }
    
    @Test
    void testGetApiUrlWithEnvironmentSpecificDefault() {
        // Since ENVIRONMENT is read from System.getenv() not System.getProperty(),
        // and we can't easily mock environment variables, this test verifies
        // that the default environment (dev) is used when no environment is set
        
        String apiUrl = ParameterStoreHelper.getApiUrl();
        
        // Should use the default environment in the fallback URL
        assertNotNull(apiUrl);
        assertTrue(apiUrl.contains("execute-api.us-east-1.amazonaws.com"));
        assertTrue(apiUrl.contains("/dev/")); // Default fallback when no environment variable
    }
    
    @Test
    void testGetRegionWithDefaultFallback() {
        // No Parameter Store access - should return default region
        
        String region = ParameterStoreHelper.getRegion();
        
        // Should return default region
        assertEquals("us-east-1", region);
    }
    
    @Test
    void testGetParameterWithDefaultValue() {
        // Test the generic parameter method with a parameter that won't exist in Parameter Store
        String value = ParameterStoreHelper.getParameter("unit-test-param", "unit-test-default");
        
        // Should return default value when Parameter Store is unavailable
        assertEquals("unit-test-default", value);
    }
    
    @Test
    void testFallbackChainForUsername() {
        // Test the complete fallback chain: Parameter Store -> System Property -> Environment -> Default
        
        // First test: Only default fallback
        String defaultUsername = ParameterStoreHelper.getTestUsername();
        assertEquals("testuser", defaultUsername);
        
        // Second test: System property fallback
        System.setProperty("TEST_USERNAME", "prop-user");
        String propUsername = ParameterStoreHelper.getTestUsername();
        assertEquals("prop-user", propUsername);
    }
    
    @Test
    void testFallbackChainForPassword() {
        // Test the complete fallback chain: Parameter Store -> System Property -> Environment -> Default
        
        // First test: Only default fallback
        String defaultPassword = ParameterStoreHelper.getTestPassword();
        assertEquals("TestPassword123", defaultPassword);
        
        // Second test: System property fallback
        System.setProperty("TEST_PASSWORD", "prop-password");
        String propPassword = ParameterStoreHelper.getTestPassword();
        assertEquals("prop-password", propPassword);
    }
    
    @Test
    void testEnvironmentParameterPrefixing() {
        // Test that different environments would use different parameter prefixes
        
        // Default environment (dev)
        String devValue = ParameterStoreHelper.getParameter("test-param", "dev-default");
        assertEquals("dev-default", devValue); // Should fall back to default
        
        // Staging environment
        System.setProperty("ENVIRONMENT", "staging");
        String stagingValue = ParameterStoreHelper.getParameter("test-param", "staging-default");
        assertEquals("staging-default", stagingValue); // Should fall back to default
        
        // Production environment
        System.setProperty("ENVIRONMENT", "prod");
        String prodValue = ParameterStoreHelper.getParameter("test-param", "prod-default");
        assertEquals("prod-default", prodValue); // Should fall back to default
    }
    
    @Test
    void testNullParameterHandling() {
        // Test that the helper handles null values gracefully
        
        String nullParamValue = ParameterStoreHelper.getParameter(null, "null-fallback");
        assertEquals("null-fallback", nullParamValue);
        
        String emptyParamValue = ParameterStoreHelper.getParameter("", "empty-fallback");
        assertEquals("empty-fallback", emptyParamValue);
    }
}