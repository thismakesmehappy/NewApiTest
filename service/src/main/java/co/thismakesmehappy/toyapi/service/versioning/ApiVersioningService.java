package co.thismakesmehappy.toyapi.service.versioning;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import java.util.Map;

/**
 * Service for handling API versioning across different strategies.
 * Supports header-based, URL-based, and query parameter versioning.
 */
public class ApiVersioningService {
    
    private static final String VERSION_HEADER = "Accept";
    private static final String VERSION_HEADER_ALTERNATIVE = "API-Version";
    private static final String VERSION_QUERY_PARAM = "version";
    
    /**
     * Extract API version from the request using multiple strategies.
     * Priority: Header > Query Parameter > URL Path > Default
     */
    public ApiVersion extractVersion(APIGatewayProxyRequestEvent request) {
        // Strategy 1: Header-based versioning (Accept header)
        ApiVersion headerVersion = extractFromAcceptHeader(request);
        if (headerVersion != null) {
            return headerVersion;
        }
        
        // Strategy 2: Alternative header (API-Version)
        ApiVersion altHeaderVersion = extractFromVersionHeader(request);
        if (altHeaderVersion != null) {
            return altHeaderVersion;
        }
        
        // Strategy 3: Query parameter versioning
        ApiVersion queryVersion = extractFromQueryParameter(request);
        if (queryVersion != null) {
            return queryVersion;
        }
        
        // Strategy 4: URL path versioning
        ApiVersion pathVersion = extractFromPath(request);
        if (pathVersion != null) {
            return pathVersion;
        }
        
        // Default version
        return ApiVersion.getDefault();
    }
    
    /**
     * Extract version from Accept header (e.g., "application/vnd.toyapi.v1+json").
     */
    private ApiVersion extractFromAcceptHeader(APIGatewayProxyRequestEvent request) {
        Map<String, String> headers = request.getHeaders();
        if (headers == null) return null;
        
        String acceptHeader = headers.get(VERSION_HEADER);
        if (acceptHeader == null) {
            // Try case-insensitive lookup
            acceptHeader = headers.entrySet().stream()
                .filter(entry -> VERSION_HEADER.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        }
        
        if (acceptHeader != null) {
            // Look for pattern like "application/vnd.toyapi.v1+json"
            if (acceptHeader.contains("vnd.toyapi.v")) {
                int versionStart = acceptHeader.indexOf("vnd.toyapi.v") + "vnd.toyapi.v".length();
                int versionEnd = acceptHeader.indexOf("+", versionStart);
                if (versionEnd == -1) {
                    versionEnd = acceptHeader.length();
                }
                
                if (versionStart < acceptHeader.length()) {
                    String versionStr = acceptHeader.substring(versionStart, versionEnd);
                    return ApiVersion.parse(versionStr);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract version from API-Version header (e.g., "1", "v1", "1.0").
     */
    private ApiVersion extractFromVersionHeader(APIGatewayProxyRequestEvent request) {
        Map<String, String> headers = request.getHeaders();
        if (headers == null) return null;
        
        String versionHeader = headers.get(VERSION_HEADER_ALTERNATIVE);
        if (versionHeader == null) {
            // Try case-insensitive lookup
            versionHeader = headers.entrySet().stream()
                .filter(entry -> VERSION_HEADER_ALTERNATIVE.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        }
        
        if (versionHeader != null && !versionHeader.trim().isEmpty()) {
            return ApiVersion.parse(versionHeader.trim());
        }
        
        return null;
    }
    
    /**
     * Extract version from query parameter (e.g., "?version=1" or "?version=v1.0").
     */
    private ApiVersion extractFromQueryParameter(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();
        if (queryParams == null) return null;
        
        String versionParam = queryParams.get(VERSION_QUERY_PARAM);
        if (versionParam != null && !versionParam.trim().isEmpty()) {
            return ApiVersion.parse(versionParam.trim());
        }
        
        return null;
    }
    
    /**
     * Extract version from URL path (e.g., "/v1/items" or "/api/v2/users").
     */
    private ApiVersion extractFromPath(APIGatewayProxyRequestEvent request) {
        String path = request.getPath();
        if (path == null) return null;
        
        // Look for version pattern in path segments
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.toLowerCase().startsWith("v") && segment.length() > 1) {
                String versionPart = segment.substring(1);
                try {
                    return ApiVersion.parse(versionPart);
                } catch (Exception e) {
                    // Continue searching other segments
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if the requested version is supported.
     */
    public boolean isVersionSupported(ApiVersion version) {
        // Currently we only support v1.x.x
        return version.getMajor() == 1;
    }
    
    /**
     * Get the appropriate content type for the response based on version.
     */
    public String getResponseContentType(ApiVersion version) {
        return "application/vnd.toyapi." + version.getMajorVersionString() + "+json";
    }
    
    /**
     * Create version-specific response headers.
     */
    public Map<String, String> createVersionHeaders(ApiVersion version) {
        return Map.of(
            "API-Version", version.getVersionString(),
            "Content-Type", getResponseContentType(version),
            "X-API-Version-Deprecated", version.isOlderThan(ApiVersion.getLatest()) ? "true" : "false"
        );
    }
    
    /**
     * Handle version compatibility and migration warnings.
     */
    public String getVersionWarning(ApiVersion requestedVersion) {
        if (!isVersionSupported(requestedVersion)) {
            return "API version " + requestedVersion + " is not supported. Using default version " + ApiVersion.getDefault();
        }
        
        if (requestedVersion.isOlderThan(ApiVersion.getLatest())) {
            return "API version " + requestedVersion + " is deprecated. Consider upgrading to " + ApiVersion.getLatest();
        }
        
        return null;
    }
}