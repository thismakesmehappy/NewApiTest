package co.thismakesmehappy.toyapi.service.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ParameterStoreService implementation.
 * Demonstrates the improved testability with dependency injection.
 */
class ParameterStoreServiceTest {

    private MockParameterStoreService mockService;
    private ParameterStoreService originalService;

    @BeforeEach
    void setUp() {
        // Create mock service for testing
        mockService = new MockParameterStoreService();
        
        // Store original service for cleanup
        originalService = null; // Will be set by ParameterStoreHelper if needed
        
        // Set mock service in ParameterStoreHelper for backward compatibility tests
        ParameterStoreHelper.setParameterStoreService(mockService);
    }

    @AfterEach
    void tearDown() {
        // Reset to original service if it was set
        if (originalService != null) {
            ParameterStoreHelper.setParameterStoreService(originalService);
        } else {
            ParameterStoreHelper.setParameterStoreService(null);
        }
    }

    @Test
    void testMockParameterStoreService() {
        // Test direct mock service usage
        mockService.setParameter("test-param", "test-value");
        
        assertEquals("test-value", mockService.getParameter("test-param", "default"));
        assertEquals("default", mockService.getParameter("non-existent", "default"));
    }

    @Test
    void testMockServiceCredentialMethods() {
        // Test credential methods
        mockService.setTestUsername("mockuser");
        mockService.setTestPassword("mockpass");
        mockService.setApiUrl("https://mock.example.com/api/");
        mockService.setRegion("us-west-2");
        
        assertEquals("mockuser", mockService.getTestUsername());
        assertEquals("mockpass", mockService.getTestPassword());
        assertEquals("https://mock.example.com/api/", mockService.getApiUrl());
        assertEquals("us-west-2", mockService.getRegion());
    }

    @Test
    void testParameterStoreHelperWithMock() {
        // Test that ParameterStoreHelper uses our injected mock
        mockService.setParameter("config/test-param", "injected-value");
        mockService.setTestUsername("injected-user");
        mockService.setTestPassword("injected-pass");
        
        assertEquals("injected-value", ParameterStoreHelper.getParameter("config/test-param", "default"));
        assertEquals("injected-user", ParameterStoreHelper.getTestUsername());
        assertEquals("injected-pass", ParameterStoreHelper.getTestPassword());
    }

    @Test
    void testDependencyInjectionIsolation() {
        // Test that changes to mock don't affect other tests
        mockService.setTestUsername("isolated-user");
        assertEquals("isolated-user", ParameterStoreHelper.getTestUsername());
        
        // Create a new mock to simulate test isolation
        MockParameterStoreService newMock = new MockParameterStoreService();
        newMock.setTestUsername("different-user");
        ParameterStoreHelper.setParameterStoreService(newMock);
        
        assertEquals("different-user", ParameterStoreHelper.getTestUsername());
        assertNotEquals("isolated-user", ParameterStoreHelper.getTestUsername());
    }

    @Test
    void testFallbackBehavior() {
        // Test that our mock handles non-existent parameters correctly
        assertEquals("fallback-value", mockService.getParameter("does-not-exist", "fallback-value"));
        
        // Test default credential values
        assertEquals("mockuser", mockService.getTestUsername()); // Default from MockParameterStoreService
        assertEquals("mockpass", mockService.getTestPassword());  // Default from MockParameterStoreService
    }
}