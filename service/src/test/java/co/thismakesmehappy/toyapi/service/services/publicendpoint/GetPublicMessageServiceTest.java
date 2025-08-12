package co.thismakesmehappy.toyapi.service.services.publicendpoint;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GetPublicMessageService.
 * Tests the business logic for GET /public/message endpoint.
 */
class GetPublicMessageServiceTest {

    @Test
    void testExecute() {
        // Create service with test environment
        GetPublicMessageService service = new GetPublicMessageService("test");
        
        // Execute the service
        Map<String, Object> result = service.execute();
        
        // Verify response structure
        assertNotNull(result);
        assertTrue(result.containsKey("message"));
        assertTrue(result.containsKey("timestamp"));
        
        // Verify content
        String message = (String) result.get("message");
        assertTrue(message.contains("Hello from ToyApi public endpoint"));
        assertTrue(message.contains("Environment: test"));
        
        // Verify timestamp format (basic check)
        String timestamp = (String) result.get("timestamp");
        assertNotNull(timestamp);
        assertTrue(timestamp.contains("T")); // ISO format contains T
    }
    
    @Test
    void testExecuteWithDifferentEnvironment() {
        // Create service with production environment
        GetPublicMessageService service = new GetPublicMessageService("production");
        
        // Execute the service
        Map<String, Object> result = service.execute();
        
        // Verify environment is reflected in message
        String message = (String) result.get("message");
        assertTrue(message.contains("Environment: production"));
    }
    
    @Test
    void testExecuteWithNullEnvironment() {
        // Create service with null environment
        GetPublicMessageService service = new GetPublicMessageService(null);
        
        // Execute the service
        Map<String, Object> result = service.execute();
        
        // Verify null is handled gracefully
        String message = (String) result.get("message");
        assertTrue(message.contains("Environment: null"));
    }
}