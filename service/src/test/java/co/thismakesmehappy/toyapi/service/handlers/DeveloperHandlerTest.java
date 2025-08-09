package co.thismakesmehappy.toyapi.service.handlers;

import co.thismakesmehappy.toyapi.service.utils.MockDynamoDbService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeveloperHandler with dependency injection.
 * Demonstrates improved testability through mock DynamoDB service.
 */
class DeveloperHandlerTest {

    private MockDynamoDbService mockDynamoDbService;
    private DeveloperHandler developerHandler;
    private Context mockContext;
    private LambdaLogger mockLogger;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockDynamoDbService = new MockDynamoDbService();
        mockDynamoDbService.clearTable(); // Ensure clean state
        developerHandler = new DeveloperHandler(mockDynamoDbService);
        objectMapper = new ObjectMapper();
        
        // Mock Lambda context
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
        
        // Set environment variables for testing
        System.setProperty("TABLE_NAME", "test-table");
        System.setProperty("API_NAME_PREFIX", "test-api");
        System.setProperty("USAGE_PLAN_PREFIX", "test-plan");
    }

    @AfterEach
    void tearDown() {
        mockDynamoDbService.clearTable();
        // Clean up system properties
        System.clearProperty("TABLE_NAME");
        System.clearProperty("API_NAME_PREFIX");
        System.clearProperty("USAGE_PLAN_PREFIX");
    }

    @Test
    void testRegisterDeveloper() throws Exception {
        // Create registration request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/developer/register");
        request.setHttpMethod("POST");
        request.setBody("{\n" +
            "  \"email\": \"test@example.com\",\n" +
            "  \"name\": \"Test Developer\",\n" +
            "  \"organization\": \"Test Org\",\n" +
            "  \"purpose\": \"API Testing\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = developerHandler.handleRequest(request, mockContext);

        // Verify response
        assertEquals(201, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("test@example.com", responseBody.get("email").asText());
        assertEquals("Test Developer", responseBody.get("name").asText());
        assertEquals("ACTIVE", responseBody.get("status").asText());
        assertEquals("Developer registered successfully", responseBody.get("message").asText());
        assertNotNull(responseBody.get("developerId"));
        
        // Verify data was stored in mock DynamoDB
        assertEquals(1, mockDynamoDbService.getTableSize());
    }

    @Test
    void testRegisterDeveloperDuplicate() throws Exception {
        // First registration
        testRegisterDeveloper(); // This will register test@example.com
        
        // Try to register same email again
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/developer/register");
        request.setHttpMethod("POST");
        request.setBody("{\n" +
            "  \"email\": \"test@example.com\",\n" +
            "  \"name\": \"Another Developer\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = developerHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(409, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("Developer already registered", responseBody.get("error").asText());
        assertEquals(409, responseBody.get("statusCode").asInt());
    }

    @Test
    void testGetDeveloperProfile() throws Exception {
        // First register a developer
        testRegisterDeveloper();
        
        // Get profile request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/developer/profile");
        request.setHttpMethod("GET");
        
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("email", "test@example.com");
        request.setQueryStringParameters(queryParams);

        // Process request
        APIGatewayProxyResponseEvent response = developerHandler.handleRequest(request, mockContext);

        // Verify response
        assertEquals(200, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("test@example.com", responseBody.get("email").asText());
        assertEquals("Test Developer", responseBody.get("name").asText());
        assertEquals("Test Org", responseBody.get("organization").asText());
        assertEquals("API Testing", responseBody.get("purpose").asText());
        assertEquals("ACTIVE", responseBody.get("status").asText());
        assertNotNull(responseBody.get("developerId"));
        assertNotNull(responseBody.get("createdAt"));
        assertNotNull(responseBody.get("updatedAt"));
    }

    @Test
    void testGetNonExistentDeveloperProfile() {
        // Get profile request for non-existent developer
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/developer/profile");
        request.setHttpMethod("GET");
        
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("email", "nonexistent@example.com");
        request.setQueryStringParameters(queryParams);

        // Process request
        APIGatewayProxyResponseEvent response = developerHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(404, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("Developer not found", responseBody.get("error").asText());
            assertEquals(404, responseBody.get("statusCode").asInt());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testUpdateDeveloperProfile() throws Exception {
        // First register a developer
        testRegisterDeveloper();
        
        // Update profile request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/developer/profile");
        request.setHttpMethod("PUT");
        request.setBody("{\n" +
            "  \"email\": \"test@example.com\",\n" +
            "  \"name\": \"Updated Developer Name\",\n" +
            "  \"organization\": \"Updated Organization\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = developerHandler.handleRequest(request, mockContext);

        // Verify response
        assertEquals(200, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("test@example.com", responseBody.get("email").asText());
        assertEquals("Updated Developer Name", responseBody.get("name").asText());
        assertEquals("Updated Organization", responseBody.get("organization").asText());
        assertEquals("ACTIVE", responseBody.get("status").asText());
        assertEquals("Developer profile updated successfully", responseBody.get("message").asText());
        assertNotNull(responseBody.get("updatedAt"));
    }

    @Test
    void testInvalidEndpoint() {
        // Request to invalid endpoint
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/developer/invalid");
        request.setHttpMethod("GET");

        // Process request
        APIGatewayProxyResponseEvent response = developerHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(404, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("Endpoint not found", responseBody.get("error").asText());
            assertEquals(404, responseBody.get("statusCode").asInt());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testDynamoDbExceptionHandling() {
        // Configure mock to throw exception
        mockDynamoDbService.setThrowException(true, "Mock DynamoDB failure");
        
        // Registration request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/developer/register");
        request.setHttpMethod("POST");
        request.setBody("{\n" +
            "  \"email\": \"error@example.com\",\n" +
            "  \"name\": \"Error Test\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = developerHandler.handleRequest(request, mockContext);

        // Verify error handling
        assertEquals(500, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("Failed to register developer", responseBody.get("error").asText());
            assertEquals(500, responseBody.get("statusCode").asInt());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
        
        // Verify error logging
        verify(mockLogger, atLeastOnce()).log(contains("Error"));
    }
}