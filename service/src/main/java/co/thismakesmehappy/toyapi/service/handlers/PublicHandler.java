package co.thismakesmehappy.toyapi.service.handlers;

import co.thismakesmehappy.toyapi.service.services.publicendpoint.GetPublicMessageService;
import co.thismakesmehappy.toyapi.service.versioning.ApiVersion;
import co.thismakesmehappy.toyapi.service.versioning.ApiVersioningService;
import co.thismakesmehappy.toyapi.service.versioning.VersionedResponseBuilder;
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
    
    private final GetPublicMessageService getPublicMessageService;
    private final ApiVersioningService versioningService;
    
    /**
     * Default constructor for Lambda runtime.
     */
    public PublicHandler() {
        String environment = System.getenv("ENVIRONMENT");
        this.getPublicMessageService = new GetPublicMessageService(environment);
        this.versioningService = new ApiVersioningService();
    }
    
    /**
     * Constructor for dependency injection (testing).
     * 
     * @param getPublicMessageService The service for GET /public/message
     */
    public PublicHandler(GetPublicMessageService getPublicMessageService) {
        this.getPublicMessageService = getPublicMessageService;
        this.versioningService = new ApiVersioningService();
    }
    
    /**
     * Constructor for dependency injection with versioning service (testing).
     * 
     * @param getPublicMessageService The service for GET /public/message
     * @param versioningService The versioning service
     */
    public PublicHandler(GetPublicMessageService getPublicMessageService, ApiVersioningService versioningService) {
        this.getPublicMessageService = getPublicMessageService;
        this.versioningService = versioningService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Handling public request: {} {}", input.getHttpMethod(), input.getPath());
        
        try {
            // Extract API version from request
            ApiVersion requestedVersion = versioningService.extractVersion(input);
            logger.info("API version: {}", requestedVersion.getVersionString());
            
            // Check if version is supported
            if (!versioningService.isVersionSupported(requestedVersion)) {
                logger.warn("Unsupported API version: {}", requestedVersion.getVersionString());
                return VersionedResponseBuilder.createErrorResponse(
                    requestedVersion, 400, "Unsupported API version: " + requestedVersion.getVersionString(), "UNSUPPORTED_VERSION");
            }
            
            String path = input.getPath();
            String method = input.getHttpMethod();
            
            if ("GET".equals(method) && "/public/message".equals(path)) {
                return handleGetPublicMessage(input, context, requestedVersion);
            }
            
            // Unknown endpoint
            return VersionedResponseBuilder.createErrorResponse(
                requestedVersion, 404, "Endpoint not found", "NOT_FOUND");
            
        } catch (Exception e) {
            logger.error("Error handling request", e);
            ApiVersion fallbackVersion = ApiVersion.getDefault();
            return VersionedResponseBuilder.createErrorResponse(
                fallbackVersion, 500, "Internal server error: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }
    
    /**
     * Handles GET /public/message - returns a public message
     */
    private APIGatewayProxyResponseEvent handleGetPublicMessage(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            Map<String, Object> response = getPublicMessageService.execute();
            
            logger.info("Returning public message for version {}", version.getVersionString());
            
            return new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(200)
                    .withBody(response)
                    .build();
            
        } catch (Exception e) {
            logger.error("Error creating public message response", e);
            return VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to create response: " + e.getMessage(), "INTERNAL_ERROR");
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