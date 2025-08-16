package co.thismakesmehappy.toyapi.service.security;

import co.thismakesmehappy.toyapi.service.utils.FeatureFlagService;
import co.thismakesmehappy.toyapi.service.versioning.ApiVersion;
import co.thismakesmehappy.toyapi.service.versioning.VersionedResponseBuilder;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Security interceptor for Lambda handlers.
 * Provides centralized security checking for all API requests.
 */
public class SecurityInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityInterceptor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final SecurityService securityService;
    private final FeatureFlagService featureFlagService;
    
    public SecurityInterceptor(SecurityService securityService, FeatureFlagService featureFlagService) {
        this.securityService = securityService;
        this.featureFlagService = featureFlagService;
    }
    
    /**
     * Check request security before processing.
     */
    public SecurityCheckResult checkRequest(APIGatewayProxyRequestEvent request, ApiVersion version) {
        if (securityService == null) {
            return SecurityCheckResult.allowed("Security service not configured");
        }
        
        String clientIdentifier = extractClientIdentifier(request);
        String endpoint = request.getPath();
        String method = request.getHttpMethod();
        
        // Rate limiting check
        SecurityCheckResult rateLimitResult = securityService.checkRateLimit(clientIdentifier, endpoint);
        if (rateLimitResult.isBlocked()) {
            return rateLimitResult;
        }
        
        // JWT token validation (if present)
        String authHeader = getHeader(request, "Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            SecurityCheckResult tokenResult = securityService.validateJwtToken(token);
            if (tokenResult.isBlocked()) {
                return tokenResult;
            }
        }
        
        // Input validation for request body
        String body = request.getBody();
        if (body != null && !body.trim().isEmpty()) {
            SecurityCheckResult inputResult = securityService.validateInput(body, "request_body");
            if (inputResult.isBlocked()) {
                return inputResult;
            }
            
            // Validate JSON structure if it's JSON
            if (isJsonContent(request)) {
                SecurityCheckResult jsonResult = validateJsonInput(body, clientIdentifier);
                if (jsonResult.isBlocked()) {
                    return jsonResult;
                }
            }
        }
        
        // Validate query parameters
        Map<String, String> queryParams = request.getQueryStringParameters();
        if (queryParams != null) {
            for (Map.Entry<String, String> param : queryParams.entrySet()) {
                SecurityCheckResult paramResult = securityService.validateInput(
                    param.getValue(), "query_param_" + param.getKey());
                if (paramResult.isBlocked()) {
                    return paramResult;
                }
            }
        }
        
        // Validate headers for suspicious content
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (isUserControlledHeader(header.getKey())) {
                    SecurityCheckResult headerResult = securityService.validateInput(
                        header.getValue(), "header_" + header.getKey());
                    if (headerResult.isBlocked()) {
                        return headerResult;
                    }
                }
            }
        }
        
        // Check for suspicious activity patterns
        SecurityCheckResult activityResult = securityService.checkSuspiciousActivity(
            clientIdentifier, method + ":" + endpoint);
        if (activityResult.isBlocked()) {
            return activityResult;
        }
        
        return SecurityCheckResult.allowed();
    }
    
    /**
     * Enhance response with security headers.
     */
    public APIGatewayProxyResponseEvent enhanceResponse(APIGatewayProxyResponseEvent response) {
        if (securityService == null) {
            return response;
        }
        
        Map<String, String> securityHeaders = securityService.getSecurityHeaders();
        Map<String, String> currentHeaders = response.getHeaders();
        
        if (currentHeaders != null) {
            currentHeaders.putAll(securityHeaders);
        } else {
            response.setHeaders(securityHeaders);
        }
        
        return response;
    }
    
    /**
     * Create a security-blocked response.
     */
    public APIGatewayProxyResponseEvent createBlockedResponse(SecurityCheckResult result, ApiVersion version) {
        String clientId = "unknown";
        securityService.logSecurityEvent(clientId, "REQUEST_BLOCKED", result.getReason());
        
        // Return appropriate status code based on the type of security violation
        int statusCode = getStatusCodeForSecurityViolation(result.getReason());
        
        return VersionedResponseBuilder.createErrorResponse(
            version, statusCode, result.getReason(), "SECURITY_VIOLATION");
    }
    
    /**
     * Log security events for monitoring.
     */
    public void logSecurityEvent(String clientIdentifier, String eventType, String details) {
        if (securityService != null) {
            securityService.logSecurityEvent(clientIdentifier, eventType, details);
        }
    }
    
    private String extractClientIdentifier(APIGatewayProxyRequestEvent request) {
        // Try to get client identifier from various sources
        
        // 1. From source IP
        String sourceIp = getSourceIp(request);
        if (sourceIp != null) {
            return "ip:" + sourceIp;
        }
        
        // 2. From User-Agent (hashed for privacy)
        String userAgent = getHeader(request, "User-Agent");
        if (userAgent != null) {
            return "ua:" + Math.abs(userAgent.hashCode());
        }
        
        // 3. From request context if available
        if (request.getRequestContext() != null && 
            request.getRequestContext().getRequestId() != null) {
            return "req:" + request.getRequestContext().getRequestId();
        }
        
        // Fallback
        return "unknown";
    }
    
    private String getSourceIp(APIGatewayProxyRequestEvent request) {
        if (request.getRequestContext() != null && 
            request.getRequestContext().getIdentity() != null) {
            return request.getRequestContext().getIdentity().getSourceIp();
        }
        
        // Check X-Forwarded-For header
        String forwardedFor = getHeader(request, "X-Forwarded-For");
        if (forwardedFor != null) {
            return forwardedFor.split(",")[0].trim();
        }
        
        return null;
    }
    
    private String getHeader(APIGatewayProxyRequestEvent request, String headerName) {
        Map<String, String> headers = request.getHeaders();
        if (headers == null) return null;
        
        // Try exact match first
        String value = headers.get(headerName);
        if (value != null) return value;
        
        // Try case-insensitive match
        return headers.entrySet().stream()
            .filter(entry -> headerName.equalsIgnoreCase(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    private boolean isJsonContent(APIGatewayProxyRequestEvent request) {
        String contentType = getHeader(request, "Content-Type");
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }
    
    private SecurityCheckResult validateJsonInput(String body, String clientIdentifier) {
        try {
            JsonNode json = objectMapper.readTree(body);
            
            // Check for deeply nested objects (potential DoS)
            int depth = calculateJsonDepth(json);
            if (depth > 10) {
                securityService.logSecurityEvent(clientIdentifier, "DEEP_JSON_NESTING", 
                    "JSON with suspicious nesting depth: " + depth);
                return SecurityCheckResult.blocked("Request structure too complex");
            }
            
            // Check for excessive number of properties
            int propertyCount = countJsonProperties(json);
            if (propertyCount > 100) {
                securityService.logSecurityEvent(clientIdentifier, "EXCESSIVE_JSON_PROPERTIES", 
                    "JSON with excessive properties: " + propertyCount);
                return SecurityCheckResult.blocked("Request structure too large");
            }
            
        } catch (Exception e) {
            securityService.logSecurityEvent(clientIdentifier, "INVALID_JSON", 
                "Invalid JSON structure: " + e.getMessage());
            return SecurityCheckResult.blocked("Invalid request format");
        }
        
        return SecurityCheckResult.allowed();
    }
    
    private int calculateJsonDepth(JsonNode node) {
        if (node.isContainerNode()) {
            int maxChildDepth = 0;
            for (JsonNode child : node) {
                maxChildDepth = Math.max(maxChildDepth, calculateJsonDepth(child));
            }
            return 1 + maxChildDepth;
        }
        return 1;
    }
    
    private int countJsonProperties(JsonNode node) {
        if (node.isObject()) {
            int count = node.size();
            for (JsonNode child : node) {
                count += countJsonProperties(child);
            }
            return count;
        } else if (node.isArray()) {
            int count = 0;
            for (JsonNode child : node) {
                count += countJsonProperties(child);
            }
            return count;
        }
        return 0;
    }
    
    private boolean isUserControlledHeader(String headerName) {
        String lower = headerName.toLowerCase();
        return lower.startsWith("x-") || 
               lower.equals("referer") || 
               lower.equals("user-agent") ||
               lower.equals("origin");
    }
    
    private int getStatusCodeForSecurityViolation(String reason) {
        if (reason == null) return 403;
        
        String lower = reason.toLowerCase();
        if (lower.contains("rate limit")) return 429;
        if (lower.contains("invalid input") || lower.contains("injection")) return 400;
        if (lower.contains("token") || lower.contains("unauthorized")) return 401;
        if (lower.contains("suspicious")) return 403;
        
        return 403; // Default to Forbidden
    }
}