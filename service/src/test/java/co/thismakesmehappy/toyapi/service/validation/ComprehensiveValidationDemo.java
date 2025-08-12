package co.thismakesmehappy.toyapi.service.validation;

import co.thismakesmehappy.toyapi.service.components.items.ItemValidationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstration test showing comprehensive validation in action.
 * Shows the before/after improvement in user experience.
 */
class ComprehensiveValidationDemo {
    
    @Test
    void demonstrateComprehensiveValidationBenefits() {
        ItemValidationService validationService = new ItemValidationService();
        
        // Test with multiple validation issues
        String emptyMessage = "";
        String invalidUserId = "user@#$%invalid";
        
        // Comprehensive validation collects ALL errors
        ValidationResult result = validationService.validateCreateItemRequestComprehensive(
            emptyMessage, 
            invalidUserId
        );
        
        // Should be invalid with multiple errors
        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().size()); // message empty, userId invalid chars
        
        // Verify specific errors are captured
        String allErrors = result.getAllErrorsAsString();
        assertTrue(allErrors.contains("Message is required"));
        assertTrue(allErrors.contains("User ID can only contain"));
        
        // Test warning functionality  
        ValidationResult warningResult = validationService.validateCreateItemRequestComprehensive(
            "urgent message", 
            "valid-user"
        );
        
        // Should be valid but have warnings
        assertTrue(warningResult.isValid());
        assertTrue(warningResult.hasWarnings());
        assertTrue(warningResult.getAllWarningsAsString().contains("urgent"));
    }
    
    @Test
    void demonstrateApiErrorStructure() {
        ItemValidationService validationService = new ItemValidationService();
        
        ValidationResult result = validationService.validateCreateItemRequestComprehensive("", "");
        
        // Convert to API response format
        ValidationResponse apiResponse = result.toApiResponse();
        
        assertFalse(apiResponse.isValid());
        assertEquals(2, apiResponse.getErrors().size());
        
        // Check field-specific errors
        ApiError messageError = apiResponse.getErrors().stream()
            .filter(error -> "message".equals(error.getField()))
            .findFirst()
            .orElseThrow();
        
        assertEquals("message", messageError.getField());
        assertEquals("error", messageError.getSeverity());
        assertTrue(messageError.getMessage().contains("required"));
    }
}