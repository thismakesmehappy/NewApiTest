package co.thismakesmehappy.toyapi.service.components.items;

import co.thismakesmehappy.toyapi.service.pipeline.ValidationException;
import co.thismakesmehappy.toyapi.service.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Specialized service for item business rules validation.
 * Complex validation logic that requires enriched context data.
 */
public class ItemBusinessRulesService {
    
    private static final Logger logger = LoggerFactory.getLogger(ItemBusinessRulesService.class);
    
    /**
     * Simple business validation for item creation (backward compatibility).
     */
    public void validateItemCreation(boolean userExists, int userItemCount, String message, String userId) 
            throws ValidationException {
        
        if (!userExists) {
            throw new ValidationException("User does not exist: " + userId);
        }
        
        if (userItemCount >= 100) {
            throw new ValidationException("User has reached maximum item limit (100)");
        }
        
        if (message.toLowerCase().contains("spam")) {
            throw new ValidationException("Message contains prohibited content");
        }
    }
    
    /**
     * Comprehensive business validation for item creation.
     */
    public ValidationResult validateItemCreationComprehensive(
            boolean userExists, 
            int userItemCount, 
            String message, 
            String userId,
            ItemDecorationService.UserPreferences userPreferences) {
        
        ValidationResult result = new ValidationResult();
        
        // User existence check - fail fast since other validations depend on it
        if (!userExists) {
            result.addError("business.user", "User does not exist: " + userId);
            return result; // Fail fast - other checks need valid user
        }
        
        // User limits
        if (userItemCount >= 100) {
            result.addError("business.limit", "User has reached maximum item limit (100)");
        } else if (userItemCount >= 90) {
            result.addWarning("business.limit", "User is approaching item limit (" + userItemCount + "/100)");
        }
        
        // Content validation
        if (message.toLowerCase().contains("spam")) {
            result.addError("business.content", "Message contains prohibited content");
        }
        
        // Advanced content rules
        if (message.length() < 10 && userItemCount > 50) {
            result.addWarning("business.content", "Short messages from high-volume users may be flagged");
        }
        
        // Time-based business rules
        if (isOutsideBusinessHours() && message.toLowerCase().contains("urgent")) {
            result.addWarning("business.schedule", "Urgent items created outside business hours");
        }
        
        // User preference-based rules
        if (userPreferences != null) {
            if (!userPreferences.isNotificationsEnabled() && message.toLowerCase().contains("notify")) {
                result.addWarning("business.notifications", 
                    "User has notifications disabled but message requests notification");
            }
        }
        
        logger.debug("Business validation complete for user: {} - {} errors, {} warnings", 
                    userId, result.getErrors().size(), result.getWarnings().size());
        
        return result;
    }
    
    /**
     * Simple business validation for item retrieval.
     */
    public void validateItemRetrieval(boolean userExists, boolean hasPermission, String userId) 
            throws ValidationException {
        
        if (!userExists) {
            throw new ValidationException("User does not exist: " + userId);
        }
        
        if (!hasPermission) {
            throw new ValidationException("User does not have permission to access items");
        }
    }
    
    /**
     * Comprehensive business validation for item retrieval with filtering.
     */
    public ValidationResult validateItemRetrievalComprehensive(
            boolean userExists, 
            boolean hasPermission, 
            String userId,
            List<Map<String, Object>> items) {
        
        ValidationResult result = new ValidationResult();
        
        // User existence and permission checks
        if (!userExists) {
            result.addError("business.user", "User does not exist: " + userId);
            return result;
        }
        
        if (!hasPermission) {
            result.addError("business.permission", "User does not have permission to access items");
            return result;
        }
        
        // Data quality checks
        if (items != null) {
            long archivedCount = items.stream()
                    .mapToLong(item -> Boolean.TRUE.equals(item.get("archived")) ? 1 : 0)
                    .sum();
            
            if (archivedCount > 0) {
                result.addWarning("business.data", archivedCount + " archived items will be hidden from results");
            }
            
            // Check for potentially problematic items
            long oldItemCount = items.stream()
                    .mapToLong(item -> "old".equals(item.get("itemAge")) ? 1 : 0)
                    .sum();
            
            if (oldItemCount > items.size() * 0.8) {
                result.addWarning("business.data", "Most items are old - consider archiving or cleanup");
            }
        }
        
        return result;
    }
    
    /**
     * Filter items based on business rules.
     */
    public List<Map<String, Object>> filterItemsForUser(List<Map<String, Object>> items, String userId) {
        if (items == null) {
            return List.of();
        }
        
        return items.stream()
                .filter(item -> isItemVisibleToUser(item, userId))
                .collect(Collectors.toList());
    }
    
    private boolean isItemVisibleToUser(Map<String, Object> item, String userId) {
        // Business rule: Hide archived items
        if (Boolean.TRUE.equals(item.get("archived"))) {
            return false;
        }
        
        // Business rule: Users can only see their own items
        if (!userId.equals(item.get("userId"))) {
            return false;
        }
        
        // Business rule: Hide items marked as deleted
        if (Boolean.TRUE.equals(item.get("deleted"))) {
            return false;
        }
        
        return true;
    }
    
    private boolean isOutsideBusinessHours() {
        LocalTime now = LocalTime.now();
        LocalTime businessStart = LocalTime.of(9, 0);
        LocalTime businessEnd = LocalTime.of(17, 0);
        
        return now.isBefore(businessStart) || now.isAfter(businessEnd);
    }
}