package co.thismakesmehappy.toyapi.service.validation;

/**
 * Represents a validation error with field context and severity.
 */
public class ValidationError {
    private final String field;
    private final String message;
    private final ValidationSeverity severity;
    
    public ValidationError(String field, String message, ValidationSeverity severity) {
        this.field = field;
        this.message = message;
        this.severity = severity;
    }
    
    public String getField() { 
        return field; 
    }
    
    public String getMessage() { 
        return message; 
    }
    
    public ValidationSeverity getSeverity() { 
        return severity; 
    }
    
    @Override
    public String toString() {
        return field != null ? field + ": " + message : message;
    }
    
    public ApiError toApiError() {
        return new ApiError(field, message, severity.toString().toLowerCase());
    }
}