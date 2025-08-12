package co.thismakesmehappy.toyapi.service.pipeline;

/**
 * Exception thrown during the decoration phase when data enrichment fails.
 */
public class DecorationException extends ServicePipelineException {
    
    public DecorationException(String message) {
        super(message);
    }
    
    public DecorationException(String message, Throwable cause) {
        super(message, cause);
    }
}