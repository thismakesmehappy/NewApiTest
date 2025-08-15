package co.thismakesmehappy.toyapi.service.handlers;

import co.thismakesmehappy.toyapi.service.services.publicendpoint.GetPublicMessageService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PublicHandler with service layer separation.
 * Demonstrates improved testability through service abstraction.
 */
class PublicHandlerTest {

    private GetPublicMessageService mockGetPublicMessageService;
    private PublicHandler publicHandler;
    private Context mockContext;
    private LambdaLogger mockLogger;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockGetPublicMessageService = mock(GetPublicMessageService.class);
        publicHandler = new PublicHandler(mockGetPublicMessageService);
        objectMapper = new ObjectMapper();
        
        // Mock Lambda context
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
    }

    @Test
    void testGetPublicMessage() throws Exception {
        // Setup mock service response
        Map<String, Object> serviceResponse = new HashMap<>();
        serviceResponse.put("message", "Hello from service!");
        serviceResponse.put("timestamp", "2024-01-01T00:00:00Z");
        
        when(mockGetPublicMessageService.execute()).thenReturn(serviceResponse);
        
        // Create request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/public/message");
        request.setHttpMethod("GET");

        // Process request
        APIGatewayProxyResponseEvent response = publicHandler.handleRequest(request, mockContext);

        // Verify response
        assertEquals(200, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("Hello from service!", responseBody.get("message").asText());
        assertEquals("2024-01-01T00:00:00Z", responseBody.get("timestamp").asText());
        
        // Verify CORS headers and versioning headers
        assertNotNull(response.getHeaders());
        assertEquals("application/vnd.toyapi.v1+json", response.getHeaders().get("Content-Type"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertEquals("v1.0.0", response.getHeaders().get("API-Version"));
        
        // Verify service was called
        verify(mockGetPublicMessageService, times(1)).execute();
    }

    @Test
    void testServiceException() throws Exception {
        // Setup mock service to throw exception
        when(mockGetPublicMessageService.execute()).thenThrow(new RuntimeException("Service error"));
        
        // Create request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/public/message");
        request.setHttpMethod("GET");

        // Process request
        APIGatewayProxyResponseEvent response = publicHandler.handleRequest(request, mockContext);

        // Verify error response with versioned format
        assertEquals(500, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode errorObject = responseBody.get("error");
        assertNotNull(errorObject);
        assertEquals("INTERNAL_ERROR", errorObject.get("code").asText());
        assertTrue(errorObject.get("message").asText().contains("Failed to create response"));
        
        // Verify service was called
        verify(mockGetPublicMessageService, times(1)).execute();
    }

    @Test
    void testUnknownEndpoint() throws Exception {
        // Create request for unknown endpoint
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/unknown");
        request.setHttpMethod("GET");

        // Process request
        APIGatewayProxyResponseEvent response = publicHandler.handleRequest(request, mockContext);

        // Verify error response with versioned format
        assertEquals(404, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode errorObject = responseBody.get("error");
        assertNotNull(errorObject);
        assertEquals("NOT_FOUND", errorObject.get("code").asText());
        assertEquals("Endpoint not found", errorObject.get("message").asText());
        
        // Verify service was not called
        verify(mockGetPublicMessageService, never()).execute();
    }

    @Test
    void testWrongHttpMethod() throws Exception {
        // Create request with wrong HTTP method
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/public/message");
        request.setHttpMethod("POST");

        // Process request
        APIGatewayProxyResponseEvent response = publicHandler.handleRequest(request, mockContext);

        // Verify error response with versioned format
        assertEquals(404, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        JsonNode errorObject = responseBody.get("error");
        assertNotNull(errorObject);
        assertEquals("NOT_FOUND", errorObject.get("code").asText());
        assertEquals("Endpoint not found", errorObject.get("message").asText());
        
        // Verify service was not called
        verify(mockGetPublicMessageService, never()).execute();
    }

    @Test
    void testDefaultConstructor() {
        // Test that the default constructor works (creates service internally)
        PublicHandler handlerWithDefaults = new PublicHandler();
        assertNotNull(handlerWithDefaults);
        
        // This test verifies the constructor doesn't throw exceptions
        // The actual service functionality is tested in integration tests
    }
}