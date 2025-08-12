package co.thismakesmehappy.toyapi.service.pipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Base exception for service pipeline failures with context support.
 */
public class ServicePipelineException extends Exception {
    
    private final Map<String, Object> context = new HashMap<>();
    
    public ServicePipelineException(String message) {
        super(message);
    }
    
    public ServicePipelineException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Add context information to the exception.
     */
    public void addContext(String key, Object value) {
        context.put(key, value);
    }
    
    /**
     * Add context information only if the key doesn't already exist.
     */
    public void addContextIfMissing(String key, Object value) {
        if (!context.containsKey(key) && value != null) {
            context.put(key, value);
        }
    }
    
    /**
     * Get context information.
     */
    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }
    
    /**
     * Get specific context value.
     */
    public Object getContext(String key) {
        return context.get(key);
    }
    
    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (context.isEmpty()) {
            return baseMessage;
        }
        return baseMessage + " [Context: " + context + "]";
    }
}