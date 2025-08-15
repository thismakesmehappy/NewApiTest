package co.thismakesmehappy.toyapi.service.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for performance optimization and monitoring.
 * Provides caching, performance metrics, and optimization utilities.
 */
public class PerformanceOptimizationService {
    
    private final FeatureFlagService featureFlagService;
    private final Map<String, CacheEntry> responseCache = new ConcurrentHashMap<>();
    private final Map<String, PerformanceMetric> performanceMetrics = new ConcurrentHashMap<>();
    
    public PerformanceOptimizationService(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }
    
    /**
     * Cache a response for the specified duration.
     * Only caches if performance optimization is enabled.
     */
    public void cacheResponse(String key, Object response, Duration ttl) {
        if (!featureFlagService.isPerformanceOptimizationEnabled()) {
            return;
        }
        
        responseCache.put(key, new CacheEntry(response, Instant.now().plus(ttl)));
        
        // Clean up expired entries periodically
        cleanupExpiredEntries();
    }
    
    /**
     * Retrieve a cached response if it exists and hasn't expired.
     */
    @SuppressWarnings("unchecked")
    public <T> T getCachedResponse(String key, Class<T> type) {
        if (!featureFlagService.isPerformanceOptimizationEnabled()) {
            return null;
        }
        
        CacheEntry entry = responseCache.get(key);
        if (entry != null && entry.isValid()) {
            try {
                return type.cast(entry.response);
            } catch (ClassCastException e) {
                // Remove invalid cache entry
                responseCache.remove(key);
                return null;
            }
        }
        
        // Remove expired entry
        if (entry != null) {
            responseCache.remove(key);
        }
        
        return null;
    }
    
    /**
     * Record performance metrics for monitoring.
     */
    public void recordPerformanceMetric(String operation, Duration duration, boolean success) {
        if (!featureFlagService.isPerformanceOptimizationEnabled()) {
            return;
        }
        
        performanceMetrics.compute(operation, (key, existing) -> {
            if (existing == null) {
                return new PerformanceMetric(operation, duration, success);
            } else {
                existing.addMeasurement(duration, success);
                return existing;
            }
        });
    }
    
    /**
     * Get performance metrics for a specific operation.
     */
    public PerformanceMetric getPerformanceMetrics(String operation) {
        return performanceMetrics.get(operation);
    }
    
    /**
     * Get all performance metrics.
     */
    public Map<String, PerformanceMetric> getAllPerformanceMetrics() {
        return Map.copyOf(performanceMetrics);
    }
    
    /**
     * Clear all caches and metrics (useful for testing).
     */
    public void clearAll() {
        responseCache.clear();
        performanceMetrics.clear();
    }
    
    /**
     * Generate a cache key for DynamoDB queries.
     */
    public String generateCacheKey(String tableName, String operation, Object... params) {
        StringBuilder key = new StringBuilder()
                .append(tableName)
                .append(":")
                .append(operation);
        
        for (Object param : params) {
            key.append(":").append(param != null ? param.toString() : "null");
        }
        
        return key.toString();
    }
    
    /**
     * Clean up expired cache entries.
     */
    private void cleanupExpiredEntries() {
        Instant now = Instant.now();
        responseCache.entrySet().removeIf(entry -> !entry.getValue().isValid(now));
    }
    
    /**
     * Cache entry with expiration.
     */
    private static class CacheEntry {
        private final Object response;
        private final Instant expiresAt;
        
        public CacheEntry(Object response, Instant expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
        
        public boolean isValid() {
            return isValid(Instant.now());
        }
        
        public boolean isValid(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
    
    /**
     * Performance metrics container.
     */
    public static class PerformanceMetric {
        private final String operation;
        private long totalCalls = 0;
        private long successfulCalls = 0;
        private Duration totalDuration = Duration.ZERO;
        private Duration minDuration = Duration.ofMillis(Long.MAX_VALUE);
        private Duration maxDuration = Duration.ZERO;
        
        public PerformanceMetric(String operation, Duration duration, boolean success) {
            this.operation = operation;
            addMeasurement(duration, success);
        }
        
        public void addMeasurement(Duration duration, boolean success) {
            totalCalls++;
            totalDuration = totalDuration.plus(duration);
            
            if (success) {
                successfulCalls++;
            }
            
            if (duration.compareTo(minDuration) < 0) {
                minDuration = duration;
            }
            if (duration.compareTo(maxDuration) > 0) {
                maxDuration = duration;
            }
        }
        
        public String getOperation() { return operation; }
        public long getTotalCalls() { return totalCalls; }
        public long getSuccessfulCalls() { return successfulCalls; }
        public double getSuccessRate() { return totalCalls > 0 ? (double) successfulCalls / totalCalls : 0.0; }
        public Duration getAverageDuration() { 
            return totalCalls > 0 ? totalDuration.dividedBy(totalCalls) : Duration.ZERO; 
        }
        public Duration getMinDuration() { return minDuration.equals(Duration.ofMillis(Long.MAX_VALUE)) ? Duration.ZERO : minDuration; }
        public Duration getMaxDuration() { return maxDuration; }
        
        @Override
        public String toString() {
            return String.format(
                "PerformanceMetric{operation='%s', calls=%d, success=%.2f%%, avg=%dms, min=%dms, max=%dms}",
                operation, totalCalls, getSuccessRate() * 100, 
                getAverageDuration().toMillis(), getMinDuration().toMillis(), getMaxDuration().toMillis()
            );
        }
    }
}