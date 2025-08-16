package co.thismakesmehappy.toyapi.service.security;

/**
 * Result of a security check operation.
 * Indicates whether the operation should be allowed or blocked.
 */
public class SecurityCheckResult {
    
    private final boolean allowed;
    private final String reason;
    private final String details;
    
    private SecurityCheckResult(boolean allowed, String reason, String details) {
        this.allowed = allowed;
        this.reason = reason;
        this.details = details;
    }
    
    /**
     * Create a result indicating the operation is allowed.
     */
    public static SecurityCheckResult allowed() {
        return new SecurityCheckResult(true, null, null);
    }
    
    /**
     * Create a result indicating the operation is allowed with additional context.
     */
    public static SecurityCheckResult allowed(String details) {
        return new SecurityCheckResult(true, null, details);
    }
    
    /**
     * Create a result indicating the operation is blocked.
     */
    public static SecurityCheckResult blocked(String reason) {
        return new SecurityCheckResult(false, reason, null);
    }
    
    /**
     * Create a result indicating the operation is blocked with additional details.
     */
    public static SecurityCheckResult blocked(String reason, String details) {
        return new SecurityCheckResult(false, reason, details);
    }
    
    /**
     * Check if the operation is allowed.
     */
    public boolean isAllowed() {
        return allowed;
    }
    
    /**
     * Check if the operation is blocked.
     */
    public boolean isBlocked() {
        return !allowed;
    }
    
    /**
     * Get the reason for blocking (null if allowed).
     */
    public String getReason() {
        return reason;
    }
    
    /**
     * Get additional details about the check result.
     */
    public String getDetails() {
        return details;
    }
    
    @Override
    public String toString() {
        if (allowed) {
            return "SecurityCheckResult{allowed=true" + 
                   (details != null ? ", details='" + details + "'" : "") + "}";
        } else {
            return "SecurityCheckResult{allowed=false, reason='" + reason + "'" +
                   (details != null ? ", details='" + details + "'" : "") + "}";
        }
    }
}