package co.thismakesmehappy.toyapi.service.components.items;

import co.thismakesmehappy.toyapi.service.pipeline.ValidationException;
import co.thismakesmehappy.toyapi.service.validation.ValidationResult;
import co.thismakesmehappy.toyapi.service.utils.FeatureFlagService;

/**
 * Specialized service for item validation logic.
 * Separates validation concerns from pipeline orchestration.
 * Uses feature flags to control validation behavior.
 */
public class ItemValidationService {
    
    private final FeatureFlagService featureFlags;
    
    /**
     * Constructor with feature flag support.
     * 
     * @param featureFlags Feature flag service for configuration
     */
    public ItemValidationService(FeatureFlagService featureFlags) {
        this.featureFlags = featureFlags;
    }
    
    /**
     * Default constructor - uses default feature flag behavior.
     * For backward compatibility with existing code.
     */
    public ItemValidationService() {
        this.featureFlags = null; // Will use default behavior
    }
    
    /**
     * Simple validation that throws on first error (backward compatibility).
     * Can be controlled by comprehensive-validation feature flag.
     */
    public void validateCreateItemRequest(String message, String userId) throws ValidationException {
        // Feature flag: Use comprehensive validation if enabled
        if (featureFlags != null && featureFlags.isComprehensiveValidationEnabled()) {
            ValidationResult result = validateCreateItemRequestComprehensive(message, userId);
            if (!result.isValid()) {
                throw new ValidationException(result.getAllErrorsAsString());
            }
            return;
        }
        
        // Default behavior - fail fast on first error
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException("Message is required and cannot be empty");
        }
        if (message.length() > getMaxMessageLength()) {
            throw new ValidationException("Message cannot exceed " + getMaxMessageLength() + " characters");
        }
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
    }
    
    /**
     * Comprehensive validation that collects ALL errors.
     */
    public ValidationResult validateCreateItemRequestComprehensive(String message, String userId) {
        ValidationResult result = new ValidationResult();
        
        // Message validation
        if (message == null || message.trim().isEmpty()) {
            result.addError("message", "Message is required and cannot be empty");
        } else {
            int maxLength = getMaxMessageLength();
            if (message.length() > maxLength) {
                result.addError("message", "Message cannot exceed " + maxLength + " characters");
            }
            if (message.trim().length() < 3) {
                result.addError("message", "Message must be at least 3 characters long");
            }
            // Content warnings
            if (message.toLowerCase().contains("urgent")) {
                result.addWarning("message", "Message marked as urgent - consider priority setting");
            }
        }
        
        // User ID validation
        if (userId == null || userId.trim().isEmpty()) {
            result.addError("userId", "User ID is required");
        } else {
            if (userId.length() > 50) {
                result.addError("userId", "User ID cannot exceed 50 characters");
            }
            if (!userId.matches("^[a-zA-Z0-9-_]+$")) {
                result.addError("userId", "User ID can only contain letters, numbers, hyphens, and underscores");
            }
        }
        
        return result;
    }
    
    /**
     * Get maximum message length from feature flags.
     * 
     * @return Maximum message length (default: 1000)
     */
    private int getMaxMessageLength() {
        if (featureFlags != null) {
            return featureFlags.getConfigValueAsInt("max-message-length", 1000);
        }
        return 1000; // Default
    }
    
    /**
     * Get maximum items per page from feature flags.
     * 
     * @return Maximum items per page (default: 100)
     */
    private int getMaxItemsPerPage() {
        if (featureFlags != null) {
            return featureFlags.getMaxItemsPerPage();
        }
        return 100; // Default
    }
    
    /**
     * Simple validation for GET requests.
     */
    public void validateGetItemsRequest(String userId, Integer limit, String sortOrder) throws ValidationException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
        
        int maxLimit = getMaxItemsPerPage();
        if (limit != null && (limit < 1 || limit > maxLimit)) {
            throw new ValidationException("Limit must be between 1 and " + maxLimit);
        }
        
        if (sortOrder != null && 
            !sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            throw new ValidationException("Sort order must be 'asc' or 'desc'");
        }
    }
    
    /**
     * Comprehensive validation for GET requests.
     */
    public ValidationResult validateGetItemsRequestComprehensive(String userId, Integer limit, String sortOrder) {
        ValidationResult result = new ValidationResult();
        
        // User ID validation
        if (userId == null || userId.trim().isEmpty()) {
            result.addError("userId", "User ID is required");
        } else if (userId.length() > 50) {
            result.addError("userId", "User ID cannot exceed 50 characters");
        }
        
        // Limit validation
        if (limit != null) {
            int maxLimit = getMaxItemsPerPage();
            if (limit < 1 || limit > maxLimit) {
                result.addError("limit", "Limit must be between 1 and " + maxLimit);
            } else if (limit > maxLimit / 2) {
                result.addWarning("limit", "Large limit values may impact performance - consider pagination");
            }
        }
        
        // Sort order validation
        if (sortOrder != null && 
            !sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            result.addError("sortOrder", "Sort order must be 'asc' or 'desc'");
        }
        
        return result;
    }
}