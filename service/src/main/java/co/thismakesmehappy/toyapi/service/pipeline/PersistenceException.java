package co.thismakesmehappy.toyapi.service.pipeline;

/**
 * Exception thrown during the persistence phase when data cannot be saved.
 */
public class PersistenceException extends ServicePipelineException {
    
    public PersistenceException(String message) {
        super(message);
    }
    
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}