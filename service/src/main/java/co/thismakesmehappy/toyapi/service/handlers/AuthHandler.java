package co.thismakesmehappy.toyapi.service.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import co.thismakesmehappy.toyapi.service.utils.ParameterStoreHelper;

/**
 * Lambda handler for authentication-related API endpoints.
 * Handles login and authenticated message endpoints.
 */
public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    public AuthHandler() {
        this.cognitoClient = CognitoIdentityProviderClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .build();
    }
    
    private final String environment = System.getenv("ENVIRONMENT");
    private final String userPoolId = System.getenv("USER_POOL_ID");
    private final String userPoolClientId = System.getenv("USER_POOL_CLIENT_ID");
    private final boolean mockAuthentication = "true".equals(System.getenv("MOCK_AUTHENTICATION"));
    private final CognitoIdentityProviderClient cognitoClient;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Handling auth request: {} {}", input.getHttpMethod(), input.getPath());
        logger.info("Environment: {}", environment);
        logger.info("Mock Authentication: {}", mockAuthentication);
        logger.info("MOCK_AUTHENTICATION env var: {}", System.getenv("MOCK_AUTHENTICATION"));
        
        try {
            String path = input.getPath();
            String method = input.getHttpMethod();
            
            if ("POST".equals(method) && "/auth/login".equals(path)) {
                return handleLogin(input, context);
            } else if ("GET".equals(method) && "/auth/message".equals(path)) {
                return handleAuthenticatedMessage(input, context);
            } else if ("GET".equals(method) && path.matches("/auth/user/[^/]+/message")) {
                return handleUserMessage(input, context);
            }
            
            // Unknown endpoint
            return createErrorResponse(404, "NOT_FOUND", "Endpoint not found", null);
            
        } catch (Exception e) {
            logger.error("Error handling auth request", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Internal server error", e.getMessage());
        }
    }
    
    /**
     * Handles POST /auth/login - authenticate user and return JWT token
     */
    private APIGatewayProxyResponseEvent handleLogin(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String body = input.getBody();
            if (body == null || body.trim().isEmpty()) {
                return createErrorResponse(400, "BAD_REQUEST", "Request body is required", null);
            }
            
            JsonNode requestJson = objectMapper.readTree(body);
            String username = requestJson.get("username") != null ? requestJson.get("username").asText() : null;
            String password = requestJson.get("password") != null ? requestJson.get("password").asText() : null;
            
            if (username == null || password == null) {
                return createErrorResponse(400, "BAD_REQUEST", "Username and password are required", null);
            }
            
            // Use mock authentication for local development
            if (mockAuthentication) {
                logger.info("Using mock authentication for local development");
                
                // Get test credentials from Parameter Store for validation
                String testUsername = ParameterStoreHelper.getTestUsername();
                String testPassword = ParameterStoreHelper.getTestPassword();
                
                // Validate credentials against Parameter Store values
                if (testUsername.equals(username) && testPassword.equals(password)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("accessToken", "mock-access-token-" + username);
                    response.put("idToken", "local-dev-mock-token-12345");
                    response.put("refreshToken", "mock-refresh-token-" + username);
                    response.put("tokenType", "Bearer");
                    response.put("expiresIn", 3600);
                    
                    return createSuccessResponse(200, response);
                } else {
                    logger.warn("Mock authentication failed for user: {} - Invalid test credentials", username);
                    return createErrorResponse(401, "UNAUTHORIZED", "Invalid test credentials", null);
                }
            }
            
            // Authenticate with Cognito
            try {
                AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                        .userPoolId(userPoolId)
                        .clientId(userPoolClientId)
                        .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                        .authParameters(Map.of(
                                "USERNAME", username,
                                "PASSWORD", password
                        ))
                        .build();
                
                AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(authRequest);
                
                if (authResponse.authenticationResult() != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("accessToken", authResponse.authenticationResult().accessToken());
                    response.put("idToken", authResponse.authenticationResult().idToken());
                    response.put("refreshToken", authResponse.authenticationResult().refreshToken());
                    response.put("expiresIn", authResponse.authenticationResult().expiresIn());
                    response.put("tokenType", authResponse.authenticationResult().tokenType());
                    
                    logger.info("Authentication successful for user: {}", username);
                    return createSuccessResponse(200, response);
                } else {
                    logger.warn("Authentication failed for user: {} - No authentication result", username);
                    return createErrorResponse(401, "UNAUTHORIZED", "Invalid credentials", null);
                }
                
            } catch (NotAuthorizedException e) {
                logger.warn("Authentication failed for user: {} - Invalid credentials", username);
                return createErrorResponse(401, "UNAUTHORIZED", "Invalid credentials", null);
            } catch (UserNotFoundException e) {
                logger.warn("Authentication failed for user: {} - User not found", username);
                return createErrorResponse(401, "UNAUTHORIZED", "Invalid credentials", null);
            } catch (Exception e) {
                logger.error("Error during authentication for user: {}", username, e);
                return createErrorResponse(500, "INTERNAL_ERROR", "Authentication service error", e.getMessage());
            }
            
        } catch (Exception e) {
            logger.error("Error handling login", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Login failed", e.getMessage());
        }
    }
    
    /**
     * Handles GET /auth/message - returns authenticated message
     */
    private APIGatewayProxyResponseEvent handleAuthenticatedMessage(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Extract user info from authorization header or context
            String userId = getUserIdFromRequest(input);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Hello authenticated user! You have access to this protected endpoint. Environment: " + environment);
            response.put("userId", userId);
            response.put("timestamp", Instant.now().toString());
            
            logger.info("Returning authenticated message for user: {}", userId);
            return createSuccessResponse(200, response);
            
        } catch (Exception e) {
            logger.error("Error creating authenticated message", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to create response", e.getMessage());
        }
    }
    
    /**
     * Handles GET /auth/user/{userId}/message - returns user-specific message
     */
    private APIGatewayProxyResponseEvent handleUserMessage(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String path = input.getPath();
            String[] pathParts = path.split("/");
            String requestedUserId = pathParts[3]; // /auth/user/{userId}/message
            
            String currentUserId = getUserIdFromRequest(input);
            
            // Check if user is trying to access their own message
            if (!requestedUserId.equals(currentUserId)) {
                return createErrorResponse(403, "FORBIDDEN", "You can only access your own messages", null);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Hello " + requestedUserId + "! This is your personalized message.");
            response.put("userId", requestedUserId);
            response.put("timestamp", Instant.now().toString());
            
            logger.info("Returning user-specific message for user: {}", requestedUserId);
            return createSuccessResponse(200, response);
            
        } catch (Exception e) {
            logger.error("Error creating user message", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to create response", e.getMessage());
        }
    }
    
    /**
     * Extracts user ID from the request (from JWT token or mock authorization)
     */
    private String getUserIdFromRequest(APIGatewayProxyRequestEvent input) {
        // Use mock authentication for local development
        if (mockAuthentication) {
            String localUserId = System.getenv("LOCAL_TEST_USER_ID");
            if (localUserId != null) {
                return localUserId;
            }
            return "local-user-12345";
        }
        
        // JWT token validation for AWS Cognito environments
        // Extracts user ID from Authorization header with proper fallback handling
        Map<String, String> headers = input.getHeaders();
        if (headers != null) {
            String authHeader = headers.get("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // Mock: extract user ID from token (in real implementation, decode JWT)
                if (token.startsWith("mock-jwt-token-")) {
                    return "user-" + Math.abs(token.hashCode() % 100000);
                }
            }
        }
        
        // Fallback to mock user ID
        return "user-12345";
    }
    
    /**
     * Creates a successful API Gateway response
     */
    private APIGatewayProxyResponseEvent createSuccessResponse(int statusCode, Object body) {
        try {
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(statusCode);
            response.setBody(objectMapper.writeValueAsString(body));
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setHeaders(headers);
            
            return response;
        } catch (Exception e) {
            logger.error("Error creating success response", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to serialize response", e.getMessage());
        }
    }
    
    /**
     * Creates an error API Gateway response
     */
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String error, String message, String details) {
        try {
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", error);
            errorBody.put("message", message);
            errorBody.put("timestamp", Instant.now().toString());
            if (details != null) {
                errorBody.put("details", details);
            }
            
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(statusCode);
            response.setBody(objectMapper.writeValueAsString(errorBody));
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setHeaders(headers);
            
            return response;
        } catch (Exception e) {
            logger.error("Error creating error response", e);
            // Fallback response
            APIGatewayProxyResponseEvent fallback = new APIGatewayProxyResponseEvent();
            fallback.setStatusCode(500);
            fallback.setBody("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Failed to create error response\"}");
            return fallback;
        }
    }
}