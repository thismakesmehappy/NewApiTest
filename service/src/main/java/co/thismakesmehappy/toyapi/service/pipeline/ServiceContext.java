package co.thismakesmehappy.toyapi.service.pipeline;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Base context that flows through the service pipeline.
 * Contains the original request and accumulated state from each pipeline phase.
 */
public abstract class ServiceContext {
    
    private final Instant requestTime;
    private final String requestId;
    private final Map<String, Object> metadata;
    
    protected ServiceContext() {
        this.requestTime = Instant.now();
        this.requestId = generateRequestId();
        this.metadata = new HashMap<>();
    }
    
    /**
     * Get the timestamp when this request was created.
     */
    public Instant getRequestTime() {
        return requestTime;
    }
    
    /**
     * Get the unique request ID for tracing.
     */
    public String getRequestId() {
        return requestId;
    }
    
    /**
     * Get metadata accumulated during pipeline execution.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Add metadata to the context.
     */
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }
    
    /**
     * Get metadata value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> type) {
        return (T) metadata.get(key);
    }
    
    /**
     * Generate a unique request ID for tracing.
     */
    private String generateRequestId() {
        return "req-" + System.currentTimeMillis() + "-" + System.nanoTime();
    }
}