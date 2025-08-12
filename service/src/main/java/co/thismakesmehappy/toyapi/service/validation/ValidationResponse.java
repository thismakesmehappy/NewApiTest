package co.thismakesmehappy.toyapi.service.validation;

import java.util.List;

/**
 * API response structure for validation results.
 */
public class ValidationResponse {
    private final boolean valid;
    private final List<ApiError> errors;
    private final List<ApiError> warnings;
    
    public ValidationResponse(boolean valid, List<ApiError> errors, List<ApiError> warnings) {
        this.valid = valid;
        this.errors = errors;
        this.warnings = warnings;
    }
    
    public boolean isValid() { 
        return valid; 
    }
    
    public List<ApiError> getErrors() { 
        return errors; 
    }
    
    public List<ApiError> getWarnings() { 
        return warnings; 
    }
}