package co.thismakesmehappy.toyapi.service.handlers;

import co.thismakesmehappy.toyapi.service.utils.MockCognitoService;
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
 * Unit tests for AuthHandler with dependency injection.
 * Demonstrates improved testability through mock Cognito service.
 */
class AuthHandlerTest {

    private MockCognitoService mockCognitoService;
    private AuthHandler authHandler;
    private Context mockContext;
    private LambdaLogger mockLogger;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockCognitoService = new MockCognitoService();
        mockCognitoService.clearMockUsers(); // Ensure clean state
        authHandler = new AuthHandler(mockCognitoService);
        objectMapper = new ObjectMapper();
        
        // Mock Lambda context
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
        
        // Set system properties for testing
        System.setProperty("ENVIRONMENT", "test");
        System.setProperty("USER_POOL_ID", "test-pool");
        System.setProperty("USER_POOL_CLIENT_ID", "test-client");
        System.setProperty("MOCK_AUTHENTICATION", "false"); // Use injected Cognito service
    }

    @AfterEach
    void tearDown() {
        mockCognitoService.clearMockUsers();
        // Clean up system properties
        System.clearProperty("ENVIRONMENT");
        System.clearProperty("USER_POOL_ID");
        System.clearProperty("USER_POOL_CLIENT_ID");
        System.clearProperty("MOCK_AUTHENTICATION");
    }

    @Test
    void testSuccessfulLogin() throws Exception {
        // Add test user to mock service
        mockCognitoService.addMockUser("testuser", "testpass");
        
        // Create login request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/login");
        request.setHttpMethod("POST");
        request.setBody("{\n" +
            "  \"username\": \"testuser\",\n" +
            "  \"password\": \"testpass\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify response
        assertEquals(200, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("mock-access-token-testuser", responseBody.get("accessToken").asText());
        assertEquals("mock-id-token-testuser", responseBody.get("idToken").asText());
        assertEquals("mock-refresh-token-testuser", responseBody.get("refreshToken").asText());
        assertEquals(3600, responseBody.get("expiresIn").asInt());
    }

    @Test
    void testInvalidCredentials() throws Exception {
        // Add test user with different password
        mockCognitoService.addMockUser("testuser", "correctpass");
        
        // Create login request with wrong password
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/login");
        request.setHttpMethod("POST");
        request.setBody("{\n" +
            "  \"username\": \"testuser\",\n" +
            "  \"password\": \"wrongpass\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(401, response.getStatusCode());
        
        JsonNode responseBody = objectMapper.readTree(response.getBody());
        assertEquals("UNAUTHORIZED", responseBody.get("error").asText());
        assertEquals("Invalid credentials", responseBody.get("message").asText());
    }

    @Test
    void testMissingCredentials() {
        // Create login request without password
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/login");
        request.setHttpMethod("POST");
        request.setBody("{\n" +
            "  \"username\": \"testuser\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(400, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("BAD_REQUEST", responseBody.get("error").asText());
            assertEquals("Username and password are required", responseBody.get("message").asText());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testEmptyRequestBody() {
        // Create login request with empty body
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/login");
        request.setHttpMethod("POST");
        request.setBody("");

        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(400, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("BAD_REQUEST", responseBody.get("error").asText());
            assertEquals("Request body is required", responseBody.get("message").asText());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testAuthenticatedMessage() {
        // Create authenticated message request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/message");
        request.setHttpMethod("GET");
        
        // Set mock authentication (since not using Parameter Store in unit tests)
        System.setProperty("MOCK_AUTHENTICATION", "true");
        
        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify response
        assertEquals(200, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertTrue(responseBody.get("message").asText().contains("Hello authenticated user"));
            assertNotNull(responseBody.get("userId"));
            assertNotNull(responseBody.get("timestamp"));
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testUserMessage() {
        // Create user-specific message request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/user/user-12345/message");
        request.setHttpMethod("GET");
        
        // Use the default fallback user ID that AuthHandler returns when mockAuthentication=false
        
        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify response
        assertEquals(200, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertTrue(responseBody.get("message").asText().contains("user-12345"));
            assertEquals("user-12345", responseBody.get("userId").asText());
            assertNotNull(responseBody.get("timestamp"));
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testUserMessageUnauthorizedAccess() {
        // Create user-specific message request for different user
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/user/other-user/message");
        request.setHttpMethod("GET");
        
        // Set mock authentication with different user ID
        System.setProperty("MOCK_AUTHENTICATION", "true");
        System.setProperty("LOCAL_TEST_USER_ID", "test-user");
        
        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(403, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("FORBIDDEN", responseBody.get("error").asText());
            assertEquals("You can only access your own messages", responseBody.get("message").asText());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testInvalidEndpoint() {
        // Request to invalid endpoint
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/invalid");
        request.setHttpMethod("GET");

        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify error response
        assertEquals(404, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("NOT_FOUND", responseBody.get("error").asText());
            assertEquals("Endpoint not found", responseBody.get("message").asText());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }

    @Test
    void testCognitoExceptionHandling() {
        // Configure mock to throw exception
        mockCognitoService.setThrowException(true, "Mock Cognito failure");
        
        // Login request
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setPath("/auth/login");
        request.setHttpMethod("POST");
        request.setBody("{\n" +
            "  \"username\": \"testuser\",\n" +
            "  \"password\": \"testpass\"\n" +
            "}");

        // Process request
        APIGatewayProxyResponseEvent response = authHandler.handleRequest(request, mockContext);

        // Verify error handling
        assertEquals(500, response.getStatusCode());
        
        try {
            JsonNode responseBody = objectMapper.readTree(response.getBody());
            assertEquals("INTERNAL_ERROR", responseBody.get("error").asText());
            assertEquals("Authentication service error", responseBody.get("message").asText());
        } catch (Exception e) {
            fail("Failed to parse response body: " + e.getMessage());
        }
    }
}