package co.thismakesmehappy.toyapi.service.versioning;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating version-aware API responses.
 * Handles response formatting based on API version requirements.
 */
public class VersionedResponseBuilder {
    
    private final ApiVersioningService versioningService;
    private final ObjectMapper objectMapper;
    
    private ApiVersion version;
    private int statusCode = 200;
    private Object body;
    private Map<String, String> headers = new HashMap<>();
    private boolean enableCors = true;
    
    public VersionedResponseBuilder(ApiVersioningService versioningService) {
        this.versioningService = versioningService;
        this.objectMapper = new ObjectMapper();
    }
    
    public VersionedResponseBuilder withVersion(ApiVersion version) {
        this.version = version;
        return this;
    }
    
    public VersionedResponseBuilder withStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }
    
    public VersionedResponseBuilder withBody(Object body) {
        this.body = body;
        return this;
    }
    
    public VersionedResponseBuilder withHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }
    
    public VersionedResponseBuilder withHeaders(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }
    
    public VersionedResponseBuilder withCors(boolean enableCors) {
        this.enableCors = enableCors;
        return this;
    }
    
    public APIGatewayProxyResponseEvent build() {
        if (version == null) {
            version = ApiVersion.getDefault();
        }
        
        // Build response headers
        Map<String, String> responseHeaders = new HashMap<>();
        
        // Add CORS headers if enabled
        if (enableCors) {
            responseHeaders.put("Access-Control-Allow-Origin", "*");
            responseHeaders.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            responseHeaders.put("Access-Control-Allow-Headers", "Content-Type, Authorization, API-Version, Accept");
            responseHeaders.put("Access-Control-Expose-Headers", "API-Version, X-API-Version-Deprecated");
        }
        
        // Add version-specific headers
        responseHeaders.putAll(versioningService.createVersionHeaders(version));
        
        // Add custom headers
        responseHeaders.putAll(headers);
        
        // Build response body
        String responseBody = formatBody(body, version);
        
        // Check for version warnings
        String warning = versioningService.getVersionWarning(version);
        if (warning != null) {
            responseHeaders.put("X-API-Warning", warning);
        }
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(responseHeaders)
                .withBody(responseBody);
    }
    
    /**
     * Format response body based on API version requirements.
     */
    private String formatBody(Object body, ApiVersion version) {
        if (body == null) {
            return null;
        }
        
        try {
            // For v1.x.x, use standard JSON formatting
            if (version.getMajor() == 1) {
                return formatV1Response(body);
            }
            
            // Future versions can have different formatting
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            // Fallback to simple string representation
            return body.toString();
        }
    }
    
    /**
     * Format response for API version 1.x.x.
     */
    private String formatV1Response(Object body) throws Exception {
        if (body instanceof String) {
            return (String) body;
        }
        
        // Wrap response in standard v1 envelope if it's not already wrapped
        if (isSimpleObject(body)) {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("data", body);
            envelope.put("version", version.getVersionString());
            envelope.put("timestamp", java.time.Instant.now().toString());
            
            return objectMapper.writeValueAsString(envelope);
        }
        
        // For complex objects, serialize directly
        return objectMapper.writeValueAsString(body);
    }
    
    /**
     * Check if object is a simple value that should be wrapped in envelope.
     */
    private boolean isSimpleObject(Object obj) {
        if (obj == null) return true;
        
        // Don't wrap these types in envelope
        if (obj instanceof Map || obj instanceof java.util.Collection) {
            return false;
        }
        
        // Check if it's already a response object with expected fields
        if (obj.getClass().getName().contains("Response") || 
            obj.getClass().getName().contains("Result")) {
            return false;
        }
        
        // Wrap primitive types and simple objects
        return true;
    }
    
    /**
     * Create an error response with version information.
     */
    public static APIGatewayProxyResponseEvent createErrorResponse(
            ApiVersion version, int statusCode, String errorMessage, String errorCode) {
        
        ApiVersioningService versioningService = new ApiVersioningService();
        
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", Map.of(
            "code", errorCode != null ? errorCode : "GENERIC_ERROR",
            "message", errorMessage,
            "version", version.getVersionString(),
            "timestamp", java.time.Instant.now().toString()
        ));
        
        return new VersionedResponseBuilder(versioningService)
                .withVersion(version)
                .withStatusCode(statusCode)
                .withBody(errorBody)
                .build();
    }
    
    /**
     * Create a success response with version information.
     */
    public static APIGatewayProxyResponseEvent createSuccessResponse(
            ApiVersion version, Object data) {
        
        ApiVersioningService versioningService = new ApiVersioningService();
        
        return new VersionedResponseBuilder(versioningService)
                .withVersion(version)
                .withStatusCode(200)
                .withBody(data)
                .build();
    }
}