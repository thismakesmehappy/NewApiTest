package co.thismakesmehappy.toyapi.service.validation;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Enhanced ValidationResult that supports field-specific errors and warnings.
 * Enables comprehensive validation that collects ALL errors before failing.
 */
public class ValidationResult {
    private final List<ValidationError> errors = new ArrayList<>();
    private final List<ValidationError> warnings = new ArrayList<>();
    
    public void addError(String field, String message) {
        errors.add(new ValidationError(field, message, ValidationSeverity.ERROR));
    }
    
    public void addError(String message) {
        errors.add(new ValidationError(null, message, ValidationSeverity.ERROR));
    }
    
    public void addWarning(String field, String message) {
        warnings.add(new ValidationError(field, message, ValidationSeverity.WARNING));
    }
    
    public void addWarning(String message) {
        warnings.add(new ValidationError(null, message, ValidationSeverity.WARNING));
    }
    
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public List<ValidationError> getErrors() {
        return new ArrayList<>(errors);
    }
    
    public List<ValidationError> getWarnings() {
        return new ArrayList<>(warnings);
    }
    
    public String getAllErrorsAsString() {
        return errors.stream()
                .map(ValidationError::toString)
                .collect(Collectors.joining("; "));
    }
    
    public String getAllWarningsAsString() {
        return warnings.stream()
                .map(ValidationError::toString)
                .collect(Collectors.joining("; "));
    }
    
    /**
     * Returns structured validation response suitable for API responses.
     */
    public ValidationResponse toApiResponse() {
        return new ValidationResponse(
            isValid(),
            errors.stream().map(ValidationError::toApiError).collect(Collectors.toList()),
            warnings.stream().map(ValidationError::toApiError).collect(Collectors.toList())
        );
    }
    
    /**
     * Merge another ValidationResult into this one.
     */
    public void merge(ValidationResult other) {
        this.errors.addAll(other.errors);
        this.warnings.addAll(other.warnings);
    }
}