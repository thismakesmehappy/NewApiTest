package co.thismakesmehappy.toyapi.service.pipeline;

/**
 * Exception thrown during input or business validation phases.
 */
public class ValidationException extends ServicePipelineException {
    
    public ValidationException(String message) {
        super(message);
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}