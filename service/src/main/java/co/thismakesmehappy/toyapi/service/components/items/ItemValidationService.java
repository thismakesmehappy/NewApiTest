package co.thismakesmehappy.toyapi.service.components.items;

import co.thismakesmehappy.toyapi.service.pipeline.ValidationException;
import co.thismakesmehappy.toyapi.service.validation.ValidationResult;

/**
 * Specialized service for item validation logic.
 * Separates validation concerns from pipeline orchestration.
 */
public class ItemValidationService {
    
    /**
     * Simple validation that throws on first error (backward compatibility).
     */
    public void validateCreateItemRequest(String message, String userId) throws ValidationException {
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException("Message is required and cannot be empty");
        }
        if (message.length() > 1000) {
            throw new ValidationException("Message cannot exceed 1000 characters");
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
            if (message.length() > 1000) {
                result.addError("message", "Message cannot exceed 1000 characters");
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
     * Simple validation for GET requests.
     */
    public void validateGetItemsRequest(String userId, Integer limit, String sortOrder) throws ValidationException {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
        
        if (limit != null && (limit < 1 || limit > 100)) {
            throw new ValidationException("Limit must be between 1 and 100");
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
            if (limit < 1 || limit > 100) {
                result.addError("limit", "Limit must be between 1 and 100");
            } else if (limit > 50) {
                result.addWarning("limit", "Large limit may impact performance");
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