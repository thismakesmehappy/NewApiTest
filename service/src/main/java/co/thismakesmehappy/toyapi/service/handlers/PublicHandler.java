package co.thismakesmehappy.toyapi.service.handlers;

import co.thismakesmehappy.toyapi.service.services.publicendpoint.GetPublicMessageService;
import co.thismakesmehappy.toyapi.service.versioning.ApiVersion;
import co.thismakesmehappy.toyapi.service.versioning.ApiVersioningService;
import co.thismakesmehappy.toyapi.service.versioning.VersionedResponseBuilder;
import co.thismakesmehappy.toyapi.service.security.SecurityService;
import co.thismakesmehappy.toyapi.service.security.SecurityInterceptor;
import co.thismakesmehappy.toyapi.service.security.SecurityCheckResult;
import co.thismakesmehappy.toyapi.service.utils.FeatureFlagService;
import co.thismakesmehappy.toyapi.service.utils.ParameterStoreFeatureFlagService;
import co.thismakesmehappy.toyapi.service.utils.ParameterStoreService;
import co.thismakesmehappy.toyapi.service.utils.AwsParameterStoreService;
import software.amazon.awssdk.services.ssm.SsmClient;
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
    private final SecurityInterceptor securityInterceptor;
    
    /**
     * Default constructor for Lambda runtime.
     */
    public PublicHandler() {
        String environment = System.getenv("ENVIRONMENT");
        this.getPublicMessageService = new GetPublicMessageService(environment);
        this.versioningService = new ApiVersioningService();
        
        // Initialize security components
        SsmClient ssmClient = SsmClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .build();
        ParameterStoreService parameterStoreService = new AwsParameterStoreService(ssmClient);
        FeatureFlagService featureFlagService = new ParameterStoreFeatureFlagService(parameterStoreService);
        SecurityService securityService = new SecurityService(featureFlagService);
        this.securityInterceptor = new SecurityInterceptor(securityService, featureFlagService);
    }
    
    /**
     * Constructor for dependency injection (testing).
     * 
     * @param getPublicMessageService The service for GET /public/message
     */
    public PublicHandler(GetPublicMessageService getPublicMessageService) {
        this.getPublicMessageService = getPublicMessageService;
        this.versioningService = new ApiVersioningService();
        this.securityInterceptor = null; // For testing
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
        this.securityInterceptor = null; // For testing
    }
    
    /**
     * Constructor for dependency injection with security (testing).
     * 
     * @param getPublicMessageService The service for GET /public/message
     * @param versioningService The versioning service
     * @param securityInterceptor The security interceptor
     */
    public PublicHandler(GetPublicMessageService getPublicMessageService, ApiVersioningService versioningService, 
                        SecurityInterceptor securityInterceptor) {
        this.getPublicMessageService = getPublicMessageService;
        this.versioningService = versioningService;
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        logger.info("Handling public request: {} {}", input.getHttpMethod(), input.getPath());
        
        try {
            // Extract API version from request
            ApiVersion requestedVersion = versioningService.extractVersion(input);
            logger.info("API version: {}", requestedVersion.getVersionString());
            
            // Security checks
            if (securityInterceptor != null) {
                SecurityCheckResult securityResult = securityInterceptor.checkRequest(input, requestedVersion);
                if (securityResult.isBlocked()) {
                    logger.warn("Request blocked by security: {}", securityResult.getReason());
                    return securityInterceptor.enhanceResponse(
                        securityInterceptor.createBlockedResponse(securityResult, requestedVersion));
                }
            }
            
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
            APIGatewayProxyResponseEvent errorResponse = VersionedResponseBuilder.createErrorResponse(
                fallbackVersion, 500, "Internal server error: " + e.getMessage(), "INTERNAL_ERROR");
            return securityInterceptor != null ? securityInterceptor.enhanceResponse(errorResponse) : errorResponse;
        }
    }
    
    /**
     * Handles GET /public/message - returns a public message
     */
    private APIGatewayProxyResponseEvent handleGetPublicMessage(APIGatewayProxyRequestEvent input, Context context, ApiVersion version) {
        try {
            Map<String, Object> response = getPublicMessageService.execute();
            
            logger.info("Returning public message for version {}", version.getVersionString());
            
            APIGatewayProxyResponseEvent apiResponse = new VersionedResponseBuilder(versioningService)
                    .withVersion(version)
                    .withStatusCode(200)
                    .withBody(response)
                    .build();
            
            // Apply security headers
            return securityInterceptor != null ? securityInterceptor.enhanceResponse(apiResponse) : apiResponse;
            
        } catch (Exception e) {
            logger.error("Error creating public message response", e);
            APIGatewayProxyResponseEvent errorResponse = VersionedResponseBuilder.createErrorResponse(
                version, 500, "Failed to create response: " + e.getMessage(), "INTERNAL_ERROR");
            return securityInterceptor != null ? securityInterceptor.enhanceResponse(errorResponse) : errorResponse;
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