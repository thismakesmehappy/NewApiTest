package co.thismakesmehappy.toyapi.service.validation;

/**
 * Severity levels for validation issues.
 */
public enum ValidationSeverity {
    ERROR,   // Blocks execution
    WARNING  // Informational, execution can continue
}