package co.thismakesmehappy.toyapi.service.validation;

/**
 * API error structure for validation responses.
 */
public class ApiError {
    private final String field;
    private final String message;
    private final String severity;
    
    public ApiError(String field, String message, String severity) {
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
    
    public String getSeverity() { 
        return severity; 
    }
}