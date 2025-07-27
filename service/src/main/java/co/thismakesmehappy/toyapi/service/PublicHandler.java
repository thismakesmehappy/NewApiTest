package co.thismakesmehappy.toyapi.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for public API endpoints.
 * Handles endpoints that don't require authentication.
 */
public class PublicHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(PublicHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    private final String environment = System.getenv("ENVIRONMENT");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Handling public request: {} {}", input.getHttpMethod(), input.getPath());
        
        try {
            String path = input.getPath();
            String method = input.getHttpMethod();
            
            if ("GET".equals(method) && "/public/message".equals(path)) {
                return handleGetPublicMessage(input, context);
            }
            
            // Unknown endpoint
            return createErrorResponse(404, "NOT_FOUND", "Endpoint not found", null);
            
        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Internal server error", e.getMessage());
        }
    }
    
    /**
     * Handles GET /public/message - returns a public message
     */
    private APIGatewayProxyResponseEvent handleGetPublicMessage(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Hello from ToyApi public endpoint! Environment: " + environment);
            response.put("timestamp", Instant.now().toString());
            
            logger.info("Returning public message for environment: {}", environment);
            
            return createSuccessResponse(200, response);
            
        } catch (Exception e) {
            logger.error("Error creating public message response", e);
            return createErrorResponse(500, "INTERNAL_ERROR", "Failed to create response", e.getMessage());
        }
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