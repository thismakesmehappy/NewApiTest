package co.thismakesmehappy.toyapi.service.security;

import co.thismakesmehappy.toyapi.service.utils.FeatureFlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Security service providing comprehensive security features for the API.
 * Implements rate limiting, input validation, threat detection, and security monitoring.
 * Designed to be free-tier compatible with minimal AWS resource usage.
 */
public class SecurityService {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);
    
    private final FeatureFlagService featureFlagService;
    
    // Rate limiting storage (in-memory for free tier compatibility)
    private final Map<String, RateLimitTracker> rateLimitTracker = new ConcurrentHashMap<>();
    
    // Security event tracking
    private final Map<String, SecurityEvent> securityEvents = new ConcurrentHashMap<>();
    
    // Input validation patterns
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|vbscript|onload|onerror).*"
    );
    
    private static final Pattern XSS_PATTERN = Pattern.compile(
        "(?i).*(<script|javascript:|vbscript:|onload=|onerror=|alert\\(|confirm\\(|prompt\\().*"
    );
    
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile(
        "(?i).*(\\||&|;|`|\\$\\(|\\$\\{|<\\(|>\\)).*"
    );
    
    // Rate limiting configurations
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 100;
    private static final int STRICT_RATE_LIMIT_PER_MINUTE = 20;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    
    // Security thresholds
    private static final int SUSPICIOUS_ACTIVITY_THRESHOLD = 5;
    private static final Duration SECURITY_EVENT_WINDOW = Duration.ofMinutes(10);
    
    public SecurityService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }
    
    /**
     * Check if a request is allowed based on rate limiting.
     */
    public SecurityCheckResult checkRateLimit(String clientIdentifier, String endpoint) {
        if (!isSecurityEnabled()) {
            return SecurityCheckResult.allowed();
        }
        
        String key = clientIdentifier + ":" + endpoint;
        RateLimitTracker tracker = rateLimitTracker.computeIfAbsent(key, k -> new RateLimitTracker());
        
        Instant now = Instant.now();
        tracker.cleanupOldRequests(now);
        
        int limit = isStrictEndpoint(endpoint) ? STRICT_RATE_LIMIT_PER_MINUTE : DEFAULT_RATE_LIMIT_PER_MINUTE;
        
        if (tracker.getRequestCount() >= limit) {
            logSecurityEvent(clientIdentifier, "RATE_LIMIT_EXCEEDED", 
                "Rate limit exceeded for endpoint: " + endpoint + " (limit: " + limit + ")");
            return SecurityCheckResult.blocked("Rate limit exceeded");
        }
        
        tracker.addRequest(now);
        return SecurityCheckResult.allowed();
    }
    
    /**
     * Validate input for common security threats.
     */
    public SecurityCheckResult validateInput(String input, String context) {
        if (!isSecurityEnabled() || input == null) {
            return SecurityCheckResult.allowed();
        }
        
        // Check for SQL injection
        if (SQL_INJECTION_PATTERN.matcher(input).matches()) {
            logSecurityEvent("unknown", "SQL_INJECTION_ATTEMPT", 
                "Potential SQL injection detected in " + context + ": " + sanitizeLogMessage(input));
            return SecurityCheckResult.blocked("Invalid input detected");
        }
        
        // Check for XSS
        if (XSS_PATTERN.matcher(input).matches()) {
            logSecurityEvent("unknown", "XSS_ATTEMPT", 
                "Potential XSS detected in " + context + ": " + sanitizeLogMessage(input));
            return SecurityCheckResult.blocked("Invalid input detected");
        }
        
        // Check for command injection
        if (COMMAND_INJECTION_PATTERN.matcher(input).matches()) {
            logSecurityEvent("unknown", "COMMAND_INJECTION_ATTEMPT", 
                "Potential command injection detected in " + context + ": " + sanitizeLogMessage(input));
            return SecurityCheckResult.blocked("Invalid input detected");
        }
        
        // Check input length (prevent DoS through large payloads)
        if (input.length() > 10000) {
            logSecurityEvent("unknown", "LARGE_PAYLOAD_ATTEMPT", 
                "Excessively large input detected in " + context + " (length: " + input.length() + ")");
            return SecurityCheckResult.blocked("Input too large");
        }
        
        return SecurityCheckResult.allowed();
    }
    
    /**
     * Check for suspicious activity patterns.
     */
    public SecurityCheckResult checkSuspiciousActivity(String clientIdentifier, String activityType) {
        if (!isSecurityEnabled()) {
            return SecurityCheckResult.allowed();
        }
        
        String key = clientIdentifier + ":" + activityType;
        SecurityEvent event = securityEvents.get(key);
        
        if (event != null) {
            Instant cutoff = Instant.now().minus(SECURITY_EVENT_WINDOW);
            if (event.getLastOccurrence().isAfter(cutoff)) {
                if (event.getCount() >= SUSPICIOUS_ACTIVITY_THRESHOLD) {
                    logSecurityEvent(clientIdentifier, "SUSPICIOUS_ACTIVITY_BLOCKED", 
                        "Client blocked due to suspicious activity: " + activityType + 
                        " (count: " + event.getCount() + ")");
                    return SecurityCheckResult.blocked("Suspicious activity detected");
                }
            } else {
                // Reset counter if outside window
                securityEvents.remove(key);
            }
        }
        
        return SecurityCheckResult.allowed();
    }
    
    /**
     * Log a security event for monitoring and alerting.
     */
    public void logSecurityEvent(String clientIdentifier, String eventType, String details) {
        // Always log security events regardless of feature flag status
        logger.warn("SECURITY_EVENT: client={}, type={}, details={}", 
            clientIdentifier, eventType, details);
        
        // Track security events for pattern detection
        String key = clientIdentifier + ":" + eventType;
        SecurityEvent event = securityEvents.computeIfAbsent(key, k -> new SecurityEvent());
        event.increment();
        
        // Clean up old events to prevent memory issues
        cleanupOldSecurityEvents();
    }
    
    /**
     * Validate JWT token format and basic structure.
     */
    public SecurityCheckResult validateJwtToken(String token) {
        if (!isSecurityEnabled() || token == null) {
            return SecurityCheckResult.allowed();
        }
        
        // Basic JWT format validation (3 parts separated by dots)
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            logSecurityEvent("unknown", "INVALID_JWT_FORMAT", 
                "Invalid JWT token format (parts: " + parts.length + ")");
            return SecurityCheckResult.blocked("Invalid token format");
        }
        
        // Check for suspiciously long tokens (potential attack)
        if (token.length() > 2048) {
            logSecurityEvent("unknown", "OVERSIZED_JWT_TOKEN", 
                "Suspiciously large JWT token (length: " + token.length() + ")");
            return SecurityCheckResult.blocked("Token too large");
        }
        
        return SecurityCheckResult.allowed();
    }
    
    /**
     * Generate security headers for responses.
     */
    public Map<String, String> getSecurityHeaders() {
        Map<String, String> headers = new ConcurrentHashMap<>();
        
        if (isSecurityEnabled()) {
            headers.put("X-Content-Type-Options", "nosniff");
            headers.put("X-Frame-Options", "DENY");
            headers.put("X-XSS-Protection", "1; mode=block");
            headers.put("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            headers.put("Content-Security-Policy", "default-src 'self'");
            headers.put("Referrer-Policy", "strict-origin-when-cross-origin");
        }
        
        return headers;
    }
    
    /**
     * Clean up old entries to prevent memory leaks.
     */
    public void performMaintenance() {
        cleanupOldRateLimitEntries();
        cleanupOldSecurityEvents();
    }
    
    private boolean isSecurityEnabled() {
        return featureFlagService != null && featureFlagService.isSecurityAlarmsEnabled();
    }
    
    private boolean isStrictEndpoint(String endpoint) {
        return endpoint.contains("/auth/") || endpoint.contains("/admin/");
    }
    
    private String sanitizeLogMessage(String message) {
        if (message == null) return "null";
        // Remove potentially dangerous characters and limit length for logging
        return message.replaceAll("[<>\"'&]", "_").substring(0, Math.min(message.length(), 100));
    }
    
    private void cleanupOldRateLimitEntries() {
        Instant cutoff = Instant.now().minus(RATE_LIMIT_WINDOW);
        rateLimitTracker.entrySet().removeIf(entry -> {
            entry.getValue().cleanupOldRequests(cutoff);
            return entry.getValue().getRequestCount() == 0;
        });
    }
    
    private void cleanupOldSecurityEvents() {
        Instant cutoff = Instant.now().minus(SECURITY_EVENT_WINDOW);
        securityEvents.entrySet().removeIf(entry -> 
            entry.getValue().getLastOccurrence().isBefore(cutoff));
    }
    
    /**
     * Rate limit tracker for individual clients/endpoints.
     */
    private static class RateLimitTracker {
        private final Map<Instant, AtomicInteger> requests = new ConcurrentHashMap<>();
        
        public void addRequest(Instant timestamp) {
            // Round to the nearest minute for rate limiting window
            Instant minute = timestamp.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            requests.computeIfAbsent(minute, k -> new AtomicInteger(0)).incrementAndGet();
        }
        
        public int getRequestCount() {
            return requests.values().stream().mapToInt(AtomicInteger::get).sum();
        }
        
        public void cleanupOldRequests(Instant cutoff) {
            requests.entrySet().removeIf(entry -> entry.getKey().isBefore(cutoff));
        }
    }
    
    /**
     * Security event tracker.
     */
    private static class SecurityEvent {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile Instant lastOccurrence = Instant.now();
        
        public void increment() {
            count.incrementAndGet();
            lastOccurrence = Instant.now();
        }
        
        public int getCount() {
            return count.get();
        }
        
        public Instant getLastOccurrence() {
            return lastOccurrence;
        }
    }
}